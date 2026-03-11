package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.ConfidenceSource;
import com.dsi.rfp.domain.model.RepairComponentType;
import com.dsi.rfp.domain.model.RepairStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepairDecisionTableTest {

    private final RepairDecisionTable table = new RepairDecisionTable(
        new RepairPolicyConfig()
    );

    @Test
    void shouldReturnRetrySectionSegmentationForSection() {
        assertThat(table.strategyFor(RepairComponentType.SECTION, ConfidenceSource.HEADING_STYLE))
            .isEqualTo(RepairStrategy.RETRY_SECTION_SEGMENTATION);
    }

    @Test
    void shouldReturnSwitchTableModeForLatticeTable() {
        assertThat(table.strategyFor(RepairComponentType.TABLE, ConfidenceSource.LATTICE))
            .isEqualTo(RepairStrategy.SWITCH_TABLE_MODE);
    }

    @Test
    void shouldReturnSwitchTableModeForStreamTable() {
        assertThat(table.strategyFor(RepairComponentType.TABLE, ConfidenceSource.STREAM))
            .isEqualTo(RepairStrategy.SWITCH_TABLE_MODE);
    }

    @Test
    void shouldReturnRetryHigherDpiForVlmTableReconstructTable() {
        assertThat(table.strategyFor(RepairComponentType.TABLE, ConfidenceSource.VLM_TABLE_RECONSTRUCT))
            .isEqualTo(RepairStrategy.RETRY_SCANNED_TABLE_AT_HIGHER_DPI);
    }

    @Test
    void shouldReturnWidenEntityContextForEntity() {
        assertThat(table.strategyFor(RepairComponentType.ENTITY, ConfidenceSource.LLM))
            .isEqualTo(RepairStrategy.WIDEN_ENTITY_CONTEXT);
    }
}
