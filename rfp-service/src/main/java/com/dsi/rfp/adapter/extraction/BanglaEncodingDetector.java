package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EncodingDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BanglaEncodingDetector {

    private final BanglaScriptAnalyzer scriptAnalyzer;
    private final LegacyBanglaPatternMatcher patternMatcher;
    private final BanglaEncodingDecisionPolicy decisionPolicy;

    BanglaEncodingDetector(
        BanglaScriptAnalyzer scriptAnalyzer,
        LegacyBanglaPatternMatcher patternMatcher,
        BanglaEncodingDecisionPolicy decisionPolicy
    ) {
        this.scriptAnalyzer = scriptAnalyzer;
        this.patternMatcher = patternMatcher;
        this.decisionPolicy = decisionPolicy;
    }

    public EncodingDetectionResult detect(String text) {
        if (text == null || text.isBlank()) {
            return decisionPolicy.decide(
                new BanglaEncodingSignals(
                    0,
                    0,
                    0
                )
            );
        }

        BanglaEncodingSignals scriptSignals = scriptAnalyzer.analyze(text);
        int suspiciousPatternHits = patternMatcher.countMatches(text);
        BanglaEncodingSignals signals = new BanglaEncodingSignals(
            scriptSignals.banglaCount(),
            scriptSignals.asciiPrintableCount(),
            suspiciousPatternHits
        );

        EncodingDetectionResult result = decisionPolicy.decide(signals);

        if (result.isSuspectedLegacy()) {
            log.info(
                "event=legacy.encoding.suspected component=BanglaEncodingDetector banglaChars={} mixRatio={} suspiciousPatterns={} confidence={}",
                signals.banglaCount(),
                signals.banglaMixRatio(),
                signals.suspiciousPatternHits(),
                result.getConfidence()
            );
        }

        return result;
    }
}
