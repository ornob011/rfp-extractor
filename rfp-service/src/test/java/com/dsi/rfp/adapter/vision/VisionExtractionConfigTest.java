package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.config.YamlConfigLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisionExtractionConfigTest {

    private final VisionExtractionConfig config = new VisionExtractionConfig(
        new YamlConfigLoader()
    );

    @Test
    void shouldLoadVisionExtractionMetadata() {
        assertThat(config.fullPageDpi()).isEqualTo(150);
        assertThat(config.tableOnlyDpi()).isEqualTo(150);
        assertThat(config.jpegQuality()).isEqualTo(0.85f);
        assertThat(config.tableMethod()).isEqualTo("vlm");
        assertThat(config.fullPageSystemPromptResource().exists()).isTrue();
        assertThat(config.tableOnlySystemPromptResource().exists()).isTrue();
        assertThat(config.tablePresenceBatchSystemPromptResource().exists()).isTrue();
        assertThat(config.fullPageUserPromptTemplate()).contains("additional_instructions");
        assertThat(config.tableOnlyUserPromptTemplate()).contains("additional_instructions");
        assertThat(config.tablePresenceBatchUserPromptTemplate()).contains("page_list");
        assertThat(config.tablePresenceBatchSize()).isEqualTo(2);
    }
}
