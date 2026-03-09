package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FontHeadingGrammarParserTest {

    private FontHeadingGrammarParser parser;

    @BeforeEach
    void setUp() {
        parser = new FontHeadingGrammarParser();
    }

    @Test
    void shouldParseAttachedHeadingLevel() {
        assertThat(parser.parseLevel("Heading2")).isPresent();
        assertThat(parser.parseLevel("Heading2").getAsInt()).isEqualTo(2);
        assertThat(parser.parseLevel("h3")).isPresent();
        assertThat(parser.parseLevel("h3").getAsInt()).isEqualTo(3);
    }

    @Test
    void shouldParseSpacedMarkerAndLevel() {
        assertThat(parser.parseLevel("heading 4")).isPresent();
        assertThat(parser.parseLevel("heading 4").getAsInt()).isEqualTo(4);
    }

    @Test
    void shouldRejectInvalidMarker() {
        assertThat(parser.parseLevel("Arial 2")).isEmpty();
    }
}
