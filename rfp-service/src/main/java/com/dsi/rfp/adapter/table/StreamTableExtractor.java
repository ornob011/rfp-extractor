package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.*;
import com.dsi.rfp.domain.port.out.TableEnginePort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class StreamTableExtractor {

    private final TableEnginePort tableEngineClient;
    private final TableTypeClassifier classifier;
    private final TableExtractionConfig config;

    public StreamTableExtractor(
        TableEnginePort tableEngineClient,
        TableTypeClassifier classifier,
        TableExtractionConfig config
    ) {
        this.tableEngineClient = tableEngineClient;
        this.classifier = classifier;
        this.config = config;
    }

    public List<TableExtractionResult> extractFromPage(
        String documentPath,
        int pageNum
    ) {
        return tableEngineClient.extractTables(
                                    documentPath,
                                    pageNum,
                                    config.streamStrategy()
                                ).stream()
                                .map(table -> toDomain(table, pageNum))
                                .toList();
    }

    private TableExtractionResult toDomain(
        TableEngineTable table,
        int pageNum
    ) {
        List<String> headers = Optional.ofNullable(table.headers())
                                       .filter(list -> !list.isEmpty())
                                       .orElseGet(() -> deriveHeaders(table.grid()));

        TableType tableType = classifier.classify(
            headers,
            table.caption()
        );

        return TableExtractionResult.builder()
                                    .pageStart(pageNum)
                                    .pageEnd(pageNum)
                                    .provenance(TableProvenance.DIGITAL)
                                    .caption(table.caption())
                                    .type(tableType)
                                    .headers(headers)
                                    .grid(toGrid(table.grid()))
                                    .confidence(ExtractionConfidence.builder()
                                                                    .score(resolveConfidence(table.confidence(), config.streamConfidence()))
                                                                    .method(resolveMethod(table.method(), config.streamMethod()))
                                                                    .build())
                                    .build();
    }

    private double resolveConfidence(
        double score,
        double fallback
    ) {
        return Optional.of(score)
                       .filter(value -> value > 0.0)
                       .orElse(fallback);
    }

    private String resolveMethod(
        String method,
        String fallback
    ) {
        return Optional.ofNullable(method)
                       .filter(text -> !text.isBlank())
                       .orElse(fallback);
    }

    private List<String> deriveHeaders(
        List<List<TableEngineCell>> grid
    ) {
        return toGrid(grid).stream()
                           .findFirst()
                           .orElse(List.of())
                           .stream()
                           .map(TableCell::getValue)
                           .toList();
    }

    private List<List<TableCell>> toGrid(
        List<List<TableEngineCell>> engineGrid
    ) {
        return Optional.ofNullable(engineGrid)
                       .orElse(List.of())
                       .stream()
                       .map(this::toRow)
                       .toList();
    }

    private List<TableCell> toRow(
        List<TableEngineCell> row
    ) {
        return Optional.ofNullable(row)
                       .orElse(List.of())
                       .stream()
                       .map(this::toCell)
                       .toList();
    }

    private TableCell toCell(TableEngineCell cell) {
        return TableCell.builder()
                        .row(cell.row())
                        .col(cell.col())
                        .value(cell.value())
                        .rowspan(cell.rowspan())
                        .colspan(cell.colspan())
                        .isHeader(cell.isHeader())
                        .build();
    }
}
