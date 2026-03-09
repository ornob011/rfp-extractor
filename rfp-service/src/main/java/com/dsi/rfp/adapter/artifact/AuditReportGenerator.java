package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RulePackResults;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditReportGenerator {

    private static final TypeReference<Map<String, Object>> ROOT_MODEL_TYPE =
        new TypeReference<>() {
        };

    private final Configuration freemarkerConfig;
    private final ArtifactGenerationConfig config;
    private final AuditReportModelFactory modelFactory;
    private final ObjectMapper objectMapper;

    public String generate(
        RfpDocument document,
        ExtractionState state,
        RulePackResults results
    ) {
        try {
            Template template = freemarkerConfig.getTemplate(
                config.auditReportTemplateName()
            );
            StringWriter writer = new StringWriter();
            template.process(
                objectMapper.convertValue(
                    modelFactory.create(
                        document,
                        state,
                        results
                    ),
                    ROOT_MODEL_TYPE
                ),
                writer
            );

            String html = writer.toString();
            log.debug("Audit report generated: {} chars", html.length());
            return html;
        } catch (IOException | TemplateException exception) {
            throw new SystemIoException(
                "Failed to generate audit report",
                exception
            );
        }
    }
}
