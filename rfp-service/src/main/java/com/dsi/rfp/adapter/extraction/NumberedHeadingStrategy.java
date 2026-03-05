package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.*;
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
import java.util.Optional;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class NumberedHeadingStrategy implements HeadingStrategy {

    private final LineTokenizer tokenizer;
    private final HeadingNumberParser headingNumberParser;
    private final UnicodeClassifier unicodeClassifier;
    private final NumberedKeywordGrammarParser numberedKeywordGrammarParser;
    private final NumberedHeadingLexicon numberedHeadingLexicon;

    @Override
    public List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException {
        List<HeadingCandidate> results = new ArrayList<>();
        int pageCount = loader.getPageCount(pdfPath);

        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            String pageText = loader.loadPageText(pdfPath, pageIdx);

            for (String line : tokenizer.tokenizeLines(pageText)) {
                detectInLine(
                    line,
                    pageIdx,
                    results
                );
            }
        }

        return results;
    }

    @Override
    public HeadingDetectionMethod strategyName() {
        return HeadingDetectionMethod.NUMBERED;
    }

    private void detectInLine(
        String line,
        int pageIdx,
        List<HeadingCandidate> results
    ) {
        if (StringUtils.isBlank(line)) {
            return;
        }

        var numbered = headingNumberParser.parseNumberedHeading(line);
        if (numbered.isPresent() && isHeadingContentStart(numbered.get().remainingText())) {
            results.add(
                buildCandidate(
                    line,
                    numbered.get().level(),
                    pageIdx
                )
            );

            return;
        }

        Optional<NumberedKeywordGrammarParser.KeywordMatch> keywordMatch = numberedKeywordGrammarParser.parse(
            line,
            numberedHeadingLexicon
        );

        keywordMatch.ifPresent(
            match -> results.add(
                buildCandidate(
                    line,
                    match.level(),
                    pageIdx
                )
            )
        );
    }

    private boolean isHeadingContentStart(String remainingText) {
        if (unicodeClassifier.startsWithLatinUppercase(remainingText)) {
            return true;
        }

        return unicodeClassifier.startsWithBanglaLetter(remainingText);
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
