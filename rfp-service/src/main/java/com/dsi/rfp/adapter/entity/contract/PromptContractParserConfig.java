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
public class PromptContractParserConfig {

    private static final String CONFIG_PATH = "metadata/prompt-contract-parser-v1.yml";

    private final ConfigDocument config;

    public PromptContractParserConfig() {
        config = loadConfig();
    }

    public String fieldsSectionHeading() {
        return config.fieldsSectionHeading();
    }

    public String fieldColumnHeader() {
        return config.fieldColumnHeader();
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load prompt contract parser config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        String fieldsSectionHeading,
        String fieldColumnHeader
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Prompt parser config must define version");
            }

            if (fieldsSectionHeading == null) {
                throw new EntityMetadataContractException("Prompt parser config must define fieldsSectionHeading");
            }

            if (fieldColumnHeader == null) {
                throw new EntityMetadataContractException("Prompt parser config must define fieldColumnHeader");
            }
        }
    }
}
