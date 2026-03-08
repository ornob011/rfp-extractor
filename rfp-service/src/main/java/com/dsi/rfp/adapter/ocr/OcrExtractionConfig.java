package com.dsi.rfp.adapter.ocr;

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
public class OcrExtractionConfig {

    private static final String CONFIG_PATH = "metadata/ocr-extraction-v1.yml";

    private final ConfigDocument config;

    public OcrExtractionConfig() {
        config = loadConfig();
    }

    public int renderDpi() {
        return config.render().dpi();
    }

    public String language() {
        return config.ocr().language();
    }

    public double textQualityBlockDivisor() {
        return config.merge().textQualityBlockDivisor();
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load OCR extraction config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        Render render,
        Ocr ocr,
        Merge merge
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("OCR extraction config must define version");
            }

            if (render == null) {
                throw new EntityMetadataContractException("OCR extraction config must define render");
            }

            if (ocr == null) {
                throw new EntityMetadataContractException("OCR extraction config must define ocr");
            }

            if (merge == null) {
                throw new EntityMetadataContractException("OCR extraction config must define merge");
            }
        }
    }

    private record Render(
        int dpi
    ) {
    }

    private record Ocr(
        String language
    ) {
    }

    private record Merge(
        double textQualityBlockDivisor
    ) {
    }
}
