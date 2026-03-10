package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class SectionFallbackConfig {

    private static final String CONFIG_PATH = "metadata/section-fallback-v1.yml";

    private final ConfigDocument config;

    public SectionFallbackConfig() {
        config = loadConfig();
    }

    public int minPages() {
        return config.trigger().minPages();
    }

    public int minSections() {
        return config.trigger().minSections();
    }

    public int pageWindow() {
        return config.prompt().pageWindow();
    }

    public int maxTextChars() {
        return config.prompt().maxTextChars();
    }

    public int dedupLevenshteinThreshold() {
        return config.merge().dedupLevenshteinThreshold();
    }

    public double confidenceScore() {
        return config.confidence().score();
    }

    public Resource systemPromptResource() {
        return new ClassPathResource(config.systemPromptResourcePath());
    }

    public Resource promptResource() {
        return new ClassPathResource(config.promptResourcePath());
    }

    public String promptTemplate() {
        Resource resource = promptResource();

        try (InputStream input = resource.getInputStream()) {
            return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load section fallback prompt resource: %s",
                    resource.getDescription()
                ),
                exception
            );
        }
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load section fallback config: %s",
                    CONFIG_PATH
                ),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        int version,
        String promptResourcePath,
        String systemPromptResourcePath,
        Trigger trigger,
        Prompt prompt,
        Merge merge,
        Confidence confidence
    ) {
    }

    private record Trigger(
        int minPages,
        int minSections
    ) {
    }

    private record Prompt(
        int pageWindow,
        int maxTextChars
    ) {
    }

    private record Merge(
        int dedupLevenshteinThreshold
    ) {
    }

    private record Confidence(
        double score
    ) {
    }
}
