package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LatticeTableExtractor {

    private final TableEngineClient tableEngineClient;
    private final TableTypeClassifier classifier;
    private final TableExtractionConfig config;

    public LatticeTableExtractor(
        TableEngineClient tableEngineClient,
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
                                    config.latticeStrategy()
                                ).stream()
                                .map(table -> toDomain(table, pageNum))
                                .toList();
    }

    public List<TableExtractionResult> toDomainList(
        List<TableEngineTable> tables,
        int pageNum
    ) {
        return tables.stream()
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
                                                                    .score(table.confidence())
                                                                    .method(resolveMethod(table.method(), config.latticeMethod()))
                                                                    .build())
                                    .build();
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
