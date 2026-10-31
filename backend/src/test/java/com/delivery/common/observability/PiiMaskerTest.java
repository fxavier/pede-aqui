package com.delivery.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * LAC-63 log hygiene: {@link PiiMasker} masks e-mails, phone numbers and tokens and leaves ids, amounts and
 * timestamps alone; {@link PiiMaskingJsonMembersCustomizer} applies it to every string field of the structured JSON
 * line (message, MDC, stack trace) when registered as {@code logging.structured.json.customizer}.
 */
class PiiMaskerTest {

    @Test
    void masksEmailsKeepingOnlyTheFirstCharacter() {
        assertThat(PiiMasker.mask("Failed for ana.machava+test@example.co.mz now")).isEqualTo("Failed for a***@*** now");
        assertThat(PiiMasker.mask("a@b.io, z@x.org")).isEqualTo("a***@***, z***@***");
    }

    @Test
    void masksPhonesKeepingTheLastTwoDigits() {
        assertThat(PiiMasker.mask("sms to +258841234567 failed")).isEqualTo("sms to +**********67 failed");
        assertThat(PiiMasker.mask("call 84 123 4567 or 258 861234567")).isEqualTo("call *******67 or **********67");
        assertThat(PiiMasker.mask("phone=821234567")).isEqualTo("phone=*******67");
        assertThat(PiiMasker.mask("intl +351 912 345 678")).isEqualTo("intl +**********78");
    }

    @Test
    void masksTokens() {
        assertThat(PiiMasker.mask("token eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl end")).isEqualTo("token [jwt] end");
        assertThat(PiiMasker.mask("Authorization: Bearer abcDEF123456789xyz")).isEqualTo("Authorization: Bearer [redacted]");
    }

    @Test
    void leavesIdsAmountsTimestampsAndAlreadyMaskedValuesAlone() {
        String untouched = "order 0f8fad5b-d9cb-469f-a165-70867728950e total 1250.00 at 2026-10-31T10:15:30Z took 842 ms";
        assertThat(PiiMasker.mask(untouched)).isEqualTo(untouched);
        assertThat(PiiMasker.mask("recipient +25884*****67")).isEqualTo("recipient +25884*****67");
        assertThat(PiiMasker.mask("trace 4bf92f3577b34da6a3ce929d0e0e4736 span 00f067aa0ba902b7")).isEqualTo(
                "trace 4bf92f3577b34da6a3ce929d0e0e4736 span 00f067aa0ba902b7");
        assertThat(PiiMasker.mask("ref 1234567890123")).isEqualTo("ref 1234567890123");
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("plain text")).isEqualTo("plain text");
    }

    @Test
    void structuredJsonLinesAreMaskedInMessageMdcAndStackTrace() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("logging.structured.json.customizer", PiiMaskingJsonMembersCustomizer.class.getName());
        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), environment);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("logstash");
        encoder.start();

        LoggingEvent event = new LoggingEvent("test", context.getLogger("test"), Level.WARN,
                "Keycloak rejected ana@example.test ({})", new IllegalStateException("duplicate key (email)=(ana@example.test)"),
                new Object[] {"+258841234567"});
        event.setMDCPropertyMap(Map.of("correlationId", "cid-1", "note", "call 841234567"));

        JsonNode line = new ObjectMapper().readTree(new String(encoder.encode(event), StandardCharsets.UTF_8));
        assertThat(line.get("message").asText()).isEqualTo("Keycloak rejected a***@*** (+**********67)");
        assertThat(line.get("note").asText()).isEqualTo("call *******67");
        assertThat(line.get("correlationId").asText()).isEqualTo("cid-1");
        assertThat(line.get("stack_trace").asText()).contains("(email)=(a***@***)").doesNotContain("ana@example.test");
    }
}
