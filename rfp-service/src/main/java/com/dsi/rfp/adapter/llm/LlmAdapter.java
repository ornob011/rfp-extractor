package com.dsi.rfp.adapter.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAdapter {

    private static final ResponseTextCleaner JSON_RESPONSE_CLEANER = CompositeResponseTextCleaner.builder()
                                                                                                 .addCleaner(new MarkdownCodeBlockCleaner())
                                                                                                 .addCleaner(new ThinkingTagCleaner())
                                                                                                 .build();

    private final LlmResilientCaller caller;
    private final ObjectMapper objectMapper;

    private static String joinAndUnwrap(CompletableFuture<String> future) {
        return future.join();
    }

    public <T> Optional<T> extractStructured(
        String systemPrompt,
        String userContent,
        Class<T> responseType
    ) {
        return parseResponse(
            joinAndUnwrap(
                caller.call(
                    systemPrompt,
                    userContent
                )
            ),
            responseType
        );
    }

    public Optional<String> extractRaw(
        String systemPrompt,
        String userContent
    ) {
        String raw = joinAndUnwrap(
            caller.call(systemPrompt, userContent)
        );

        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }

        return Optional.of(
            JSON_RESPONSE_CLEANER.clean(raw)
        );
    }

    public Optional<String> judgeSnippet(
        String prompt,
        String snippet
    ) {
        return Optional.ofNullable(
                           joinAndUnwrap(
                               caller.callJudge(
                                   String.format("%s%n%n%s", prompt, snippet)
                               )
                           )
                       )
                       .filter(StringUtils::hasText);
    }

    private <T> Optional<T> parseResponse(
        String raw,
        Class<T> type
    ) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }

        T result = new BeanOutputConverter<>(type, objectMapper, JSON_RESPONSE_CLEANER).convert(raw);

        return Optional.ofNullable(result);
    }
}
