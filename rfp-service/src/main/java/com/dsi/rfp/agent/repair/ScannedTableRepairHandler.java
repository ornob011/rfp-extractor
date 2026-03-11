package com.dsi.rfp.agent.repair;

import com.dsi.rfp.adapter.table.ScannedTableReconstructor;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairStrategy;
import com.dsi.rfp.domain.model.TableExtractionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ScannedTableRepairHandler implements RepairHandler {

    private final ScannedTableReconstructor scannedTableReconstructor;
    private final TableRepairSupport tableRepairSupport;

    public ScannedTableRepairHandler(
        ScannedTableReconstructor scannedTableReconstructor,
        TableRepairSupport tableRepairSupport
    ) {
        this.scannedTableReconstructor = scannedTableReconstructor;
        this.tableRepairSupport = tableRepairSupport;
    }

    @Override
    public RepairStrategy strategy() {
        return RepairStrategy.RETRY_SCANNED_TABLE_OCR_AT_HIGHER_DPI;
    }

    @Override
    public Map<String, Object> repair(
        String componentId,
        ExtractionState state,
        int attemptNumber
    ) throws Exception {
        TableExtractionResult current = tableRepairSupport.findCurrentTable(
            componentId,
            state
        );

        List<TableExtractionResult> repaired = scannedTableReconstructor.reconstructTables(
            state.documentPath(),
            state.pageTexts().get(current.getPageStart()),
            current.getPageStart(),
            state.pageConfidences().getOrDefault(current.getPageStart(), 0.0)
        );

        return Map.of(
            ExtractionState.Key.TABLES.value(),
            tableRepairSupport.replaceWithPreservedIdentity(
                state.tables(),
                current,
                repaired
            )
        );
    }
}
