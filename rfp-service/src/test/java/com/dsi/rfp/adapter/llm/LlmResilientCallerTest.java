package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmResilientCallerTest {

    private ChatClient chatClient;
    private LlmResilientCaller caller;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        caller = new LlmResilientCaller(chatClient);
    }

    @Test
    void callShouldReturnContentFromChatClient() throws Exception {
        stubChatClientToReturn("hello");
        CompletableFuture<String> result = caller.call("sys", "user");
        assertThat(result.get()).isEqualTo("hello");
    }

    @Test
    void callJudgeShouldReturnContentFromChatClient() throws Exception {
        stubChatClientToReturn("PASS");
        CompletableFuture<String> result = caller.callJudge("full prompt");
        assertThat(result.get()).isEqualTo("PASS");
    }

    @Test
    void fallbackThreeArgShouldThrowLlmUnavailableException() {
        RuntimeException cause = new RuntimeException("circuit open");
        assertThatThrownBy(() -> caller.fallback("sys", "user", cause))
            .isInstanceOf(LlmUnavailableException.class)
            .hasMessageContaining("LLM unavailable")
            .hasMessageContaining("circuit open");
    }

    @Test
    void fallbackTwoArgShouldThrowLlmUnavailableException() {
        RuntimeException cause = new RuntimeException("timeout");
        assertThatThrownBy(() -> caller.fallback("prompt", cause))
            .isInstanceOf(LlmUnavailableException.class)
            .hasMessageContaining("LLM unavailable")
            .hasMessageContaining("timeout");
    }

    private void stubChatClientToReturn(String response) {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }
}
