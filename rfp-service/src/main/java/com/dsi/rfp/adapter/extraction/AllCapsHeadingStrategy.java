package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
import com.dsi.rfp.adapter.extraction.parser.UnicodeClassifier;
import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class AllCapsHeadingStrategy implements HeadingStrategy {

    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 80;

    private final LineTokenizer lineTokenizer;
    private final UnicodeClassifier unicodeClassifier;

    @Override
    public List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();

        int pageCount = loader.getPageCount(pdfPath);

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(
                pdfPath,
                pageIdx
            );

            List<String> lines = lineTokenizer.tokenizeLines(pageText);

            detectAllCapsInLines(
                lines,
                pageIdx,
                results
            );
        }

        return results;
    }

    @Override
    public HeadingDetectionMethod strategyName() {
        return HeadingDetectionMethod.ALL_CAPS;
    }

    private void detectAllCapsInLines(
        List<String> lines,
        int pageIdx,
        List<HeadingCandidate> results
    ) {
        for (int i = 0; i < lines.size(); i++) {
            String line = StringUtils.strip(lines.get(i));

            if (isAllCapsHeading(line, lines, i)) {
                HeadingCandidate candidate = HeadingCandidate.builder()
                                                             .text(line)
                                                             .level(1)
                                                             .pageNumber(pageIdx)
                                                             .startY(0.0f)
                                                             .detectedBy(strategyName())
                                                             .build();

                results.add(candidate);
            }
        }
    }

    private boolean isAllCapsHeading(
        String line,
        List<String> lines,
        int index
    ) {
        if (StringUtils.isBlank(line)) {
            return false;
        }

        if (line.length() < MIN_LENGTH || line.length() > MAX_LENGTH) {
            return false;
        }

        if (!unicodeClassifier.hasLetterAndAllLettersUppercase(line)) {
            return false;
        }

        return isSurroundedByBlanks(
            lines,
            index
        );
    }

    private boolean isSurroundedByBlanks(
        List<String> lines,
        int index
    ) {
        String previous = previousLine(
            lines,
            index
        );

        String next = nextLine(
            lines,
            index
        );

        return StringUtils.isBlank(previous) && StringUtils.isBlank(next);
    }

    private String previousLine(
        List<String> lines,
        int index
    ) {
        if (index == 0) {
            return StringUtils.EMPTY;
        }

        return lines.get(index - 1);
    }

    private String nextLine(
        List<String> lines,
        int index
    ) {
        if (index == lines.size() - 1) {
            return StringUtils.EMPTY;
        }

        return lines.get(index + 1);
    }
}
