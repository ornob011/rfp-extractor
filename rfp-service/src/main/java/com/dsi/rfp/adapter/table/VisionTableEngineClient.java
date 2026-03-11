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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@Qualifier("visionTableEngine")
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
        Map<Integer, List<TableEngineTable>> results = new HashMap<>();

        for (int pageNumber : pageNumbers) {
            List<TableEngineTable> tables = extractTables(
                documentPath,
                pageNumber,
                TableExtractionStrategy.LATTICE
            );

            results.put(pageNumber, tables);
        }

        log.info(
            "event=vision.tableBatch component=VisionTableEngineClient"
            + " pages={}",
            pageNumbers.size()
        );

        return results;
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
