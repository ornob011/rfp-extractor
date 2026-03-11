package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ScannedTableReconstructor {

    private final VisionExtractionAdapter visionAdapter;
    private final ScannedTableResultMapper resultMapper;

    public ScannedTableReconstructor(
        VisionExtractionAdapter visionAdapter,
        ScannedTableResultMapper resultMapper
    ) {
        this.visionAdapter = visionAdapter;
        this.resultMapper = resultMapper;
    }

    public List<TableExtractionResult> reconstructTables(
        String documentPath,
        String ocrText,
        int pageNum,
        double ocrPageConfidence
    ) {
        VisionPageResult result = extractFullPage(
            documentPath,
            pageNum
        );

        log.info(
            "event=table.visionReconstruct component=ScannedTableReconstructor"
            + " page={} tables={}",
            pageNum,
            result.tables().size()
        );

        return result.tables()
                     .stream()
                     .filter(table -> !table.headers().isEmpty())
                     .map(table -> toTableResult(table, pageNum))
                     .toList();
    }

    private VisionPageResult extractFullPage(
        String documentPath,
        int pageNum
    ) {
        try {
            return visionAdapter.extractFullPage(
                documentPath,
                pageNum
            );
        } catch (java.io.IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Scanned table vision extraction failed for page %d",
                    pageNum
                ),
                exception
            );
        }
    }

    private TableExtractionResult toTableResult(
        VisionTableResult table,
        int pageNum
    ) {
        return resultMapper.fromLlm(
            new ScannedTableResponse(
                table.headers(),
                table.grid()
            ),
            pageNum,
            table.confidence()
        );
    }
}
