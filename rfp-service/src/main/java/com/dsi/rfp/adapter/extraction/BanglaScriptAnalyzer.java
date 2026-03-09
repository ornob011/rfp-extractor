package com.dsi.rfp.adapter.extraction;

import com.ibm.icu.text.UnicodeSet;
import org.springframework.stereotype.Component;

@Component
class BanglaScriptAnalyzer {

    private static final UnicodeSet BENGALI = new UnicodeSet("[[:Bengali:]]").freeze();
    private static final UnicodeSet ASCII_PRINTABLE = new UnicodeSet("[\\u0020-\\u007E]").freeze();

    BanglaEncodingSignals analyze(String text) {
        int banglaCount = Math.toIntExact(
            text.codePoints()
                .filter(BENGALI::contains)
                .count()
        );

        int asciiPrintableCount = Math.toIntExact(
            text.codePoints()
                .filter(ASCII_PRINTABLE::contains)
                .count()
        );

        return new BanglaEncodingSignals(
            banglaCount,
            asciiPrintableCount,
            0
        );
    }
}
