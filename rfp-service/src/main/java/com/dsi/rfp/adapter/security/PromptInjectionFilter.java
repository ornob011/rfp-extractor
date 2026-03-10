package com.dsi.rfp.adapter.security;

import com.dsi.rfp.config.PromptInjectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Token;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PromptInjectionFilter {

    private final PromptInjectionProperties properties;
    private final Trie trie;

    public PromptInjectionFilter(PromptInjectionProperties properties) {
        this.properties = properties;
        this.trie = Trie.builder()
                        .ignoreCase()
                        .addKeywords(properties.phrases())
                        .build();
    }

    public String sanitize(String content) {
        if (content == null) {
            return null;
        }

        String sanitized = replaceEmits(
            trie.tokenize(content)
        );

        return String.format(
            "%s%n%s%n%s",
            properties.delimiterOpen(),
            sanitized,
            properties.delimiterClose()
        );
    }

    public boolean containsInjectionPattern(String content) {
        if (content == null) {
            return false;
        }

        return !trie.parseText(content).isEmpty();
    }

    private String replaceEmits(
        Iterable<Token> tokens
    ) {
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            builder.append(fragmentFor(token));
        }

        return builder.toString();
    }

    private String fragmentFor(Token token) {
        if (token.isMatch()) {
            return filteredFragment(token);
        }

        return token.getFragment();
    }

    private String filteredFragment(Token token) {
        log.warn(
            "event=prompt.injection.detected component=PromptInjectionFilter pattern={}",
            token.getEmit().getKeyword()
        );

        return properties.filteredMarker();
    }
}
