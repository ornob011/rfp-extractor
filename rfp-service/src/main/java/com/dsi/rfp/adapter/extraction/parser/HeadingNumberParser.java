package com.dsi.rfp.adapter.extraction.parser;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class HeadingNumberParser {

    private static final int MAX_LEVEL = 6;

    private final SectionNumberGrammarValidator grammarValidator;

    public Optional<ParsedHeadingNumber> parseNumberedHeading(String line) {
        String normalized = StringUtils.stripStart(line, null);
        if (StringUtils.isBlank(normalized)) {
            return Optional.empty();
        }

        Optional<PrefixSplit> split = splitPrefix(normalized);
        if (split.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> sectionNumber = parseSectionNumberToken(split.get().prefixToken());
        if (sectionNumber.isEmpty()) {
            return Optional.empty();
        }

        String remainingText = StringUtils.trimToEmpty(split.get().remainingText());
        if (StringUtils.isBlank(remainingText)) {
            return Optional.empty();
        }

        int level = Math.min(
            sectionSegments(sectionNumber.get()).size(),
            MAX_LEVEL
        );

        return Optional.of(
            new ParsedHeadingNumber(level, remainingText)
        );
    }

    public Optional<String> parseLeadingSectionNumber(String text) {
        String normalized = StringUtils.trimToEmpty(text);
        if (StringUtils.isBlank(normalized)) {
            return Optional.empty();
        }

        Optional<PrefixSplit> split = splitPrefix(normalized);
        if (split.isEmpty()) {
            return Optional.empty();
        }

        return parseSectionNumberToken(split.get().prefixToken());
    }

    private Optional<PrefixSplit> splitPrefix(String text) {
        String[] tokens = StringUtils.split(text, null, 2);
        if (tokens == null) {
            return Optional.empty();
        }

        if (tokens.length == 0) {
            return Optional.empty();
        }

        if (tokens.length == 1) {
            return Optional.of(
                new PrefixSplit(tokens[0], StringUtils.EMPTY)
            );
        }

        return Optional.of(
            new PrefixSplit(tokens[0], tokens[1])
        );
    }

    private Optional<String> parseSectionNumberToken(String token) {
        if (!grammarValidator.isValid(token)) {
            return Optional.empty();
        }

        String normalized = StringUtils.stripEnd(token, ".");
        if (StringUtils.isBlank(normalized)) {
            return Optional.empty();
        }

        return Optional.of(normalized);
    }

    private List<String> sectionSegments(String sectionNumber) {
        String[] segments = StringUtils.split(sectionNumber, '.');
        if (segments == null) {
            return List.of();
        }

        return List.of(segments);
    }

    public record ParsedHeadingNumber(
        int level,
        String remainingText
    ) {
    }

    private record PrefixSplit(
        String prefixToken,
        String remainingText
    ) {
    }
}
