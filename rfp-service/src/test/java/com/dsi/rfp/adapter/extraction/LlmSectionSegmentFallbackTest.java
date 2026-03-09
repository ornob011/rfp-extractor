package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.Section;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmSectionSegmentFallbackTest {

    @Mock
    private LlmAdapter llmAdapter;

    private LlmSectionSegmentFallback createFallback() {
        return new LlmSectionSegmentFallback(
            llmAdapter,
            new SectionFallbackConfig(),
            new PromptTemplateRenderer()
        );
    }

    @Test
    void shouldFireWhenMoreThan20PagesAndFewerThan3Sections() {
        assertThat(createFallback().shouldFire(buildState(25, 2))).isTrue();
    }

    @Test
    void shouldNotFireWhenEnoughSectionsExist() {
        assertThat(createFallback().shouldFire(buildState(25, 5))).isFalse();
    }

    @Test
    void shouldNotFireForShortDocuments() {
        assertThat(createFallback().shouldFire(buildState(15, 1))).isFalse();
    }

    @Test
    void shouldMergeLlmSectionsDeduplicatingByLevenshtein() {
        SectionFallbackResponse response = new SectionFallbackResponse(
            List.of(
                new SectionFallbackResponse.SectionSuggestion("Introduction", 1, 1),
                new SectionFallbackResponse.SectionSuggestion("Background", 1, 2),
                new SectionFallbackResponse.SectionSuggestion("Scope", 1, 3),
                new SectionFallbackResponse.SectionSuggestion("Evaluation", 1, 5)
            )
        );
        when(llmAdapter.extractStructured(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(SectionFallbackResponse.class)))
            .thenReturn(Optional.of(response));

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.PAGE_CLASSIFICATIONS.value(), buildPages(25));
        data.put(ExtractionState.Key.SECTIONS.value(), new ArrayList<>(List.of(
            Section.builder()
                   .id(UUID.randomUUID())
                   .title("Introduction")
                   .level(1)
                   .pageStart(1)
                   .pageEnd(2)
                   .build()
        )));
        data.put(ExtractionState.Key.PAGE_TEXTS.value(), Map.of(
            0, "Some text page 0",
            1, "Some text page 1",
            2, "Some text page 2"
        ));

        Map<String, Object> result = createFallback().execute(new ExtractionState(data));

        List<Section> sections = sectionsFrom(result);
        assertThat(sections).hasSize(4);
    }

    @Test
    void shouldNotModifyStateWhenLlmCallFails() {
        when(llmAdapter.extractStructured(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(SectionFallbackResponse.class)))
            .thenReturn(Optional.empty());

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.PAGE_CLASSIFICATIONS.value(), buildPages(25));
        data.put(ExtractionState.Key.SECTIONS.value(), new ArrayList<>(List.of(
            Section.builder()
                   .id(UUID.randomUUID())
                   .title("Introduction")
                   .level(1)
                   .pageStart(1)
                   .pageEnd(2)
                   .build()
        )));

        List<Section> sections = sectionsFrom(
            createFallback().execute(new ExtractionState(data))
        );

        assertThat(sections).hasSize(1);
    }

    @Test
    void shouldTruncateDocumentTextToMaxChars() {
        when(llmAdapter.extractStructured(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(SectionFallbackResponse.class)))
            .thenReturn(Optional.of(new SectionFallbackResponse(List.of())));

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.PAGE_CLASSIFICATIONS.value(), buildPages(25));
        data.put(ExtractionState.Key.PAGE_TEXTS.value(), Map.of(0, "A".repeat(5000)));
        data.put(ExtractionState.Key.SECTIONS.value(), List.of());

        Map<String, Object> result = createFallback().execute(new ExtractionState(data));

        assertThat(result).containsKey(ExtractionState.Key.SECTIONS.value());
    }

    private ExtractionState buildState(int pageCount, int sectionCount) {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.PAGE_CLASSIFICATIONS.value(), buildPages(pageCount));
        data.put(
            ExtractionState.Key.SECTIONS.value(),
            IntStream.rangeClosed(1, sectionCount)
                     .mapToObj(i -> Section.builder()
                                           .id(UUID.randomUUID())
                                           .title(String.format("Section %d", i))
                                           .level(1)
                                           .pageStart(i)
                                           .pageEnd(i + 1)
                                           .build())
                     .toList()
        );

        return new ExtractionState(data);
    }

    private List<PageSummary> buildPages(int count) {
        return IntStream.rangeClosed(1, count)
                        .mapToObj(i -> PageSummary.builder()
                                                  .pageNumber(i)
                                                  .classification(PageClassification.DIGITAL)
                                                  .build())
                        .toList();
    }

    private List<Section> sectionsFrom(Map<String, Object> state) {
        return Optional.ofNullable(state.get(ExtractionState.Key.SECTIONS.value()))
                       .filter(List.class::isInstance)
                       .map(List.class::cast)
                       .stream()
                       .flatMap(List::stream)
                       .filter(Section.class::isInstance)
                       .map(Section.class::cast)
                       .toList();
    }
}
