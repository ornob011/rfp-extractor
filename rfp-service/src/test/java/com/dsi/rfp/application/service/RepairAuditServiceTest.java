package com.dsi.rfp.application.service;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairComponentType;
import com.dsi.rfp.domain.model.RepairLogEntry;
import com.dsi.rfp.domain.model.RepairOutcome;
import com.dsi.rfp.domain.model.RepairStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepairAuditServiceTest {

    private final RepairAuditService service = new RepairAuditService();

    @Test
    void shouldFormatImprovedEntryWithImprovedLabel() {
        RepairLogEntry entry = RepairLogEntry.builder()
                                             .componentId("t-001")
                                             .componentType(RepairComponentType.TABLE)
                                             .attemptNumber(1)
                                             .strategy(RepairStrategy.SWITCH_TABLE_MODE)
                                             .beforeConfidence(0.55)
                                             .afterConfidence(0.87)
                                             .outcome(RepairOutcome.IMPROVED)
                                             .reason("SWITCH_TABLE_MODE")
                                             .timestamp(Instant.now())
                                             .build();

        String formatted = service.formatEntry(entry);

        assertThat(formatted).contains("SWITCH_TABLE_MODE");
        assertThat(formatted).contains("0.550");
        assertThat(formatted).contains("0.870");
        assertThat(formatted).contains("IMPROVED");
    }

    @Test
    void shouldFormatNoImprovementEntryWithNotImprovedLabel() {
        RepairLogEntry entry = RepairLogEntry.builder()
                                             .componentId("e-001")
                                             .componentType(RepairComponentType.ENTITY)
                                             .attemptNumber(1)
                                             .strategy(RepairStrategy.WIDEN_ENTITY_CONTEXT)
                                             .beforeConfidence(0.45)
                                             .afterConfidence(0.45)
                                             .outcome(RepairOutcome.NOT_IMPROVED)
                                             .reason("WIDEN_ENTITY_CONTEXT")
                                             .timestamp(Instant.now())
                                             .build();

        String formatted = service.formatEntry(entry);

        assertThat(formatted).contains("WIDEN_ENTITY_CONTEXT");
        assertThat(formatted).contains("NOT IMPROVED");
    }

    @Test
    void shouldFormatSummaryWithCorrectCounts() {
        List<RepairLogEntry> entries = new ArrayList<>(List.of(
            buildEntry(0.55, 0.87),
            buildEntry(0.45, 0.80),
            buildEntry(0.40, 0.40)
        ));

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.REPAIR_LOG.value(), entries);
        data.put(
            ExtractionState.Key.TOTAL_REPAIR_ITERATIONS.value(),
            3
        );
        data.put(
            ExtractionState.Key.MANUAL_REVIEW_REQUIRED.value(),
            List.of("comp-1")
        );
        ExtractionState state = new ExtractionState(data);

        String summary = service.formatSummary(state);

        assertThat(summary).contains("3 repairs attempted");
        assertThat(summary).contains("2 improved");
        assertThat(summary).contains("manualReviewRequired=1");
    }

    private RepairLogEntry buildEntry(
        double before,
        double after
    ) {
        return RepairLogEntry.builder()
                             .componentId("comp")
                             .componentType(RepairComponentType.TABLE)
                             .attemptNumber(1)
                             .strategy(RepairStrategy.SWITCH_TABLE_MODE)
                             .beforeConfidence(before)
                             .afterConfidence(after)
                             .outcome(resolveOutcome(
                                 before,
                                 after
                             ))
                             .reason("test")
                             .timestamp(Instant.now())
                             .build();
    }

    private RepairOutcome resolveOutcome(
        double before,
        double after
    ) {
        return switch (Integer.signum(Double.compare(
            after,
            before
        ))) {
            case 1 -> RepairOutcome.IMPROVED;
            default -> RepairOutcome.NOT_IMPROVED;
        };
    }
}
