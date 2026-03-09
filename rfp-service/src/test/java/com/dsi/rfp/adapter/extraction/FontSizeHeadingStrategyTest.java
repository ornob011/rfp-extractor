package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.FontSizeHeadingLexicon;
import com.dsi.rfp.adapter.extraction.parser.FontSizeLevelResolver;
import com.dsi.rfp.adapter.extraction.parser.FontSizeStatistics;
import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.TextBlock;
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
class FontSizeHeadingStrategyTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private PdfDocumentLoader loader;
    private FontSizeHeadingStrategy strategy;

    @BeforeEach
    void setUp() {
        FontSizeHeadingLexicon lexicon = new FontSizeHeadingLexicon();
        strategy = new FontSizeHeadingStrategy(
            new FontSizeStatistics(),
            new FontSizeLevelResolver(lexicon),
            lexicon
        );
    }

    @Test
    void shouldDetectLargerFontAsHeading() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(0))).thenReturn(List.of(
            block("Title Text", 18f, 0),
            block("Body text one", 10f, 0),
            block("Body text two", 10f, 0),
            block("Body text three", 10f, 0)
        ));

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getText()).isEqualTo("Title Text");
    }

    @Test
    void shouldNotDetectLongTextAsHeading() throws IOException {
        String longText = "A".repeat(101);
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(0))).thenReturn(List.of(
            block(longText, 18f, 0),
            block("Body text", 10f, 0),
            block("Body text two", 10f, 0)
        ));

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUniformFontSize() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(0))).thenReturn(List.of(
            block("Line one", 10f, 0),
            block("Line two", 10f, 0),
            block("Line three", 10f, 0)
        ));

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyDocument() throws IOException {
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(0))).thenReturn(List.of());

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }

    private TextBlock block(String text, float fontSize, int page) {
        return TextBlock.builder()
                        .text(text).fontSize(fontSize).fontName("Arial")
                        .x(0).y(0).width(100).height(fontSize)
                        .pageNumber(page)
                        .build();
    }
}
