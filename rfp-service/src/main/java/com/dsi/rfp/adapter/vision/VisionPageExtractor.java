package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.domain.model.PageExtractionMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.Locale;

@Slf4j
@Component
public class VisionPageExtractor {

    private final VisionExtractionAdapter visionAdapter;

    public VisionPageExtractor(
        VisionExtractionAdapter visionAdapter
    ) {
        this.visionAdapter = visionAdapter;
    }

    public VisionPageExtractionResult extractPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        VisionPageResult result = visionAdapter.extractFullPage(
            documentPath,
            pageNum
        );

        int wordCount = countWords(result.text());

        log.info(
            "event=vlm.page component=VisionPageExtractor"
            + " page={} confidence={} words={}",
            pageNum,
            result.confidence(),
            wordCount
        );

        return new VisionPageExtractionResult(
            pageNum,
            result.text(),
            result.confidence(),
            wordCount,
            PageExtractionMethod.VLM,
            result.tables()
        );
    }

    private int countWords(
        String text
    ) {
        BreakIterator iterator = BreakIterator.getWordInstance(
            Locale.ROOT
        );
        iterator.setText(text);

        int count = 0;
        int start = iterator.first();

        for (int end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {
            String token = text.substring(start, end).trim();

            if (token.isEmpty()) {
                continue;
            }

            if (Character.isLetterOrDigit(token.codePointAt(0))) {
                count++;
            }
        }

        return count;
    }
}
