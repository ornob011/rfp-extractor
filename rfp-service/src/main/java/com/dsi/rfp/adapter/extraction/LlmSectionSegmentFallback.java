package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.model.SectionConfidence;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LlmSectionSegmentFallback {

    private final LlmAdapter llmAdapter;
    private final SectionFallbackConfig config;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final LevenshteinDistance levenshteinDistance;
    private final String promptTemplate;
    private final Resource systemPromptResource;

    public LlmSectionSegmentFallback(
        LlmAdapter llmAdapter,
        SectionFallbackConfig config,
        PromptTemplateRenderer promptTemplateRenderer
    ) {
        this.llmAdapter = llmAdapter;
        this.config = config;
        this.promptTemplateRenderer = promptTemplateRenderer;
        levenshteinDistance = LevenshteinDistance.getDefaultInstance();
        promptTemplate = config.promptTemplate();
        systemPromptResource = config.systemPromptResource();
    }

    public boolean shouldFire(ExtractionState state) {
        return state.pageClassifications().size() > config.minPages()
               && state.sections().size() < config.minSections();
    }

    public Map<String, Object> execute(ExtractionState state) {
        String prompt = promptTemplateRenderer.render(
            promptTemplate,
            Map.of(
                "document_text",
                buildDocumentText(state)
            )
        );

        List<Section> mergedSections = new ArrayList<>(state.sections());

        llmAdapter.extractStructured(
                      systemPromptResource,
                      prompt,
                      SectionFallbackResponse.class
                  )
                  .map(SectionFallbackResponse::sections)
                  .stream()
                  .flatMap(List::stream)
                  .map(this::toSection)
                  .forEach(section -> mergeSection(
                      mergedSections,
                      section
                  ));

        log.info(
            "event=llm.section.fallback component=LlmSectionSegmentFallback"
            + " before={} after={}",
            state.sections().size(),
            mergedSections.size()
        );

        return Map.of(
            ExtractionState.Key.SECTIONS.value(),
            mergedSections
        );
    }

    private String buildDocumentText(ExtractionState state) {
        String excerpt = state.pageTexts()
                              .entrySet()
                              .stream()
                              .sorted(Map.Entry.comparingByKey())
                              .limit(config.pageWindow())
                              .map(Map.Entry::getValue)
                              .filter(StringUtils::isNotBlank)
                              .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()))
                              .strip();

        return excerpt.substring(
            0,
            Math.min(
                config.maxTextChars(),
                excerpt.length()
            )
        );
    }

    private Section toSection(SectionFallbackResponse.SectionSuggestion suggestion) {
        return Section.builder()
                      .id(UUID.randomUUID())
                      .title(suggestion.title())
                      .level(suggestion.level())
                      .pageStart(suggestion.approximate_page())
                      .pageEnd(suggestion.approximate_page())
                      .confidence(SectionConfidence.builder()
                                                   .score(config.confidenceScore())
                                                   .method(HeadingDetectionMethod.LLM)
                                                   .build())
                      .build();
    }

    private void mergeSection(
        List<Section> existingSections,
        Section candidate
    ) {
        findMatchingSection(
            existingSections,
            candidate
        ).ifPresentOrElse(
            existing -> updateExisting(
                existing,
                candidate
            ),
            () -> existingSections.add(candidate)
        );
    }

    private Optional<Section> findMatchingSection(
        List<Section> sections,
        Section candidate
    ) {
        return flatten(sections).stream()
                                .min(Comparator.comparingInt(section -> levenshteinDistance.apply(
                                    normalizeTitle(section.getTitle()),
                                    normalizeTitle(candidate.getTitle())
                                )))
                                .filter(section -> levenshteinDistance.apply(
                                    normalizeTitle(section.getTitle()),
                                    normalizeTitle(candidate.getTitle())
                                ) < config.dedupLevenshteinThreshold());
    }

    private List<Section> flatten(List<Section> sections) {
        return sections.stream()
                       .flatMap(section -> java.util.stream.Stream.concat(
                           java.util.stream.Stream.of(section),
                           flatten(section.getChildren()).stream()
                       ))
                       .toList();
    }

    private void updateExisting(
        Section existing,
        Section candidate
    ) {
        existing.setTitle(candidate.getTitle());
        existing.setLevel(candidate.getLevel());
        existing.setPageStart(candidate.getPageStart());
        existing.setPageEnd(candidate.getPageEnd());
        existing.setConfidence(candidate.getConfidence());
    }

    private String normalizeTitle(String title) {
        return Optional.ofNullable(title)
                       .map(String::strip)
                       .map(String::toLowerCase)
                       .orElse(StringUtils.EMPTY);
    }

}
