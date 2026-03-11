package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionExtractionConfig;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.TableEngineCell;
import com.dsi.rfp.domain.model.TableEngineTable;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import com.dsi.rfp.domain.port.out.TableEnginePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@Primary
public class VisionTableEngineClient implements TableEnginePort {

    private final VisionExtractionAdapter visionAdapter;
    private final VisionExtractionConfig config;

    public VisionTableEngineClient(
        VisionExtractionAdapter visionAdapter,
        VisionExtractionConfig config
    ) {
        this.visionAdapter = visionAdapter;
        this.config = config;
    }

    private static List<List<TableEngineCell>> buildGrid(
        List<String> headers,
        List<List<String>> rows
    ) {
        List<TableEngineCell> headerRow = buildRow(
            headers,
            0,
            true
        );

        List<List<TableEngineCell>> dataRows = new java.util.ArrayList<>();
        dataRows.add(headerRow);

        for (int i = 0; i < rows.size(); i++) {
            dataRows.add(
                buildRow(rows.get(i), i + 1, false)
            );
        }

        return dataRows;
    }

    private static List<TableEngineCell> buildRow(
        List<String> values,
        int rowIndex,
        boolean isHeader
    ) {
        List<TableEngineCell> cells = new java.util.ArrayList<>();

        for (int col = 0; col < values.size(); col++) {
            cells.add(new TableEngineCell(
                rowIndex,
                col,
                values.get(col),
                1,
                1,
                isHeader
            ));
        }

        return cells;
    }

    @Override
    public List<TableEngineTable> extractTables(
        String documentPath,
        int pageNumber,
        TableExtractionStrategy strategy
    ) {
        log.info(
            "event=vision.tableExtract component=VisionTableEngineClient"
            + " page={} strategy={}",
            pageNumber,
            strategy
        );

        try {
            VisionPageResult result = visionAdapter.extractTablesOnly(
                documentPath,
                pageNumber
            );

            return result.tables()
                         .stream()
                         .map(this::toEngineTable)
                         .toList();
        } catch (IOException exception) {
            throw new LlmUnavailableException(
                String.format(
                    "Vision table extraction failed for page %d",
                    pageNumber
                ),
                exception
            );
        }
    }

    @Override
    public Map<Integer, List<TableEngineTable>> extractTablesBatch(
        String documentPath,
        List<Integer> pageNumbers
    ) {
        if (pageNumbers.isEmpty()) {
            log.info(
                "event=vision.tableBatch component=VisionTableEngineClient"
                + " pages=0 candidatePages=0"
            );
            return Map.of();
        }

        Map<Integer, Boolean> tablePresence = detectTablePresenceByBatch(
            documentPath,
            pageNumbers
        );

        List<Integer> candidatePages = pageNumbers.stream()
                                                  .filter(pageNumber -> tablePresence.getOrDefault(
                                                      pageNumber,
                                                      false
                                                  ))
                                                  .toList();

        Map<Integer, List<TableEngineTable>> results =
            pageNumbers.stream()
                       .collect(Collectors.toMap(
                           Function.identity(),
                           pageNumber -> List.<TableEngineTable>of()
                       ));

        for (Integer pageNumber : candidatePages) {
            results.put(
                pageNumber,
                extractTables(
                    documentPath,
                    pageNumber,
                    TableExtractionStrategy.LATTICE
                )
            );
        }

        log.info(
            "event=vision.tableBatch component=VisionTableEngineClient"
            + " pages={} candidatePages={}",
            pageNumbers.size(),
            candidatePages.size()
        );

        return results;
    }

    private Map<Integer, Boolean> detectTablePresenceByBatch(
        String documentPath,
        List<Integer> pageNumbers
    ) {
        List<List<Integer>> pageBatches = partition(
            pageNumbers,
            config.tablePresenceBatchSize()
        );

        Map<Integer, Boolean> tablePresence = new java.util.LinkedHashMap<>();

        for (List<Integer> batch : pageBatches) {
            tablePresence.putAll(
                detectTablePresenceForBatch(
                    documentPath,
                    batch
                )
            );
        }

        return tablePresence;
    }

    private Map<Integer, Boolean> detectTablePresenceForBatch(
        String documentPath,
        List<Integer> pageNumbers
    ) {
        try {
            return visionAdapter.detectTablePresenceBatch(
                documentPath,
                pageNumbers
            );
        } catch (IOException exception) {
            throw new LlmUnavailableException(
                String.format(
                    "Vision table presence detection failed for pages %s",
                    pageNumbers
                ),
                exception
            );
        }
    }

    private List<List<Integer>> partition(
        List<Integer> pageNumbers,
        int batchSize
    ) {
        List<List<Integer>> batches = new java.util.ArrayList<>();

        for (int start = 0; start < pageNumbers.size(); start += batchSize) {
            int end = Math.min(
                start + batchSize,
                pageNumbers.size()
            );
            batches.add(pageNumbers.subList(start, end));
        }

        return batches;
    }

    private TableEngineTable toEngineTable(
        VisionTableResult visionTable
    ) {
        List<List<TableEngineCell>> grid = buildGrid(
            visionTable.headers(),
            visionTable.grid()
        );

        return new TableEngineTable(
            visionTable.caption(),
            visionTable.headers(),
            grid,
            visionTable.confidence(),
            config.tableMethod()
        );
    }
}
