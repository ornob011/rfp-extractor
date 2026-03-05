package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FontSizeHeadingLexiconTest {

    @Test
    void shouldLoadFontSizeRulesFromYaml() {
        FontSizeHeadingLexicon lexicon = new FontSizeHeadingLexicon();

        assertThat(lexicon.defaultMedianFontSize()).isEqualTo(10.0f);
        assertThat(lexicon.headingFontDelta()).isEqualTo(2.0f);
        assertThat(lexicon.maxHeadingLength()).isEqualTo(100);
        assertThat(lexicon.levels()).hasSize(4);
    }
}
