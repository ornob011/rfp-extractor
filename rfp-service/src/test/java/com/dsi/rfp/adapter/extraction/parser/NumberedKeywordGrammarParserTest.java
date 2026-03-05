package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumberedKeywordGrammarParserTest {

    private NumberedKeywordGrammarParser parser;
    private NumberedHeadingLexicon lexicon;

    @BeforeEach
    void setUp() {
        parser = new NumberedKeywordGrammarParser();
        lexicon = new NumberedHeadingLexicon();
    }

    @Test
    void shouldParseKeywordHeadingLine() {
        var match = parser.parse("SECTION II ELIGIBILITY CRITERIA", lexicon);

        assertThat(match).isPresent();
        assertThat(match.get().type())
            .isEqualTo(NumberedKeywordGrammarParser.KeywordType.KEYWORD_HEADING);
        assertThat(match.get().level()).isEqualTo(1);
    }

    @Test
    void shouldParseChapterHeadingLine() {
        var match = parser.parse("Chapter 1 Introduction", lexicon);

        assertThat(match).isPresent();
        assertThat(match.get().type())
            .isEqualTo(NumberedKeywordGrammarParser.KeywordType.CHAPTER_HEADING);
    }

    @Test
    void shouldRejectUnknownKeyword() {
        var match = parser.parse("SECT II ELIGIBILITY", lexicon);

        assertThat(match).isEmpty();
    }
}
