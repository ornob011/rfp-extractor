package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableCell;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Trie;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Component
public class TableContinuationDetector {

    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();
    private final TableExtractionConfig config;
    private final Trie footerTrie;
    private final List<BiPredicate<TableExtractionResult, TableExtractionResult>> continuationSignals;

    public TableContinuationDetector(TableExtractionConfig config) {
        this.config = config;
        footerTrie = Trie.builder()
                         .ignoreCase()
                         .addKeywords(config.continuationFooterKeywords())
                         .build();
        continuationSignals = List.of(
            this::noFooterSignal,
            this::similarHeadersSignal
        );
    }

    public List<TableExtractionResult> detect(
        List<TableExtractionResult> tables
    ) {
        if (tables.size() < 2) {
            return tables;
        }

        List<TableExtractionResult> merged = new ArrayList<>();
        TableExtractionResult current = tables.getFirst();

        for (TableExtractionResult next : tables.subList(1, tables.size())) {
            boolean mergeCandidate = shouldMerge(current, next);

            if (mergeCandidate) {
                log.info(
                    "event=table.merge pages={}-{}",
                    current.getPageStart(),
                    next.getPageEnd()
                );
                current = merge(current, next);
                continue;
            }

            merged.add(current);
            current = next;
        }

        merged.add(current);
        return merged;
    }

    private boolean shouldMerge(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        boolean structuralCompatibility = isAdjacentPage(current, next) && hasCompatibleColumns(current, next);

        int signals = continuationSignals.stream()
                                         .map(signal -> signal.test(current, next))
                                         .mapToInt(this::toInt)
                                         .sum();

        return structuralCompatibility && signals >= config.continuationRequiredSignals();
    }

    private boolean hasCompatibleColumns(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        return current.getHeaders().size() == next.getHeaders().size();
    }

    private boolean isAdjacentPage(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        return next.getPageStart() == current.getPageEnd() + 1;
    }

    private boolean noFooterSignal(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        return !hasFooterRow(current);
    }

    private boolean similarHeadersSignal(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        return hasSimilarHeaders(current, next);
    }

    private boolean hasFooterRow(TableExtractionResult table) {
        String rowText = table.getGrid()
                              .stream()
                              .reduce((left, right) -> right)
                              .orElse(List.of())
                              .stream()
                              .map(TableCell::getValue)
                              .filter(value -> !value.isBlank())
                              .collect(Collectors.joining(StringUtils.SPACE));

        return !footerTrie.parseText(rowText).isEmpty();
    }

    private boolean hasSimilarHeaders(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        List<String> leftHeaders = current.getHeaders();
        List<String> rightHeaders = next.getHeaders();

        boolean comparable = leftHeaders.size() == rightHeaders.size()
                             && !leftHeaders.isEmpty();

        return comparable
               && IntStream.range(0, leftHeaders.size())
                           .allMatch(index -> withinDistance(
                               leftHeaders.get(index),
                               rightHeaders.get(index)
                           ));
    }

    private boolean withinDistance(
        String left,
        String right
    ) {
        int distance = levenshtein.apply(
            left.toLowerCase(),
            right.toLowerCase()
        );

        return distance <= config.continuationHeaderDistanceThreshold();
    }

    private TableExtractionResult merge(
        TableExtractionResult current,
        TableExtractionResult next
    ) {
        int startIndex = toInt(hasSimilarHeaders(current, next));
        int rowBase = current.getGrid().size();
        List<List<TableCell>> nextRows = next.getGrid();

        List<List<TableCell>> mergedGrid = Stream.concat(
            current.getGrid().stream(),
            IntStream.range(startIndex, nextRows.size())
                     .mapToObj(index -> reindexRow(
                         nextRows.get(index),
                         rowBase + index - startIndex
                     ))
        ).toList();

        return TableExtractionResult.builder()
                                    .tableId(current.getTableId())
                                    .sectionId(current.getSectionId())
                                    .clauseId(current.getClauseId())
                                    .pageStart(current.getPageStart())
                                    .pageEnd(next.getPageEnd())
                                    .provenance(current.getProvenance())
                                    .caption(current.getCaption())
                                    .type(current.getType())
                                    .headers(current.getHeaders())
                                    .grid(mergedGrid)
                                    .confidence(current.getConfidence())
                                    .build();
    }

    private List<TableCell> reindexRow(
        List<TableCell> row,
        int rowIndex
    ) {
        return row.stream()
                  .map(cell -> TableCell.builder()
                                        .row(rowIndex)
                                        .col(cell.getCol())
                                        .value(cell.getValue())
                                        .rowspan(cell.getRowspan())
                                        .colspan(cell.getColspan())
                                        .isHeader(false)
                                        .build())
                  .toList();
    }

    private int toInt(boolean value) {
        return Boolean.compare(value, false);
    }
}
