package com.dsi.rfp.adapter.extraction.parser;

import com.dsi.rfp.domain.model.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FontSizeLevelResolverTest {

    private FontSizeLevelResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new FontSizeLevelResolver(new FontSizeHeadingLexicon());
    }

    @Test
    void shouldMapLargeDeltaToLevelOne() {
        assertThat(resolver.levelFromDelta(11f)).isEqualTo(1);
    }

    @Test
    void shouldAcceptShortNonBlankHighFontTextAsHeading() {
        TextBlock block = TextBlock.builder()
                                   .text("Heading")
                                   .fontSize(15f)
                                   .pageNumber(0)
                                   .build();

        assertThat(resolver.isHeadingCandidate(block, 10f)).isTrue();
    }

    @Test
    void shouldRejectOverlyLongHeadingCandidate() {
        TextBlock block = TextBlock.builder()
                                   .text("A".repeat(101))
                                   .fontSize(20f)
                                   .pageNumber(0)
                                   .build();

        assertThat(resolver.isHeadingCandidate(block, 10f)).isFalse();
    }
}
