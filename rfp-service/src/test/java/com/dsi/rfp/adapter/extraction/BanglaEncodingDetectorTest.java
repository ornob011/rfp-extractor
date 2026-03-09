package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.config.BanglaEncodingProperties;
import com.dsi.rfp.domain.model.EncodingDetectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BanglaEncodingDetectorTest {

    private BanglaEncodingDetector detector;

    @BeforeEach
    void setUp() {
        BanglaEncodingProperties properties = defaultProperties();

        detector = new BanglaEncodingDetector(
            new BanglaScriptAnalyzer(),
            new LegacyBanglaPatternMatcher(properties),
            new BanglaEncodingDecisionPolicy(properties)
        );
    }

    @Test
    void shouldDetectLegacyEncoding() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append('\u0985');
        }
        sb.append("Some ASCII text here");

        EncodingDetectionResult result = detector.detect(sb.toString());

        assertThat(result.isSuspectedLegacy()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldReturnCleanForPureAscii() {
        EncodingDetectionResult result = detector.detect(
            "This is pure ASCII text without any Bangla characters."
        );

        assertThat(result.isSuspectedLegacy()).isFalse();
        assertThat(result.getConfidence()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnCleanForNullInput() {
        EncodingDetectionResult result = detector.detect(null);

        assertThat(result.isSuspectedLegacy()).isFalse();
    }

    @Test
    void shouldReturnCleanForEmptyInput() {
        EncodingDetectionResult result = detector.detect("");

        assertThat(result.isSuspectedLegacy()).isFalse();
    }

    @Test
    void shouldReturnCleanForFewBanglaChars() {
        EncodingDetectionResult result = detector.detect(
            "\u0985\u0986\u0987 Some long English text here to dilute the ratio"
        );

        assertThat(result.isSuspectedLegacy()).isFalse();
    }

    @Test
    void shouldReturnHighConfidenceForHeavyBangla() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append('\u09AC');
        }
        sb.append("text");

        EncodingDetectionResult result = detector.detect(sb.toString());

        assertThat(result.isSuspectedLegacy()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.8);
    }

    @Test
    void shouldDetectConfiguredSuspiciousPatterns() {
        EncodingDetectionResult result = detector.detect(
            "এই টেক্সটে cÖ এবং wK এবং ‡h আছে"
        );

        assertThat(result.isSuspectedLegacy()).isTrue();
        assertThat(result.getConfidence()).isGreaterThan(0.8);
    }

    private BanglaEncodingProperties defaultProperties() {
        return new BanglaEncodingProperties(
            3,
            0.8,
            10,
            0.30,
            0.60,
            1,
            3,
            0.60,
            0.85,
            "No legacy encoding detected",
            "Detected %d Bangla Unicode characters with mix ratio %.2f and %d suspicious legacy patterns; possible SutonnyMJ/Bijoy legacy encoding",
            java.util.List.of(
                "cÖ",
                "wK",
                "‡h"
            )
        );
    }
}
