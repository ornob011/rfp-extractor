package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.Section;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class ArtifactDocumentContextResolver {

    private final ArtifactGenerationConfig config;

    String title(RfpDocument document) {
        return document.getSections().stream()
                       .findFirst()
                       .map(Section::getTitle)
                       .filter(StringUtils::isNotBlank)
                       .orElse(config.auditUntitledDocumentLabel());
    }

    String procurementReference(RfpDocument document) {
        return document.getClauses().stream()
                       .findFirst()
                       .map(Clause::getClauseId)
                       .map(clauseId -> StringUtils.substringBefore(
                           clauseId,
                           ":"
                       ))
                       .filter(StringUtils::isNotBlank)
                       .orElse(config.auditMissingValueLabel());
    }

    String displayValue(Object value) {
        return Optional.ofNullable(value)
                       .map(Object::toString)
                       .filter(StringUtils::isNotBlank)
                       .orElse(config.auditMissingValueLabel());
    }
}
