package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmResilientCallerTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private ChatClient chatClient;
    private ChatClient judgeChatClient;
    private LlmResilientCaller caller;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        judgeChatClient = mock(ChatClient.class);
        caller = new LlmResilientCaller(
            chatClient,
            judgeChatClient,
            DIRECT_EXECUTOR
        );
    }

    @Test
    void callShouldReturnContentFromChatClient() throws Exception {
        stubChatClientToReturn(chatClient, "hello");
        CompletableFuture<String> result = caller.call("sys", "user");
        assertThat(result.get()).isEqualTo("hello");
    }

    @Test
    void callJudgeShouldReturnContentFromJudgeChatClient() throws Exception {
        stubChatClientToReturn(judgeChatClient, "PASS");
        CompletableFuture<String> result = caller.callJudge("full prompt");
        assertThat(result.get()).isEqualTo("PASS");
    }

    @Test
    void fallbackThreeArgShouldReturnFailedFutureWithLlmUnavailableException() {
        RuntimeException cause = new RuntimeException("circuit open");
        CompletableFuture<String> result = caller.fallback("sys", "user", cause);
        assertThat(result.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(result::get)
            .hasCauseInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void fallbackTwoArgShouldReturnFailedFutureWithLlmUnavailableException() {
        RuntimeException cause = new RuntimeException("timeout");
        CompletableFuture<String> result = caller.fallback("prompt", cause);
        assertThat(result.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(result::get)
            .hasCauseInstanceOf(LlmUnavailableException.class);
    }

    private void stubChatClientToReturn(
        ChatClient client,
        String response
    ) {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(client.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }
}
