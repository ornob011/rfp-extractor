package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BanglaHeadingLexiconTest {

    @Test
    void shouldLoadVersionedPrefixRulesFromYaml() {
        BanglaHeadingLexicon lexicon = new BanglaHeadingLexicon();

        assertThat(lexicon.rules()).isNotEmpty();
        assertThat(lexicon.rules())
            .extracting(BanglaHeadingLexicon.HeadingRule::prefix)
            .contains("অধ্যায়", "ধারা", "অনুচ্ছেদ");
    }
}
