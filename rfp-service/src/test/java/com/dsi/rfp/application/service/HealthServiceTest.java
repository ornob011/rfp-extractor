package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import com.dsi.rfp.domain.model.SidecarReachability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private ChatModel chatModel;

    private HealthService healthService;

    @BeforeEach
    void setUp() {
        ChatOptions chatOptions = mock(ChatOptions.class);
        lenient().when(chatOptions.getModel()).thenReturn("google/gemini-2.0-flash-001");
        lenient().when(chatModel.getDefaultOptions()).thenReturn(chatOptions);
        healthService = new HealthService("openrouter", chatModel, restClient);
    }

    @Test
    void shouldReturnReachableWhenOcrSidecarResponds() {
        stubOcrSidecarSuccess();
        HealthResponse response = healthService.check();
        assertThat(response.getOcrSidecar()).isEqualTo(SidecarReachability.REACHABLE);
    }

    @Test
    void shouldReturnUnreachableWhenOcrSidecarThrows() {
        stubOcrSidecarFailure();
        HealthResponse response = healthService.check();
        assertThat(response.getOcrSidecar()).isEqualTo(SidecarReachability.UNREACHABLE);
    }

    @Test
    void shouldReturnProviderFromProperties() {
        stubOcrSidecarSuccess();
        HealthResponse response = healthService.check();
        assertThat(response.getProvider()).isEqualTo(LlmProvider.OPENROUTER);
    }

    @Test
    void shouldReturnExtractionModelName() {
        stubOcrSidecarSuccess();
        HealthResponse response = healthService.check();
        assertThat(response.getModel()).isEqualTo("google/gemini-2.0-flash-001");
    }

    @Test
    void shouldReturnStatusUpWhenHealthy() {
        stubOcrSidecarSuccess();
        HealthResponse response = healthService.check();
        assertThat(response.getStatus()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void shouldReturnOllamaModelWhenProviderIsOllama() {
        ChatOptions ollamaOptions = mock(ChatOptions.class);
        when(ollamaOptions.getModel()).thenReturn("llama3.1:8b");
        when(chatModel.getDefaultOptions()).thenReturn(ollamaOptions);
        HealthService ollamaService = new HealthService("ollama", chatModel, restClient);
        stubOcrSidecarSuccess();
        HealthResponse response = ollamaService.check();
        assertThat(response.getModel()).isEqualTo("llama3.1:8b");
        assertThat(response.getProvider()).isEqualTo(LlmProvider.OLLAMA);
    }

    private void stubOcrSidecarSuccess() {
        RestClient.RequestHeadersUriSpec<?> requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headerSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        doReturn(requestSpec).when(restClient).get();
        doReturn(headerSpec).when(requestSpec).uri(anyString());
        when(headerSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"status\":\"ok\"}");
    }

    private void stubOcrSidecarFailure() {
        RestClient.RequestHeadersUriSpec<?> requestSpec =
            mock(RestClient.RequestHeadersUriSpec.class);
        doReturn(requestSpec).when(restClient).get();
        when(requestSpec.uri(anyString()))
            .thenThrow(new ResourceAccessException("Connection refused"));
    }
}
