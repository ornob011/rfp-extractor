package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.config.YamlConfigLoader;
import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;

@Component
public class VisionPageExtractionConfig {

    private static final String CONFIG_PATH = "metadata/vision-page-extraction-v1.yml";

    private final ConfigDocument config;

    public VisionPageExtractionConfig(
        YamlConfigLoader yamlConfigLoader
    ) {
        config = yamlConfigLoader.load(
            CONFIG_PATH,
            ConfigDocument.class,
            "vision page extraction config"
        );
    }

    public int renderDpi() {
        return config.render().dpi();
    }

    public String language() {
        return config.vision().language();
    }

    public double textQualityBlockDivisor() {
        return config.merge().textQualityBlockDivisor();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        Render render,
        Vision vision,
        Merge merge
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Vision page extraction config must define version");
            }

            if (render == null) {
                throw new EntityMetadataContractException("Vision page extraction config must define render");
            }

            if (vision == null) {
                throw new EntityMetadataContractException("Vision page extraction config must define vision");
            }

            if (merge == null) {
                throw new EntityMetadataContractException("Vision page extraction config must define merge");
            }
        }
    }

    private record Render(
        int dpi
    ) {
    }

    private record Vision(
        String language
    ) {
    }

    private record Merge(
        double textQualityBlockDivisor
    ) {
    }
}
