package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.config.LlmProviderProperties;
import com.dsi.rfp.domain.exception.LlmResponseParseException;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.LlmProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAdapter {

    private static final int PARSE_ERROR_PREVIEW_LENGTH = 200;
    private final LlmResilientCaller caller;
    private final ObjectMapper objectMapper;
    private final LlmProviderProperties props;

    private static <T> T joinUnwrapped(CompletableFuture<T> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new LlmUnavailableException(
                "Unexpected LLM error: " + cause.getMessage(), cause);
        }
    }

    public <T> Optional<T> extractStructured(
        String systemPrompt, String userContent, Class<T> responseType
    ) {
        long startMs = System.currentTimeMillis();
        String model = resolveExtractionModel();
        String raw = joinUnwrapped(caller.call(systemPrompt, userContent));
        try {
            Optional<T> result = parseResponse(raw, responseType);
            logLlmCall("extract", model, startMs, true, null);
            return result;
        } catch (LlmResponseParseException e) {
            logLlmCall("extract", model, startMs, false, "PARSE_ERROR");
            throw e;
        }
    }

    public Optional<String> judgeSnippet(String prompt, String snippet) {
        long startMs = System.currentTimeMillis();
        String model = resolveJudgeModel();
        String raw = joinUnwrapped(caller.callJudge(prompt + "\n\n" + snippet));
        logLlmCall("judge", model, startMs, true, null);
        return Optional.ofNullable(raw).filter(StringUtils::hasText);
    }

    private <T> Optional<T> parseResponse(String rawResponse, Class<T> responseType) {
        if (!StringUtils.hasText(rawResponse)) {
            warnEmpty(responseType);
            return Optional.empty();
        }
        String cleaned = stripCodeFences(rawResponse.strip());
        try {
            return Optional.of(objectMapper.readValue(cleaned, responseType));
        } catch (JsonProcessingException e) {
            throw warnAndBuildParseFail(cleaned, responseType, e);
        }
    }

    private void warnEmpty(Class<?> type) {
        log.warn(
            "event=llm.empty component=LlmAdapter status=WARN"
            + " type={} traceId={} spanId={}",
            type.getSimpleName(), MDC.get("traceId"), MDC.get("spanId"));
    }

    private LlmResponseParseException warnAndBuildParseFail(
        String cleaned, Class<?> type, JsonProcessingException cause
    ) {
        String preview = cleaned.substring(
            0, Math.min(PARSE_ERROR_PREVIEW_LENGTH, cleaned.length()));
        log.warn(
            "event=llm.parse.fail component=LlmAdapter status=WARN"
            + " errorCode=LLM_PARSE_FAIL type={} preview={} traceId={} spanId={}",
            type.getSimpleName(), preview, MDC.get("traceId"), MDC.get("spanId"));
        return new LlmResponseParseException(
            "LLM response could not be parsed as " + type.getSimpleName(), cleaned, cause);
    }

    private String stripCodeFences(String text) {
        String result = text;
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.strip();
    }

    private void logLlmCall(
        String operation, String model, long startMs, boolean success, String failReason
    ) {
        long latencyMs = System.currentTimeMillis() - startMs;
        if (success) {
            log.info(
                "event=llm.call component=LlmAdapter status=SUCCESS"
                + " op={} model={} provider={} latencyMs={}",
                operation, model, props.getProvider().jsonValue(), latencyMs);
        } else {
            log.warn(
                "event=llm.call component=LlmAdapter status=FAIL"
                + " op={} model={} provider={} latencyMs={} reason={}",
                operation, model, props.getProvider().jsonValue(), latencyMs, failReason);
        }
    }

    private String resolveExtractionModel() {
        return props.getProvider() == LlmProvider.OPENROUTER
            ? props.getOpenrouter().getModel()
            : props.getOllama().getModel();
    }

    private String resolveJudgeModel() {
        return props.getProvider() == LlmProvider.OPENROUTER
            ? props.getOpenrouter().getModelJudge()
            : props.getOllama().getModelJudge();
    }
}
