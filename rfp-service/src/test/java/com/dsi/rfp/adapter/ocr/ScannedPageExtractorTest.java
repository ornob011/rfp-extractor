package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.domain.model.PageExtractionMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScannedPageExtractorTest {

    @Mock
    private OcrSidecarClient ocrClient;

    @Mock
    private com.dsi.rfp.adapter.extraction.PageImageRenderer pageImageRenderer;

    @Spy
    private OcrExtractionConfig config = new OcrExtractionConfig();

    @InjectMocks
    private ScannedPageExtractor extractor;

    @Test
    void shouldThrowWhenRenderingFails() throws IOException {
        when(pageImageRenderer.renderPage("/nonexistent/path.pdf", 1))
            .thenThrow(new IOException("render failed"));

        assertThatThrownBy(() -> extractor.extractPage("/nonexistent/path.pdf", 1))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldExposeOcrExtractionMethodContract() {
        ScannedPageExtractionResult result = new ScannedPageExtractionResult(
            5,
            "text",
            0.4,
            2,
            PageExtractionMethod.OCR
        );

        assertThat(result.pageNum()).isEqualTo(5);
        assertThat(result.extractionMethod()).isEqualTo(PageExtractionMethod.OCR);
    }
}
