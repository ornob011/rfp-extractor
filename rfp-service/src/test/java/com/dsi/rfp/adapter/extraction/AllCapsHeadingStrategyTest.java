package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
import com.dsi.rfp.adapter.extraction.parser.UnicodeClassifier;
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
class AllCapsHeadingStrategyTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private PdfDocumentLoader loader;
    private AllCapsHeadingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AllCapsHeadingStrategy(
            new LineTokenizer(),
            new UnicodeClassifier()
        );
    }

    @Test
    void shouldDetectStandaloneAllCapsLine() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\nTERMS AND CONDITIONS\n\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getText()).isEqualTo("TERMS AND CONDITIONS");
    }

    @Test
    void shouldNotDetectAllCapsWithoutSurroundingBlanks() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("Some text before\nTERMS AND CONDITIONS\nSome text after\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotDetectLineLongerThanEightyChars() throws IOException {
        String longCaps = "A".repeat(81);
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\n" + longCaps + "\n\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotDetectLineShorterThanFiveChars() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("\nABC\n\n");

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }
}
