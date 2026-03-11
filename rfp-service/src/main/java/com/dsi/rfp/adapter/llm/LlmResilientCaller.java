package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
class LlmResilientCaller {

    private static final String RESILIENCE_INSTANCE = "llm";

    private final ChatClient chatClient;
    private final ChatClient judgeChatClient;
    private final Executor llmExecutor;

    LlmResilientCaller(
        ChatClient chatClient,
        @Qualifier("judgeChatClient") ChatClient judgeChatClient,
        @Qualifier("llmTaskExecutor") Executor llmExecutor
    ) {
        this.chatClient = chatClient;
        this.judgeChatClient = judgeChatClient;
        this.llmExecutor = llmExecutor;
    }

    @CircuitBreaker(
        name = RESILIENCE_INSTANCE,
        fallbackMethod = "fallback"
    )
    @RateLimiter(
        name = RESILIENCE_INSTANCE
    )
    @Retry(
        name = RESILIENCE_INSTANCE
    )
    public CompletableFuture<String> call(
        String systemPrompt,
        String userContent
    ) {
        return CompletableFuture.supplyAsync(
            () -> chatClient.prompt()
                            .system(systemPrompt)
                            .user(userContent)
                            .call()
                            .content(),
            llmExecutor
        ).orTimeout(45, TimeUnit.SECONDS);
    }

    @CircuitBreaker(
        name = RESILIENCE_INSTANCE,
        fallbackMethod = "fallback"
    )
    @RateLimiter(
        name = RESILIENCE_INSTANCE
    )
    @Retry(
        name = RESILIENCE_INSTANCE
    )
    public CompletableFuture<String> callJudge(
        String fullPrompt
    ) {
        return CompletableFuture.supplyAsync(
            () -> judgeChatClient.prompt()
                                 .user(fullPrompt)
                                 .call()
                                 .content(),
            llmExecutor
        ).orTimeout(45, TimeUnit.SECONDS);
    }

    @CircuitBreaker(
        name = RESILIENCE_INSTANCE,
        fallbackMethod = "fallbackWithImage"
    )
    @RateLimiter(
        name = RESILIENCE_INSTANCE
    )
    @Retry(
        name = RESILIENCE_INSTANCE
    )
    public CompletableFuture<String> callWithImage(
        String systemPrompt,
        String userContent,
        byte[] imageBytes,
        MimeType mimeType
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                Media imageMedia = Media.builder()
                                        .mimeType(mimeType)
                                        .data(imageBytes)
                                        .build();

                UserMessage userMessage = UserMessage.builder()
                                                     .text(userContent)
                                                     .media(imageMedia)
                                                     .build();

                Prompt prompt = new Prompt(
                    List.of(
                        new SystemMessage(systemPrompt),
                        userMessage
                    )
                );

                return chatClient.prompt(prompt)
                                 .call()
                                 .content();
            },
            llmExecutor
        ).orTimeout(45, TimeUnit.SECONDS);
    }

    public CompletableFuture<String> fallbackWithImage(
        String systemPrompt,
        String userContent,
        byte[] imageBytes,
        MimeType mimeType,
        Throwable cause
    ) {
        return CompletableFuture.failedFuture(
            new LlmUnavailableException(
                "LLM unavailable for vision extraction",
                cause
            )
        );
    }

    public CompletableFuture<String> fallback(
        String systemPrompt,
        String userContent,
        Throwable cause
    ) {
        return CompletableFuture.failedFuture(
            new LlmUnavailableException(
                "LLM unavailable for response generation",
                cause
            )
        );
    }

    public CompletableFuture<String> fallback(
        String fullPrompt,
        Throwable cause
    ) {
        return CompletableFuture.failedFuture(
            new LlmUnavailableException(
                "LLM unavailable for judge",
                cause
            )
        );
    }
}
