package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.config.YamlConfigLoader;
import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class VisionExtractionConfig {

    private static final String CONFIG_PATH = "metadata/vision-extraction-v1.yml";

    private final ConfigDocument config;

    public VisionExtractionConfig(
        YamlConfigLoader yamlConfigLoader
    ) {
        config = yamlConfigLoader.load(
            CONFIG_PATH,
            ConfigDocument.class,
            "vision extraction config"
        );
    }

    public int fullPageDpi() {
        return config.render().fullPageDpi();
    }

    public int tableOnlyDpi() {
        return config.render().tableOnlyDpi();
    }

    public int tablePresenceDpi() {
        return config.render().tablePresenceDpi();
    }

    public float jpegQuality() {
        return config.render().jpegQuality();
    }

    public double defaultVlmConfidence() {
        return config.confidence().defaultVlm();
    }

    public double tableVlmConfidence() {
        return config.confidence().tableVlm();
    }

    public String tableMethod() {
        return config.methods().table();
    }

    public Resource fullPageSystemPromptResource() {
        return new ClassPathResource(config.prompts().fullPageSystem());
    }

    public String fullPageUserPromptTemplate() {
        return loadPromptTemplate(config.prompts().fullPageUser());
    }

    public Resource tableOnlySystemPromptResource() {
        return new ClassPathResource(config.prompts().tableOnlySystem());
    }

    public Resource tablePresenceSystemPromptResource() {
        return new ClassPathResource(config.prompts().tablePresenceSystem());
    }

    public String tablePresenceUserPromptTemplate() {
        return loadPromptTemplate(config.prompts().tablePresenceUser());
    }

    public Resource tablePresenceBatchSystemPromptResource() {
        return new ClassPathResource(config.prompts().tablePresenceBatchSystem());
    }

    public String tablePresenceBatchUserPromptTemplate() {
        return loadPromptTemplate(config.prompts().tablePresenceBatchUser());
    }

    public String tableOnlyUserPromptTemplate() {
        return loadPromptTemplate(config.prompts().tableOnlyUser());
    }

    public int tablePresenceBatchSize() {
        return config.batch().tablePresenceBatchSize();
    }

    private String loadPromptTemplate(String path) {
        Resource resource = new ClassPathResource(path);

        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load vision prompt template: %s",
                    path
                ),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConfigDocument(
        Integer version,
        Render render,
        Prompts prompts,
        Methods methods,
        Confidence confidence,
        Batch batch
    ) {

        ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException(
                    "Vision extraction config must define version"
                );
            }

            if (render == null) {
                throw new EntityMetadataContractException(
                    "Vision extraction config must define render"
                );
            }

            if (prompts == null) {
                throw new EntityMetadataContractException(
                    "Vision extraction config must define prompts"
                );
            }

            if (confidence == null) {
                throw new EntityMetadataContractException(
                    "Vision extraction config must define confidence"
                );
            }

            if (methods == null) {
                throw new EntityMetadataContractException(
                    "Vision extraction config must define methods"
                );
            }

            if (batch == null) {
                throw new EntityMetadataContractException(
                    "Vision extraction config must define batch"
                );
            }
        }
    }

    record Render(
        int fullPageDpi,
        int tablePresenceDpi,
        int tableOnlyDpi,
        float jpegQuality
    ) {
    }

    record Prompts(
        String fullPageSystem,
        String fullPageUser,
        String tablePresenceSystem,
        String tablePresenceUser,
        String tablePresenceBatchSystem,
        String tablePresenceBatchUser,
        String tableOnlySystem,
        String tableOnlyUser
    ) {
    }

    record Methods(
        String table
    ) {
    }

    record Confidence(
        double defaultVlm,
        double tableVlm
    ) {
    }

    record Batch(
        int tablePresenceBatchSize
    ) {
    }
}
