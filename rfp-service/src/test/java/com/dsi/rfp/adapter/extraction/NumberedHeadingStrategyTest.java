package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.*;
import com.dsi.rfp.domain.model.HeadingCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NumberedHeadingStrategyTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private PdfDocumentLoader loader;
    private NumberedHeadingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NumberedHeadingStrategy(
            new LineTokenizer(),
            new HeadingNumberParser(new SectionNumberGrammarValidator()),
            new UnicodeClassifier(),
            new NumberedKeywordGrammarParser(),
            new NumberedHeadingLexicon()
        );
    }

    @Test
    void shouldDetectLevelOneForSingleDotPrefix() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("1. Scope of Work\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldDetectLevelTwoForTwoDotPrefix() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("2.1 Technical Requirements\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(2);
    }

    @Test
    void shouldDetectLevelThreeForThreeDotPrefix() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("2.3.1 Specific Requirements\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(3);
    }

    @Test
    void shouldDetectSectionKeywordAsLevelOne() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("SECTION II ELIGIBILITY CRITERIA\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldDetectPartKeywordAsLevelOne() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("PART III Financial Proposal\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldDetectChapterAsLevelOne() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("Chapter 1 Introduction\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreBlankLines() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("   \n\n  \n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldIgnoreMidSentenceNumbers() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("The value is approximately 1. million dollars\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDetectBanglaStartingAfterNumber() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("1. \u0985\u09A7\u09CD\u09AF\u09BE\u09AF\u09BC \u098F\u0995\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
    }
}
