package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.ocr.*;
import com.dsi.rfp.domain.model.ReadingOrderMethod;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.model.TableProvenance;
import com.dsi.rfp.domain.model.TableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScannedTableReconstructorTest {

    @Mock
    private LlmAdapter llmAdapter;

    @Mock
    private OcrSidecarClient ocrSidecarClient;

    @Mock
    private PageImageRenderer pageImageRenderer;

    @Mock
    private TableTypeClassifier typeClassifier;

    @Spy
    private TableExtractionConfig config = new TableExtractionConfig();

    private ScannedTableReconstructor reconstructor;

    @BeforeEach
    void setUp() {
        reconstructor = new ScannedTableReconstructor(
            llmAdapter,
            ocrSidecarClient,
            pageImageRenderer,
            new ScannedTableResultMapper(typeClassifier, config),
            config
        );
    }

    @Test
    void shouldUseSidecarTablesBeforeLlm() throws IOException {
        when(typeClassifier.classify(anyList(), anyString()))
            .thenReturn(TableType.OTHER);
        when(pageImageRenderer.renderPage("/tmp/sample.pdf", 1))
            .thenReturn("png".getBytes());
        when(ocrSidecarClient.extractPageWithLayout(any(), anyString(), anyInt()))
            .thenReturn(new OcrPageWithLayoutResultDto(
                new OcrResultDto("ocr", List.of(), 0.8, 1, "easyocr"),
                new LayoutDetectionDto(false, List.of(), List.of()),
                new ReadingOrderDto("ordered", ReadingOrderMethod.OCR_TEXT_FLOW),
                List.of(
                    new OcrScannedTableDto(
                        List.of("Name", "Value"),
                        List.of(List.of("A", "1")),
                        0.7,
                        "lattice"
                    )
                )
            ));

        List<TableExtractionResult> result = reconstructor.reconstructTables(
            "/tmp/sample.pdf",
            "OCR text",
            1,
            0.8
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getProvenance()).isEqualTo(TableProvenance.SCANNED);
        verify(llmAdapter, never()).extractStructured(anyString(), anyString(), any());
    }

    @Test
    void shouldFallbackToLlmWhenSidecarReturnsNoTables() throws IOException {
        when(typeClassifier.classify(anyList(), anyString()))
            .thenReturn(TableType.OTHER);
        when(pageImageRenderer.renderPage("/tmp/sample.pdf", 3))
            .thenReturn("png".getBytes());
        when(ocrSidecarClient.extractPageWithLayout(any(), anyString(), anyInt()))
            .thenReturn(new OcrPageWithLayoutResultDto(
                new OcrResultDto("ocr", List.of(), 0.8, 1, "easyocr"),
                new LayoutDetectionDto(false, List.of(), List.of()),
                new ReadingOrderDto("ordered", ReadingOrderMethod.OCR_TEXT_FLOW),
                List.of()
            ));
        doReturn(Optional.of(
            new ScannedTableResponse(
                List.of("Name", "Value"),
                List.of(
                    List.of("Item1", "100"),
                    List.of("Item2", "200")
                )
            )
        )).when(llmAdapter).extractStructured(
            anyString(),
            anyString(),
            any()
        );

        List<TableExtractionResult> result = reconstructor.reconstructTables(
            "/tmp/sample.pdf",
            "OCR table text",
            3,
            0.75
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getHeaders())
            .containsExactly("Name", "Value");
        assertThat(result.getFirst().getConfidence().getScore())
            .isEqualTo(0.75 * config.scannedConfidenceFactor());
        assertThat(result.getFirst().getConfidence().getMethod())
            .isEqualTo(config.scannedLlmMethod());
    }
}
