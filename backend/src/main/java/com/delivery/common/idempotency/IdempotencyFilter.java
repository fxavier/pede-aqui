package com.delivery.common.idempotency;

import com.delivery.common.exception.ApiProblems;
import com.delivery.common.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Generic {@code Idempotency-Key} support for money-moving endpoints (LAC-24 generalisation, W7-A, V069). Runs after
 * the security chain and the tenant filter, and only for the endpoints in {@code app.idempotency.endpoints} that carry
 * the (optional) header:
 * <ul>
 *   <li>first request: claims {@code (tenant, caller sub, key)} IN_PROGRESS with the request fingerprint (SHA-256 of
 *       method, path, query and canonical JSON body), runs the request and stores a 2xx response (any other outcome
 *       releases the key so the client may retry);</li>
 *   <li>same key + same fingerprint: replays the stored status/body with {@code Idempotency-Replayed: true};</li>
 *   <li>same key + other fingerprint → 409 {@code idempotency_key_reused}; while the first is still running → 409
 *       {@code idempotency_request_in_progress}; malformed key → 400 {@code invalid_idempotency_key}.</li>
 * </ul>
 * It sits in front of — and never replaces — the domain idempotency (checkout key, refund keys, the Sales
 * Idempotency-Key header), which still decides what a first request does. Replays are served before the rate limiter.
 */
public class IdempotencyFilter extends OncePerRequestFilter {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]{8,128}");
    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private final IdempotencyProperties properties;
    private final IdempotencyRecordRepository repository;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final List<String[]> endpoints;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public IdempotencyFilter(IdempotencyProperties properties, IdempotencyRecordRepository repository, TenantContext tenantContext,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.endpoints = properties.getEndpoints().stream().map(entry -> entry.trim().split("\\s+", 2)).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled() || request.getHeader(IDEMPOTENCY_KEY_HEADER) == null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return endpoints.stream().noneMatch(e -> e.length == 2 && e[0].equalsIgnoreCase(request.getMethod()) && matcher.match(e[1], path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!KEY.matcher(key).matches()) {
            writeProblem(request, response, HttpStatus.BAD_REQUEST, "invalid_idempotency_key",
                    "Idempotency-Key must be 8-128 characters of [A-Za-z0-9_.:-]");
            return;
        }
        Optional<UUID> tenantId = tenantContext.currentTenantId();
        Optional<String> subject = tenantContext.currentKeycloakUserId();
        if (tenantId.isEmpty() || subject.isEmpty()) {
            chain.doFilter(request, response); // anonymous/tenant-less: the endpoint answers 401/403 itself
            return;
        }
        byte[] head = request.getInputStream().readNBytes(properties.getMaxRequestBytes() + 1);
        CachedBodyRequest cached = new CachedBodyRequest(request, head);
        if (head.length > properties.getMaxRequestBytes()) {
            log.warn("Idempotency-Key ignored: request body above {} bytes", properties.getMaxRequestBytes());
            chain.doFilter(cached, response);
            return;
        }
        String fingerprint = fingerprint(request, head);
        for (int round = 0; round < 3; round++) {
            Instant now = Instant.now();
            Optional<UUID> claimed = repository.tryClaim(tenantId.get(), subject.get(), key, fingerprint, now, now.plus(properties.getTtl()));
            if (claimed.isPresent()) {
                execute(claimed.get(), cached, response, chain);
                return;
            }
            Optional<IdempotencyRecordRepository.IdempotencyRecord> existing = repository.find(tenantId.get(), subject.get(), key);
            if (existing.isEmpty()) {
                continue; // released/expired in between: claim again
            }
            IdempotencyRecordRepository.IdempotencyRecord record = existing.get();
            boolean abandoned = !record.completed() && record.createdAt().isBefore(now.minus(properties.getInProgressLease()));
            if (record.expiresAt().isBefore(now) || abandoned) {
                repository.deleteStale(record.id());
                continue;
            }
            if (!record.fingerprint().equals(fingerprint)) {
                writeProblem(request, response, HttpStatus.CONFLICT, "idempotency_key_reused",
                        "This Idempotency-Key was already used for a different request");
                return;
            }
            if (!record.completed()) {
                writeProblem(request, response, HttpStatus.CONFLICT, "idempotency_request_in_progress",
                        "A request with this Idempotency-Key is still being processed; retry later");
                return;
            }
            replay(record, response);
            return;
        }
        writeProblem(request, response, HttpStatus.CONFLICT, "idempotency_request_in_progress",
                "A request with this Idempotency-Key is still being processed; retry later");
    }

    private void execute(UUID recordId, CachedBodyRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(request, wrapper);
            int status = wrapper.getStatus();
            byte[] body = wrapper.getContentAsByteArray();
            if (status >= 200 && status < 300 && body.length <= properties.getMaxResponseBytes() && !request.isAsyncStarted()) {
                repository.complete(recordId, status, wrapper.getContentType(), body);
                stored = true;
            }
        } finally {
            if (!stored) {
                repository.release(recordId);
            }
            wrapper.copyBodyToResponse();
        }
    }

    private void replay(IdempotencyRecordRepository.IdempotencyRecord record, HttpServletResponse response) throws IOException {
        response.setStatus(record.responseStatus());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        if (record.body() != null) {
            response.setContentLength(record.body().length);
            response.getOutputStream().write(record.body());
        }
    }

    private String fingerprint(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            if (request.getQueryString() != null) {
                digest.update(request.getQueryString().getBytes(StandardCharsets.UTF_8));
            }
            digest.update((byte) '\n');
            digest.update(canonical(body));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JSON bodies are compared with sorted keys and no insignificant whitespace; anything else byte for byte. */
    private byte[] canonical(byte[] body) {
        if (body.length == 0) {
            return body;
        }
        try {
            Object tree = objectMapper.readValue(body, Object.class);
            return canonicalMapper.writeValueAsBytes(tree);
        } catch (IOException notJson) {
            return body;
        }
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        ProblemDetail problem = ApiProblems.create(status, code, detail, request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /** Re-readable request: the bytes read for the fingerprint followed by whatever was not read. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] head;
        private boolean consumed;

        CachedBodyRequest(HttpServletRequest request, byte[] head) {
            super(request);
            this.head = head;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            InputStream source = consumed ? InputStream.nullInputStream()
                    : new SequenceInputStream(new ByteArrayInputStream(head), super.getInputStream());
            consumed = true;
            return new ServletInputStream() {
                private boolean finished;

                @Override
                public int read() throws IOException {
                    int value = source.read();
                    finished = value < 0;
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = source.read(buffer, offset, length);
                    finished = count < 0;
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return finished;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("async read not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }
}
