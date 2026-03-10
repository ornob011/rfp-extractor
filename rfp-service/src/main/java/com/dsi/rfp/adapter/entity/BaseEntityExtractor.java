package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.exception.LlmResponseParseException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.Section;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseEntityExtractor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    protected final LlmAdapter llmAdapter;
    protected final DocumentChunkingService chunkingService;
    protected final ObjectMapper objectMapper;
    protected final EntityExtractorMetadataRegistry metadataRegistry;

    public final Map<String, Object> extract(
        List<Section> sections,
        List<Clause> clauses,
        ExtractionState state
    ) {
        List<DocumentChunk> chunks = chunkingService.chunkDocument(
            sections,
            clauses
        );

        Map<String, Object> accumulated = new LinkedHashMap<>();

        for (DocumentChunk chunk : chunks) {
            String prompt = buildPrompt(chunk);

            Map<String, Object> parsed = callAndParse(
                prompt,
                state.jobId()
            );

            validateFields(parsed);

            mergeInto(
                accumulated,
                parsed
            );
        }

        log.info(
            "event=entity.extract.done component={} jobId={} fields={}",
            getClass().getSimpleName(),
            state.jobId(),
            accumulated.size()
        );

        return accumulated;
    }

    protected String buildPrompt(DocumentChunk chunk) {
        String template = loadPromptTemplate();

        return template.replace("{{contextHeader}}", chunk.getContextHeader())
                       .replace("{{chunkText}}", chunk.getRawText());
    }

    protected abstract PromptKey promptKey();

    protected void validateFields(Map<String, Object> parsed) {
        metadataRegistry.requiredFields(promptKey()).stream()
                        .filter(f -> !parsed.containsKey(f))
                        .forEach(f -> log.warn(
                            "event=entity.field.missing component={} field={}",
                            getClass().getSimpleName(),
                            f
                        ));
    }

    private String loadPromptTemplate() {
        Resource promptResource = metadataRegistry.promptResource(promptKey());

        try (InputStream input = promptResource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load prompt resource for key: %s", promptKey()),
                exception
            );
        }
    }

    private Map<String, Object> callAndParse(
        String prompt,
        Long jobId
    ) {
        return llmAdapter.extractRaw(metadataRegistry.systemPromptResource(), prompt)
                         .map(raw -> parseJson(raw, jobId))
                         .orElseGet(Map::of);
    }

    private Map<String, Object> parseJson(
        String json,
        Long jobId
    ) {
        log.info(
            "event=entity.parse.start component={} jobId={}",
            getClass().getSimpleName(),
            jobId
        );

        return parseJson(json);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new LlmResponseParseException(
                String.format("Failed to parse LLM response for extractor: %s", getClass().getSimpleName()),
                json,
                exception
            );
        }
    }

    private void mergeInto(
        Map<String, Object> target,
        Map<String, Object> source
    ) {
        source.forEach(target::putIfAbsent);
    }
}
