package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableType;
import com.ibm.icu.text.Normalizer2;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TableTypeClassifier {

    private static final Normalizer2 NORMALIZER = Normalizer2.getNFKCInstance();

    private final Map<TableType, TypeMatcher> matchers;
    private final Map<TableType, Integer> tieBreakRank;

    public TableTypeClassifier(TableTypeClassifierConfigRegistry configRegistry) {
        matchers = buildMatchers(configRegistry.weightedKeywords());
        tieBreakRank = buildTieBreakRank(configRegistry.tieBreakOrder());
    }

    public TableType classify(
        List<String> headers,
        String caption
    ) {
        String text = normalizeText(headers, caption);

        return matchers.entrySet()
                       .stream()
                       .map(entry -> new ScoredType(
                           entry.getKey(),
                           score(entry.getValue(), text),
                           tieBreakRank.getOrDefault(entry.getKey(), Integer.MAX_VALUE)
                       ))
                       .filter(scoredType -> scoredType.score() > 0.0)
                       .sorted(this::compareScoredTypes)
                       .map(ScoredType::tableType)
                       .findFirst()
                       .orElse(TableType.OTHER);
    }

    private int compareScoredTypes(
        ScoredType left,
        ScoredType right
    ) {
        int byScore = Double.compare(right.score(), left.score());

        return Optional.of(byScore)
                       .filter(score -> score != 0)
                       .orElseGet(() -> Integer.compare(
                           left.tieBreakRank(),
                           right.tieBreakRank()
                       ));
    }

    private Map<TableType, TypeMatcher> buildMatchers(
        Map<TableType, Map<String, Double>> weightedKeywords
    ) {
        EnumMap<TableType, TypeMatcher> result = new EnumMap<>(TableType.class);

        weightedKeywords.forEach((type, weights) ->
            result.put(
                type,
                new TypeMatcher(
                    Trie.builder()
                        .ignoreCase()
                        .addKeywords(weights.keySet())
                        .build(),
                    normalizeKeywordWeights(weights)
                )
            )
        );

        return Map.copyOf(result);
    }

    private Map<String, Double> normalizeKeywordWeights(
        Map<String, Double> weights
    ) {
        return weights.entrySet()
                      .stream()
                      .collect(Collectors.toMap(
                          entry -> entry.getKey().toLowerCase(Locale.ROOT),
                          Map.Entry::getValue
                      ));
    }

    private Map<TableType, Integer> buildTieBreakRank(
        List<TableType> tieBreakOrder
    ) {
        EnumMap<TableType, Integer> rank = new EnumMap<>(TableType.class);

        for (int index = 0; index < tieBreakOrder.size(); index++) {
            rank.put(tieBreakOrder.get(index), index);
        }

        return Map.copyOf(rank);
    }

    private String normalizeText(
        List<String> headers,
        String caption
    ) {
        String merged = Stream.concat(
                                  headers.stream(),
                                  Optional.ofNullable(caption).stream()
                              )
                              .collect(Collectors.joining(StringUtils.SPACE));

        String unicodeNormalized = NORMALIZER.normalize(merged);
        String[] tokens = SimpleTokenizer.INSTANCE.tokenize(unicodeNormalized);

        return String.join(StringUtils.SPACE, tokens)
                     .toLowerCase(Locale.ROOT);
    }

    private double score(
        TypeMatcher matcher,
        String text
    ) {
        return matcher.trie()
                      .parseText(text)
                      .stream()
                      .map(Emit::getKeyword)
                      .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                      .mapToDouble(keyword -> matcher.keywordWeights()
                                                     .getOrDefault(keyword, 0.0))
                      .sum();
    }

    private record TypeMatcher(
        Trie trie,
        Map<String, Double> keywordWeights
    ) {
    }

    private record ScoredType(
        TableType tableType,
        double score,
        int tieBreakRank
    ) {
    }
}
