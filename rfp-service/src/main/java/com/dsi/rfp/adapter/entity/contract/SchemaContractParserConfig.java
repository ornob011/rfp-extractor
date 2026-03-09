package com.dsi.rfp.adapter.entity.contract;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class SchemaContractParserConfig {

    private static final String CONFIG_PATH = "metadata/schema-contract-parser-v1.yml";

    private final ConfigDocument config;

    public SchemaContractParserConfig() {
        config = loadConfig();
    }

    public String schemaResource() {
        return config.schemaResource();
    }

    public String definitionsNode() {
        return config.definitionsNode();
    }

    public String propertiesNode() {
        return config.propertiesNode();
    }

    public String entitiesPrefix() {
        return config.entitiesPrefix();
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load schema parser config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        String schemaResource,
        String definitionsNode,
        String propertiesNode,
        String entitiesPrefix
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Schema parser config must define version");
            }

            if (schemaResource == null) {
                throw new EntityMetadataContractException("Schema parser config must define schemaResource");
            }

            if (definitionsNode == null) {
                throw new EntityMetadataContractException("Schema parser config must define definitionsNode");
            }

            if (propertiesNode == null) {
                throw new EntityMetadataContractException("Schema parser config must define propertiesNode");
            }

            if (entitiesPrefix == null) {
                throw new EntityMetadataContractException("Schema parser config must define entitiesPrefix");
            }
        }
    }
}
