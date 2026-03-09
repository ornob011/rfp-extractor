package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
class LlmResilientCaller {

    private static final String RESILIENCE_INSTANCE = "llm";

    private final ChatClient chatClient;

    @CircuitBreaker(
        name = RESILIENCE_INSTANCE,
        fallbackMethod = "fallback"
    )
    @RateLimiter(
        name = RESILIENCE_INSTANCE
    )
    @TimeLimiter(
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
                            .content()
        );
    }

    @CircuitBreaker(
        name = RESILIENCE_INSTANCE,
        fallbackMethod = "fallback"
    )
    @RateLimiter(
        name = RESILIENCE_INSTANCE
    )
    @TimeLimiter(
        name = RESILIENCE_INSTANCE
    )
    @Retry(
        name = RESILIENCE_INSTANCE
    )
    public CompletableFuture<String> callJudge(
        String fullPrompt
    ) {
        return CompletableFuture.supplyAsync(
            () -> chatClient.prompt()
                            .user(fullPrompt)
                            .call()
                            .content()
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
