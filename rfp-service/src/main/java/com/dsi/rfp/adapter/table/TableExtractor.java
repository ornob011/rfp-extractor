package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.TableEngineTable;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.port.out.TableEnginePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TableExtractor {

    private final TableEnginePort tableEngineClient;
    private final LatticeTableExtractor latticeExtractor;
    private final TableCandidatePageSelector candidatePageSelector;

    public TableExtractor(
        TableEnginePort tableEngineClient,
        LatticeTableExtractor latticeExtractor,
        TableCandidatePageSelector candidatePageSelector
    ) {
        this.tableEngineClient = tableEngineClient;
        this.latticeExtractor = latticeExtractor;
        this.candidatePageSelector = candidatePageSelector;
    }

    public List<TableExtractionResult> extractFromDocument(
        String documentPath,
        List<PageSummary> pageClassifications,
        Map<Integer, String> pageTexts
    ) {
        List<Integer> digitalPages = pageClassifications.stream()
                                                        .filter(page -> page.getClassification() == PageClassification.DIGITAL)
                                                        .map(PageSummary::getPageNumber)
                                                        .toList();

        log.info(
            "event=table.extract component=TableExtractor"
            + " digitalPages={}",
            digitalPages.size()
        );

        if (digitalPages.isEmpty()) {
            return List.of();
        }

        List<Integer> candidatePages = candidatePageSelector.selectDigitalCandidatePages(
            pageClassifications,
            pageTexts
        );

        log.info(
            "event=table.candidates component=TableExtractor"
            + " digitalPages={} candidatePages={}",
            digitalPages.size(),
            candidatePages.size()
        );

        if (candidatePages.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<TableEngineTable>> results =
            tableEngineClient.extractTablesBatch(
                documentPath,
                candidatePages
            );

        return results.entrySet()
                      .stream()
                      .sorted(Map.Entry.comparingByKey())
                      .flatMap(entry -> latticeExtractor.toDomainList(
                          entry.getValue(),
                          entry.getKey()
                      ).stream())
                      .toList();
    }
}
