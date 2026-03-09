package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.adapter.ocr.OcrPageWithLayoutResultDto;
import com.dsi.rfp.adapter.ocr.OcrResultDto;
import com.dsi.rfp.adapter.ocr.OcrScannedTableDto;
import com.dsi.rfp.adapter.ocr.OcrSidecarClient;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class ScannedTableReconstructor {

    private final LlmAdapter llmAdapter;
    private final OcrSidecarClient ocrSidecarClient;
    private final PageImageRenderer pageImageRenderer;
    private final ScannedTableResultMapper resultMapper;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final TableExtractionConfig config;
    private final String promptTemplate;

    public ScannedTableReconstructor(
        LlmAdapter llmAdapter,
        OcrSidecarClient ocrSidecarClient,
        PageImageRenderer pageImageRenderer,
        ScannedTableResultMapper resultMapper,
        PromptTemplateRenderer promptTemplateRenderer,
        TableExtractionConfig config
    ) {
        this.llmAdapter = llmAdapter;
        this.ocrSidecarClient = ocrSidecarClient;
        this.pageImageRenderer = pageImageRenderer;
        this.resultMapper = resultMapper;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.config = config;
        promptTemplate = config.scannedPromptTemplate();
    }

    public List<com.dsi.rfp.domain.model.TableExtractionResult> reconstructTables(
        String documentPath,
        String ocrText,
        int pageNum,
        double ocrPageConfidence
    ) throws IOException {
        OcrPageWithLayoutResultDto result = extractWithSidecar(
            documentPath,
            pageNum
        );

        List<TableExtractionResult> sidecarTables = result.scannedTables()
                                                          .stream()
                                                          .filter(this::hasHeaders)
                                                          .map(table -> resultMapper.fromSidecar(
                                                              table,
                                                              pageNum
                                                          ))
                                                          .toList();

        return Optional.of(sidecarTables)
                       .filter(tables -> !tables.isEmpty())
                       .orElseGet(() -> reconstructWithLlm(
                           resolveOcrText(
                               ocrText,
                               result
                           ),
                           pageNum,
                           resolveOcrConfidence(
                               ocrPageConfidence,
                               result
                           )
                       ).stream().toList());
    }

    public List<TableExtractionResult> reconstructTablesAtDpi(
        String documentPath,
        String ocrText,
        int pageNum,
        double ocrPageConfidence,
        int dpi
    ) throws IOException {
        OcrPageWithLayoutResultDto result = extractWithSidecar(
            documentPath,
            pageNum,
            dpi
        );

        List<TableExtractionResult> sidecarTables = result.scannedTables()
                                                          .stream()
                                                          .filter(this::hasHeaders)
                                                          .map(table -> resultMapper.fromSidecar(
                                                              table,
                                                              pageNum
                                                          ))
                                                          .toList();

        return Optional.of(sidecarTables)
                       .filter(tables -> !tables.isEmpty())
                       .orElseGet(() -> reconstructWithLlm(
                           resolveOcrText(
                               ocrText,
                               result
                           ),
                           pageNum,
                           resolveOcrConfidence(
                               ocrPageConfidence,
                               result
                           )
                       ).stream().toList());
    }

    private OcrPageWithLayoutResultDto extractWithSidecar(
        String documentPath,
        int pageNum
    ) throws IOException {
        byte[] imageBytes = pageImageRenderer.renderPage(
            documentPath,
            pageNum
        );

        return ocrSidecarClient.extractPageWithLayout(
            imageBytes,
            documentPath,
            pageNum
        );
    }

    private OcrPageWithLayoutResultDto extractWithSidecar(
        String documentPath,
        int pageNum,
        int dpi
    ) throws IOException {
        byte[] imageBytes = pageImageRenderer.renderPage(
            documentPath,
            pageNum,
            dpi
        );

        return ocrSidecarClient.extractPageWithLayout(
            imageBytes,
            documentPath,
            pageNum,
            dpi
        );
    }

    private Optional<TableExtractionResult> reconstructWithLlm(
        String ocrText,
        int pageNum,
        double ocrPageConfidence
    ) {
        String truncatedText = truncateText(ocrText);
        String prompt = promptTemplateRenderer.render(
            promptTemplate,
            Map.of(
                "ocr_text",
                truncatedText
            )
        );

        return llmAdapter.extractStructured(
                             StringUtils.EMPTY,
                             prompt,
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

    private String truncateText(
        String text
    ) {
        String safeText = Optional.ofNullable(text)
                                  .orElse(StringUtils.EMPTY);

        return safeText.substring(
            0,
            Math.min(
                safeText.length(),
                config.scannedMaxOcrTextLength()
            )
        );
    }

    private String resolveOcrText(
        String fallback,
        OcrPageWithLayoutResultDto result
    ) {
        return Optional.ofNullable(result.ocrResult())
                       .map(OcrResultDto::text)
                       .filter(StringUtils::isNotBlank)
                       .orElse(fallback);
    }

    private double resolveOcrConfidence(
        double fallback,
        OcrPageWithLayoutResultDto result
    ) {
        return Optional.ofNullable(result.ocrResult())
                       .map(OcrResultDto::pageConfidence)
                       .filter(confidence -> confidence > 0.0)
                       .orElse(fallback);
    }
}
