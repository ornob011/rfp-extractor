package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@Order(1)
public class BookmarkHeadingStrategy implements HeadingStrategy {

    @Override
    public List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDDocumentOutline outline = doc.getDocumentCatalog().getDocumentOutline();

            if (Objects.isNull(outline)) {
                return results;
            }

            traverseOutline(
                outline.getFirstChild(),
                1,
                results
            );
        }

        log.debug(
            "event=headings.detected component=BookmarkHeadingStrategy count={} file={}",
            results.size(),
            pdfPath.getFileName()
        );

        return results;
    }

    @Override
    public HeadingDetectionMethod strategyName() {
        return HeadingDetectionMethod.BOOKMARK;
    }

    private void traverseOutline(
        PDOutlineItem item,
        int level,
        List<HeadingCandidate> results
    ) {
        while (Objects.nonNull(item)) {
            String title = item.getTitle();

            if (Objects.nonNull(title) && StringUtils.hasText(title)) {
                results.add(HeadingCandidate.builder()
                                            .text(title.strip())
                                            .level(Math.min(level, 6))
                                            .pageNumber(0)
                                            .startY(0.0f)
                                            .detectedBy(strategyName())
                                            .build());
            }

            if (item.hasChildren()) {
                traverseOutline(
                    item.getFirstChild(),
                    level + 1,
                    results
                );
            }

            item = item.getNextSibling();
        }
    }
}
