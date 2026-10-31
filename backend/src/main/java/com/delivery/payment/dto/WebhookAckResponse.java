package com.delivery.payment.dto;

/** Answer to a verified provider callback: {@code processed}, {@code ignored}, {@code duplicate} or {@code unmatched} (always 200). */
public record WebhookAckResponse(String result) {
}
