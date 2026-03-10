package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class TableExtractor {

    private static final int TABLE_BATCH_CHUNK_SIZE = 15;

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
            "event=table.batchStart component=TableExtractor"
            + " digitalPages={}",
            digitalPages.size()
        );

        Map<Integer, List<TableEngineTable>> batchResults = extractInChunks(
            documentPath,
            digitalPages
        );

        return batchResults.entrySet()
                           .stream()
                           .sorted(Map.Entry.comparingByKey())
                           .flatMap(entry -> latticeExtractor.toDomainList(
                               entry.getValue(),
                               entry.getKey()
                           ).stream())
                           .toList();
    }

    private Map<Integer, List<TableEngineTable>> extractInChunks(
        String documentPath,
        List<Integer> pageNumbers
    ) {
        List<List<Integer>> chunks = IntStream.range(0, pageNumbers.size())
            .boxed()
            .collect(Collectors.groupingBy(
                i -> i / TABLE_BATCH_CHUNK_SIZE,
                Collectors.mapping(pageNumbers::get, Collectors.toList())
            ))
            .values()
            .stream()
            .toList();

        Map<Integer, List<TableEngineTable>> merged = new HashMap<>();

        for (List<Integer> chunk : chunks) {
            log.info(
                "event=table.chunk component=TableExtractor"
                + " chunkSize={} totalPages={}",
                chunk.size(),
                pageNumbers.size()
            );

            merged.putAll(
                tableEngineClient.extractTablesBatch(documentPath, chunk)
            );
        }

        return merged;
    }
}

