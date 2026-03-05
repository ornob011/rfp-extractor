package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.FontHeadingGrammarParser;
import com.dsi.rfp.adapter.extraction.parser.FontHeadingLexicon;
import com.dsi.rfp.adapter.extraction.parser.FontNameHeadingParser;
import com.dsi.rfp.adapter.extraction.parser.LineTokenizer;
import com.dsi.rfp.domain.model.FontInfo;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeadingStyleStrategyTest {

    private final Path pdfPath = Path.of("test.pdf");
    @Mock
    private PdfDocumentLoader loader;
    private HeadingStyleStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new HeadingStyleStrategy(
            new FontNameHeadingParser(
                new LineTokenizer(),
                new FontHeadingLexicon(),
                new FontHeadingGrammarParser()
            )
        );
    }

    @Test
    void shouldDetectHeading1FontAsLevelOne() throws IOException {
        Map<String, FontInfo> fonts = Map.of(
            "Heading1", FontInfo.builder()
                                .fontName("Heading1").minFontSize(14).maxFontSize(14)
                                .averageFontSize(14).occurrenceCount(1).build()
        );
        when(loader.loadFontMetadata(any())).thenReturn(fonts);
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(0))).thenReturn(List.of(
            TextBlock.builder().text("Title").fontName("Heading1")
                     .fontSize(14).x(0).y(0).width(100).height(14).pageNumber(0).build()
        ));

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldDetectHeading2FontAsLevelTwo() throws IOException {
        Map<String, FontInfo> fonts = Map.of(
            "Heading2", FontInfo.builder()
                                .fontName("Heading2").minFontSize(12).maxFontSize(12)
                                .averageFontSize(12).occurrenceCount(1).build()
        );
        when(loader.loadFontMetadata(any())).thenReturn(fonts);
        when(loader.getPageCount(any())).thenReturn(1);
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(0))).thenReturn(List.of(
            TextBlock.builder().text("Subtitle").fontName("Heading2")
                     .fontSize(12).x(0).y(0).width(100).height(12).pageNumber(0).build()
        ));

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLevel()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyWhenNoHeadingFonts() throws IOException {
        Map<String, FontInfo> fonts = Map.of(
            "Arial", FontInfo.builder()
                             .fontName("Arial").minFontSize(10).maxFontSize(10)
                             .averageFontSize(10).occurrenceCount(10).build()
        );
        when(loader.loadFontMetadata(any())).thenReturn(fonts);

        List<HeadingCandidate> result = strategy.detectHeadings(pdfPath, loader);
        assertThat(result).isEmpty();
    }
}
