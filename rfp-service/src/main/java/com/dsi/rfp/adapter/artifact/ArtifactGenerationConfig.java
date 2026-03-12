package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

@Component
public class ArtifactGenerationConfig {

    private static final String CONFIG_PATH = "metadata/artifact-generation-v1.yml";

    private final ConfigDocument config;

    public ArtifactGenerationConfig() {
        config = loadConfig();
    }

    public String clarificationQuestionPromptTemplate() {
        return loadResource(config.prompts().clarificationQuestionResourcePath());
    }

    public String riskMitigationPromptTemplate() {
        return loadResource(config.prompts().riskMitigationResourcePath());
    }

    public Resource systemPromptResource() {
        return new ClassPathResource(config.prompts().systemPromptResourcePath());
    }

    public String clarificationQuestionsTemplateName() {
        return config.templates().clarificationQuestionsResourcePath();
    }

    public String auditReportTemplateName() {
        return config.templates().auditReportResourcePath();
    }

    public String clarificationTitle() {
        return config.clarificationDocument().title();
    }

    public String clarificationProjectLabel() {
        return config.clarificationDocument().projectLabel();
    }

    public String clarificationReferenceLabel() {
        return config.clarificationDocument().referenceLabel();
    }

    public String clarificationDateLabel() {
        return config.clarificationDocument().dateLabel();
    }

    public String clarificationSourceLabel() {
        return config.clarificationDocument().sourceLabel();
    }

    public String clarificationSourceSectionFallback() {
        return config.clarificationDocument().sourceSectionFallback();
    }

    public String clarificationClosingInstruction() {
        return config.clarificationDocument().closingInstruction();
    }

    public String complianceChecklistSheetName() {
        return config.complianceChecklist().sheetName();
    }

    public String complianceEmptyAnswerLabel() {
        return config.complianceChecklist().emptyAnswerLabel();
    }

    public List<ChecklistItemDef> complianceChecklistItems() {
        return Collections.unmodifiableList(
            config.complianceChecklist().items()
        );
    }

    public String riskLogSheetName() {
        return config.riskLog().sheetName();
    }

    public String riskLogEmptySourceLabel() {
        return config.riskLog().emptySourceLabel();
    }

    public String riskLogEmptyMitigationLabel() {
        return config.riskLog().emptyMitigationLabel();
    }

    public int maxQuestions() {
        return config.generation().maxQuestions();
    }

    public int maxTriggers() {
        return config.generation().maxTriggers();
    }

    public double lowConfidenceThreshold() {
        return config.generation().lowConfidenceThreshold();
    }

    public int maxLlmMitigations() {
        return config.generation().maxLlmMitigations();
    }

    public int auditValueTruncateLength() {
        return config.generation().auditValueTruncateLength();
    }

    public String auditUntitledDocumentLabel() {
        return config.audit().untitledDocumentLabel();
    }

    public String auditMissingValueLabel() {
        return config.audit().missingValueLabel();
    }

    public NavigableMap<Double, AuditReportTemplateModel.ConfidenceBand> auditConfidenceBands() {
        return new TreeMap<>(config.audit().confidenceBands());
    }

    public String defaultRiskOwner() {
        return config.generation().defaultRiskOwner();
    }

    public String clarificationQuestionsFilename() {
        return config.artifacts().clarificationQuestionsFilename();
    }

    public String ambiguityRegisterFilename() {
        return config.artifacts().ambiguityRegisterFilename();
    }

    public String complianceChecklistFilename() {
        return config.artifacts().complianceChecklistFilename();
    }

    public String riskLogFilename() {
        return config.artifacts().riskLogFilename();
    }

    public String auditReportFilename() {
        return config.artifacts().auditReportFilename();
    }

    public List<String> dateFormatPatterns() {
        return config.dateFormats();
    }

    private String loadResource(String path) {
        ClassPathResource resource = new ClassPathResource(path);

        try (InputStream input = resource.getInputStream()) {
            return StreamUtils.copyToString(
                input,
                StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load artifact resource: %s",
                    path
                ),
                exception
            );
        }
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(
                input,
                ConfigDocument.class
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load artifact generation config: %s",
                    CONFIG_PATH
                ),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        int version,
        Prompts prompts,
        Templates templates,
        ClarificationDocument clarificationDocument,
        ComplianceChecklist complianceChecklist,
        RiskLog riskLog,
        Generation generation,
        Artifacts artifacts,
        Audit audit,
        List<String> dateFormats
    ) {
    }

    private record Prompts(
        String clarificationQuestionResourcePath,
        String riskMitigationResourcePath,
        String systemPromptResourcePath
    ) {
    }

    private record Templates(
        String clarificationQuestionsResourcePath,
        String auditReportResourcePath
    ) {
    }

    private record ClarificationDocument(
        String title,
        String projectLabel,
        String referenceLabel,
        String dateLabel,
        String sourceLabel,
        String sourceSectionFallback,
        String closingInstruction
    ) {
    }

    private record ComplianceChecklist(
        String sheetName,
        String emptyAnswerLabel,
        List<ChecklistItemDef> items
    ) {
    }

    public record ChecklistItemDef(
        String title,
        String entityField
    ) {
    }

    private record RiskLog(
        String sheetName,
        String emptySourceLabel,
        String emptyMitigationLabel
    ) {
    }

    private record Generation(
        int maxQuestions,
        int maxTriggers,
        double lowConfidenceThreshold,
        int maxLlmMitigations,
        int auditValueTruncateLength,
        String defaultRiskOwner
    ) {
    }

    private record Artifacts(
        String clarificationQuestionsFilename,
        String ambiguityRegisterFilename,
        String complianceChecklistFilename,
        String riskLogFilename,
        String auditReportFilename
    ) {
    }

    private record Audit(
        String untitledDocumentLabel,
        String missingValueLabel,
        NavigableMap<Double, AuditReportTemplateModel.ConfidenceBand> confidenceBands
    ) {
    }
}
