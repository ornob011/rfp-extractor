package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BanglaHeadingGrammarParserTest {

    private BanglaHeadingGrammarParser parser;

    @BeforeEach
    void setUp() {
        parser = new BanglaHeadingGrammarParser();
    }

    @Test
    void shouldParseChapterLineAsLevelOne() {
        assertThat(parser.parseLevel("অধ্যায় 1 পরিচিতি")).isPresent();
        assertThat(parser.parseLevel("অধ্যায় 1 পরিচিতি").getAsInt()).isEqualTo(1);
    }

    @Test
    void shouldParseDharaAsLevelTwo() {
        assertThat(parser.parseLevel("ধারা ৩ প্রযোজ্যতা")).isPresent();
        assertThat(parser.parseLevel("ধারা ৩ প্রযোজ্যতা").getAsInt()).isEqualTo(2);
    }

    @Test
    void shouldRejectDharaWithoutNumber() {
        assertThat(parser.parseLevel("ধারা প্রযোজ্যতা")).isEmpty();
    }
}
