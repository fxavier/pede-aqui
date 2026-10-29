package com.pedeaqui.keycloak.smsotp;

/**
 * One SMS to send. {@code text} carries the secret and goes only to the SMS provider; {@code redactedText} is the
 * same text with the code masked and is the only form that may appear in logs, events or errors.
 *
 * @param toE164       destination in E.164
 * @param text         full text (contains the code)
 * @param redactedText text safe to log
 */
public record SmsMessage(String toE164, String text, String redactedText) {
    @Override
    public String toString() {
        return "SmsMessage[to=" + PhoneNumbers.mask(toE164) + ", text=" + redactedText + "]";
    }
}
