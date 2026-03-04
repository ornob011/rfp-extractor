package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmResilientCallerTest {

    @Mock
    private ChatClient chatClient;

    private LlmResilientCaller caller;

    @BeforeEach
    void setUp() {
        caller = new LlmResilientCaller(chatClient);
    }

    @Test
    void fallbackForCallShouldThrowLlmUnavailableException() {
        RuntimeException cause = new RuntimeException("circuit open");
        assertThatThrownBy(() -> caller.fallback("system", "user", cause))
            .isInstanceOf(LlmUnavailableException.class)
            .hasMessageContaining("LLM unavailable")
            .hasMessageContaining("circuit open");
    }

    @Test
    void fallbackForCallJudgeShouldThrowLlmUnavailableException() {
        RuntimeException cause = new RuntimeException("rate limited");
        assertThatThrownBy(() -> caller.fallback("prompt", cause))
            .isInstanceOf(LlmUnavailableException.class)
            .hasMessageContaining("LLM unavailable")
            .hasMessageContaining("rate limited");
    }

    @Test
    void callShouldReturnFutureWithLlmResponse() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.ChatClientRequestSpec systemSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(systemSpec);
        when(systemSpec.user(anyString())).thenReturn(systemSpec);
        when(systemSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("response");

        CompletableFuture<String> result = caller.call("system prompt", "user content");
        String content = result.get();
        org.assertj.core.api.Assertions.assertThat(content).isEqualTo("response");
    }

    @Test
    void callJudgeShouldReturnFutureWithLlmResponse() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("PASS");

        CompletableFuture<String> result = caller.callJudge("full prompt");
        String content = result.get();
        org.assertj.core.api.Assertions.assertThat(content).isEqualTo("PASS");
    }
}
