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

    public TableExtractor(
        TableEnginePort tableEngineClient,
        LatticeTableExtractor latticeExtractor
    ) {
        this.tableEngineClient = tableEngineClient;
        this.latticeExtractor = latticeExtractor;
    }

    public List<TableExtractionResult> extractFromDocument(
        String documentPath,
        List<PageSummary> pageClassifications
    ) {
        List<Integer> candidatePages = pageClassifications.stream()
                                                          .filter(page -> page.getClassification() == PageClassification.DIGITAL)
                                                          .map(PageSummary::getPageNumber)
                                                          .toList();

        log.info(
            "event=table.extract component=TableExtractor"
            + " digitalPages={} candidatePages={}",
            candidatePages.size(),
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
