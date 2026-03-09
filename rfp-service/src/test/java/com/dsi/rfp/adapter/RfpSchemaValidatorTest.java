package com.dsi.rfp.adapter;

import com.dsi.rfp.domain.exception.RfpSchemaLoadException;
import com.dsi.rfp.domain.model.SchemaValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RfpSchemaValidatorTest {

    private RfpSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RfpSchemaValidator(new ObjectMapper());
        Path schemaPath = Path.of("../schema/rfp-schema-v1.json").toAbsolutePath().normalize();
        ReflectionTestUtils.setField(validator, "schemaPath", schemaPath.toString());
        validator.loadSchema();
    }

    @Test
    void shouldReturnValidWhenRfpJsonConformsToSchema() throws IOException {
        String validJson = """
            {
                "schema_version": "1.0",
                "doc_meta": {"procurement_ref": "TEST-001"},
                "sections": [],
                "clauses": [],
                "tables": [],
                "entities": {},
                "rule_pack_results": {},
                "extraction_state": {}
            }
            """;

        SchemaValidationResult result = validator.validate(validJson);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldReturnInvalidWhenRequiredFieldMissing() throws IOException {
        String invalidJson = """
            {
                "doc_meta": {"procurement_ref": "TEST-001"},
                "sections": [],
                "clauses": [],
                "tables": [],
                "entities": {}
            }
            """;

        SchemaValidationResult result = validator.validate(invalidJson);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void shouldReturnInvalidWhenJsonIsMalformed() {
        String malformed = "{ not valid json }";
        assertThatThrownBy(() -> validator.validate(malformed))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldFailWhenSchemaPathInvalid() {
        RfpSchemaValidator bad = new RfpSchemaValidator(new ObjectMapper());
        ReflectionTestUtils.setField(bad, "schemaPath", "nonexistent/schema.json");
        assertThatThrownBy(bad::loadSchema)
            .isInstanceOf(RfpSchemaLoadException.class);
    }
}
