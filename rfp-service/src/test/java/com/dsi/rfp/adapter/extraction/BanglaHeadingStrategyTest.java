package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.BanglaHeadingGrammarParser;
import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
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
class BanglaHeadingStrategyTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private PdfDocumentLoader loader;
    private BanglaHeadingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BanglaHeadingStrategy(
            new LineTokenizer(),
            new BanglaHeadingGrammarParser()
        );
    }

    @Test
    void shouldDetectAdhyayAsLevelOne() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\u0985\u09A7\u09CD\u09AF\u09BE\u09AF\u09BC 1 \u09AA\u09B0\u09BF\u099A\u09BF\u09A4\u09BF\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldDetectDharaAsLevelTwo() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\u09A7\u09BE\u09B0\u09BE 3 \u09AA\u09CD\u09B0\u09AF\u09CB\u099C\u09CD\u09AF\u09A4\u09BE\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(2);
    }

    @Test
    void shouldDetectAnuchchhedAsLevelThree() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\u0985\u09A8\u09C1\u099A\u09CD\u099B\u09C7\u09A6 5 \u09AC\u09BF\u09AC\u09B0\u09A3\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(3);
    }

    @Test
    void shouldNotDetectEnglishTextAsBanglaHeading() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("Chapter 1 Introduction\nSection 2 Scope\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleBanglaNumerals() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\u09A7\u09BE\u09B0\u09BE \u09E9 \u09AA\u09CD\u09B0\u09AF\u09CB\u099C\u09CD\u09AF\u09A4\u09BE\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(2);
    }

    @Test
    void shouldHandleMixedBanglaAndAsciiNumerals() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\u0985\u09A8\u09C1\u099A\u09CD\u099B\u09C7\u09A6 2 description\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
    }
}
