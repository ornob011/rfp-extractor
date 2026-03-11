package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import com.dsi.rfp.domain.model.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MixedPageExtractorTest {

    @Mock
    private PdfDocumentLoader loader;

    @Mock
    private VisionExtractionAdapter visionAdapter;

    @Mock
    private TextLayerQualityPolicy qualityPolicy;

    @InjectMocks
    private MixedPageExtractor extractor;

    @Test
    void shouldMergeTextLayerAndVlmResult() throws IOException {
        List<TextBlock> blocks = List.of(textBlock("Hello"));
        VisionPageResult vlmResult = new VisionPageResult(
            "merged text from VLM",
            0.85,
            List.of(),
            false
        );

        when(loader.loadPageBoundingBoxes(any(Path.class), eq(1)))
            .thenReturn(blocks);
        when(qualityPolicy.score(blocks))
            .thenReturn(0.9);
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenReturn(vlmResult);

        MixedPageContent result = extractor.extractPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result.text()).isEqualTo("merged text from VLM");
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.method()).isEqualTo(PageExtractionMethod.TEXT_PLUS_OCR);
        assertThat(result.tables()).isEmpty();
        verify(visionAdapter).extractFullPage("/tmp/sample.pdf", 1);
    }

    @Test
    void shouldPassThroughVlmTables() throws IOException {
        VisionTableResult table = new VisionTableResult(
            "Budget",
            List.of("Item", "Cost"),
            List.of(List.of("Server", "50000")),
            0.80
        );

        List<TextBlock> blocks = List.of(textBlock("Hello"));

        when(loader.loadPageBoundingBoxes(any(Path.class), eq(1)))
            .thenReturn(blocks);
        when(qualityPolicy.score(blocks))
            .thenReturn(0.9);
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageResult(
                "text with table",
                0.85,
                List.of(table),
                true
            ));

        MixedPageContent result = extractor.extractPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables().getFirst().headers())
            .containsExactly("Item", "Cost");
    }

    @Test
    void shouldUseMinConfidence() throws IOException {
        List<TextBlock> blocks = List.of(textBlock("Hello"));

        when(loader.loadPageBoundingBoxes(any(Path.class), eq(1)))
            .thenReturn(blocks);
        when(qualityPolicy.score(blocks))
            .thenReturn(0.5);
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageResult("text", 0.85, List.of(), false));

        MixedPageContent result = extractor.extractPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result.confidence()).isEqualTo(0.5);
    }

    @Test
    void shouldPropagateIOExceptionFromVlm() throws IOException {
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(1)))
            .thenReturn(List.of(textBlock("Hello")));
        when(qualityPolicy.score(any()))
            .thenReturn(0.9);
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenThrow(new IOException("VLM failed"));

        assertThatThrownBy(() -> extractor.extractPage("/tmp/sample.pdf", 1))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldUseEnumMethodForTextPlusOcr() {
        MixedPageContent content = MixedPageContent.textPlusOcr(
            "text",
            0.6,
            List.of()
        );

        assertThat(content.confidence()).isEqualTo(0.6);
        assertThat(content.method()).isEqualTo(PageExtractionMethod.TEXT_PLUS_OCR);
    }

    private TextBlock textBlock(String text) {
        return TextBlock.builder()
                        .x(10.0f)
                        .y(10.0f)
                        .width(100.0f)
                        .height(12.0f)
                        .text(text)
                        .fontSize(12.0f)
                        .fontName("Arial")
                        .pageNumber(1)
                        .build();
    }
}
