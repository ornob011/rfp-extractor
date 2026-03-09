package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import com.dsi.rfp.domain.port.out.RulePackPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulePackExecutionServiceTest {

    @Mock
    private RulePackPort rulePackPort;

    @Mock
    private RfpTypeClassifier rfpTypeClassifier;

    @Mock
    private RulePackConfig config;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RulePackExecutionService rulePackExecutionService;

    @Test
    void shouldRunConfiguredPackWhenTypeHasMapping() {
        RulePackDefinition pack = RulePackDefinition.builder()
                                                    .packId("bd-govt-ict-v1")
                                                    .packVersion("1.0.0")
                                                    .rfpType(RfpType.ICT)
                                                    .build();
        RulePackResults results = singleResult("bd-govt-ict-v1");

        when(rfpTypeClassifier.classify(any())).thenReturn(classification(
            RfpType.ICT,
            List.of("bd-govt-ict-v1"),
            0.9d
        ));
        when(rulePackPort.listPacks()).thenReturn(List.of(pack));
        when(rulePackPort.runPack(any(), anyString())).thenReturn(results);

        RulePackResults result = rulePackExecutionService.execute(RfpDocument.builder().build());

        assertThat(result.getPackId()).isEqualTo("bd-govt-ict-v1");
        assertThat(result.getRfpType()).isEqualTo(RfpType.ICT);
    }

    @Test
    void shouldMergeCandidatePackResultsWhenClassificationIsAmbiguous() {
        RulePackDefinition ictPack = RulePackDefinition.builder()
                                                       .packId("bd-govt-ict-v1")
                                                       .packVersion("1.0.0")
                                                       .rfpType(RfpType.ICT)
                                                       .build();
        RulePackDefinition goodsPack = RulePackDefinition.builder()
                                                         .packId("bd-govt-goods-v1")
                                                         .packVersion("1.0.0")
                                                         .rfpType(RfpType.GOODS)
                                                         .build();

        when(rfpTypeClassifier.classify(any())).thenReturn(classification(
            RfpType.UNKNOWN,
            List.of("bd-govt-ict-v1", "bd-govt-goods-v1"),
            0.4d
        ));
        when(config.mergedPackId()).thenReturn("all-applicable");
        when(config.mergedPackVersion()).thenReturn("1.0.0");
        when(rulePackPort.listPacks()).thenReturn(List.of(ictPack, goodsPack));
        when(rulePackPort.runPack(any(), anyString()))
            .thenReturn(singleResult("bd-govt-ict-v1"), singleResult("bd-govt-goods-v1"));

        RulePackResults result = rulePackExecutionService.execute(RfpDocument.builder().build());

        assertThat(result.getPackId()).isEqualTo("all-applicable");
        assertThat(result.getFindings()).hasSize(2);
        assertThat(result.getSummary().get(RuleSeverity.HIGH)).isEqualTo(2);
        assertThat(result.getRfpType()).isEqualTo(RfpType.UNKNOWN);
    }

    @Test
    void shouldReturnEmptyResultsWhenNoPackMatchesAndFallbackIsDisabled() {
        when(rfpTypeClassifier.classify(any())).thenReturn(classification(
            RfpType.UNKNOWN,
            List.of(),
            0.0d
        ));
        when(config.mergedPackId()).thenReturn("all-applicable");
        when(config.mergedPackVersion()).thenReturn("1.0.0");
        when(config.runAllWhenNoCandidate()).thenReturn(false);
        when(rulePackPort.listPacks()).thenReturn(List.of());

        RulePackResults result = rulePackExecutionService.execute(RfpDocument.builder().build());

        assertThat(result.getFindings()).isEmpty();
        assertThat(result.getPackId()).isEqualTo("all-applicable");
        assertThat(result.getRfpType()).isEqualTo(RfpType.UNKNOWN);
    }

    @Test
    void shouldRunAllLoadedPacksWhenNoCandidateAndFallbackEnabled() {
        RulePackDefinition ictPack = RulePackDefinition.builder()
                                                       .packId("bd-govt-ict-v1")
                                                       .packVersion("1.0.0")
                                                       .rfpType(RfpType.ICT)
                                                       .build();

        when(rfpTypeClassifier.classify(any())).thenReturn(classification(
            RfpType.UNKNOWN,
            List.of(),
            0.0d
        ));
        when(config.runAllWhenNoCandidate()).thenReturn(true);
        when(config.enabledPackIds()).thenReturn(Set.of("bd-govt-ict-v1"));
        when(rulePackPort.listPacks()).thenReturn(List.of(ictPack));
        when(rulePackPort.runPack(any(), anyString())).thenReturn(singleResult("bd-govt-ict-v1"));

        RulePackResults result = rulePackExecutionService.execute(RfpDocument.builder().build());

        assertThat(result.getFindings()).hasSize(1);
        assertThat(result.getPackId()).isEqualTo("bd-govt-ict-v1");
        assertThat(result.getRfpType()).isEqualTo(RfpType.ICT);
    }

    private RulePackClassification classification(
        RfpType type,
        List<String> candidatePackIds,
        double confidence
    ) {
        return RulePackClassification.builder()
                                     .resolvedType(type)
                                     .ambiguous(candidatePackIds.size() > 1)
                                     .confidence(confidence)
                                     .packScores(Map.of())
                                     .candidatePackIds(candidatePackIds)
                                     .build();
    }

    private RulePackResults singleResult(String packId) {
        return RulePackResults.builder()
                              .packId(packId)
                              .packVersion("1.0.0")
                              .rfpType(resultType(packId))
                              .runTimestamp(Instant.now())
                              .summary(Map.of(RuleSeverity.HIGH, 1))
                              .findings(List.of(
                                  RuleFinding.builder()
                                             .ruleId(String.format("%s-001", packId))
                                             .severity(RuleSeverity.HIGH)
                                             .status(RuleStatus.FAIL)
                                             .message("Failure")
                                             .build()
                              ))
                              .build();
    }

    private RfpType resultType(String packId) {
        return switch (packId) {
            case "bd-govt-goods-v1" -> RfpType.GOODS;
            default -> RfpType.ICT;
        };
    }
}
