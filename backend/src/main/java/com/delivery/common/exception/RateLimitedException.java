package com.delivery.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 429 rate_limited (LAC-11); the exception handler copies {@link #getRetryAfterSeconds()} into Retry-After. */
public class RateLimitedException extends BusinessException {
    public static final String CODE = "rate_limited";

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(CODE, message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Object getDetails() {
        return Map.of("retryAfterSeconds", retryAfterSeconds);
    }
}
