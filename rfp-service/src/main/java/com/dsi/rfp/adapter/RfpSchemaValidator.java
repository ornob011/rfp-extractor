package com.dsi.rfp.adapter;

import com.dsi.rfp.domain.exception.RfpSchemaLoadException;
import com.dsi.rfp.domain.model.SchemaValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class RfpSchemaValidator {

    private final ObjectMapper objectMapper;

    @Value("${app.schema.rfp-schema-path:schema/rfp-schema-v1.json}")
    private String schemaPath;

    private JsonSchema jsonSchema;

    public RfpSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadSchema() {
        try {
            Path path = Path.of(schemaPath);

            InputStream schemaStream = Files.newInputStream(path);

            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V7
            );

            jsonSchema = factory.getSchema(schemaStream);

            log.info(
                "event=schema.loaded component=RfpSchemaValidator path={}",
                schemaPath
            );
        } catch (IOException ex) {
            throw new RfpSchemaLoadException(
                String.format("Failed to load RFP schema from %s", schemaPath),
                ex
            );
        }
    }

    public SchemaValidationResult validate(String rfpJson) throws IOException {
        JsonNode node = objectMapper.readTree(rfpJson);

        Set<ValidationMessage> messages = jsonSchema.validate(node);

        if (messages.isEmpty()) {
            return SchemaValidationResult.ok();
        }

        List<String> errors = messages.stream()
                                      .map(ValidationMessage::getMessage)
                                      .toList();

        return SchemaValidationResult.fail(errors);
    }
}
