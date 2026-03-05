package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;
import com.dsi.rfp.domain.model.Section;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionSegmenterTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private TocDetector tocDetector;
    @Mock
    private BookmarkHeadingStrategy bookmarkStrategy;
    @Mock
    private NumberedHeadingStrategy numberedStrategy;
    @Mock
    private PdfDocumentLoader loader;
    private SectionSegmenter segmenter;

    @BeforeEach
    void setUp() {
        segmenter = new SectionSegmenter(
            tocDetector,
            List.of(bookmarkStrategy, numberedStrategy)
        );
    }

    @Test
    void shouldUseTocWhenTocDetectorFindsResult() throws IOException {
        List<HeadingCandidate> tocCandidates = List.of(
            candidate("Intro", 1, 0),
            candidate("Scope", 1, 3),
            candidate("Terms", 1, 7)
        );
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.of(tocCandidates));

        List<Section> result = segmenter.segment(pdfPath, loader, 20);
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().getConfidence().getMethod()).isEqualTo(HeadingDetectionMethod.TOC);
    }

    @Test
    void shouldUseBookmarkStrategyBeforeNumbered() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("Ch 1", 1, 0),
            candidate("Ch 2", 1, 5),
            candidate("Ch 3", 1, 10)
        ));
        when(bookmarkStrategy.strategyName()).thenReturn(HeadingDetectionMethod.BOOKMARK);

        List<Section> result = segmenter.segment(pdfPath, loader, 20);
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().getConfidence().getMethod())
            .isEqualTo(HeadingDetectionMethod.BOOKMARK);
    }

    @Test
    void shouldSkipStrategyWhenFewerThanThreeHeadings() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("Ch 1", 1, 0)
        ));
        when(numberedStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("1. Scope", 1, 0),
            candidate("2. Requirements", 1, 3),
            candidate("3. Timeline", 1, 8)
        ));
        when(numberedStrategy.strategyName()).thenReturn(HeadingDetectionMethod.NUMBERED);

        List<Section> result = segmenter.segment(pdfPath, loader, 15);
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().getConfidence().getMethod())
            .isEqualTo(HeadingDetectionMethod.NUMBERED);
    }

    @Test
    void shouldReturnEmptyWhenAllStrategiesProduceFewHeadings() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of());
        when(numberedStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("Only One", 1, 0)
        ));

        List<Section> result = segmenter.segment(pdfPath, loader, 10);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldBuildChildSectionsWhenLevel2FollowsLevel1() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("Chapter 1", 1, 0),
            candidate("Section 1.1", 2, 2),
            candidate("Section 1.2", 2, 4),
            candidate("Chapter 2", 1, 6)
        ));
        when(bookmarkStrategy.strategyName()).thenReturn(HeadingDetectionMethod.BOOKMARK);

        List<Section> result = segmenter.segment(pdfPath, loader, 10);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getChildren()).hasSize(2);
    }

    @Test
    void shouldComputePageEndAsOneLessThanNextSiblingStart() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("Section A", 1, 5),
            candidate("Section B", 1, 12),
            candidate("Section C", 1, 18)
        ));
        when(bookmarkStrategy.strategyName()).thenReturn(HeadingDetectionMethod.BOOKMARK);

        List<Section> result = segmenter.segment(pdfPath, loader, 25);
        assertThat(result.get(0).getPageEnd()).isEqualTo(11);
        assertThat(result.get(1).getPageEnd()).isEqualTo(17);
        assertThat(result.get(2).getPageEnd()).isEqualTo(24);
    }

    @Test
    void shouldAssignUniqueUuidsToAllSections() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("A", 1, 0),
            candidate("B", 1, 5),
            candidate("C", 1, 10)
        ));
        when(bookmarkStrategy.strategyName()).thenReturn(HeadingDetectionMethod.BOOKMARK);

        List<Section> result = segmenter.segment(pdfPath, loader, 15);
        List<UUID> ids = result.stream().map(Section::getId).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void shouldSetConfidenceMethodToWinningStrategy() throws IOException {
        when(tocDetector.findToc(any(), any())).thenReturn(Optional.empty());
        when(bookmarkStrategy.detectHeadings(any(), any())).thenReturn(List.of());
        when(numberedStrategy.detectHeadings(any(), any())).thenReturn(List.of(
            candidate("1. A", 1, 0),
            candidate("2. B", 1, 3),
            candidate("3. C", 1, 6)
        ));
        when(numberedStrategy.strategyName()).thenReturn(HeadingDetectionMethod.NUMBERED);

        List<Section> result = segmenter.segment(pdfPath, loader, 10);
        assertThat(result).allSatisfy(s ->
            assertThat(s.getConfidence().getMethod())
                .isEqualTo(HeadingDetectionMethod.NUMBERED)
        );
    }

    private HeadingCandidate candidate(String text, int level, int page) {
        return HeadingCandidate.builder()
                               .text(text).level(level).pageNumber(page)
                               .startY(0).detectedBy(HeadingDetectionMethod.TOC).build();
    }
}
