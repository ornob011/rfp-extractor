package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.BanglaHeadingGrammarParser;
import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
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
import java.util.OptionalInt;

@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class BanglaHeadingStrategy implements HeadingStrategy {

    private final LineTokenizer lineTokenizer;
    private final BanglaHeadingGrammarParser banglaHeadingGrammarParser;

    @Override
    public List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        int pageCount = loader.getPageCount(pdfPath);

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(pdfPath, pageIdx);

            for (String line : lineTokenizer.tokenizeLines(pageText)) {
                detectInLine(line, pageIdx, results);
            }
        }

        log.debug(
            "event=headings.detected component=BanglaHeadingStrategy count={} file={}",
            results.size(),
            pdfPath.getFileName()
        );

        return results;
    }

    @Override
    public HeadingDetectionMethod strategyName() {
        return HeadingDetectionMethod.BANGLA;
    }

    private void detectInLine(
        String line,
        int pageIdx,
        List<HeadingCandidate> results
    ) {
        if (StringUtils.isBlank(line)) {
            return;
        }

        OptionalInt level = banglaHeadingGrammarParser.parseLevel(line);
        level.ifPresent(value -> results.add(buildCandidate(line, value, pageIdx)));
    }

    private HeadingCandidate buildCandidate(
        String text,
        int level,
        int pageIdx
    ) {
        return HeadingCandidate.builder()
                               .text(text)
                               .level(level)
                               .pageNumber(pageIdx)
                               .startY(0.0f)
                               .detectedBy(strategyName())
                               .build();
    }
}
