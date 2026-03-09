package com.dsi.rfp.adapter.extraction.parser;

import com.dsi.rfp.domain.model.TextBlock;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;

@Component
@RequiredArgsConstructor
public class FontSizeLevelResolver {

    private final FontSizeHeadingLexicon lexicon;

    public boolean isHeadingCandidate(
        TextBlock block,
        float medianFontSize
    ) {
        if (block.getFontSize() <= medianFontSize + lexicon.headingFontDelta()) {
            return false;
        }

        if (StringUtils.isBlank(block.getText())) {
            return false;
        }

        return block.getText().length() <= lexicon.maxHeadingLength();
    }

    public int levelFromDelta(float delta) {
        OptionalInt level = lexicon.levels().stream()
                                   .filter(rule -> matches(rule, delta))
                                   .mapToInt(FontSizeHeadingLexicon.FontSizeRule::level)
                                   .findFirst();

        return level.orElse(4);
    }

    private boolean matches(
        FontSizeHeadingLexicon.FontSizeRule rule,
        float delta
    ) {
        if (rule.minExclusive() != null && delta <= rule.minExclusive()) {
            return false;
        }

        return rule.maxInclusive() == null || delta <= rule.maxInclusive();
    }
}
