package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScannedPageExtractorTest {

    @Mock
    private VisionExtractionAdapter visionAdapter;

    @InjectMocks
    private ScannedPageExtractor extractor;

    @Test
    void shouldExtractPageUsingVlm() throws IOException {
        VisionPageResult vlmResult = new VisionPageResult(
            "extracted text from scanned page",
            0.85,
            List.of(),
            false
        );

        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenReturn(vlmResult);

        ScannedPageExtractionResult result = extractor.extractPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result.text()).isEqualTo("extracted text from scanned page");
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.extractionMethod()).isEqualTo(PageExtractionMethod.VLM);
        assertThat(result.wordCount()).isEqualTo(5);
        assertThat(result.tables()).isEmpty();
    }

    @Test
    void shouldPassThroughVlmTables() throws IOException {
        VisionTableResult table = new VisionTableResult(
            "Budget",
            List.of("Item", "Cost"),
            List.of(List.of("Server", "50000")),
            0.80
        );

        VisionPageResult vlmResult = new VisionPageResult(
            "page with table",
            0.85,
            List.of(table),
            true
        );

        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 3))
            .thenReturn(vlmResult);

        ScannedPageExtractionResult result = extractor.extractPage(
            "/tmp/sample.pdf",
            3
        );

        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables().getFirst().headers())
            .containsExactly("Item", "Cost");
    }

    @Test
    void shouldThrowWhenVlmFails() throws IOException {
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenThrow(new IOException("VLM extraction failed"));

        assertThatThrownBy(() -> extractor.extractPage("/tmp/sample.pdf", 1))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldExposeVlmExtractionMethodContract() {
        ScannedPageExtractionResult result = new ScannedPageExtractionResult(
            5,
            "text",
            0.4,
            2,
            PageExtractionMethod.VLM,
            List.of()
        );

        assertThat(result.pageNum()).isEqualTo(5);
        assertThat(result.extractionMethod()).isEqualTo(PageExtractionMethod.VLM);
    }
}
