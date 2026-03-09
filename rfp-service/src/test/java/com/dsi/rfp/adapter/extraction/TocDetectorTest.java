package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
import com.dsi.rfp.adapter.extraction.parser.TocEntryParser;
import com.dsi.rfp.adapter.extraction.parser.TocLineGrammarParser;
import com.dsi.rfp.domain.model.HeadingCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TocDetectorTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private PdfDocumentLoader loader;
    private TocDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TocDetector(
            new LineTokenizer(),
            new TocEntryParser(new TocLineGrammarParser())
        );
    }

    @Test
    void shouldReturnEmptyWhenNoTocPage() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0)))
            .thenReturn("Just regular text\nNothing special\n");

        Optional<List<HeadingCandidate>> result = detector.findToc(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnPresentWhenDottedLeaderTocDetected() throws IOException {
        StringBuilder tocPage = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            tocPage.append(String.format("Section %d ......... %d\n", i, i * 5));
        }
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0))).thenReturn(tocPage.toString());

        Optional<List<HeadingCandidate>> result = detector.findToc(pdfPath, loader);
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(10);
    }

    @Test
    void shouldReturnPresentWhenSpacedTocDetected() throws IOException {
        StringBuilder tocPage = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            tocPage.append(String.format("Section %d        %d\n", i, i * 5));
        }
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0))).thenReturn(tocPage.toString());

        Optional<List<HeadingCandidate>> result = detector.findToc(pdfPath, loader);
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(10);
    }

    @Test
    void shouldComputeLevelOneForUnindentedEntry() throws IOException {
        StringBuilder tocPage = new StringBuilder();
        for (int i = 1; i <= 9; i++) {
            tocPage.append(String.format("Entry %d ......... %d\n", i, i));
        }
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0))).thenReturn(tocPage.toString());

        Optional<List<HeadingCandidate>> result = detector.findToc(pdfPath, loader);
        assertThat(result).isPresent();
        assertThat(result.get().getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldComputeLevelTwoForIndentedEntry() throws IOException {
        StringBuilder tocPage = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            tocPage.append(String.format("Entry %d ......... %d\n", i, i));
        }
        tocPage.append("  Sub Entry ......... 15\n");
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0))).thenReturn(tocPage.toString());

        Optional<List<HeadingCandidate>> result = detector.findToc(pdfPath, loader);
        assertThat(result).isPresent();
        List<HeadingCandidate> entries = result.get();
        HeadingCandidate indented = entries.get(entries.size() - 1);
        assertThat(indented.getLevel()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyWhenFewerThanEightTocLines() throws IOException {
        StringBuilder tocPage = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            tocPage.append(String.format("Section %d ......... %d\n", i, i));
        }
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageText(any(), eq(0))).thenReturn(tocPage.toString());

        Optional<List<HeadingCandidate>> result = detector.findToc(pdfPath, loader);
        assertThat(result).isEmpty();
    }
}
