package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.LlmProvider;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderConfigTest {

    @Test
    void shouldThrowWhenOpenRouterApiKeyIsBlankAndProviderIsOpenrouter() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider(LlmProvider.OPENROUTER);
        props.getOpenrouter().setApiKey(StringUtils.EMPTY);
        LlmProviderConfig config = new LlmProviderConfig(props);
        assertThatThrownBy(config::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenRouter API key must not be blank");
    }

    @Test
    void shouldThrowWhenOpenRouterApiKeyIsNullAndProviderIsOpenrouter() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider(LlmProvider.OPENROUTER);
        props.getOpenrouter().setApiKey(null);
        LlmProviderConfig config = new LlmProviderConfig(props);
        assertThatThrownBy(config::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenRouter API key must not be blank");
    }

    @Test
    void shouldNotThrowWhenOllamaProviderAndNoApiKey() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider(LlmProvider.OLLAMA);
        LlmProviderConfig config = new LlmProviderConfig(props);
        assertThatNoException().isThrownBy(config::validateConfiguration);
    }

    @Test
    void shouldNotThrowWhenOpenRouterApiKeyIsSet() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider(LlmProvider.OPENROUTER);
        props.getOpenrouter().setApiKey("sk-or-v1-valid-key");
        LlmProviderConfig config = new LlmProviderConfig(props);
        assertThatNoException().isThrownBy(config::validateConfiguration);
    }
}
