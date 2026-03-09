package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.artifact.*;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.*;
import com.dsi.rfp.domain.port.out.ArtifactPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactApplicationServiceTest {

    @Mock
    private ClarificationQuestionTrigger triggerDetector;
    @Mock
    private ClarificationQuestionsGenerator questionGenerator;
    @Mock
    private ClarificationQuestionsDocxWriter docxWriter;
    @Mock
    private AmbiguityRegisterXlsxWriter ambiguityWriter;
    @Mock
    private ComplianceChecklistXlsxWriter complianceWriter;
    @Mock
    private ComplianceItemProjector complianceItemProjector;
    @Mock
    private RiskLogXlsxWriter riskWriter;
    @Mock
    private AuditReportGenerator auditReportGenerator;
    @Mock
    private ArtifactPort artifactPort;

    private ArtifactApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ArtifactApplicationService(
            triggerDetector,
            questionGenerator,
            docxWriter,
            ambiguityWriter,
            complianceWriter,
            complianceItemProjector,
            riskWriter,
            auditReportGenerator,
            artifactPort,
            new ArtifactGenerationConfig()
        );
    }

    @Test
    void shouldStoreAllFiveArtifactsWhenGenerateAllCalled() {
        when(triggerDetector.detect(any(), any(), any()))
            .thenReturn(List.of());
        when(questionGenerator.generate(any(), any()))
            .thenReturn(List.of());
        when(docxWriter.write(any(), any()))
            .thenReturn("docx".getBytes());
        when(ambiguityWriter.write(any()))
            .thenReturn("xlsx".getBytes());
        when(complianceWriter.write(any(), any()))
            .thenReturn("xlsx".getBytes());
        when(riskWriter.write(any(), any()))
            .thenReturn("xlsx".getBytes());
        when(auditReportGenerator.generate(any(), any(), any()))
            .thenReturn("<html></html>");
        when(artifactPort.replaceArtifacts(any(), any()))
            .thenReturn(List.of());

        BidClarityPack pack = service.generateAll(
            1L,
            minimalDoc(),
            mockState(),
            minimalResults()
        );

        verify(artifactPort).replaceArtifacts(
            eq(1L),
            any()
        );

        assertThat(pack.getJobId()).isEqualTo(1L);
        assertThat(pack.getGeneratedAt()).isNotNull();
    }

    @Test
    void shouldPropagateWhenArtifactReplacementFails() {
        when(triggerDetector.detect(any(), any(), any()))
            .thenReturn(List.of());
        when(questionGenerator.generate(any(), any()))
            .thenReturn(List.of());
        when(docxWriter.write(any(), any()))
            .thenReturn("docx".getBytes());
        when(ambiguityWriter.write(any()))
            .thenReturn("xlsx".getBytes());
        when(complianceWriter.write(any(), any()))
            .thenReturn("xlsx".getBytes());
        when(riskWriter.write(any(), any()))
            .thenReturn("xlsx".getBytes());
        when(auditReportGenerator.generate(any(), any(), any()))
            .thenReturn("<html></html>");
        when(artifactPort.replaceArtifacts(any(), any()))
            .thenThrow(new SystemIoException("boom", new RuntimeException("boom")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateAll(
            1L,
            minimalDoc(),
            mockState(),
            minimalResults()
        )).isInstanceOf(SystemIoException.class);
    }

    @Test
    void shouldBuildAmbiguityItemsFromFailFindings() {
        RulePackResults results = RulePackResults.builder()
                                                 .packId("test")
                                                 .findings(List.of(
                                                     finding("R1", RuleSeverity.FATAL, RuleStatus.FAIL),
                                                     finding("R2", RuleSeverity.HIGH, RuleStatus.PASS),
                                                     finding("R3", RuleSeverity.MEDIUM, RuleStatus.FAIL)
                                                 ))
                                                 .build();

        List<AmbiguityItem> items = service.buildAmbiguityItems(
            results,
            minimalDoc()
        );

        assertThat(items).hasSize(2);
    }

    @Test
    void shouldBuildRiskItemsFromFatalAndHighFailFindings() {
        RulePackResults results = RulePackResults.builder()
                                                 .packId("test")
                                                 .findings(List.of(
                                                     finding("R1", RuleSeverity.FATAL, RuleStatus.FAIL),
                                                     finding("R2", RuleSeverity.HIGH, RuleStatus.FAIL),
                                                     finding("R3", RuleSeverity.MEDIUM, RuleStatus.FAIL)
                                                 ))
                                                 .build();

        List<RiskItem> items = service.buildRiskItems(
            results,
            minimalDoc()
        );

        assertThat(items).hasSize(2);
        assertThat(items.getFirst().getImpact()).isEqualTo(RiskImpact.HIGH);
        assertThat(items.get(1).getImpact()).isEqualTo(RiskImpact.MEDIUM);
    }

    private RuleFinding finding(
        String ruleId,
        RuleSeverity severity,
        RuleStatus status
    ) {
        return RuleFinding.builder()
                          .ruleId(ruleId)
                          .severity(severity)
                          .status(status)
                          .message(String.format("Check %s", ruleId))
                          .checkedAt(Instant.now())
                          .build();
    }

    private RfpDocument minimalDoc() {
        return RfpDocument.builder()
                          .sections(List.of())
                          .entities(RfpEntities.builder().build())
                          .build();
    }

    private ExtractionState mockState() {
        return mock(ExtractionState.class);
    }

    private RulePackResults minimalResults() {
        return RulePackResults.builder()
                              .packId("test")
                              .findings(List.of())
                              .build();
    }
}
