package com.delivery.common.exception;

/** Represents a validation failure for one request field. */
public record ValidationError(String field, String message) {
}
