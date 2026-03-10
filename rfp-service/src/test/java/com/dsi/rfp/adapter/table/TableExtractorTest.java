package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableExtractorTest {

    @Mock
    private TableEngineClient tableEngineClient;

    @Mock
    private LatticeTableExtractor latticeExtractor;

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
        verifyNoInteractions(tableEngineClient);
        verifyNoInteractions(latticeExtractor);
    }

    @Test
    void shouldExtractDigitalPagesViaBatch() {
        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.DIGITAL),
            pageSummary(2, PageClassification.DIGITAL)
        );

        TableEngineTable engineTable = new TableEngineTable(
            null,
            List.of("A", "B"),
            List.of(),
            0.9,
            "lattice"
        );

        when(tableEngineClient.extractTablesBatch(any(), eq(List.of(1, 2))))
            .thenReturn(Map.of(
                1, List.of(engineTable),
                2, List.of()
            ));

        TableExtractionResult domainTable = sampleTable(1);
        when(latticeExtractor.toDomainList(List.of(engineTable), 1))
            .thenReturn(List.of(domainTable));
        when(latticeExtractor.toDomainList(List.of(), 2))
            .thenReturn(List.of());

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldProcessMixedPagesAsDigital() {
        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.MIXED)
        );

        when(tableEngineClient.extractTablesBatch(any(), eq(List.of(1))))
            .thenReturn(Map.of(1, List.of()));
        when(latticeExtractor.toDomainList(List.of(), 1))
            .thenReturn(List.of());

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFilterScannedAndBatchDigital() {
        List<PageSummary> pages = List.of(
            pageSummary(1, PageClassification.DIGITAL),
            pageSummary(2, PageClassification.SCANNED),
            pageSummary(3, PageClassification.DIGITAL)
        );

        TableEngineTable engineTable = new TableEngineTable(
            null,
            List.of("X"),
            List.of(),
            0.85,
            "lattice"
        );

        when(tableEngineClient.extractTablesBatch(any(), eq(List.of(1, 3))))
            .thenReturn(Map.of(
                1, List.of(engineTable),
                3, List.of()
            ));

        TableExtractionResult domainTable = sampleTable(1);
        when(latticeExtractor.toDomainList(List.of(engineTable), 1))
            .thenReturn(List.of(domainTable));
        when(latticeExtractor.toDomainList(List.of(), 3))
            .thenReturn(List.of());

        List<TableExtractionResult> result = extractor.extractFromDocument(
            "/tmp/sample.pdf",
            pages
        );

        assertThat(result).hasSize(1);
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
