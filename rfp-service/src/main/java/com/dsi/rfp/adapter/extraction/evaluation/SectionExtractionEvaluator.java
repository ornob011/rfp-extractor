package com.dsi.rfp.adapter.extraction.evaluation;

import com.dsi.rfp.domain.model.AnnotatedSection;
import com.dsi.rfp.domain.model.Section;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class SectionExtractionEvaluator {

    public EvaluationResult evaluate(
        List<Section> extracted,
        List<AnnotatedSection> expected
    ) {
        Set<String> extractedTitles = flattenTitles(extracted);
        Set<String> expectedTitles = flattenAnnotatedTitles(expected);

        int tp = countIntersection(extractedTitles, expectedTitles);

        int fp = extractedTitles.size() - tp;
        int fn = expectedTitles.size() - tp;

        double precision = safeDivide(tp, extractedTitles.size());
        double recall = safeDivide(tp, expectedTitles.size());
        double f1 = f1Score(precision, recall);

        log.info(
            "event=evaluation.complete component=SectionExtractionEvaluator tp={} fp={} fn={} f1={}",
            tp, fp, fn, f1
        );

        return EvaluationResult.builder()
                               .precision(precision)
                               .recall(recall)
                               .f1Score(f1)
                               .truePositives(tp)
                               .falsePositives(fp)
                               .falseNegatives(fn)
                               .build();
    }

    private Set<String> flattenTitles(List<Section> sections) {
        Set<String> titles = new HashSet<>();

        for (Section s : sections) {
            titles.add(
                normalizeTitle(s.getTitle())
            );

            titles.addAll(
                flattenTitles(s.getChildren())
            );
        }

        return titles;
    }

    private Set<String> flattenAnnotatedTitles(
        List<AnnotatedSection> sections
    ) {
        Set<String> titles = new HashSet<>();

        for (AnnotatedSection s : sections) {
            titles.add(
                normalizeTitle(s.getTitle())
            );

            titles.addAll(
                flattenAnnotatedTitles(s.getChildren())
            );
        }

        return titles;
    }

    private String normalizeTitle(String title) {
        return title.strip().toLowerCase();
    }

    private int countIntersection(
        Set<String> extractedTitles,
        Set<String> expectedTitles
    ) {
        return (int) extractedTitles.stream()
                                    .filter(expectedTitles::contains)
                                    .count();
    }

    private double safeDivide(
        int numerator,
        int denominator
    ) {
        if (denominator == 0) {
            return 0.0;
        }

        return (double) numerator / denominator;
    }

    private double f1Score(
        double precision,
        double recall
    ) {
        double sum = precision + recall;

        if (sum == 0.0) {
            return 0.0;
        }

        return (2 * precision * recall) / sum;
    }

    @Data
    @Builder
    public static class EvaluationResult {

        private double precision;
        private double recall;
        private double f1Score;
        private int truePositives;
        private int falsePositives;
        private int falseNegatives;
    }
}
