package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FontHeadingLexiconTest {

    @Test
    void shouldLoadMarkerLexiconFromYaml() {
        FontHeadingLexicon lexicon = new FontHeadingLexicon();

        assertThat(lexicon.markers()).contains("heading", "h");
    }
}
