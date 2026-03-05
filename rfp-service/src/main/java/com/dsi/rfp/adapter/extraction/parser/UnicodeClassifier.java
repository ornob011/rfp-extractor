package com.dsi.rfp.adapter.extraction.parser;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.UnicodeSet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class UnicodeClassifier {

    private static final int INVALID_CODE_POINT = -1;

    private static final UnicodeSet LETTER_SET = new UnicodeSet("[:L:]").freeze();

    private static final UnicodeSet UPPERCASE_LETTER_SET = new UnicodeSet("[:Lu:]").freeze();

    public boolean startsWithDigit(String text) {
        int cp = firstCodePoint(text);

        if (cp == INVALID_CODE_POINT) {
            return false;
        }

        return UCharacter.isDigit(cp);
    }

    public boolean isDigitToken(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }

        return StringUtils.defaultString(token).codePoints().allMatch(UCharacter::isDigit);
    }

    public boolean startsWithLatinUppercase(String text) {
        int cp = firstCodePoint(text);

        if (cp == INVALID_CODE_POINT) {
            return false;
        }

        if (!UCharacter.isUpperCase(cp)) {
            return false;
        }

        return UScript.getScript(cp) == UScript.LATIN;
    }

    public boolean startsWithBanglaLetter(String text) {
        int cp = firstCodePoint(text);

        if (cp == INVALID_CODE_POINT) {
            return false;
        }

        if (!UCharacter.isLetter(cp)) {
            return false;
        }

        return UScript.getScript(cp) == UScript.BENGALI;
    }

    public boolean isRomanNumeralToken(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }

        String upper = StringUtils.upperCase(token);

        return StringUtils.containsOnly(upper, "IVXLCDM");
    }

    public boolean hasLetterAndAllLettersUppercase(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }

        String input = StringUtils.defaultString(text);

        if (!LETTER_SET.containsSome(input)) {
            return false;
        }

        return input.codePoints()
                    .filter(UCharacter::isLetter)
                    .allMatch(UPPERCASE_LETTER_SET::contains);
    }

    private int firstCodePoint(String text) {
        if (StringUtils.isBlank(text)) {
            return INVALID_CODE_POINT;
        }

        return StringUtils.defaultString(text).codePointAt(0);
    }
}
