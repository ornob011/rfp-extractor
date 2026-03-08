package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.ocr.OcrPageWithLayoutResultDto;
import com.dsi.rfp.adapter.ocr.OcrScannedTableDto;
import com.dsi.rfp.adapter.ocr.OcrSidecarClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ScannedTableReconstructor {

    private final LlmAdapter llmAdapter;
    private final OcrSidecarClient ocrSidecarClient;
    private final PageImageRenderer pageImageRenderer;
    private final ScannedTableResultMapper resultMapper;
    private final TableExtractionConfig config;
    private final String promptTemplate;

    public ScannedTableReconstructor(
        LlmAdapter llmAdapter,
        OcrSidecarClient ocrSidecarClient,
        PageImageRenderer pageImageRenderer,
        ScannedTableResultMapper resultMapper,
        TableExtractionConfig config
    ) {
        this.llmAdapter = llmAdapter;
        this.ocrSidecarClient = ocrSidecarClient;
        this.pageImageRenderer = pageImageRenderer;
        this.resultMapper = resultMapper;
        this.config = config;
        promptTemplate = config.scannedPromptTemplate();
    }

    public List<com.dsi.rfp.domain.model.TableExtractionResult> reconstructTables(
        String documentPath,
        String ocrText,
        int pageNum,
        double ocrPageConfidence
    ) throws IOException {
        List<com.dsi.rfp.domain.model.TableExtractionResult> sidecarTables = extractWithSidecar(
            documentPath,
            pageNum
        );

        return Optional.of(sidecarTables)
                       .filter(tables -> !tables.isEmpty())
                       .orElseGet(() -> reconstructWithLlm(
                           ocrText,
                           pageNum,
                           ocrPageConfidence
                       ).stream().toList());
    }

    private List<com.dsi.rfp.domain.model.TableExtractionResult> extractWithSidecar(
        String documentPath,
        int pageNum
    ) throws IOException {
        byte[] imageBytes = pageImageRenderer.renderPage(
            documentPath,
            pageNum
        );
        OcrPageWithLayoutResultDto result = ocrSidecarClient.extractPageWithLayout(
            imageBytes,
            documentPath,
            pageNum
        );

        return result.scannedTables()
                     .stream()
                     .filter(this::hasHeaders)
                     .map(table -> resultMapper.fromSidecar(
                         table,
                         pageNum
                     ))
                     .toList();
    }

    private Optional<com.dsi.rfp.domain.model.TableExtractionResult> reconstructWithLlm(
        String ocrText,
        int pageNum,
        double ocrPageConfidence
    ) {
        String truncatedText = truncateText(ocrText);

        return llmAdapter.extractStructured(
                             buildPrompt(truncatedText),
                             truncatedText,
                             ScannedTableResponse.class
                         )
                         .filter(response -> !response.headers().isEmpty())
                         .map(response -> resultMapper.fromLlm(
                             response,
                             pageNum,
                             ocrPageConfidence
                         ));
    }

    private boolean hasHeaders(
        OcrScannedTableDto table
    ) {
        return !table.headers().isEmpty();
    }

    private String buildPrompt(
        String ocrText
    ) {
        return promptTemplate.replace("{{ocr_text}}", ocrText);
    }

    private String truncateText(
        String text
    ) {
        String safeText = Optional.ofNullable(text).orElse(StringUtils.EMPTY);

        return safeText.substring(
            0,
            Math.min(
                safeText.length(),
                config.scannedMaxOcrTextLength()
            )
        );
    }
}
