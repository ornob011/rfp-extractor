package com.dsi.rfp.adapter.extraction;

record BanglaEncodingSignals(
    int banglaCount,
    int asciiPrintableCount,
    int suspiciousPatternHits
) {

    int relevantCharacterCount() {
        return banglaCount + asciiPrintableCount;
    }

    double banglaMixRatio() {
        int relevantCharacterCount = relevantCharacterCount();

        if (relevantCharacterCount == 0) {
            return 0.0;
        }

        return (double) banglaCount / relevantCharacterCount;
    }
}
