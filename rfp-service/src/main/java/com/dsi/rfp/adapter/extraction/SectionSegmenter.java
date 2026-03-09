package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.model.SectionConfidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SectionSegmenter {

    private static final int MIN_HEADINGS = 3;

    private final TocDetector tocDetector;
    private final List<HeadingStrategy> strategies;

    public List<Section> segment(
        Path pdfPath,
        PdfDocumentLoader loader,
        int totalPages
    ) throws IOException {
        Optional<List<HeadingCandidate>> tocResult = tocDetector.findToc(
            pdfPath,
            loader
        );

        if (tocResult.isPresent()) {
            return buildSectionTree(
                tocResult.get(),
                totalPages,
                HeadingDetectionMethod.TOC,
                1.0
            );
        }

        for (HeadingStrategy strategy : strategies) {
            List<HeadingCandidate> candidates = strategy.detectHeadings(pdfPath, loader);

            if (candidates.size() >= MIN_HEADINGS) {
                log.info(
                    "event=strategy.selected component=SectionSegmenter strategy={} candidates={}",
                    strategy.strategyName(),
                    candidates.size()
                );

                double confidence = computeConfidence(
                    strategy,
                    candidates.size()
                );

                return buildSectionTree(
                    candidates,
                    totalPages,
                    strategy.strategyName(),
                    confidence
                );
            }
        }

        log.warn(
            "event=no.strategy.found component=SectionSegmenter minRequired={}",
            MIN_HEADINGS
        );

        return List.of();
    }

    private List<Section> buildSectionTree(
        List<HeadingCandidate> candidates,
        int totalPages,
        HeadingDetectionMethod method,
        double confidence
    ) {
        List<HeadingCandidate> sorted = candidates.stream()
                                                  .sorted(Comparator
                                                      .comparingInt(HeadingCandidate::getPageNumber)
                                                      .thenComparingDouble(HeadingCandidate::getStartY))
                                                  .toList();

        List<Section> roots = new ArrayList<>();
        Deque<Section> stack = new ArrayDeque<>();

        for (int i = 0; i < sorted.size(); i++) {
            HeadingCandidate headingCandidate = sorted.get(i);

            int pageEnd = computePageEnd(
                sorted,
                i,
                totalPages
            );

            Section section = Section.builder()
                                     .id(UUID.randomUUID())
                                     .title(headingCandidate.getText())
                                     .level(headingCandidate.getLevel())
                                     .pageStart(headingCandidate.getPageNumber())
                                     .pageEnd(pageEnd)
                                     .confidence(SectionConfidence.builder()
                                                                  .score(confidence)
                                                                  .method(method)
                                                                  .build())
                                     .build();

            placeInHierarchy(section, stack, roots);
        }
        return roots;
    }

    private void placeInHierarchy(
        Section section,
        Deque<Section> stack,
        List<Section> roots
    ) {
        while (!stack.isEmpty() && stack.peek().getLevel() >= section.getLevel()) {
            stack.pop();
        }

        if (stack.isEmpty()) {
            roots.add(section);
        } else {
            stack.peek().getChildren().add(section);
        }

        stack.push(section);
    }

    private int computePageEnd(
        List<HeadingCandidate> sorted,
        int index,
        int totalPages
    ) {
        for (int j = index + 1; j < sorted.size(); j++) {
            if (sorted.get(j).getLevel() <= sorted.get(index).getLevel()) {
                return Math.max(
                    sorted.get(index).getPageNumber(),
                    sorted.get(j).getPageNumber() - 1
                );
            }
        }

        return totalPages - 1;
    }

    private double computeConfidence(
        HeadingStrategy strategy,
        int candidateCount
    ) {
        if (strategy instanceof BookmarkHeadingStrategy) {
            return 0.95;
        }

        if (strategy instanceof HeadingStyleStrategy) {
            return 0.90;
        }

        if (candidateCount > 10) {
            return 0.80;
        }

        return 0.65;
    }
}
