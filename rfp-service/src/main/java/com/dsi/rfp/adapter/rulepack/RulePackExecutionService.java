package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.RulePackExecutionException;
import com.dsi.rfp.domain.model.*;
import com.dsi.rfp.domain.port.out.RulePackPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RulePackExecutionService {

    private final RulePackPort rulePackPort;
    private final RfpTypeClassifier rfpTypeClassifier;
    private final RulePackConfig config;
    private final ObjectMapper objectMapper;

    public RulePackResults execute(RfpDocument document) {
        String rfpJson = serializeDocument(document);
        RulePackClassification classification = rfpTypeClassifier.classify(document);
        List<RulePackDefinition> packs = resolvePacks(classification);

        return Optional.of(packs)
                       .filter(resolvedPacks -> !resolvedPacks.isEmpty())
                       .map(resolvedPacks -> mergeResults(
                           classification,
                           resolvedPacks.stream()
                                        .map(pack -> rulePackPort.runPack(
                                            pack,
                                            rfpJson
                                        ))
                                        .toList()
                       ))
                       .orElseGet(this::emptyResults);
    }

    private List<RulePackDefinition> resolvePacks(RulePackClassification classification) {
        Map<String, RulePackDefinition> packIndex = rulePackPort.listPacks()
                                                                .stream()
                                                                .collect(Collectors.toMap(
                                                                    RulePackDefinition::getPackId,
                                                                    Function.identity()
                                                                ));

        List<RulePackDefinition> candidatePacks = classification.candidatePackIds()
                                                                .stream()
                                                                .map(packIndex::get)
                                                                .filter(Objects::nonNull)
                                                                .toList();

        return Optional.of(candidatePacks)
                       .filter(packs -> !packs.isEmpty())
                       .orElseGet(() -> resolveFallbackPacks(packIndex));
    }

    private List<RulePackDefinition> resolveFallbackPacks(Map<String, RulePackDefinition> packIndex) {
        return Optional.of(config.runAllWhenNoCandidate())
                       .filter(Boolean::booleanValue)
                       .map(ignored -> config.enabledPackIds()
                                             .stream()
                                             .map(packIndex::get)
                                             .filter(Objects::nonNull)
                                             .toList())
                       .orElse(List.of());
    }

    private RulePackResults mergeResults(
        RulePackClassification classification,
        List<RulePackResults> results
    ) {
        return switch (results.size()) {
            case 0 -> emptyResults();
            case 1 -> results.getFirst();
            default -> RulePackResults.builder()
                                      .packId(config.mergedPackId())
                                      .packVersion(config.mergedPackVersion())
                                      .rfpType(classification.resolvedType())
                                      .runTimestamp(resolveRunTimestamp(results))
                                      .summary(buildSummary(results))
                                      .findings(
                                          results.stream()
                                                 .flatMap(result -> result.getFindings().stream())
                                                 .toList()
                                      )
                                      .build();
        };
    }

    private String serializeDocument(RfpDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new RulePackExecutionException(
                "Failed to serialize RFP document for rule pack execution",
                exception
            );
        }
    }

    private Instant resolveRunTimestamp(List<RulePackResults> results) {
        return results.stream()
                      .map(RulePackResults::getRunTimestamp)
                      .findFirst()
                      .orElseGet(Instant::now);
    }

    private Map<RuleSeverity, Integer> buildSummary(List<RulePackResults> results) {
        Map<RuleSeverity, Integer> summary = new EnumMap<>(RuleSeverity.class);

        results.stream()
               .flatMap(result -> result.getFindings().stream())
               .filter(finding -> finding.getStatus() == RuleStatus.FAIL)
               .forEach(finding -> summary.merge(
                   finding.getSeverity(),
                   1,
                   Integer::sum
               ));

        return summary;
    }

    private RulePackResults emptyResults() {
        return RulePackResults.builder()
                              .packId(config.mergedPackId())
                              .packVersion(config.mergedPackVersion())
                              .rfpType(RfpType.UNKNOWN)
                              .runTimestamp(Instant.now())
                              .summary(Map.of())
                              .findings(List.of())
                              .build();
    }
}
