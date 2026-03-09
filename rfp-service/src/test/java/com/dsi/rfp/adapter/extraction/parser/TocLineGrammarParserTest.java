package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TocLineGrammarParserTest {

    private TocLineGrammarParser parser;

    @BeforeEach
    void setUp() {
        parser = new TocLineGrammarParser();
    }

    @Test
    void shouldParseDottedLeaderLine() {
        var parsed = parser.parse("Section 1 ......... 10");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().title()).isEqualTo("Section 1");
        assertThat(parsed.get().pageNumber()).isEqualTo("10");
    }

    @Test
    void shouldParseSpacedLeaderLine() {
        var parsed = parser.parse("Section 1        10");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().title()).isEqualTo("Section 1");
    }

    @Test
    void shouldRejectLineWithoutTrailingPage() {
        var parsed = parser.parse("Section 1 ........");

        assertThat(parsed).isEmpty();
    }
}
