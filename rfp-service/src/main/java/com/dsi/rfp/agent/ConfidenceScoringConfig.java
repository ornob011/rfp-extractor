package com.dsi.rfp.agent;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class ConfidenceScoringConfig {

    private static final String CONFIG_PATH = "metadata/confidence-scoring-v1.yml";

    private final ConfigDocument config;
    private final List<String> criticalFieldKeys;

    public ConfidenceScoringConfig() {
        config = loadConfig();

        criticalFieldKeys = config.entityFields().stream()
                                  .filter(EntityFieldDef::critical)
                                  .map(EntityFieldDef::key)
                                  .toList();
    }

    public double lowConfidenceThreshold() {
        return config.thresholds().lowConfidence();
    }

    public double manualReviewThreshold() {
        return config.thresholds().manualReview();
    }

    public int shortTextLengthThreshold() {
        return config.thresholds().shortTextLength();
    }

    public double badgeHighThreshold() {
        return config.badge().high();
    }

    public double badgeMediumThreshold() {
        return config.badge().medium();
    }

    public String completenessKey() {
        return config.completeness().key();
    }

    public List<String> criticalFields() {
        return criticalFieldKeys;
    }

    public List<EntityFieldDef> entityFields() {
        return config.entityFields();
    }

    public Map<String, Double> sectionStrategyConfidence() {
        return config.sectionStrategyConfidence();
    }

    public Map<String, Double> tableProvenanceConfidence() {
        return config.tableProvenanceConfidence();
    }

    public int maxTotalRepairIterations() {
        return config.repair().maxTotalIterations();
    }

    public int maxRetriesPerItem() {
        return config.repair().maxRetriesPerItem();
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load confidence scoring config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Thresholds thresholds,
        Badge badge,
        Completeness completeness,
        List<EntityFieldDef> entityFields,
        Map<String, Double> sectionStrategyConfidence,
        Map<String, Double> tableProvenanceConfidence,
        Repair repair
    ) {
    }

    private record Thresholds(
        double lowConfidence,
        double manualReview,
        int shortTextLength
    ) {
    }

    private record Badge(
        double high,
        double medium
    ) {
    }

    private record Completeness(
        String key
    ) {
    }

    private record Repair(
        int maxTotalIterations,
        int maxRetriesPerItem
    ) {
    }

    public record EntityFieldDef(
        String key,
        String label,
        String category,
        String type,
        boolean critical
    ) {
    }
}
