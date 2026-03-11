package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TableExtractor {

    private final TableEngineClient tableEngineClient;
    private final LatticeTableExtractor latticeExtractor;

    public TableExtractor(
        TableEngineClient tableEngineClient,
        LatticeTableExtractor latticeExtractor
    ) {
        this.tableEngineClient = tableEngineClient;
        this.latticeExtractor = latticeExtractor;
    }

    public List<TableExtractionResult> extractFromDocument(
        String documentPath,
        List<PageSummary> pageClassifications
    ) {
        List<Integer> digitalPages = pageClassifications.stream()
                                                        .filter(p -> p.getClassification() != PageClassification.SCANNED)
                                                        .map(PageSummary::getPageNumber)
                                                        .toList();

        log.info(
            "event=table.extract component=TableExtractor"
            + " digitalPages={}",
            digitalPages.size()
        );

        Map<Integer, List<TableEngineTable>> results =
            tableEngineClient.extractTablesBatch(
                documentPath,
                digitalPages
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
