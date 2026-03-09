package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableContinuationDetectorTest {

    @Test
    void shouldReturnSameListWhenSingleTable() {
        TableContinuationDetector detector = detector();
        List<TableExtractionResult> tables = List.of(
            table(1, 1, List.of("A", "B"))
        );

        List<TableExtractionResult> result = detector.detect(tables);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldMergeAdjacentTablesWithSameHeaders() {
        TableContinuationDetector detector = detector();
        List<TableExtractionResult> tables = List.of(
            table(1, 1, List.of("Name", "Score")),
            table(2, 2, List.of("Name", "Score"))
        );

        List<TableExtractionResult> result = detector.detect(tables);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPageStart()).isEqualTo(1);
        assertThat(result.getFirst().getPageEnd()).isEqualTo(2);
    }

    @Test
    void shouldNotMergeNonAdjacentPages() {
        TableContinuationDetector detector = detector();
        List<TableExtractionResult> tables = List.of(
            table(1, 1, List.of("A", "B")),
            table(5, 5, List.of("A", "B"))
        );

        List<TableExtractionResult> result = detector.detect(tables);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldNotMergeTablesWithDifferentHeaders() {
        TableContinuationDetector detector = detector();
        List<TableExtractionResult> tables = List.of(
            table(1, 1, List.of("Name", "Score")),
            table(2, 2, List.of("Item", "Price", "Qty"))
        );

        List<TableExtractionResult> result = detector.detect(tables);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldMergeThreeConsecutiveTables() {
        TableContinuationDetector detector = detector();
        List<TableExtractionResult> tables = List.of(
            table(1, 1, List.of("Col1", "Col2")),
            table(2, 2, List.of("Col1", "Col2")),
            table(3, 3, List.of("Col1", "Col2"))
        );

        List<TableExtractionResult> result = detector.detect(tables);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPageEnd()).isEqualTo(3);
    }

    @Test
    void shouldRemoveDuplicateHeadersOnMerge() {
        TableContinuationDetector detector = detector();
        TableExtractionResult t1 = table(1, 1, List.of("A", "B"));
        TableExtractionResult t2 = table(2, 2, List.of("A", "B"));

        int originalGridSize = t1.getGrid().size() + t2.getGrid().size();

        List<TableExtractionResult> result = detector.detect(
            List.of(t1, t2)
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getGrid().size())
            .isLessThan(originalGridSize);
    }

    private TableContinuationDetector detector() {
        return new TableContinuationDetector(new TableExtractionConfig());
    }

    private TableExtractionResult table(
        int pageStart,
        int pageEnd,
        List<String> headers
    ) {
        List<TableCell> headerRow = new java.util.ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            headerRow.add(TableCell.builder()
                                   .row(0)
                                   .col(i)
                                   .value(headers.get(i))
                                   .isHeader(true)
                                   .build());
        }

        List<TableCell> dataRow = new java.util.ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            dataRow.add(TableCell.builder()
                                 .row(1)
                                 .col(i)
                                 .value(String.format("data%d", i))
                                 .build());
        }

        return TableExtractionResult.builder()
                                    .pageStart(pageStart)
                                    .pageEnd(pageEnd)
                                    .provenance(TableProvenance.DIGITAL)
                                    .type(TableType.OTHER)
                                    .headers(headers)
                                    .grid(List.of(headerRow, dataRow))
                                    .confidence(ExtractionConfidence.builder()
                                                                    .score(0.8)
                                                                    .method("lattice")
                                                                    .build())
                                    .build();
    }
}
