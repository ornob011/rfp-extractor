package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private ChatModel chatModel;

    private HealthService healthService;

    @BeforeEach
    void setUp() {
        ChatOptions chatOptions = mock(ChatOptions.class);
        lenient().when(chatOptions.getModel()).thenReturn("qwen3.5:9b");
        lenient().when(chatModel.getDefaultOptions()).thenReturn(chatOptions);
        healthService = new HealthService("ollama", chatModel);
    }

    @Test
    void shouldReturnProviderFromProperties() {
        HealthResponse response = healthService.check();
        assertThat(response.getProvider()).isEqualTo(LlmProvider.OLLAMA);
    }

    @Test
    void shouldReturnModelName() {
        HealthResponse response = healthService.check();
        assertThat(response.getModel()).isEqualTo("qwen3.5:9b");
    }

    @Test
    void shouldReturnStatusUp() {
        HealthResponse response = healthService.check();
        assertThat(response.getStatus()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void shouldReturnOpenRouterProvider() {
        ChatOptions chatOptions = mock(ChatOptions.class);
        when(chatOptions.getModel()).thenReturn("google/gemini-2.0-flash-001");
        when(chatModel.getDefaultOptions()).thenReturn(chatOptions);

        HealthService openRouterService = new HealthService(
            "openrouter",
            chatModel
        );

        HealthResponse response = openRouterService.check();
        assertThat(response.getProvider()).isEqualTo(LlmProvider.OPENROUTER);
        assertThat(response.getModel()).isEqualTo("google/gemini-2.0-flash-001");
    }
}
