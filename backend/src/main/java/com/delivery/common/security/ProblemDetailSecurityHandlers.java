package com.delivery.common.security;

import com.delivery.common.exception.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes 401/403 decided by the security filter chain as RFC 9457 problem documents (INC-14, AS-17). Delegates to
 * Spring's bearer-token handlers first so status and {@code WWW-Authenticate} stay exactly as before; only a body is
 * added. Token contents and authentication error descriptions are never copied into the body.
 */
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthenticationEntryPoint entryPointDelegate = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler accessDeniedDelegate = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        entryPointDelegate.commence(request, response, exception);
        // The delegate picks the status (401, or 400 for a malformed bearer request); mirror it in the body.
        HttpStatus status = HttpStatus.resolve(response.getStatus());
        if (status == null || status.is2xxSuccessful()) {
            status = HttpStatus.UNAUTHORIZED;
        }
        String detail = status == HttpStatus.UNAUTHORIZED
                ? "Authentication is required to access this resource"
                : "The authentication request is invalid";
        write(request, response, ApiProblems.create(status, ApiProblems.codeForStatus(status), detail, request));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        accessDeniedDelegate.handle(request, response, exception);
        write(request, response, ApiProblems.create(
                HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to perform this action", request));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
