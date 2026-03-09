package com.dsi.rfp.adapter.extraction.parser;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TocEntryParser {

    private final TocLineGrammarParser tocLineGrammarParser;

    public Optional<TocEntry> parse(String line) {
        if (StringUtils.isBlank(line)) {
            return Optional.empty();
        }

        return tocLineGrammarParser.parse(line)
                                   .map(parsed -> new TocEntry(parsed.indent(), parsed.title()));
    }

    public record TocEntry(
        int indent,
        String title
    ) {
    }
}
