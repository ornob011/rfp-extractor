package com.dsi.rfp.application.service;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairLogEntry;
import com.dsi.rfp.domain.model.RepairOutcome;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RepairAuditService {

    public String formatEntry(RepairLogEntry entry) {
        RepairOutcome outcome = resolveOutcome(entry);

        return String.format(
            "[%s] component=%s strategy=%s attempt=%d %.3f → %.3f %s reason=%s",
            entry.getTimestamp(),
            entry.getComponentId(),
            entry.getStrategy(),
            entry.getAttemptNumber(),
            entry.getBeforeConfidence(),
            entry.getAfterConfidence(),
            outcome.label(),
            entry.getReason()
        );
    }

    public String formatSummary(ExtractionState state) {
        long improved = state.repairLog().stream()
                             .map(this::resolveOutcome)
                             .filter(outcome -> outcome == RepairOutcome.IMPROVED)
                             .count();

        return String.format(
            "Repair summary: %d repairs attempted, %d improved, manualReviewRequired=%d, totalIterations=%d",
            state.repairLog().size(),
            improved,
            state.manualReviewRequired().size(),
            state.totalRepairIterations()
        );
    }

    public List<String> formatAll(List<RepairLogEntry> entries) {
        return entries.stream()
                      .map(this::formatEntry)
                      .toList();
    }

    private RepairOutcome resolveOutcome(RepairLogEntry entry) {
        return java.util.Optional.ofNullable(entry.getOutcome())
                                 .orElse(RepairOutcome.NOT_IMPROVED);
    }
}
