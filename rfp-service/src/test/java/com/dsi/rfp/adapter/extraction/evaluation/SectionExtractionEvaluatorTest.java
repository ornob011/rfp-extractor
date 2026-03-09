package com.dsi.rfp.adapter.extraction.evaluation;

import com.dsi.rfp.domain.model.AnnotatedSection;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.model.SectionConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SectionExtractionEvaluatorTest {

    private SectionExtractionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SectionExtractionEvaluator();
    }

    @Test
    void shouldReturnPerfectScoreWhenAllSectionsMatch() {
        List<Section> extracted = List.of(
            section("Introduction"), section("Scope"), section("Requirements")
        );
        List<AnnotatedSection> expected = List.of(
            annotated("Introduction"), annotated("Scope"), annotated("Requirements")
        );

        var result = evaluator.evaluate(extracted, expected);
        assertThat(result.getF1Score()).isEqualTo(1.0);
        assertThat(result.getPrecision()).isEqualTo(1.0);
        assertThat(result.getRecall()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnZeroWhenNoSectionsMatch() {
        List<Section> extracted = List.of(section("A"), section("B"));
        List<AnnotatedSection> expected = List.of(annotated("X"), annotated("Y"));

        var result = evaluator.evaluate(extracted, expected);
        assertThat(result.getF1Score()).isEqualTo(0.0);
        assertThat(result.getTruePositives()).isEqualTo(0);
    }

    @Test
    void shouldComputePartialMatch() {
        List<Section> extracted = List.of(
            section("Introduction"), section("Scope"), section("Extra")
        );
        List<AnnotatedSection> expected = List.of(
            annotated("Introduction"), annotated("Scope"), annotated("Missing")
        );

        var result = evaluator.evaluate(extracted, expected);
        assertThat(result.getTruePositives()).isEqualTo(2);
        assertThat(result.getFalsePositives()).isEqualTo(1);
        assertThat(result.getFalseNegatives()).isEqualTo(1);
    }

    @Test
    void shouldHandleEmptyExtracted() {
        List<Section> extracted = List.of();
        List<AnnotatedSection> expected = List.of(annotated("A"), annotated("B"));

        var result = evaluator.evaluate(extracted, expected);
        assertThat(result.getF1Score()).isEqualTo(0.0);
        assertThat(result.getFalseNegatives()).isEqualTo(2);
    }

    @Test
    void shouldHandleEmptyExpected() {
        List<Section> extracted = List.of(section("A"));
        List<AnnotatedSection> expected = List.of();

        var result = evaluator.evaluate(extracted, expected);
        assertThat(result.getRecall()).isEqualTo(0.0);
        assertThat(result.getFalsePositives()).isEqualTo(1);
    }

    @Test
    void shouldHandleBothEmpty() {
        var result = evaluator.evaluate(List.of(), List.of());
        assertThat(result.getF1Score()).isEqualTo(0.0);
        assertThat(result.getTruePositives()).isEqualTo(0);
    }

    @Test
    void shouldFlattenChildrenForComparison() {
        Section parent = section("Parent");
        parent.getChildren().add(section("Child A"));
        parent.getChildren().add(section("Child B"));

        AnnotatedSection expectedParent = annotated("Parent");
        expectedParent.getChildren().add(annotated("Child A"));
        expectedParent.getChildren().add(annotated("Child B"));

        var result = evaluator.evaluate(List.of(parent), List.of(expectedParent));
        assertThat(result.getTruePositives()).isEqualTo(3);
        assertThat(result.getF1Score()).isEqualTo(1.0);
    }

    @Test
    void shouldNormalizeTitleCaseForComparison() {
        List<Section> extracted = List.of(section("INTRODUCTION"));
        List<AnnotatedSection> expected = List.of(annotated("Introduction"));

        var result = evaluator.evaluate(extracted, expected);
        assertThat(result.getTruePositives()).isEqualTo(1);
        assertThat(result.getF1Score()).isEqualTo(1.0);
    }

    private Section section(String title) {
        return Section.builder()
                      .id(UUID.randomUUID())
                      .title(title).level(1).pageStart(0).pageEnd(5)
                      .confidence(SectionConfidence.builder().score(0.9).method(HeadingDetectionMethod.TOC).build())
                      .build();
    }

    private AnnotatedSection annotated(String title) {
        return AnnotatedSection.builder()
                               .title(title).level(1).pageStart(0).pageEnd(5).build();
    }
}
