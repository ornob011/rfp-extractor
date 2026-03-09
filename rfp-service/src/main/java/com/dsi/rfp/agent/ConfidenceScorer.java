package com.dsi.rfp.agent;

import com.dsi.rfp.adapter.table.TableExtractionConfig;
import com.dsi.rfp.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfidenceScorer {

    private final ConfidenceScoringConfig scoringConfig;
    private final EntityFieldReader entityFieldReader;
    private final TableExtractionConfig tableExtractionConfig;

    public Map<String, Double> scoreEntities(RfpEntities entities) {
        return entityFieldReader.readAllFields(entities)
                                .entrySet()
                                .stream()
                                .collect(
                                    LinkedHashMap::new,
                                    (map, entry) -> map.put(
                                        entry.getKey(),
                                        scoreValue(entry.getValue())
                                    ),
                                    Map::putAll
                                );
    }

    public double scoreSection(Section section) {
        return scoringConfig.sectionStrategyConfidence()
                            .getOrDefault(
                                sectionSource(section).name(),
                                0.0
                            );
    }

    public double scoreTable(TableExtractionResult table) {
        return scoringConfig.tableProvenanceConfidence()
                            .getOrDefault(
                                tableSource(table).name(),
                                0.0
                            );
    }

    public ConfidenceSource sectionSource(Section section) {
        return Optional.ofNullable(section.getConfidence())
                       .map(SectionConfidence::getMethod)
                       .map(this::sectionSource)
                       .orElse(ConfidenceSource.UNKNOWN);
    }

    public ConfidenceSource tableSource(TableExtractionResult table) {
        return Optional.ofNullable(table.getConfidence())
                       .map(ExtractionConfidence::getMethod)
                       .map(this::tableSource)
                       .orElse(ConfidenceSource.UNKNOWN);
    }

    public RepairableComponent repairableEntity(
        String componentId,
        Object value
    ) {
        return RepairableComponent.builder()
                                  .componentId(componentId)
                                  .componentType(RepairComponentType.ENTITY)
                                  .confidenceSource(ConfidenceSource.LLM)
                                  .currentConfidence(scoreValue(value))
                                  .build();
    }

    public RepairableComponent repairableSection(Section section) {
        return RepairableComponent.builder()
                                  .componentId(section.getId().toString())
                                  .componentType(RepairComponentType.SECTION)
                                  .confidenceSource(sectionSource(section))
                                  .currentConfidence(scoreSection(section))
                                  .build();
    }

    public RepairableComponent repairableTable(TableExtractionResult table) {
        return RepairableComponent.builder()
                                  .componentId(table.getTableId().toString())
                                  .componentType(RepairComponentType.TABLE)
                                  .confidenceSource(tableSource(table))
                                  .currentConfidence(scoreTable(table))
                                  .build();
    }

    public double scoreValue(Object value) {
        String normalized = Optional.ofNullable(value)
                                    .map(Object::toString)
                                    .map(String::strip)
                                    .orElse(StringUtils.EMPTY);

        return switch (normalized.length()) {
            case 0 -> 0.0;
            default -> Optional.of(normalized)
                               .filter(text -> text.length() < scoringConfig.shortTextLengthThreshold())
                               .map(text -> 0.5)
                               .orElse(1.0);
        };
    }

    public double completeness(Map<String, Double> scores) {
        long populated = scoringConfig.criticalFields()
                                      .stream()
                                      .filter(field -> scores.getOrDefault(field, 0.0) > 0.0)
                                      .count();

        return (double) populated / scoringConfig.criticalFields().size();
    }

    private ConfidenceSource sectionSource(HeadingDetectionMethod method) {
        return switch (method) {
            case TOC -> ConfidenceSource.TOC;
            case BOOKMARK -> ConfidenceSource.BOOKMARK;
            case HEADING_STYLE -> ConfidenceSource.HEADING_STYLE;
            case NUMBERED -> ConfidenceSource.NUMBERED;
            case BANGLA -> ConfidenceSource.BANGLA;
            case FONT_SIZE -> ConfidenceSource.FONT_SIZE;
            case ALL_CAPS -> ConfidenceSource.ALL_CAPS;
            case LLM -> ConfidenceSource.LLM;
        };
    }

    private ConfidenceSource tableSource(String method) {
        return Optional.ofNullable(method)
                       .filter(StringUtils::isNotBlank)
                       .map(this::resolveConfiguredTableSource)
                       .orElse(ConfidenceSource.UNKNOWN);
    }

    private ConfidenceSource resolveConfiguredTableSource(String method) {
        return Optional.of(method)
                       .filter(value -> value.equalsIgnoreCase(
                           tableExtractionConfig.scannedLlmMethod()
                       ))
                       .map(value -> ConfidenceSource.OCR_LLM_RECONSTRUCT)
                       .orElseGet(() -> switch (TableExtractionStrategy.fromString(method)) {
                           case LATTICE -> ConfidenceSource.LATTICE;
                           case STREAM -> ConfidenceSource.STREAM;
                       });
    }
}
