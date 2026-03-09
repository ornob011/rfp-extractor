package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.model.LlmJudgmentResult;
import com.dsi.rfp.domain.model.RuleStatus;
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

    private static LlmJudgmentResult skippedJudgment() {
        return LlmJudgmentResult.builder()
                                .status(RuleStatus.SKIPPED)
                                .build();
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

    public LlmJudgmentResult judgeSnippet(
        String prompt,
        String snippet
    ) {
        String raw = joinAndUnwrap(
            caller.callJudge(
                String.format("%s%n%n%s", prompt, snippet)
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
