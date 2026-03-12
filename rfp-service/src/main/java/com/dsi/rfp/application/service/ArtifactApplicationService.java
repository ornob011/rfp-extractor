package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.artifact.*;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import com.dsi.rfp.domain.port.out.ArtifactPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactApplicationService {

    private final ClarificationQuestionTrigger triggerDetector;
    private final ClarificationQuestionsGenerator questionGenerator;
    private final ClarificationQuestionsDocxWriter docxWriter;
    private final AmbiguityRegisterXlsxWriter ambiguityWriter;
    private final ComplianceChecklistXlsxWriter complianceWriter;
    private final ComplianceItemProjector complianceItemProjector;
    private final RiskLogXlsxWriter riskWriter;
    private final AuditReportGenerator auditReportGenerator;
    private final ArtifactPort artifactPort;
    private final ArtifactGenerationConfig config;

    public BidClarityPack generateAll(
        Long jobId,
        RfpDocument doc,
        ExtractionState state,
        RulePackResults results
    ) {
        List<ClarificationTrigger> triggers = triggerDetector.detect(
            doc,
            results,
            state
        );

        List<ClarificationQuestion> questions = questionGenerator.generate(
            triggers,
            doc
        );

        List<AmbiguityItem> ambiguityItems = buildAmbiguityItems(
            results,
            doc
        );

        List<ComplianceItem> complianceItems = buildComplianceItems(doc);

        List<RiskItem> riskItems = buildRiskItems(results, doc);
        List<StoredArtifact> artifacts = generateArtifacts(
            questions,
            ambiguityItems,
            results,
            riskItems,
            doc,
            state
        );

        artifactPort.replaceArtifacts(
            jobId,
            artifacts
        );

        BidClarityPack pack = BidClarityPack.builder()
                                            .jobId(jobId)
                                            .generatedAt(java.time.Instant.now())
                                            .clarificationQuestions(questions)
                                            .ambiguityItems(ambiguityItems)
                                            .complianceItems(complianceItems)
                                            .riskItems(riskItems)
                                            .build();

        log.info(
            "Bid Clarity Pack generated for job {}: {} clarification questions, 5 artifacts stored",
            jobId,
            questions.size()
        );

        return pack;
    }

    public List<ArtifactMetadata> listArtifacts(Long jobId) {
        return artifactPort.listArtifacts(jobId);
    }

    public ArtifactDownloadPayload loadArtifact(
        Long jobId,
        String filename
    ) {
        ArtifactMetadata metadata = artifactPort.listArtifacts(jobId)
                                                .stream()
                                                .filter(artifact -> artifact.getFilename().equals(filename))
                                                .findFirst()
                                                .orElseGet(() -> ArtifactMetadata.builder()
                                                                                 .jobId(jobId)
                                                                                 .filename(filename)
                                                                                 .fileType(null)
                                                                                 .build());

        byte[] content = artifactPort.loadArtifact(
            jobId,
            filename
        );

        return new ArtifactDownloadPayload(
            metadata,
            content
        );
    }

    private List<StoredArtifact> generateArtifacts(
        List<ClarificationQuestion> questions,
        List<AmbiguityItem> ambiguityItems,
        RulePackResults results,
        List<RiskItem> riskItems,
        RfpDocument doc,
        ExtractionState state
    ) {
        return List.of(
            new StoredArtifact(
                config.clarificationQuestionsFilename(),
                docxWriter.write(questions, doc)
            ),
            new StoredArtifact(
                config.ambiguityRegisterFilename(),
                ambiguityWriter.write(ambiguityItems)
            ),
            new StoredArtifact(
                config.complianceChecklistFilename(),
                complianceWriter.write(doc)
            ),
            new StoredArtifact(
                config.riskLogFilename(),
                riskWriter.write(riskItems, doc)
            ),
            new StoredArtifact(
                config.auditReportFilename(),
                auditReportGenerator.generate(doc, state, results).getBytes()
            )
        );
    }

    List<AmbiguityItem> buildAmbiguityItems(
        RulePackResults results,
        RfpDocument doc
    ) {
        return results.getFindings().stream()
                      .filter(f -> f.getStatus() == RuleStatus.FAIL)
                      .map(this::toAmbiguityItem)
                      .toList();
    }

    private AmbiguityItem toAmbiguityItem(RuleFinding finding) {
        AmbiguityCategory category = switch (finding.getSeverity()) {
            case FATAL -> AmbiguityCategory.MISSING_FIELD;
            case HIGH -> AmbiguityCategory.VAGUE_REQUIREMENT;
            case MEDIUM, LOW, INFO -> AmbiguityCategory.MINOR_GAP;
        };

        return AmbiguityItem.builder()
                            .id(UUID.randomUUID().toString())
                            .severity(finding.getSeverity())
                            .category(category)
                            .description(finding.getMessage())
                            .sourceClauseId(null)
                            .recommendedAction(String.format(
                                "Review rule %s finding and address",
                                finding.getRuleId()
                            ))
                            .build();
    }

    List<ComplianceItem> buildComplianceItems(RfpDocument doc) {
        return complianceItemProjector.project(doc);
    }

    List<RiskItem> buildRiskItems(
        RulePackResults results,
        RfpDocument doc
    ) {
        return results.getFindings().stream()
                      .filter(f -> f.getStatus() == RuleStatus.FAIL)
                      .filter(this::isFatalOrHigh)
                      .map(this::toRiskItem)
                      .toList();
    }

    private RiskItem toRiskItem(RuleFinding finding) {
        RiskImpact impact = switch (finding.getSeverity()) {
            case FATAL -> RiskImpact.HIGH;
            case HIGH -> RiskImpact.MEDIUM;
            case MEDIUM, LOW, INFO -> RiskImpact.LOW;
        };

        return RiskItem.builder()
                       .id(UUID.randomUUID().toString())
                       .riskDescription(finding.getMessage())
                       .source(String.format("rule:%s", finding.getRuleId()))
                       .impact(impact)
                       .owner(config.defaultRiskOwner())
                       .build();
    }

    private boolean isFatalOrHigh(RuleFinding finding) {
        return switch (finding.getSeverity()) {
            case FATAL, HIGH -> true;
            case MEDIUM, LOW, INFO -> false;
        };
    }

    public record ArtifactDownloadPayload(
        ArtifactMetadata metadata,
        byte[] content
    ) {

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof ArtifactDownloadPayload(
                ArtifactMetadata otherMetadata,
                byte[] otherContent
            ))) {
                return false;
            }

            return java.util.Objects.equals(metadata, otherMetadata)
                   && Arrays.equals(content, otherContent);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                metadata,
                Arrays.hashCode(content)
            );
        }

        @Override
        public String toString() {
            return String.format(
                "ArtifactDownloadPayload[metadata=%s, content=%s]",
                metadata,
                Arrays.toString(content)
            );
        }
    }

}
