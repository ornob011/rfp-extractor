package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.FontNameHeadingParser;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.FontInfo;
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
import java.util.Map;
import java.util.OptionalInt;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class HeadingStyleStrategy implements HeadingStrategy {

    private final FontNameHeadingParser fontNameHeadingParser;

    @Override
    public List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        Map<String, FontInfo> fontMap = loader.loadFontMetadata(pdfPath);
        List<HeadingCandidate> results = new ArrayList<>();

        for (Map.Entry<String, FontInfo> entry : fontMap.entrySet()) {
            String fontName = entry.getKey();
            OptionalInt level = fontNameHeadingParser.parseLevel(fontName);
            level.ifPresent(parsedLevel -> collectByFont(
                pdfPath,
                loader,
                results,
                fontName,
                Math.min(parsedLevel, 6)
            ));
        }
        return results;
    }

    @Override
    public HeadingDetectionMethod strategyName() {
        return HeadingDetectionMethod.HEADING_STYLE;
    }

    private void collectByFont(
        Path pdfPath,
        PdfDocumentLoader loader,
        List<HeadingCandidate> results,
        String fontName,
        int level
    ) {
        try {
            results.addAll(findTextWithFont(pdfPath, loader, fontName, level));
        } catch (IOException ex) {
            throw new SystemIoException(
                String.format(
                    "Failed to process PDF for font-based heading detection. pdfPath=%s fontName=%s",
                    pdfPath,
                    fontName
                ),
                ex
            );
        }
    }

    private List<HeadingCandidate> findTextWithFont(
        Path pdfPath,
        PdfDocumentLoader loader,
        String targetFont,
        int level
    ) throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();

        int pageCount = loader.getPageCount(pdfPath);

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            List<TextBlock> blocks = loader.loadPageBoundingBoxes(
                pdfPath,
                pageIdx
            );

            collectFontBlocks(
                blocks,
                targetFont,
                level,
                pageIdx,
                results
            );
        }

        return results;
    }

    private void collectFontBlocks(
        List<TextBlock> blocks,
        String targetFont,
        int level,
        int pageIdx,
        List<HeadingCandidate> results
    ) {
        StringBuilder current = new StringBuilder();
        float startY = 0;

        for (TextBlock block : blocks) {
            if (targetFont.equals(block.getFontName())) {
                if (current.isEmpty()) {
                    startY = block.getY();
                }
                current.append(block.getText());
                continue;
            }

            if (!current.isEmpty()) {
                results.add(
                    buildCandidate(
                        current.toString(),
                        level,
                        pageIdx,
                        startY,
                        targetFont
                    )
                );

                current.setLength(0);
            }
        }
        if (current.isEmpty()) {
            return;
        }

        results.add(
            buildCandidate(
                current.toString(),
                level,
                pageIdx,
                startY,
                targetFont
            )
        );
    }

    private HeadingCandidate buildCandidate(
        String text,
        int level,
        int page,
        float y,
        String font
    ) {
        return HeadingCandidate.builder()
                               .text(text.strip())
                               .level(level)
                               .pageNumber(page)
                               .startY(y)
                               .fontName(font)
                               .fontSize(0)
                               .detectedBy(strategyName())
                               .build();
    }
}
