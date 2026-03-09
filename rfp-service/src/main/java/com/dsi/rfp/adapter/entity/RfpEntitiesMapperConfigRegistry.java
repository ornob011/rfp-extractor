package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class RfpEntitiesMapperConfigRegistry {

    private static final String CONFIG_PATH = "metadata/rfp-entities-mapper-v1.yml";

    private final Map<String, RfpEntitiesMapper.ValueKind> fields;

    public RfpEntitiesMapperConfigRegistry() {
        fields = Map.copyOf(loadConfig().fields());
    }

    public Map<String, RfpEntitiesMapper.ValueKind> fields() {
        return fields;
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load entities mapper config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        Map<String, RfpEntitiesMapper.ValueKind> fields
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Entities mapper config must define version");
            }

            if (fields == null) {
                throw new EntityMetadataContractException("Entities mapper config must define fields");
            }
        }
    }
}
