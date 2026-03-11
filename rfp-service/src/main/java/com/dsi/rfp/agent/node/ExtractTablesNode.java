package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.table.ScannedTableReconstructor;
import com.dsi.rfp.adapter.table.TableContinuationDetector;
import com.dsi.rfp.adapter.table.TableExtractor;
import com.dsi.rfp.adapter.table.TableSectionLinker;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExtractTablesNode implements NodeAction<ExtractionState> {

    private final TableExtractor tableExtractor;
    private final TableContinuationDetector continuationDetector;
    private final TableSectionLinker sectionLinker;
    private final ScannedTableReconstructor scannedTableReconstructor;

    public ExtractTablesNode(
        TableExtractor tableExtractor,
        TableContinuationDetector continuationDetector,
        TableSectionLinker sectionLinker,
        ScannedTableReconstructor scannedTableReconstructor
    ) {
        this.tableExtractor = tableExtractor;
        this.continuationDetector = continuationDetector;
        this.sectionLinker = sectionLinker;
        this.scannedTableReconstructor = scannedTableReconstructor;
    }

    @Override
    public Map<String, Object> apply(
        ExtractionState state
    ) throws Exception {
        log.info(
            "event=tables.start component=ExtractTablesNode jobId={}",
            state.jobId()
        );

        List<TableExtractionResult> tables = new ArrayList<>(
            tableExtractor.extractFromDocument(
                state.documentPath(),
                state.pageClassifications()
            )
        );

        reconstructScannedTables(state, tables);

        tables = continuationDetector.detect(tables);

        sectionLinker.link(
            tables,
            state.sections(),
            state.clauses()
        );

        log.info(
            "event=tables.complete jobId={} count={}",
            state.jobId(),
            tables.size()
        );

        return Map.of(
            ExtractionState.Key.TABLES.value(),
            tables
        );
    }

    private void reconstructScannedTables(
        ExtractionState state,
        List<TableExtractionResult> tables
    ) {
        Map<Integer, List<TableExtractionResult>> cachedVlmTables = state.vlmTables();
        Map<Integer, String> pageTexts = state.pageTexts();
        Map<Integer, Double> pageConfidences = state.pageConfidences();

        List<PageSummary> scannedPages = state.pageClassifications()
                                              .stream()
                                              .filter(p -> p.getClassification() == PageClassification.SCANNED)
                                              .toList();

        Set<Integer> cachedPages = cachedVlmTables.entrySet()
                                                  .stream()
                                                  .filter(entry -> !entry.getValue().isEmpty())
                                                  .map(Map.Entry::getKey)
                                                  .collect(Collectors.toSet());

        scannedPages.stream()
                    .filter(page -> cachedPages.contains(page.getPageNumber()))
                    .forEach(page -> {
                        log.info(
                            "event=table.vlmCacheHit page={}",
                            page.getPageNumber()
                        );
                        tables.addAll(
                            cachedVlmTables.get(page.getPageNumber())
                        );
                    });

        List<PageSummary> uncachedPages = scannedPages.stream()
                                                      .filter(page -> !cachedPages.contains(page.getPageNumber()))
                                                      .toList();

        uncachedPages.forEach(page -> tables.addAll(
            reconstructForPage(
                state.documentPath(),
                page.getPageNumber(),
                pageTexts.getOrDefault(
                    page.getPageNumber(),
                    ""
                ),
                pageConfidences.getOrDefault(
                    page.getPageNumber(),
                    0.0
                )
            )
        ));
    }

    private List<TableExtractionResult> reconstructForPage(
        String documentPath,
        int pageNum,
        String ocrText,
        double confidence
    ) {
        return scannedTableReconstructor.reconstructTables(
            documentPath,
            ocrText,
            pageNum,
            confidence
        );
    }
}
