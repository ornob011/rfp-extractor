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
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        Map<Integer, String> pageTexts = state.pageTexts();
        Map<Integer, Double> pageConfidences = state.pageConfidences();

        List<PageSummary> scannedPages = state.pageClassifications()
                                              .stream()
                                              .filter(p -> p.getClassification() == PageClassification.SCANNED)
                                              .toList();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            List<CompletableFuture<List<TableExtractionResult>>> futures = scannedPages.stream()
                                                                                       .map(page -> CompletableFuture.supplyAsync(
                                                                                           () -> reconstructForPage(
                                                                                               state.documentPath(),
                                                                                               page.getPageNumber(),
                                                                                               pageTexts.getOrDefault(page.getPageNumber(), StringUtils.EMPTY),
                                                                                               pageConfidences.getOrDefault(page.getPageNumber(), 0.0)
                                                                                           ),
                                                                                           executor
                                                                                       ))
                                                                                       .toList();

            futures.stream()
                   .map(CompletableFuture::join)
                   .forEach(tables::addAll);
        } finally {
            executor.shutdown();
        }
    }

    private List<TableExtractionResult> reconstructForPage(
        String documentPath,
        int pageNum,
        String ocrText,
        double confidence
    ) {
        try {
            return scannedTableReconstructor.reconstructTables(
                documentPath,
                ocrText,
                pageNum,
                confidence
            );
        } catch (IOException exception) {
            log.warn(
                "event=table.scannedReconstructFailed page={} error={}",
                pageNum,
                exception.getMessage(),
                exception
            );

            return List.of();
        }
    }
}
