package com.dsi.rfp.adapter.extraction.parser;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class LineTokenizer {

    public List<String> tokenizeLines(String text) {
        String[] lines = StringUtils.splitPreserveAllTokens(
            StringUtils.defaultString(text),
            '\n'
        );

        return Arrays.asList(ArrayUtils.nullToEmpty(lines));
    }

    public List<String> tokenizeWords(String line) {
        String[] tokens = SimpleTokenizer.INSTANCE.tokenize(
            StringUtils.defaultString(line)
        );

        return Arrays.asList(ArrayUtils.nullToEmpty(tokens));
    }

    public List<TokenSpan> tokenizeWithOffsets(String line) {
        String source = StringUtils.defaultString(line);
        Span[] spans = SimpleTokenizer.INSTANCE.tokenizePos(
            source
        );
        if (spans == null) {
            return List.of();
        }
        return Arrays.stream(spans)
                     .map(span -> new TokenSpan(
                         span.getCoveredText(source).toString(),
                         span.getStart(),
                         span.getEnd()
                     ))
                     .toList();
    }

    public record TokenSpan(
        String token,
        int start,
        int end
    ) {
    }
}
