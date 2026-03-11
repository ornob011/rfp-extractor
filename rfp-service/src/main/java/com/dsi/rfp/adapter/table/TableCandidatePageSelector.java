package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.extraction.DocumentEvidenceIndex;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TableCandidatePageSelector {

    private final DocumentEvidenceIndex evidenceIndex;
    private final TableCandidateRetrievalConfig config;

    public TableCandidatePageSelector(
        DocumentEvidenceIndex evidenceIndex,
        TableCandidateRetrievalConfig config
    ) {
        this.evidenceIndex = evidenceIndex;
        this.config = config;
    }

    public List<Integer> selectDigitalCandidatePages(
        List<PageSummary> pageClassifications,
        Map<Integer, String> pageTexts
    ) {
        List<Integer> digitalPages = pageClassifications.stream()
                                                        .filter(page -> page.getClassification() == PageClassification.DIGITAL)
                                                        .map(PageSummary::getPageNumber)
                                                        .toList();

        if (digitalPages.isEmpty()) {
            return List.of();
        }

        if (pageTexts.isEmpty()) {
            return digitalPages.stream()
                               .limit(config.topK())
                               .toList();
        }

        List<Integer> retrieved = evidenceIndex.retrievePageNumbers(
            pageTexts,
            digitalPages,
            config.queries(),
            config.topK(),
            config.minimumScore()
        ).items();

        if (retrieved.isEmpty()) {
            return digitalPages.stream().limit(config.topK()).toList();
        }

        return retrieved;
    }
}
