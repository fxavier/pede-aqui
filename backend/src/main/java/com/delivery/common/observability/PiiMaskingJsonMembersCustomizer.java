package com.delivery.common.observability;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Structured-log customizer (LAC-50 layout, LAC-63 hygiene, W7-C): runs every string value of every JSON log line —
 * message, MDC fields, stack trace — through {@link PiiMasker}, so an e-mail, phone number or token that slips into a
 * log message or exception text is masked before it leaves the process. Registered with
 * {@code logging.structured.json.customizer} in application.yml (applies to the JSON formats; the readable dev pattern
 * is not masked).
 */
public class PiiMaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, PiiMasker::mask));
    }
}
