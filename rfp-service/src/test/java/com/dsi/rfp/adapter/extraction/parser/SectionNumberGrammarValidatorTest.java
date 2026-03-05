package com.dsi.rfp.adapter.extraction.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionNumberGrammarValidatorTest {

    private SectionNumberGrammarValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SectionNumberGrammarValidator();
    }

    @Test
    void shouldAcceptDottedNumericSectionNumbers() {
        assertThat(validator.isValid("1.")).isTrue();
        assertThat(validator.isValid("2.3")).isTrue();
        assertThat(validator.isValid("4.2.1")).isTrue();
    }

    @Test
    void shouldRejectNonNumericSegments() {
        assertThat(validator.isValid("2.A")).isFalse();
        assertThat(validator.isValid("A.2")).isFalse();
    }
}
