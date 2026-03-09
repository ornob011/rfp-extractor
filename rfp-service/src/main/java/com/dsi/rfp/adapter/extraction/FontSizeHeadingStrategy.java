package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.FontSizeHeadingLexicon;
import com.dsi.rfp.adapter.extraction.parser.FontSizeLevelResolver;
import com.dsi.rfp.adapter.extraction.parser.FontSizeStatistics;
import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import com.dsi.rfp.domain.model.TextBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class FontSizeHeadingStrategy implements HeadingStrategy {

    private final FontSizeStatistics fontSizeStatistics;
    private final FontSizeLevelResolver fontSizeLevelResolver;
    private final FontSizeHeadingLexicon fontSizeHeadingLexicon;

    @Override
    public List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        int pageCount = loader.getPageCount(pdfPath);

        List<TextBlock> allBlocks = collectAllBlocks(
            pdfPath,
            loader,
            pageCount
        );

        if (allBlocks.isEmpty()) {
            return List.of();
        }

        float medianFontSize = fontSizeStatistics.median(
            allBlocks,
            fontSizeHeadingLexicon.defaultMedianFontSize()
        );

        log.debug(
            "event=median.computed component=FontSizeHeadingStrategy medianFontSize={}",
            medianFontSize
        );

        return allBlocks.stream()
                        .filter(block -> fontSizeLevelResolver.isHeadingCandidate(block, medianFontSize))
                        .map(block -> buildCandidate(block, medianFontSize))
                        .toList();
    }

    @Override
    public HeadingDetectionMethod strategyName() {
        return HeadingDetectionMethod.FONT_SIZE;
    }

    private List<TextBlock> collectAllBlocks(
        Path pdfPath,
        PdfDocumentLoader loader,
        int pageCount
    ) throws IOException {
        List<TextBlock> all = new ArrayList<>();

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            all.addAll(
                loader.loadPageBoundingBoxes(
                    pdfPath,
                    pageIdx
                )
            );
        }

        return all;
    }

    private HeadingCandidate buildCandidate(
        TextBlock block,
        float medianSize
    ) {
        float delta = block.getFontSize() - medianSize;
        int level = fontSizeLevelResolver.levelFromDelta(delta);

        return HeadingCandidate.builder()
                               .text(block.getText().strip())
                               .level(level)
                               .pageNumber(block.getPageNumber())
                               .startY(block.getY())
                               .fontName(block.getFontName())
                               .fontSize(block.getFontSize())
                               .detectedBy(strategyName())
                               .build();
    }
}
