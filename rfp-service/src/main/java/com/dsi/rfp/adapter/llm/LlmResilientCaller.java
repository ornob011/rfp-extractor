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

    private final ChatClient chatClient;

    @CircuitBreaker(name = "llm", fallbackMethod = "fallback")
    @RateLimiter(name = "llm")
    @TimeLimiter(name = "llm")
    @Retry(name = "llm")
    public CompletableFuture<String> call(String systemPrompt, String userContent) {
        return CompletableFuture.supplyAsync(() ->
            chatClient.prompt().system(systemPrompt).user(userContent).call().content());
    }

    @CircuitBreaker(name = "llm", fallbackMethod = "fallback")
    @RateLimiter(name = "llm")
    @TimeLimiter(name = "llm")
    @Retry(name = "llm")
    public CompletableFuture<String> callJudge(String fullPrompt) {
        return CompletableFuture.supplyAsync(() ->
            chatClient.prompt().user(fullPrompt).call().content());
    }

    public CompletableFuture<String> fallback(
        String systemPrompt,
        String userContent,
        Throwable t
    ) {
        throw new LlmUnavailableException("LLM unavailable: " + t.getMessage(), t);
    }

    public CompletableFuture<String> fallback(String fullPrompt, Throwable t) {
        throw new LlmUnavailableException("LLM unavailable: " + t.getMessage(), t);
    }
}
