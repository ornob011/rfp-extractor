package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class FontNameHeadingParserTest {

    private FontNameHeadingParser parser;

    @BeforeEach
    void setUp() {
        parser = new FontNameHeadingParser(
            new LineTokenizer(),
            new FontHeadingLexicon(),
            new FontHeadingGrammarParser()
        );
    }

    @Test
    void shouldParseHeadingTokenWithAttachedDigit() {
        OptionalInt level = parser.parseLevel("Heading2");

        assertThat(level).isPresent();
        assertThat(level.getAsInt()).isEqualTo(2);
    }

    @Test
    void shouldParsePairMarkerAndDigit() {
        OptionalInt level = parser.parseLevel("Arial-Heading-3");

        assertThat(level).isPresent();
        assertThat(level.getAsInt()).isEqualTo(3);
    }

    @Test
    void shouldParseShortMarkerWithDigit() {
        OptionalInt level = parser.parseLevel("H1");

        assertThat(level).isPresent();
        assertThat(level.getAsInt()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyForNonHeadingFont() {
        OptionalInt level = parser.parseLevel("Arial-Regular");

        assertThat(level).isEmpty();
    }
}
