package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RuleFinding;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class ClauseReferenceLocator {

    private final ArtifactGenerationConfig config;
    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();

    Optional<ClauseReference> locate(
        RuleFinding finding,
        RfpDocument document
    ) {
        String probe = probeText(finding);

        return document.getClauses().stream()
                       .map(clause -> candidate(
                           clause,
                           similarity.apply(
                               normalized(probe),
                               normalized(clause.getText())
                           )
                       ))
                       .max(Comparator.comparing(ClauseCandidate::score))
                       .filter(candidate -> candidate.score() >= config.complianceClauseMatchThreshold())
                       .map(candidate -> new ClauseReference(
                           candidate.clause().getClauseId(),
                           candidate.clause().getPageNumber()
                       ));
    }

    private String probeText(RuleFinding finding) {
        return Stream.of(
                         finding.getEvidence(),
                         finding.getMessage()
                     )
                     .filter(StringUtils::isNotBlank)
                     .findFirst()
                     .orElse(StringUtils.EMPTY);
    }

    private String normalized(String text) {
        return Optional.ofNullable(text)
                       .map(StringUtils::normalizeSpace)
                       .map(StringUtils::lowerCase)
                       .orElse(StringUtils.EMPTY);
    }

    private ClauseCandidate candidate(
        Clause clause,
        Double score
    ) {
        return new ClauseCandidate(
            clause,
            Optional.ofNullable(score).orElse(0.0)
        );
    }

    private record ClauseCandidate(
        Clause clause,
        double score
    ) {
    }
}
