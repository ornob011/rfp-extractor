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
    ) throws IOException {
        Map<Integer, String> pageTexts = state.pageTexts();
        Map<Integer, Double> pageConfidences = state.pageConfidences();

        for (PageSummary page : state.pageClassifications().stream()
                                     .filter(p -> p.getClassification() == PageClassification.SCANNED)
                                     .toList()) {
            addReconstructedTables(
                state.documentPath(),
                page,
                pageTexts,
                pageConfidences,
                tables
            );
        }
    }

    private void addReconstructedTables(
        String documentPath,
        PageSummary page,
        Map<Integer, String> pageTexts,
        Map<Integer, Double> pageConfidences,
        List<TableExtractionResult> tables
    ) throws IOException {
        int pageNum = page.getPageNumber();
        String ocrText = pageTexts.getOrDefault(pageNum, StringUtils.EMPTY);
        double confidence = pageConfidences.getOrDefault(pageNum, 0.0);

        tables.addAll(
            scannedTableReconstructor.reconstructTables(
                documentPath,
                ocrText,
                pageNum,
                confidence
            )
        );
    }
}
