package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

@Component
public class VisionExtractionConfig {

    private static final String CONFIG_PATH = "metadata/vision-extraction-v1.yml";

    private final ConfigDocument config;

    public VisionExtractionConfig() {
        config = loadConfig();
    }

    public int fullPageDpi() {
        return config.render().fullPageDpi();
    }

    public int tableOnlyDpi() {
        return config.render().tableOnlyDpi();
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

    public String tableOnlyUserPromptTemplate() {
        return loadPromptTemplate(config.prompts().tableOnlyUser());
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

    private ConfigDocument loadConfig() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(CONFIG_PATH));
        Properties properties = Optional.ofNullable(factory.getObject())
                                        .orElseThrow(() -> new SystemIoException(
                                            String.format(
                                                "Failed to load vision extraction config: %s",
                                                CONFIG_PATH
                                            ),
                                            new IllegalStateException("Missing YAML properties")
                                        ));
        Binder binder = new Binder(
            new MapConfigurationPropertySource(
                properties.entrySet()
                          .stream()
                          .collect(Collectors.toUnmodifiableMap(
                              entry -> entry.getKey().toString(),
                              Map.Entry::getValue
                          ))
            )
        );

        return binder.bind(
                         "",
                         Bindable.of(ConfigDocument.class)
                     )
                     .orElseThrow(() -> new SystemIoException(
                         String.format(
                             "Failed to bind vision extraction config: %s",
                             CONFIG_PATH
                         ),
                         new IllegalStateException("Vision extraction config binder returned empty result")
                     ));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConfigDocument(
        Integer version,
        Render render,
        Prompts prompts,
        Methods methods,
        Confidence confidence
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
        }
    }

    record Render(
        int fullPageDpi,
        int tableOnlyDpi,
        float jpegQuality
    ) {
    }

    record Prompts(
        String fullPageSystem,
        String fullPageUser,
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
}
