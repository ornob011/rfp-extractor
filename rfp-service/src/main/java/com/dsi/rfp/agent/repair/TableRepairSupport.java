package com.dsi.rfp.agent.repair;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.TableExtractionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TableRepairSupport {

    public TableExtractionResult findCurrentTable(
        String componentId,
        ExtractionState state
    ) {
        UUID tableId = UUID.fromString(componentId);

        return state.tables()
                    .stream()
                    .filter(table -> tableId.equals(table.getTableId()))
                    .findFirst()
                    .orElseThrow();
    }

    public List<TableExtractionResult> replaceWithPreservedIdentity(
        List<TableExtractionResult> existingTables,
        TableExtractionResult current,
        List<TableExtractionResult> candidates
    ) {
        TableExtractionResult replacement = selectReplacement(
            current,
            candidates
        );

        List<TableExtractionResult> updatedTables = new ArrayList<>(existingTables);
        updatedTables.replaceAll(table -> preserveCurrentIdentity(
            table,
            current,
            replacement
        ));

        return updatedTables;
    }

    private TableExtractionResult selectReplacement(
        TableExtractionResult current,
        List<TableExtractionResult> candidates
    ) {
        return Optional.of(candidates)
                       .filter(list -> !list.isEmpty())
                       .map(list -> typedCandidate(
                           current,
                           list
                       ))
                       .map(candidate -> preserveIdentity(
                           current,
                           candidate
                       ))
                       .orElse(current);
    }

    private TableExtractionResult typedCandidate(
        TableExtractionResult current,
        List<TableExtractionResult> candidates
    ) {
        return candidates.stream()
                         .filter(candidate -> candidate.getType() == current.getType())
                         .findFirst()
                         .orElse(candidates.getFirst());
    }

    private TableExtractionResult preserveCurrentIdentity(
        TableExtractionResult table,
        TableExtractionResult current,
        TableExtractionResult replacement
    ) {
        return Optional.of(table)
                       .filter(value -> value.getTableId().equals(current.getTableId()))
                       .map(value -> replacement)
                       .orElse(table);
    }

    private TableExtractionResult preserveIdentity(
        TableExtractionResult current,
        TableExtractionResult replacement
    ) {
        replacement.setTableId(current.getTableId());
        replacement.setSectionId(current.getSectionId());
        replacement.setClauseId(current.getClauseId());

        return replacement;
    }
}
