package com.dsi.rfp.adapter.extraction.parser;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class FontNameHeadingParser {

    private final LineTokenizer lineTokenizer;
    private final FontHeadingLexicon fontHeadingLexicon;
    private final FontHeadingGrammarParser fontHeadingGrammarParser;

    public OptionalInt parseLevel(String fontName) {
        if (StringUtils.isBlank(fontName)) {
            return OptionalInt.empty();
        }

        List<String> tokens = tokenizeFontName(fontName);

        OptionalInt pairedLevel = parsePairedLevel(tokens);
        if (pairedLevel.isPresent()) {
            return pairedLevel;
        }

        return parseSingleTokenLevel(tokens);
    }

    private List<String> tokenizeFontName(String fontName) {
        String normalized = StringUtils.replaceChars(fontName, "-_:", "   ");
        String[] parts = StringUtils.splitByCharacterTypeCamelCase(normalized);
        String joined = StringUtils.join(parts, StringUtils.SPACE);

        return lineTokenizer.tokenizeWords(joined)
                            .stream()
                            .map(StringUtils::lowerCase)
                            .toList();
    }

    private OptionalInt parsePairedLevel(List<String> tokens) {
        int size = tokens.size();
        if (size < 2) {
            return OptionalInt.empty();
        }

        return IntStream.range(0, size - 1)
                        .mapToObj(index -> String.format("%s %s", tokens.get(index), tokens.get(index + 1)))
                        .flatMapToInt(this::parseMatchingMarkerPair)
                        .findFirst();
    }

    private OptionalInt parseSingleTokenLevel(List<String> tokens) {
        return tokens.stream()
                     .flatMapToInt(this::parseMatchingSingle)
                     .findFirst();
    }

    private java.util.stream.IntStream parseMatchingMarkerPair(String pair) {
        boolean matchesMarker = fontHeadingLexicon.markers().stream()
                                                  .anyMatch(marker -> StringUtils.startsWith(pair, String.format("%s ", marker)));

        if (!matchesMarker) {
            return java.util.stream.IntStream.empty();
        }

        return fontHeadingGrammarParser.parseLevel(pair).stream();
    }

    private java.util.stream.IntStream parseMatchingSingle(String token) {
        boolean matchesMarker = fontHeadingLexicon.markers().stream()
                                                  .anyMatch(marker -> StringUtils.startsWith(token, marker));

        if (!matchesMarker) {
            return java.util.stream.IntStream.empty();
        }

        return fontHeadingGrammarParser.parseLevel(token).stream();
    }
}
