package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableExtractorTest {

    @Mock
    private LatticeTableExtractor latticeExtractor;

    @Mock
    private StreamTableExtractor streamExtractor;

    @Mock
    private TableExtractionConfig config;

    @InjectMocks
    private TableExtractor extractor;

    @Test
    void shouldSkipScannedPages() {
        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.SCANNED),
            pageSummary(2, PageClassification.SCANNED)
        );

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(latticeExtractor);
    }

    @Test
    void shouldUseLatticeFirst() {
        when(config.extractionOrder()).thenReturn(List.of(
            TableExtractionStrategy.LATTICE,
            TableExtractionStrategy.STREAM
        ));

        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.DIGITAL)
        );

        TableExtractionResult table = sampleTable(1);
        when(latticeExtractor.extractFromPage(any(), eq(1)))
            .thenReturn(List.of(table));

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).hasSize(1);
        verifyNoInteractions(streamExtractor);
    }

    @Test
    void shouldFallbackToStreamWhenLatticeEmpty() {
        when(config.extractionOrder()).thenReturn(List.of(
            TableExtractionStrategy.LATTICE,
            TableExtractionStrategy.STREAM
        ));

        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.DIGITAL)
        );

        when(latticeExtractor.extractFromPage(any(), eq(1)))
            .thenReturn(List.of());
        TableExtractionResult table = sampleTable(1);
        when(streamExtractor.extractFromPage(any(), eq(1)))
            .thenReturn(List.of(table));

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldProcessMixedPages() {
        when(config.extractionOrder()).thenReturn(List.of(
            TableExtractionStrategy.LATTICE,
            TableExtractionStrategy.STREAM
        ));

        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.MIXED)
        );

        when(latticeExtractor.extractFromPage(any(), eq(1)))
            .thenReturn(List.of());
        when(streamExtractor.extractFromPage(any(), eq(1)))
            .thenReturn(List.of());

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).isEmpty();
    }

    private PageSummary pageSummary(
        int pageNumber,
        PageClassification classification
    ) {
        return PageSummary.builder()
                          .pageNumber(pageNumber)
                          .classification(classification)
                          .build();
    }

    private TableExtractionResult sampleTable(int page) {
        return TableExtractionResult.builder()
                                    .pageStart(page)
                                    .pageEnd(page)
                                    .provenance(TableProvenance.DIGITAL)
                                    .type(TableType.OTHER)
                                    .headers(List.of("A", "B"))
                                    .confidence(ExtractionConfidence.builder()
                                                                    .score(0.8)
                                                                    .method("lattice")
                                                                    .build())
                                    .build();
    }
}
