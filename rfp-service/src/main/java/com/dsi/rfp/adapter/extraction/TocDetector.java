package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
import com.dsi.rfp.adapter.extraction.parser.TocEntryParser;
import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TocDetector {

    private static final int SCAN_PAGE_LIMIT = 10;
    private static final int TOC_LINE_THRESHOLD = 8;

    private final LineTokenizer lineTokenizer;
    private final TocEntryParser tocEntryParser;

    public Optional<List<HeadingCandidate>> findToc(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        int pageCount = Math.min(
            loader.getPageCount(pdfPath),
            SCAN_PAGE_LIMIT
        );

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(pdfPath, pageIdx);

            List<HeadingCandidate> candidates = extractTocEntries(
                lineTokenizer.tokenizeLines(pageText),
                pageIdx
            );

            if (candidates.size() < TOC_LINE_THRESHOLD) {
                continue;
            }

            log.info(
                "event=toc.detected component=TocDetector page={} entries={}",
                pageIdx + 1,
                candidates.size()
            );

            return Optional.of(candidates);
        }

        return Optional.empty();
    }

    private List<HeadingCandidate> extractTocEntries(
        List<String> lines,
        int pageIdx
    ) {
        List<HeadingCandidate> entries = new ArrayList<>();

        for (String line : lines) {
            tocEntryParser.parse(line).ifPresent(parsed -> entries.add(
                HeadingCandidate.builder()
                                .text(parsed.title().strip())
                                .level(computeLevelFromIndent(parsed.indent()))
                                .pageNumber(pageIdx)
                                .startY(0.0f)
                                .detectedBy(HeadingDetectionMethod.TOC)
                                .build()
            ));
        }
        return entries;
    }

    private int computeLevelFromIndent(int spaces) {
        if (spaces == 0) {
            return 1;
        }

        if (spaces <= 2) {
            return 2;
        }

        if (spaces <= 4) {
            return 3;
        }

        return Math.min(spaces / 2, 6);
    }
}
