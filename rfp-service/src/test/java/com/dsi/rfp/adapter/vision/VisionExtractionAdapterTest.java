package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionExtractionAdapterTest {

    @Mock
    private PageImageRenderer pageImageRenderer;

    @Mock
    private LlmAdapter llmAdapter;

    @Spy
    private PromptTemplateRenderer promptTemplateRenderer = new PromptTemplateRenderer();

    @Spy
    private VisionExtractionConfig config = new VisionExtractionConfig();

    private VisionExtractionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new VisionExtractionAdapter(
            pageImageRenderer,
            llmAdapter,
            promptTemplateRenderer,
            config
        );
    }

    @Test
    void shouldExtractFullPage() throws IOException {
        VisionPageResult expected = new VisionPageResult(
            "extracted text",
            0.85,
            List.of(),
            false
        );

        when(pageImageRenderer.renderPageJpeg(
            "/tmp/sample.pdf",
            1,
            config.fullPageDpi(),
            config.jpegQuality()
        )).thenReturn("jpeg".getBytes());

        when(llmAdapter.extractStructuredWithImage(
            any(),
            anyString(),
            any(byte[].class),
            any(MimeType.class),
            eq(VisionPageResult.class)
        )).thenReturn(Optional.of(expected));

        VisionPageResult result = adapter.extractFullPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result.text()).isEqualTo("extracted text");
        assertThat(result.confidence()).isEqualTo(0.85);
    }

    @Test
    void shouldExtractTablesOnly() throws IOException {
        VisionPageResult expected = new VisionPageResult(
            "",
            0.80,
            List.of(
                new VisionTableResult(
                    "Budget",
                    List.of("Item", "Cost"),
                    List.of(List.of("Server", "50000")),
                    0.80
                )
            ),
            true
        );

        when(pageImageRenderer.renderPageJpeg(
            "/tmp/sample.pdf",
            2,
            config.tableOnlyDpi(),
            config.jpegQuality()
        )).thenReturn("jpeg".getBytes());

        when(llmAdapter.extractStructuredWithImage(
            any(),
            anyString(),
            any(byte[].class),
            any(MimeType.class),
            eq(VisionPageResult.class)
        )).thenReturn(Optional.of(expected));

        VisionPageResult result = adapter.extractTablesOnly(
            "/tmp/sample.pdf",
            2
        );

        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables().getFirst().headers())
            .containsExactly("Item", "Cost");
    }

    @Test
    void shouldThrowWhenVlmReturnsEmpty() throws IOException {
        when(pageImageRenderer.renderPageJpeg(
            "/tmp/sample.pdf",
            1,
            config.fullPageDpi(),
            config.jpegQuality()
        )).thenReturn("jpeg".getBytes());

        when(llmAdapter.extractStructuredWithImage(
            any(),
            anyString(),
            any(byte[].class),
            any(MimeType.class),
            eq(VisionPageResult.class)
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.extractFullPage("/tmp/sample.pdf", 1))
            .isInstanceOf(LlmUnavailableException.class);
    }
}
