package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
class CandidatePackResolver {

    RulePackClassification resolve(
        List<RulePackMetadata.PackPolicy> packs,
        Map<String, Integer> packScores,
        int minimumLeadMargin,
        double minimumConfidenceForExclusiveRouting
    ) {
        List<Map.Entry<String, Integer>> ranked = packScores.entrySet()
                                                            .stream()
                                                            .sorted(
                                                                Map.Entry.<String, Integer>comparingByValue()
                                                                         .reversed()
                                                                         .thenComparing(Map.Entry::getKey)
                                                            )
                                                            .toList();

        String winningPackId = ranked.stream()
                                     .findFirst()
                                     .map(Map.Entry::getKey)
                                     .orElse(StringUtils.EMPTY);

        int winningScore = ranked.stream()
                                 .findFirst()
                                 .map(Map.Entry::getValue)
                                 .orElse(0);

        int secondScore = ranked.stream()
                                .skip(1)
                                .findFirst()
                                .map(Map.Entry::getValue)
                                .orElse(0);

        List<String> candidatePackIds = packs.stream()
                                             .filter(pack -> packScores.getOrDefault(
                                                 pack.packId(),
                                                 0
                                             ) >= pack.minimumApplicabilityScore())
                                             .sorted(
                                                 Comparator.<RulePackMetadata.PackPolicy>comparingInt(
                                                               pack -> packScores.getOrDefault(
                                                                   pack.packId(),
                                                                   0
                                                               )
                                                           )
                                                           .reversed()
                                                           .thenComparing(
                                                               Comparator.comparingInt(RulePackMetadata.PackPolicy::priority)
                                                                         .reversed()
                                                           )
                                                           .thenComparing(RulePackMetadata.PackPolicy::packId)
                                             )
                                             .map(RulePackMetadata.PackPolicy::packId)
                                             .toList();

        double confidence = packs.stream()
                                 .filter(pack -> StringUtils.equals(
                                     pack.packId(),
                                     winningPackId
                                 ))
                                 .findFirst()
                                 .map(pack -> normalizedConfidence(
                                     winningScore,
                                     pack.exclusiveThreshold()
                                 ))
                                 .orElse(0.0d);

        List<String> exclusiveCandidates = packs.stream()
                                                .filter(pack -> packScores.getOrDefault(
                                                    pack.packId(),
                                                    0
                                                ) >= pack.exclusiveThreshold())
                                                .filter(pack -> candidatePackIds.size() == 1)
                                                .filter(pack -> winningScore - secondScore >= minimumLeadMargin)
                                                .filter(pack -> confidence >= minimumConfidenceForExclusiveRouting)
                                                .map(RulePackMetadata.PackPolicy::packId)
                                                .toList();

        List<String> finalCandidates = switch (exclusiveCandidates.size()) {
            case 1 -> exclusiveCandidates;
            default -> candidatePackIds;
        };

        return RulePackClassification.builder()
                                     .resolvedType(resolveType(
                                         packs,
                                         finalCandidates
                                     ))
                                     .ambiguous(finalCandidates.size() > 1)
                                     .confidence(confidence)
                                     .packScores(packScores)
                                     .candidatePackIds(finalCandidates)
                                     .build();
    }

    private double normalizedConfidence(
        int winningScore,
        int threshold
    ) {
        return switch (Integer.compare(threshold, 0)) {
            case 1 -> Math.min(
                1.0d,
                (double) winningScore / (double) threshold
            );
            default -> 0.0d;
        };
    }

    private RfpType resolveType(
        List<RulePackMetadata.PackPolicy> packs,
        List<String> candidatePackIds
    ) {
        return packs.stream()
                    .filter(pack -> candidatePackIds.contains(pack.packId()))
                    .map(RulePackMetadata.PackPolicy::rfpType)
                    .distinct()
                    .reduce((left, right) -> RfpType.UNKNOWN)
                    .orElse(RfpType.UNKNOWN);
    }
}
