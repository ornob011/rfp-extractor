package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpEntities;
import com.ibm.icu.text.Normalizer2;
import org.ahocorasick.trie.Trie;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
class PackSignalScorer {

    private final Normalizer2 normalizer;
    private final EntitySignalReader entitySignalReader;

    PackSignalScorer(EntitySignalReader entitySignalReader) {
        this.entitySignalReader = entitySignalReader;
        normalizer = Normalizer2.getNFKCCasefoldInstance();
    }

    Map<String, Integer> scorePacks(
        List<RulePackMetadata.PackPolicy> packs,
        RfpEntities entities,
        String sectionTitles,
        String scopeSummary
    ) {
        Map<String, PackSignalRuntime> runtimes = packs.stream()
                                                       .collect(Collectors.toUnmodifiableMap(
                                                           RulePackMetadata.PackPolicy::packId,
                                                           this::runtimeFor
                                                       ));

        return packs.stream()
                    .collect(Collectors.toMap(
                        RulePackMetadata.PackPolicy::packId,
                        pack -> scorePack(
                            pack,
                            runtimes.get(pack.packId()),
                            entities,
                            normalize(sectionTitles),
                            normalize(scopeSummary)
                        )
                    ));
    }

    private int scorePack(
        RulePackMetadata.PackPolicy pack,
        PackSignalRuntime runtime,
        RfpEntities entities,
        String sectionTitles,
        String scopeSummary
    ) {
        int sectionScore = runtime.sectionTitleKeywords()
                                  .parseText(sectionTitles)
                                  .size()
                           * pack.signals().sectionTitleKeywords().weight();

        int scopeScore = runtime.scopeSummaryKeywords()
                                .parseText(scopeSummary)
                                .size()
                         * pack.signals().scopeSummaryKeywords().weight();

        int entityPresenceScore = (int) runtime.entityPresenceFields()
                                               .stream()
                                               .map(field -> entitySignalReader.read(
                                                   entities,
                                                   field
                                               ))
                                               .filter(this::hasSignalValue)
                                               .count()
                                  * pack.signals().entityPresence().weight();

        int entityValueScore = runtime.entityValueKeywordTries()
                                      .entrySet()
                                      .stream()
                                      .mapToInt(entry -> keywordHits(
                                          entities,
                                          entry.getKey(),
                                          entry.getValue()
                                      ))
                                      .sum()
                               * pack.signals().entityValueKeywords().weight();

        return sectionScore + scopeScore + entityPresenceScore + entityValueScore;
    }

    private int keywordHits(
        RfpEntities entities,
        String field,
        Trie trie
    ) {
        return Optional.ofNullable(entitySignalReader.read(
                           entities,
                           field
                       ))
                       .filter(this::hasSignalValue)
                       .map(Object::toString)
                       .map(this::normalize)
                       .map(text -> trie.parseText(text).size())
                       .orElse(0);
    }

    private boolean hasSignalValue(Object value) {
        return Optional.ofNullable(value)
                       .map(candidate -> switch (candidate) {
                           case String text -> !text.isBlank();
                           default -> true;
                       })
                       .orElse(false);
    }

    private PackSignalRuntime runtimeFor(RulePackMetadata.PackPolicy pack) {
        return new PackSignalRuntime(
            keywordTrie(pack.signals().sectionTitleKeywords().values()),
            keywordTrie(pack.signals().scopeSummaryKeywords().values()),
            pack.signals().entityPresence().fields(),
            pack.signals().entityValueKeywords().fields()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> keywordTrie(entry.getValue())
                ))
        );
    }

    private Trie keywordTrie(List<String> values) {
        return Trie.builder()
                   .ignoreCase()
                   .addKeywords(values)
                   .build();
    }

    private String normalize(String text) {
        return normalizer.normalize(
            Optional.ofNullable(text)
                    .orElse(StringUtils.EMPTY)
        );
    }

    private record PackSignalRuntime(
        Trie sectionTitleKeywords,
        Trie scopeSummaryKeywords,
        List<String> entityPresenceFields,
        Map<String, Trie> entityValueKeywordTries
    ) {
    }
}
