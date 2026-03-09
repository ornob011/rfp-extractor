package com.dsi.rfp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.validation.bangla")
public record BanglaEncodingProperties(
    int samplePages,
    double confidenceThreshold,
    int minBanglaChars,
    double legacyRatioThreshold,
    double highConfidenceRatio,
    int minPatternHits,
    int highConfidencePatternHits,
    double mediumConfidence,
    double highConfidence,
    String cleanReason,
    String legacyReasonTemplate,
    List<String> suspiciousPatterns
) {
}
