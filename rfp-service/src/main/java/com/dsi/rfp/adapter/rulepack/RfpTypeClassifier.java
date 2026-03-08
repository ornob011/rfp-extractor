package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.Section;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class RfpTypeClassifier {

    private final RulePackConfig config;
    private final PackSignalScorer packSignalScorer;
    private final CandidatePackResolver candidatePackResolver;

    public RfpTypeClassifier(
        RulePackConfig config,
        PackSignalScorer packSignalScorer,
        CandidatePackResolver candidatePackResolver
    ) {
        this.config = config;
        this.packSignalScorer = packSignalScorer;
        this.candidatePackResolver = candidatePackResolver;
    }

    public RulePackClassification classify(RfpDocument document) {
        return candidatePackResolver.resolve(
            config.enabledPacks(),
            packSignalScorer.scorePacks(
                config.enabledPacks(),
                document.getEntities(),
                sectionTitles(document.getSections()),
                scopeSummary(document.getEntities())
            ),
            config.minimumLeadMargin(),
            config.minimumConfidenceForExclusiveRouting()
        );
    }

    private String sectionTitles(List<Section> sections) {
        return sections.stream()
                       .map(Section::getTitle)
                       .filter(title -> title != null && !title.isBlank())
                       .collect(Collectors.joining(" "));
    }

    private String scopeSummary(RfpEntities entities) {
        return Optional.ofNullable(entities)
                       .map(RfpEntities::getScopeSummary)
                       .orElse(StringUtils.EMPTY);
    }
}
