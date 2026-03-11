package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.MixedPageExtractor;
import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.adapter.table.ScannedTableResultMapper;
import com.dsi.rfp.adapter.vision.VisionPageExtractionResult;
import com.dsi.rfp.adapter.vision.VisionPageExtractor;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractTextNodeTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Mock
    private PdfDocumentLoader pdfDocumentLoader;

    @Mock
    private VisionPageExtractor visionPageExtractor;

    @Mock
    private MixedPageExtractor mixedExtractor;

    @Mock
    private ScannedTableResultMapper tableResultMapper;

    private ExtractTextNode node;

    @BeforeEach
    void setUp() {
        node = new ExtractTextNode(
            pdfDocumentLoader,
            visionPageExtractor,
            mixedExtractor,
            tableResultMapper,
            DIRECT_EXECUTOR
        );
    }

    @Test
    void shouldExtractDigitalPageUsingTextLayer() throws Exception {
        ExtractionState state = buildState(PageClassification.DIGITAL);

        when(pdfDocumentLoader.loadPageText(
            any(Path.class),
            eq(1)
        )).thenReturn("page text");

        Map<String, Object> result = node.apply(state);

        assertThat(result.get(ExtractionState.Key.PAGE_TEXTS.value()))
            .isEqualTo(Map.of("1", "page text"));
        assertThat(result.get(ExtractionState.Key.PAGE_CONFIDENCES.value()))
            .isEqualTo(Map.of("1", 1.0));
        assertThat(result.get(ExtractionState.Key.PAGE_EXTRACTION_METHODS.value()))
            .isEqualTo(Map.of("1", PageExtractionMethod.TEXT_LAYER));
    }

    @Test
    void shouldStoreEmptyVlmTablesForDigitalPages() throws Exception {
        ExtractionState state = buildState(PageClassification.DIGITAL);

        when(pdfDocumentLoader.loadPageText(
            any(Path.class),
            eq(1)
        )).thenReturn("page text");

        Map<String, Object> result = node.apply(state);

        Map<String, List<TableExtractionResult>> vlmTables = vlmTables(result);

        assertThat(vlmTables.get("1")).isEmpty();
    }

    @Test
    void shouldCacheVlmTablesFromScannedPages() throws Exception {
        ExtractionState state = buildState(PageClassification.SCANNED);

        VisionTableResult vlmTable = new VisionTableResult(
            "Budget",
            List.of("Item", "Cost"),
            List.of(List.of("Server", "50000")),
            0.80
        );

        when(visionPageExtractor.extractPage("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageExtractionResult(
                1,
                "scanned text",
                0.85,
                2,
                PageExtractionMethod.VLM,
                List.of(vlmTable)
            ));

        TableExtractionResult mappedTable = TableExtractionResult.builder()
                                                                 .pageStart(1)
                                                                 .pageEnd(1)
                                                                 .provenance(TableProvenance.SCANNED)
                                                                 .type(TableType.OTHER)
                                                                 .headers(List.of("Item", "Cost"))
                                                                 .confidence(ExtractionConfidence.builder()
                                                                                                 .score(0.7)
                                                                                                 .method("vlm")
                                                                                                 .build())
                                                                 .build();

        when(tableResultMapper.fromLlm(any(), eq(1), eq(0.80)))
            .thenReturn(mappedTable);

        Map<String, Object> result = node.apply(state);

        Map<String, List<TableExtractionResult>> vlmTables = vlmTables(result);

        assertThat(vlmTables.get("1")).hasSize(1);
        assertThat(vlmTables.get("1").getFirst().getHeaders())
            .containsExactly("Item", "Cost");
    }

    @Test
    void shouldFilterVlmTablesWithEmptyHeaders() throws Exception {
        ExtractionState state = buildState(PageClassification.SCANNED);

        VisionTableResult emptyHeaderTable = new VisionTableResult(
            "",
            List.of(),
            List.of(),
            0.5
        );

        when(visionPageExtractor.extractPage("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageExtractionResult(
                1,
                "scanned text",
                0.85,
                2,
                PageExtractionMethod.VLM,
                List.of(emptyHeaderTable)
            ));

        Map<String, Object> result = node.apply(state);

        Map<String, List<TableExtractionResult>> vlmTables = vlmTables(result);

        assertThat(vlmTables.get("1")).isEmpty();
    }

    @Test
    void shouldStorePageKeyedStateUsingStringKeys() throws Exception {
        ExtractionState state = buildState(PageClassification.DIGITAL);

        when(pdfDocumentLoader.loadPageText(
            any(Path.class),
            eq(1)
        )).thenReturn("page text");

        Map<String, Object> result = node.apply(state);

        assertThat(result.get(ExtractionState.Key.PAGE_TEXTS.value()))
            .isEqualTo(Map.of("1", "page text"));
    }

    @Test
    void shouldExposePageKeyedStateAsIntegerMaps() {
        ExtractionState state = new ExtractionState(Map.of(
            ExtractionState.Key.PAGE_TEXTS.value(),
            Map.of("1", "page text"),
            ExtractionState.Key.PAGE_CONFIDENCES.value(),
            Map.of("1", 0.9),
            ExtractionState.Key.PAGE_EXTRACTION_METHODS.value(),
            Map.of("1", PageExtractionMethod.VLM)
        ));

        assertThat(state.pageTexts()).isEqualTo(Map.of(1, "page text"));
        assertThat(state.pageConfidences()).isEqualTo(Map.of(1, 0.9));
        assertThat(state.pageExtractionMethods()).isEqualTo(
            Map.of(1, PageExtractionMethod.VLM)
        );
    }

    private ExtractionState buildState(PageClassification classification) {
        Map<String, Object> data = ExtractionState.initial(
            42L,
            "/tmp/sample.pdf"
        );
        data.put(
            ExtractionState.Key.PAGE_CLASSIFICATIONS.value(),
            List.of(
                PageSummary.builder()
                           .pageNumber(1)
                           .classification(classification)
                           .build()
            )
        );

        return new ExtractionState(data);
    }

    private Map<String, List<TableExtractionResult>> vlmTables(
        Map<String, Object> result
    ) {
        Object raw = result.get(ExtractionState.Key.VLM_TABLES.value());

        if (raw instanceof Map<?, ?> map) {
            return map.entrySet()
                      .stream()
                      .collect(java.util.stream.Collectors.toMap(
                          entry -> entry.getKey().toString(),
                          entry -> castTableList(entry.getValue())
                      ));
        }

        throw new AssertionError("VLM tables state must be a map");
    }

    private List<TableExtractionResult> castTableList(
        Object raw
    ) {
        if (raw instanceof List<?> list) {
            return list.stream()
                       .map(TableExtractionResult.class::cast)
                       .toList();
        }

        throw new AssertionError("VLM tables entry must be a list");
    }
}
