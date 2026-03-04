package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.config.LlmProviderProperties;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import com.dsi.rfp.domain.model.SidecarReachability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private RestClient restClient;

    private HealthService healthService;
    private LlmProviderProperties props;

    @BeforeEach
    void setUp() {
        props = new LlmProviderProperties();
        props.setProvider(LlmProvider.OPENROUTER);
        props.getOpenrouter().setModel("google/gemini-2.0-flash-001");
        healthService = new HealthService(props, restClient, "http://localhost:8000");
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
        props.setProvider(LlmProvider.OLLAMA);
        props.getOllama().setModel("llama3.1:8b");
        stubOcrSidecarSuccess();
        HealthResponse response = healthService.check();
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
