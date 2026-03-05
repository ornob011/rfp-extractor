package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.table.TableContinuationDetector;
import com.dsi.rfp.adapter.table.TableExtractor;
import com.dsi.rfp.adapter.table.TableSectionLinker;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExtractTablesNode implements NodeAction<ExtractionState> {

    private final TableExtractor tableExtractor;
    private final TableContinuationDetector continuationDetector;
    private final TableSectionLinker sectionLinker;

    public ExtractTablesNode(
        TableExtractor tableExtractor,
        TableContinuationDetector continuationDetector,
        TableSectionLinker sectionLinker
    ) {
        this.tableExtractor = tableExtractor;
        this.continuationDetector = continuationDetector;
        this.sectionLinker = sectionLinker;
    }

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        log.info(
            "event=tables.start component=ExtractTablesNode jobId={}",
            state.jobId()
        );

        List<TableExtractionResult> tables =
            tableExtractor.extractFromDocument(
                state.documentPath(),
                state.pageClassifications()
            );

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
}
