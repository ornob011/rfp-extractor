package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumberedHeadingLexiconTest {

    @Test
    void shouldLoadNumberedHeadingKeywordsFromLexicon() {
        NumberedHeadingLexicon lexicon = new NumberedHeadingLexicon();

        assertThat(lexicon.keywordHeadings())
            .contains("part", "section", "schedule", "annex", "appendix");
        assertThat(lexicon.chapterKeywords()).contains("chapter");
    }
}
