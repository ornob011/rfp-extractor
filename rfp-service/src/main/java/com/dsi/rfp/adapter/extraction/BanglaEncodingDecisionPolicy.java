package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.config.BanglaEncodingProperties;
import com.dsi.rfp.domain.model.EncodingDetectionResult;
import org.springframework.stereotype.Component;

@Component
class BanglaEncodingDecisionPolicy {

    private final BanglaEncodingProperties properties;

    BanglaEncodingDecisionPolicy(BanglaEncodingProperties properties) {
        this.properties = properties;
    }

    EncodingDetectionResult decide(BanglaEncodingSignals signals) {
        if (signals.banglaCount() < properties.minBanglaChars()) {
            return clean();
        }

        if (signals.relevantCharacterCount() == 0) {
            return clean();
        }

        double ratioConfidence = ratioConfidence(signals.banglaMixRatio());
        double patternConfidence = patternConfidence(signals.suspiciousPatternHits());
        double confidence = Math.max(
            ratioConfidence,
            patternConfidence
        );

        if (confidence == 0.0) {
            return clean();
        }

        return EncodingDetectionResult.builder()
            .suspectedLegacy(true)
            .confidence(confidence)
            .reason(String.format(
                properties.legacyReasonTemplate(),
                signals.banglaCount(),
                signals.banglaMixRatio(),
                signals.suspiciousPatternHits()
            ))
            .build();
    }

    private double ratioConfidence(double banglaMixRatio) {
        if (banglaMixRatio <= properties.legacyRatioThreshold()) {
            return 0.0;
        }

        if (banglaMixRatio > properties.highConfidenceRatio()) {
            return properties.highConfidence();
        }

        return properties.mediumConfidence();
    }

    private double patternConfidence(int suspiciousPatternHits) {
        if (suspiciousPatternHits < properties.minPatternHits()) {
            return 0.0;
        }

        if (suspiciousPatternHits >= properties.highConfidencePatternHits()) {
            return properties.highConfidence();
        }

        return properties.mediumConfidence();
    }

    private EncodingDetectionResult clean() {
        return EncodingDetectionResult.builder()
            .suspectedLegacy(false)
            .confidence(0.0)
            .reason(properties.cleanReason())
            .build();
    }
}
