package com.delivery.common.exception;

import org.springframework.http.HttpStatus;

/** Exception used when a requested record does not exist or is outside the tenant. */
public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super("not_found", message, HttpStatus.NOT_FOUND);
    }
}
