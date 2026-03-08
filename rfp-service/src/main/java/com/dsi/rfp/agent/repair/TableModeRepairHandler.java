package com.dsi.rfp.agent.repair;

import com.dsi.rfp.adapter.table.LatticeTableExtractor;
import com.dsi.rfp.adapter.table.StreamTableExtractor;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.ConfidenceSource;
import com.dsi.rfp.domain.model.RepairStrategy;
import com.dsi.rfp.domain.model.RepairableComponent;
import com.dsi.rfp.domain.model.TableExtractionResult;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
public class TableModeRepairHandler implements RepairHandler {

    private final TableRepairSupport tableRepairSupport;
    private final Map<ConfidenceSource, BiFunction<String, Integer, List<TableExtractionResult>>> extractors;

    public TableModeRepairHandler(
        LatticeTableExtractor latticeExtractor,
        StreamTableExtractor streamExtractor,
        TableRepairSupport tableRepairSupport
    ) {
        this.tableRepairSupport = tableRepairSupport;
        extractors = extractorMap(
            latticeExtractor,
            streamExtractor
        );
    }

    @Override
    public RepairStrategy strategy() {
        return RepairStrategy.SWITCH_TABLE_MODE;
    }

    @Override
    public Map<String, Object> repair(
        String componentId,
        ExtractionState state,
        int attemptNumber
    ) {
        TableExtractionResult current = tableRepairSupport.findCurrentTable(
            componentId,
            state
        );

        List<TableExtractionResult> extracted = Optional.ofNullable(
                                                            state.repairableComponents().get(componentId)
                                                        )
                                                        .map(RepairableComponent::getConfidenceSource)
                                                        .map(extractors::get)
                                                        .map(extractor -> extractor.apply(
                                                            state.documentPath(),
                                                            current.getPageStart()
                                                        ))
                                                        .orElse(List.of());

        return Map.of(
            ExtractionState.Key.TABLES.value(),
            tableRepairSupport.replaceWithPreservedIdentity(
                state.tables(),
                current,
                extracted
            )
        );
    }

    private Map<ConfidenceSource, BiFunction<String, Integer, List<TableExtractionResult>>> extractorMap(
        LatticeTableExtractor latticeExtractor,
        StreamTableExtractor streamExtractor
    ) {
        EnumMap<ConfidenceSource, BiFunction<String, Integer, List<TableExtractionResult>>> map =
            new EnumMap<>(ConfidenceSource.class);

        map.put(
            ConfidenceSource.LATTICE,
            streamExtractor::extractFromPage
        );
        map.put(
            ConfidenceSource.STREAM,
            latticeExtractor::extractFromPage
        );

        return Map.copyOf(map);
    }
}
