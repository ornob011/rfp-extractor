package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.ocr.LayoutDetectionDto;
import com.dsi.rfp.adapter.ocr.OcrResultDto;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MixedPageMergeServiceTest {

    private final MixedPageMergeService service = new MixedPageMergeService();

    @Test
    void shouldUseOrderedTextWhenNoRegionsExist() {
        MixedPageContent result = service.merge(
            new MixedPageMergeInput(
                "ordered text",
                new OcrResultDto(
                    "ocr text",
                    List.of(),
                    0.7,
                    1,
                    "easyocr"
                ),
                new LayoutDetectionDto(
                    false,
                    List.of(),
                    List.of()
                ),
                0.9
            )
        );

        assertThat(result.text()).isEqualTo("ordered text");
        assertThat(result.method()).isEqualTo(PageExtractionMethod.TEXT_PLUS_OCR);
    }

    @Test
    void shouldAppendOcrTextWhenTableRegionsExist() {
        MixedPageContent result = service.merge(
            new MixedPageMergeInput(
                "ordered prose",
                new OcrResultDto(
                    "ocr text",
                    List.of(),
                    0.7,
                    1,
                    "easyocr"
                ),
                new LayoutDetectionDto(
                    true,
                    List.of(new com.dsi.rfp.adapter.ocr.BoundingBoxDto(0.1, 0.1, 0.2, 0.2)),
                    List.of()
                ),
                0.9
            )
        );

        assertThat(result.text()).contains("ordered prose");
        assertThat(result.text()).contains("ocr text");
    }
}
