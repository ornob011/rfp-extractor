package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.adapter.vision.VisionTableResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScannedTableReconstructorTest {

    @Mock
    private VisionExtractionAdapter visionAdapter;

    @Mock
    private TableTypeClassifier typeClassifier;

    @Spy
    private TableExtractionConfig config = new TableExtractionConfig();

    private ScannedTableReconstructor reconstructor;

    @BeforeEach
    void setUp() {
        reconstructor = new ScannedTableReconstructor(
            visionAdapter,
            new ScannedTableResultMapper(typeClassifier, config)
        );
    }

    @Test
    void shouldExtractTablesFromVlm() throws IOException {
        when(typeClassifier.classify(anyList(), anyString()))
            .thenReturn(TableType.OTHER);
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageResult(
                "page text",
                0.85,
                List.of(
                    new VisionTableResult(
                        "",
                        List.of("Name", "Value"),
                        List.of(List.of("A", "1")),
                        0.80
                    )
                ),
                true
            ));

        List<TableExtractionResult> result = reconstructor.reconstructTables(
            "/tmp/sample.pdf",
            "OCR text",
            1,
            0.8
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getProvenance())
            .isEqualTo(TableProvenance.SCANNED);
        assertThat(result.getFirst().getHeaders())
            .containsExactly("Name", "Value");
    }

    @Test
    void shouldReturnEmptyWhenVlmFindsNoTables() throws IOException {
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 3))
            .thenReturn(new VisionPageResult(
                "page text",
                0.85,
                List.of(),
                false
            ));

        List<TableExtractionResult> result = reconstructor.reconstructTables(
            "/tmp/sample.pdf",
            "OCR table text",
            3,
            0.75
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFilterTablesWithEmptyHeaders() throws IOException {
        when(visionAdapter.extractFullPage("/tmp/sample.pdf", 2))
            .thenReturn(new VisionPageResult(
                "page text",
                0.85,
                List.of(
                    new VisionTableResult("", List.of(), List.of(), 0.5)
                ),
                true
            ));

        List<TableExtractionResult> result = reconstructor.reconstructTables(
            "/tmp/sample.pdf",
            "OCR text",
            2,
            0.7
        );

        assertThat(result).isEmpty();
    }
}
