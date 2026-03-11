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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
class LlmResilientCaller {

    private static final String RESILIENCE_INSTANCE = "llm";

    private final ChatClient chatClient;
    private final ChatClient judgeChatClient;
    private final Executor llmExecutor;
    private final long callTimeoutSeconds;
    private final long visionCallTimeoutSeconds;

    LlmResilientCaller(
        ChatClient chatClient,
        @Qualifier("judgeChatClient") ChatClient judgeChatClient,
        @Qualifier("llmTaskExecutor") Executor llmExecutor,
        @Value("${app.llm.call-timeout-seconds:45}") long callTimeoutSeconds,
        @Value("${app.llm.vision-call-timeout-seconds:120}") long visionCallTimeoutSeconds
    ) {
        this.chatClient = chatClient;
        this.judgeChatClient = judgeChatClient;
        this.llmExecutor = llmExecutor;
        this.callTimeoutSeconds = callTimeoutSeconds;
        this.visionCallTimeoutSeconds = visionCallTimeoutSeconds;
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
            () -> executeWithTimeout(callTimeoutSeconds,
                () -> chatClient.prompt()
                                .system(systemPrompt)
                                .user(userContent)
                                .call()
                                .content()
            ),
            llmExecutor
        );
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
            () -> executeWithTimeout(callTimeoutSeconds,
                () -> judgeChatClient.prompt()
                                     .user(fullPrompt)
                                     .call()
                                     .content()
            ),
            llmExecutor
        );
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
        return callWithImages(
            systemPrompt,
            userContent,
            List.of(new LlmImageInput(
                imageBytes,
                mimeType
            ))
        );
    }

    @CircuitBreaker(
        name = RESILIENCE_INSTANCE,
        fallbackMethod = "fallbackWithImages"
    )
    @RateLimiter(
        name = RESILIENCE_INSTANCE
    )
    @Retry(
        name = RESILIENCE_INSTANCE
    )
    public CompletableFuture<String> callWithImages(
        String systemPrompt,
        String userContent,
        List<LlmImageInput> images
    ) {
        return CompletableFuture.supplyAsync(
            () -> executeWithTimeout(visionCallTimeoutSeconds, () -> {
                UserMessage userMessage = UserMessage.builder()
                                                     .text(userContent)
                                                     .media(
                                                         images.stream()
                                                               .map(image -> Media.builder()
                                                                                  .mimeType(image.mimeType())
                                                                                  .data(image.data())
                                                                                  .build())
                                                               .toList()
                                                     )
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
            }),
            llmExecutor
        );
    }

    private String executeWithTimeout(
        long timeoutSeconds,
        Supplier<String> task
    ) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(task);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new CompletionException(exception);
        } catch (ExecutionException exception) {
            throw new CompletionException(exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }

    public CompletableFuture<String> fallbackWithImage(
        String systemPrompt,
        String userContent,
        byte[] imageBytes,
        MimeType mimeType,
        Throwable cause
    ) {
        return fallbackWithImages(
            systemPrompt,
            userContent,
            List.of(new LlmImageInput(
                imageBytes,
                mimeType
            )),
            cause
        );
    }

    public CompletableFuture<String> fallbackWithImages(
        String systemPrompt,
        String userContent,
        List<LlmImageInput> images,
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
