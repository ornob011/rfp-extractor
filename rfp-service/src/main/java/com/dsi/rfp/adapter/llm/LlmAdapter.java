package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.adapter.security.PromptInjectionFilter;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.LlmJudgmentResult;
import com.dsi.rfp.domain.model.RuleStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.*;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class LlmAdapter {

    private static final ResponseTextCleaner JSON_RESPONSE_CLEANER = CompositeResponseTextCleaner.builder()
                                                                                                 .addCleaner(new MarkdownCodeBlockCleaner())
                                                                                                 .addCleaner(new ThinkingTagCleaner())
                                                                                                 .build();

    private final LlmResilientCaller caller;
    private final ObjectMapper objectMapper;
    private final PromptInjectionFilter injectionFilter;

    LlmAdapter(
        LlmResilientCaller caller,
        ObjectMapper objectMapper,
        PromptInjectionFilter injectionFilter
    ) {
        this.caller = caller;
        this.objectMapper = objectMapper;
        this.injectionFilter = injectionFilter;
    }

    private static String joinAndUnwrap(CompletableFuture<String> future) {
        return future.join();
    }

    private static LlmJudgmentResult skippedJudgment() {
        return LlmJudgmentResult.builder()
                                .status(RuleStatus.SKIPPED)
                                .build();
    }

    public <T> Optional<T> extractStructured(
        Resource systemPromptResource,
        String userContent,
        Class<T> responseType
    ) {
        String sanitized = injectionFilter.sanitize(userContent);

        return parseResponse(
            joinAndUnwrap(
                caller.call(
                    loadResource(systemPromptResource),
                    sanitized
                )
            ),
            responseType
        );
    }

    public <T> Optional<T> extractStructuredWithImage(
        Resource systemPromptResource,
        String userContent,
        byte[] imageBytes,
        MimeType mimeType,
        Class<T> responseType
    ) {
        return extractStructuredWithImages(
            systemPromptResource,
            userContent,
            java.util.List.of(new LlmImageInput(
                imageBytes,
                mimeType
            )),
            responseType
        );
    }

    public <T> Optional<T> extractStructuredWithImages(
        Resource systemPromptResource,
        String userContent,
        java.util.List<LlmImageInput> images,
        Class<T> responseType
    ) {
        return parseResponse(
            joinAndUnwrap(
                caller.callWithImages(
                    loadResource(systemPromptResource),
                    userContent,
                    images
                )
            ),
            responseType
        );
    }

    public Optional<String> extractRaw(
        Resource systemPromptResource,
        String userContent
    ) {
        String sanitized = injectionFilter.sanitize(userContent);

        String raw = joinAndUnwrap(
            caller.call(
                loadResource(systemPromptResource),
                sanitized
            )
        );

        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }

        return Optional.of(
            JSON_RESPONSE_CLEANER.clean(raw)
        );
    }

    public LlmJudgmentResult judgeSnippet(
        String prompt,
        String snippet
    ) {
        String sanitized = injectionFilter.sanitize(snippet);

        String raw = joinAndUnwrap(
            caller.callJudge(
                String.format("%s%n%n%s", prompt, sanitized)
            )
        );

        return parseJudgmentResponse(raw);
    }

    private LlmJudgmentResult parseJudgmentResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return skippedJudgment();
        }

        return parseResponse(raw, LlmJudgmentResult.class)
            .map(this::applyStatusFromFinding)
            .orElseGet(LlmAdapter::skippedJudgment);
    }

    private LlmJudgmentResult applyStatusFromFinding(LlmJudgmentResult result) {
        RuleStatus status = result.isFinding() ? RuleStatus.FAIL : RuleStatus.PASS;
        result.setStatus(status);
        return result;
    }

    private String loadResource(Resource resource) {
        try {
            return StreamUtils.copyToString(
                resource.getInputStream(),
                StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load prompt resource: %s", resource.getDescription()),
                exception
            );
        }
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
