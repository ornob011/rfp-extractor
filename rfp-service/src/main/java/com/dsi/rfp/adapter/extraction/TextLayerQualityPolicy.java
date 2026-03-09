package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.ocr.OcrExtractionConfig;
import com.dsi.rfp.domain.model.TextBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TextLayerQualityPolicy {

    private final OcrExtractionConfig config;

    public double score(
        List<TextBlock> blocks
    ) {
        return Math.min(
            1.0,
            blocks.size() / config.textQualityBlockDivisor()
        );
    }
}
