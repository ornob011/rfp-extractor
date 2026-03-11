package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.domain.model.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class MixedPageExtractor {

    private final PdfDocumentLoader loader;
    private final VisionExtractionAdapter visionAdapter;
    private final TextLayerQualityPolicy qualityPolicy;

    public MixedPageExtractor(
        PdfDocumentLoader loader,
        VisionExtractionAdapter visionAdapter,
        TextLayerQualityPolicy qualityPolicy
    ) {
        this.loader = loader;
        this.visionAdapter = visionAdapter;
        this.qualityPolicy = qualityPolicy;
    }

    public MixedPageContent extractPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        List<TextBlock> textBlocks = loader.loadPageBoundingBoxes(
            Path.of(documentPath),
            pageNum
        );

        double textLayerQuality = qualityPolicy.score(textBlocks);

        VisionPageResult vlmResult = visionAdapter.extractFullPage(
            documentPath,
            pageNum
        );

        double confidence = Math.min(
            textLayerQuality,
            vlmResult.confidence()
        );

        log.info(
            "event=vlm.mixed component=MixedPageExtractor"
            + " page={} textLayerQuality={} vlmConfidence={}",
            pageNum,
            textLayerQuality,
            vlmResult.confidence()
        );

        return MixedPageContent.textPlusOcr(
            vlmResult.text(),
            confidence,
            vlmResult.tables()
        );
    }
}
