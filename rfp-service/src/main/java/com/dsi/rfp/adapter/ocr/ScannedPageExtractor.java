package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScannedPageExtractor {

    private final OcrSidecarClient ocrClient;
    private final OcrExtractionConfig config;
    private final PageImageRenderer pageImageRenderer;

    public ScannedPageExtractionResult extractPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        byte[] pngBytes = pageImageRenderer.renderPage(
            documentPath,
            pageNum
        );

        OcrResultDto result = ocrClient.extractPage(
            pngBytes,
            config.language()
        );

        log.info(
            "event=ocr.scanned component=ScannedPageExtractor"
            + " page={} confidence={} words={}",
            pageNum,
            result.pageConfidence(),
            result.wordCount()
        );

        return new ScannedPageExtractionResult(
            pageNum,
            result.text(),
            result.pageConfidence(),
            result.wordCount(),
            PageExtractionMethod.OCR
        );
    }
}
