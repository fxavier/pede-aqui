package com.delivery.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exports the OpenAPI contract served at {@code /api-docs} to {@code target/openapi.json} (LAC-56) in a
 * deterministic form: object keys sorted, 2-space indentation, LF line endings, and the environment-dependent
 * {@code servers} block removed. CI diffs this file against the committed {@code docs/api/openapi.json} and runs
 * oasdiff on it; see {@code docs/api/README.md}. Override the destination with {@code -Dopenapi.output=<path>}.
 */
class OpenApiContractExportTest extends PostgresApiE2ETestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsSortedOpenApiContract() throws Exception {
        String raw = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JsonNode spec = mapper.readTree(raw);
        // servers[0].url reflects the host/port of whoever generated the spec; it is not part of the contract.
        ((ObjectNode) spec).remove("servers");
        // readTree builds LinkedHashMap-backed nodes; round-trip through a Map so ORDER_MAP_ENTRIES_BY_KEYS applies.
        Object sorted = mapper.treeToValue(spec, Object.class);

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator(""));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        String json = mapper.writer(printer).writeValueAsString(sorted) + "\n";

        Path output = Path.of(System.getProperty("openapi.output", "target/openapi.json"));
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, json, StandardCharsets.UTF_8);

        assertThat(spec.path("openapi").asText()).isEqualTo("3.0.1");
        assertThat(spec.path("paths").size()).isPositive();
    }
}
