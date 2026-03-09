package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.LlmProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderPropertiesTest {

    @Test
    void shouldReturnDefaultProviderWhenNotConfigured() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getProvider()).isEqualTo(LlmProvider.OPENROUTER);
    }

    @Test
    void shouldReturnDefaultTemperatureWhenNotConfigured() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getTemperature()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnDefaultMaxTokensWhenNotConfigured() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getMaxTokens()).isEqualTo(4096);
    }

    @Test
    void shouldReturnConfiguredModelWhenSet() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.getOpenrouter().setModel("google/gemini-2.0-flash-001");
        assertThat(props.getOpenrouter().getModel()).isEqualTo("google/gemini-2.0-flash-001");
    }

    @Test
    void shouldReturnDefaultTimeoutSeconds() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void shouldReturnDefaultRateLimitPerMinute() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getRateLimitPerMinute()).isEqualTo(60);
    }

    @Test
    void shouldReturnDefaultOllamaBaseUrl() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getOllama().getBaseUrl()).isEqualTo("http://localhost:11434");
    }

    @Test
    void shouldAllowProviderChange() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider(LlmProvider.OLLAMA);
        assertThat(props.getProvider()).isEqualTo(LlmProvider.OLLAMA);
    }
}
