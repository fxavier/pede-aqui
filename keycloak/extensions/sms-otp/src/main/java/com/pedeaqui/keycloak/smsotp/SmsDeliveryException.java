package com.pedeaqui.keycloak.smsotp;

/** The SMS gateway could not accept a message. The message must not contain the code or the full number. */
public class SmsDeliveryException extends RuntimeException {
    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
