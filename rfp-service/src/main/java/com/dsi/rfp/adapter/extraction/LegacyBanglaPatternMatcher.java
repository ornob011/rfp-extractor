package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.config.BanglaEncodingProperties;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;

@Component
class LegacyBanglaPatternMatcher {

    private final Trie trie;

    LegacyBanglaPatternMatcher(BanglaEncodingProperties properties) {
        Trie.TrieBuilder trieBuilder = Trie.builder()
            .ignoreCase();

        properties.suspiciousPatterns()
            .forEach(trieBuilder::addKeyword);

        trie = trieBuilder.build();
    }

    int countMatches(String text) {
        return trie.parseText(text).size();
    }
}
