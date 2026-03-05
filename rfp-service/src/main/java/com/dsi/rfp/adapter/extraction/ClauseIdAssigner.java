package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.HeadingNumberParser;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClauseIdAssigner {

    private static final int MAX_REF_LENGTH = 20;
    private static final String DEFAULT_REF = "unknown";
    private static final String CLAUSE_FMT = "%s:%s:P%d";
    private static final String FALLBACK_FMT = "%s:S%d:P%d";

    private static final UnicodeSet ALLOWED_REF_CHARS = new UnicodeSet(
        "[a-z0-9\\-\\u0020]"
    ).freeze();

    private static final Normalizer2 NORMALIZER = Normalizer2.getNFKCInstance();

    private final HeadingNumberParser headingNumberParser;

    public String assignClauseId(
        String procurementRef,
        String sectionTitle,
        int sectionIndex,
        int paragraphIndex
    ) {
        String normalizedRef = normalizeRef(procurementRef);
        Optional<String> sectionNumber = headingNumberParser.parseLeadingSectionNumber(sectionTitle);

        if (sectionNumber.isPresent()) {
            return String.format(CLAUSE_FMT, normalizedRef, sectionNumber.get(), paragraphIndex);
        }

        log.warn(
            "event=fallback.id component=ClauseIdAssigner procRef={} sectionTitle={}",
            procurementRef,
            sectionTitle
        );

        return String.format(
            FALLBACK_FMT,
            normalizedRef,
            sectionIndex + 1,
            paragraphIndex
        );
    }

    public String assignSubClauseId(
        String parentClauseId,
        String letter
    ) {
        String suffix = StringUtils.lowerCase(StringUtils.trimToEmpty(letter), Locale.ROOT);

        return String.format("%s.%s", parentClauseId, suffix);
    }

    private String normalizeRef(String ref) {
        if (StringUtils.isBlank(ref)) {
            return DEFAULT_REF;
        }

        String lowered = StringUtils.lowerCase(ref, Locale.ROOT);
        String normalized = NORMALIZER.normalize(lowered);
        String filtered = retainAllowedCharacters(normalized);
        String collapsed = StringUtils.normalizeSpace(filtered);
        String hyphenated = StringUtils.replace(collapsed, StringUtils.SPACE, "-");
        String trimmed = StringUtils.trimToEmpty(hyphenated);

        if (StringUtils.isBlank(trimmed)) {
            return DEFAULT_REF;
        }

        return StringUtils.left(trimmed, MAX_REF_LENGTH);
    }

    private String retainAllowedCharacters(String value) {
        StringBuilder builder = new StringBuilder();

        value.codePoints()
             .filter(ALLOWED_REF_CHARS::contains)
             .forEach(builder::appendCodePoint);

        return builder.toString();
    }
}
