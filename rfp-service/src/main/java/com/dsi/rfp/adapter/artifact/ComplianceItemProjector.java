package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ComplianceItemProjector {

    private final ClauseReferenceLocator clauseReferenceLocator;

    public List<ComplianceItem> project(
        RulePackResults results,
        RfpDocument document
    ) {
        return results.getFindings().stream()
                      .filter(this::isFatalOrHigh)
                      .map(finding -> project(
                          finding,
                          document
                      ))
                      .toList();
    }

    public ComplianceItem project(
        RuleFinding finding,
        RfpDocument document
    ) {
        ClauseReference reference = clauseReferenceLocator.locate(
                                                              finding,
                                                              document
                                                          )
                                                          .orElse(new ClauseReference(
                                                              null,
                                                              0
                                                          ));

        return ComplianceItem.builder()
                             .id(finding.getRuleId())
                             .requirement(finding.getMessage())
                             .sourceClauseId(reference.clauseId())
                             .page(reference.pageNumber())
                             .mandatory(finding.getSeverity() == RuleSeverity.FATAL)
                             .status(ComplianceChecklistStatus.TO_BE_FILLED)
                             .build();
    }

    private boolean isFatalOrHigh(RuleFinding finding) {
        return switch (finding.getSeverity()) {
            case FATAL, HIGH -> true;
            case MEDIUM, LOW, INFO -> false;
        };
    }
}
