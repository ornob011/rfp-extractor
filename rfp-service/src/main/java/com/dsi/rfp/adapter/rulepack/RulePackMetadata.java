package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.model.RfpType;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RulePackMetadata(
    Integer version,
    ResourceConfig resources,
    RoutingConfig routing,
    List<PackPolicy> packs
) {

    public RulePackMetadata {
        require(Objects.nonNull(version), "Rule pack config must define version");
        require(Objects.nonNull(resources), "Rule pack config must define resources");
        require(Objects.nonNull(routing), "Rule pack config must define routing");
        require(Objects.nonNull(packs) && !packs.isEmpty(), "Rule pack config must define packs");
    }

    private static void require(
        boolean condition,
        String message
    ) {
        if (!condition) {
            throw new EntityMetadataContractException(message);
        }
    }

    private static void requireText(
        String value,
        String message
    ) {
        require(
            StringUtils.isNotBlank(value),
            message
        );
    }

    public record ResourceConfig(
        String schemaResourcePath,
        String rulesPattern,
        String promptResourcePath
    ) {

        public ResourceConfig {
            requireText(schemaResourcePath, "Rule pack config must define schemaResourcePath");
            requireText(rulesPattern, "Rule pack config must define rulesPattern");
            requireText(promptResourcePath, "Rule pack config must define promptResourcePath");
        }
    }

    public record RoutingConfig(
        Integer minimumLeadMargin,
        Boolean runAllWhenNoCandidate,
        Double minimumConfidenceForExclusiveRouting,
        MergeConfig merge
    ) {

        public RoutingConfig {
            require(Objects.nonNull(minimumLeadMargin), "Rule pack config must define minimumLeadMargin");
            require(Objects.nonNull(runAllWhenNoCandidate), "Rule pack config must define runAllWhenNoCandidate");
            require(
                Objects.nonNull(minimumConfidenceForExclusiveRouting),
                "Rule pack config must define minimumConfidenceForExclusiveRouting"
            );
            require(Objects.nonNull(merge), "Rule pack config must define merge");
        }
    }

    public record MergeConfig(
        String packId,
        String packVersion
    ) {

        public MergeConfig {
            requireText(packId, "Rule pack config must define merge packId");
            requireText(packVersion, "Rule pack config must define merge packVersion");
        }
    }

    public record PackPolicy(
        String packId,
        RfpType rfpType,
        boolean enabled,
        Integer priority,
        Integer minimumApplicabilityScore,
        Integer exclusiveThreshold,
        SignalPolicy signals
    ) {

        public PackPolicy {
            requireText(packId, "Rule pack config pack must define packId");
            require(Objects.nonNull(rfpType), "Rule pack config pack must define rfpType");
            require(Objects.nonNull(priority), "Rule pack config pack must define priority");
            require(
                Objects.nonNull(minimumApplicabilityScore),
                "Rule pack config pack must define minimumApplicabilityScore"
            );
            require(Objects.nonNull(exclusiveThreshold), "Rule pack config pack must define exclusiveThreshold");
            require(Objects.nonNull(signals), "Rule pack config pack must define signals");
        }
    }

    public record SignalPolicy(
        WeightedKeywords sectionTitleKeywords,
        WeightedKeywords scopeSummaryKeywords,
        WeightedFields entityPresence,
        WeightedFieldKeywordMap entityValueKeywords
    ) {

        public SignalPolicy {
            require(sectionTitleKeywords != null, "Rule pack config signals must define sectionTitleKeywords");
            require(scopeSummaryKeywords != null, "Rule pack config signals must define scopeSummaryKeywords");
            require(entityPresence != null, "Rule pack config signals must define entityPresence");
            require(entityValueKeywords != null, "Rule pack config signals must define entityValueKeywords");
        }
    }

    public record WeightedKeywords(
        Integer weight,
        List<String> values
    ) {

        public WeightedKeywords {
            require(weight != null, "Rule pack weighted keyword signals must define weight");
            require(values != null && !values.isEmpty(), "Rule pack weighted keyword signals must define values");
        }
    }

    public record WeightedFields(
        Integer weight,
        List<String> fields
    ) {

        public WeightedFields {
            require(weight != null, "Rule pack weighted fields must define weight");
            require(fields != null && !fields.isEmpty(), "Rule pack weighted fields must define fields");
        }
    }

    public record WeightedFieldKeywordMap(
        Integer weight,
        Map<String, List<String>> fields
    ) {

        public WeightedFieldKeywordMap {
            require(weight != null, "Rule pack field keyword signals must define weight");
            require(fields != null && !fields.isEmpty(), "Rule pack field keyword signals must define fields");
        }
    }
}
