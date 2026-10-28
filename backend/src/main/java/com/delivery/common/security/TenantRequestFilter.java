package com.delivery.common.security;

import com.delivery.auth.service.CustomerProfileProvisioningService;
import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.dto.ErrorResponse;
import com.delivery.common.service.AuditLogService;
import com.delivery.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after authentication and applies ADR-0001 per request (SEC-06, AS-03):
 * <ul>
 *   <li>platform admin + X-Tenant-Id: the tenant must exist (404 otherwise, 400 if malformed) and every such
 *       request is written to audit_logs as TENANT_IMPERSONATION;</li>
 *   <li>anyone else + X-Tenant-Id: the header is ignored (TenantContext never reads it for them);</li>
 *   <li>customer principal with a resolved tenant: the AppUserProfile is provisioned if missing, so cart,
 *       checkout and orders find it.</li>
 * </ul>
 * Registered by {@link TenancyWebConfig}, not component-scanned, so MVC slice tests stay unaffected.
 */
public class TenantRequestFilter extends OncePerRequestFilter {
    public static final String IMPERSONATION_AUDIT_ACTION = "TENANT_IMPERSONATION";
    private static final Logger log = LoggerFactory.getLogger(TenantRequestFilter.class);
    private static final int BUSINESS_REFERENCE_MAX = 120;

    private final TenantContext tenantContext;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;
    private final CustomerProfileProvisioningService profileProvisioningService;
    private final ObjectMapper objectMapper;

    public TenantRequestFilter(TenantContext tenantContext, TenantRepository tenantRepository, AuditLogService auditLogService,
                               CustomerProfileProvisioningService profileProvisioningService, ObjectMapper objectMapper) {
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
        this.profileProvisioningService = profileProvisioningService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> header = tenantContext.requestedTenantHeader();
        if (header.isPresent()) {
            if (tenantContext.isPlatformAdmin()) {
                if (!authoriseImpersonation(header.get(), request, response)) {
                    return;
                }
            } else {
                log.debug("Ignoring {} header for a principal that is not a platform admin", TenantContext.TENANT_ID_HEADER);
            }
        }
        if (tenantContext.isCustomerPrincipal()) {
            tenantContext.currentTenantId().ifPresent(tenantId -> profileProvisioningService.ensureCustomerProfile(
                    tenantId, jwt.getSubject(), jwt.getClaimAsString("email"), displayName(jwt)));
        }
        chain.doFilter(request, response);
    }

    /** Validates the impersonated tenant and audits the use; false when an error response was written. */
    private boolean authoriseImpersonation(String rawTenantId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        UUID tenantId;
        try {
            tenantId = UUID.fromString(rawTenantId);
        } catch (IllegalArgumentException malformed) {
            writeError(request, response, HttpStatus.BAD_REQUEST, "invalid_tenant_header", "X-Tenant-Id must be a tenant UUID");
            return false;
        }
        if (!tenantRepository.existsById(tenantId)) {
            writeError(request, response, HttpStatus.NOT_FOUND, "tenant_not_found", "Tenant was not found");
            return false;
        }
        String reference = request.getMethod() + " " + request.getRequestURI();
        if (reference.length() > BUSINESS_REFERENCE_MAX) {
            reference = reference.substring(0, BUSINESS_REFERENCE_MAX);
        }
        auditLogService.log(IMPERSONATION_AUDIT_ACTION, "TENANT", tenantId.toString(), reference, "ALLOWED");
        return true;
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        Object correlationId = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(code, message, correlationId == null ? null : correlationId.toString()));
    }

    private static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return jwt.getClaimAsString("preferred_username");
    }
}
