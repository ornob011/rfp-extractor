package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.MixedPageExtractor;
import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.ocr.OcrPageWithLayoutResultDto;
import com.dsi.rfp.adapter.ocr.OcrResultDto;
import com.dsi.rfp.adapter.ocr.OcrSidecarClient;
import com.dsi.rfp.adapter.ocr.ReadingOrderDto;
import com.dsi.rfp.adapter.ocr.ScannedPageExtractor;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.ReadingOrderMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractTextNodeTest {

    @Mock
    private ScannedPageExtractor scannedExtractor;

    @Mock
    private MixedPageExtractor mixedExtractor;

    @Mock
    private OcrSidecarClient ocrClient;

    @Mock
    private PageImageRenderer pageImageRenderer;

    @InjectMocks
    private ExtractTextNode node;

    @Test
    void shouldStorePageKeyedStateUsingStringKeys() throws Exception {
        ExtractionState state = buildState();

        when(pageImageRenderer.renderPage(anyString(), anyInt()))
            .thenReturn(new byte[] {1});
        when(ocrClient.extractPageWithLayout(any(), anyString(), anyInt()))
            .thenReturn(new OcrPageWithLayoutResultDto(
                new OcrResultDto(
                    "page text",
                    List.of(),
                    1.0,
                    1,
                    "text_layer"
                ),
                null,
                new ReadingOrderDto(
                    "page text",
                    ReadingOrderMethod.PDFPLUMBER_LAYOUT
                ),
                List.of()
            ));

        Map<String, Object> result = node.apply(state);

        assertThat(result.get(ExtractionState.Key.PAGE_TEXTS.value()))
            .isEqualTo(Map.of("1", "page text"));
        assertThat(result.get(ExtractionState.Key.PAGE_CONFIDENCES.value()))
            .isEqualTo(Map.of("1", 1.0));
        assertThat(result.get(ExtractionState.Key.PAGE_EXTRACTION_METHODS.value()))
            .isEqualTo(Map.of("1", PageExtractionMethod.TEXT_LAYER));
    }

    @Test
    void shouldExposePageKeyedStateAsIntegerMaps() {
        ExtractionState state = new ExtractionState(Map.of(
            ExtractionState.Key.PAGE_TEXTS.value(),
            Map.of("1", "page text"),
            ExtractionState.Key.PAGE_CONFIDENCES.value(),
            Map.of("1", 0.9),
            ExtractionState.Key.PAGE_EXTRACTION_METHODS.value(),
            Map.of("1", PageExtractionMethod.OCR)
        ));

        assertThat(state.pageTexts()).isEqualTo(Map.of(1, "page text"));
        assertThat(state.pageConfidences()).isEqualTo(Map.of(1, 0.9));
        assertThat(state.pageExtractionMethods()).isEqualTo(
            Map.of(1, PageExtractionMethod.OCR)
        );
    }

    private ExtractionState buildState() {
        Map<String, Object> data = ExtractionState.initial(
            42L,
            "/tmp/sample.pdf"
        );
        data.put(
            ExtractionState.Key.PAGE_CLASSIFICATIONS.value(),
            List.of(
                PageSummary.builder()
                           .pageNumber(1)
                           .classification(PageClassification.DIGITAL)
                           .build()
            )
        );

        return new ExtractionState(data);
    }
}
