package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.ocr.*;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import com.dsi.rfp.domain.model.ReadingOrderMethod;
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
    private OcrSidecarClient ocrClient;

    @Mock
    private PageImageRenderer pageImageRenderer;

    @Mock
    private MixedPageMergeService mergeService;

    @Mock
    private TextLayerQualityPolicy qualityPolicy;

    @InjectMocks
    private MixedPageExtractor extractor;

    @Test
    void shouldDelegateToCollaborators() throws IOException {
        List<TextBlock> blocks = List.of(textBlock("Hello"));
        OcrPageWithLayoutResultDto sidecarResult = new OcrPageWithLayoutResultDto(
            new OcrResultDto(
                "table text",
                List.of(),
                0.8,
                2,
                "easyocr"
            ),
            new LayoutDetectionDto(
                true,
                List.of(),
                List.of()
            ),
            new ReadingOrderDto(
                "ordered text",
                ReadingOrderMethod.PDFPLUMBER_LAYOUT
            ),
            List.of()
        );
        MixedPageContent merged = MixedPageContent.textPlusOcr(
            "merged text",
            0.7
        );

        when(loader.loadPageBoundingBoxes(any(Path.class), eq(1)))
            .thenReturn(blocks);
        when(qualityPolicy.score(blocks))
            .thenReturn(0.9);
        when(pageImageRenderer.renderPage("/tmp/sample.pdf", 1))
            .thenReturn("png".getBytes());
        when(ocrClient.extractPageWithLayout(any(), eq("/tmp/sample.pdf"), eq(1)))
            .thenReturn(sidecarResult);
        when(mergeService.merge(any()))
            .thenReturn(merged);

        MixedPageContent result = extractor.extractPage("/tmp/sample.pdf", 1);

        assertThat(result.text()).isEqualTo("merged text");
        verify(pageImageRenderer).renderPage("/tmp/sample.pdf", 1);
        verify(ocrClient).extractPageWithLayout(any(), eq("/tmp/sample.pdf"), eq(1));
        verify(mergeService).merge(any());
    }

    @Test
    void shouldPropagateIOExceptionFromRenderer() throws IOException {
        when(loader.loadPageBoundingBoxes(any(Path.class), eq(1)))
            .thenReturn(List.of(textBlock("Hello")));
        when(qualityPolicy.score(any()))
            .thenReturn(0.9);
        when(pageImageRenderer.renderPage("/tmp/sample.pdf", 1))
            .thenThrow(new IOException("render failed"));

        assertThatThrownBy(() -> extractor.extractPage("/tmp/sample.pdf", 1))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldUseEnumMethodForTextPlusOcr() {
        MixedPageContent content = MixedPageContent.textPlusOcr("text", 0.6);

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
