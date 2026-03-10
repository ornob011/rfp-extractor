package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.ocr.OcrBatchPageResult;
import com.dsi.rfp.adapter.ocr.OcrPageWithLayoutResultDto;
import com.dsi.rfp.adapter.ocr.OcrSidecarClient;
import com.dsi.rfp.domain.model.TextBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MixedPageExtractor {

    private final PdfDocumentLoader loader;
    private final OcrSidecarClient ocrClient;
    private final PageImageRenderer pageImageRenderer;
    private final MixedPageMergeService mergeService;
    private final TextLayerQualityPolicy qualityPolicy;

    public MixedPageContent extractPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        List<TextBlock> textBlocks = loader.loadPageBoundingBoxes(
            Path.of(documentPath),
            pageNum
        );

        double textLayerQuality = qualityPolicy.score(textBlocks);

        byte[] pngBytes = pageImageRenderer.renderPage(
            documentPath,
            pageNum
        );

        OcrPageWithLayoutResultDto result = ocrClient.extractPageWithLayout(
            pngBytes,
            documentPath,
            pageNum
        );

        return mergeService.merge(
            new MixedPageMergeInput(
                result.readingOrder().orderedText(),
                result.ocrResult(),
                result.layout(),
                textLayerQuality
            )
        );
    }

    public MixedPageContent extractPageWithResult(
        String documentPath,
        int pageNum,
        OcrBatchPageResult batchResult
    ) throws IOException {
        List<TextBlock> textBlocks = loader.loadPageBoundingBoxes(
            Path.of(documentPath),
            pageNum
        );

        double textLayerQuality = qualityPolicy.score(textBlocks);

        return mergeService.merge(
            new MixedPageMergeInput(
                batchResult.readingOrder().orderedText(),
                batchResult.ocrResult(),
                batchResult.layout(),
                textLayerQuality
            )
        );
    }
}
