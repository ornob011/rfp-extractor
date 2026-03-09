package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HeadingNumberParserTest {

    private HeadingNumberParser parser;

    @BeforeEach
    void setUp() {
        parser = new HeadingNumberParser(new SectionNumberGrammarValidator());
    }

    @Test
    void shouldParseMultiSegmentSectionPrefix() {
        Optional<HeadingNumberParser.ParsedHeadingNumber> parsed =
            parser.parseNumberedHeading("2.3.1 Specific Requirements");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().level()).isEqualTo(3);
        assertThat(parsed.get().remainingText()).isEqualTo("Specific Requirements");
    }

    @Test
    void shouldRejectInvalidPrefixToken() {
        Optional<HeadingNumberParser.ParsedHeadingNumber> parsed =
            parser.parseNumberedHeading("2.A Requirements");

        assertThat(parsed).isEmpty();
    }

    @Test
    void shouldExtractLeadingSectionNumber() {
        Optional<String> section = parser.parseLeadingSectionNumber("3.2 Scope");

        assertThat(section).contains("3.2");
    }
}
