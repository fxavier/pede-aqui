package com.delivery.delivery.exception;

import com.delivery.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a submitted delivery PIN does not match. Delivery completion commits on this
 * exception (noRollbackFor) so the failed-attempt counter and its events persist.
 */
public class InvalidDeliveryCodeException extends BusinessException {
    private InvalidDeliveryCodeException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    /** Wrong PIN, attempts still remaining. */
    public static InvalidDeliveryCodeException invalid() {
        return new InvalidDeliveryCodeException("invalid_delivery_code", "Delivery confirmation code is invalid", HttpStatus.BAD_REQUEST);
    }

    /** Wrong PIN that exhausted the attempt budget; the code is now locked until OPS unlocks it. */
    public static InvalidDeliveryCodeException locked() {
        return new InvalidDeliveryCodeException("delivery_code_locked", "Delivery confirmation code is locked after too many failed attempts", HttpStatus.LOCKED);
    }
}
