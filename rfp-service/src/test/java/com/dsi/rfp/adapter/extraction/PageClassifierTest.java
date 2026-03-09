package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageClassifierTest {

    private final PageClassifier classifier = new PageClassifier();

    @Test
    void shouldClassifyDigitalPage() {
        String text = "A".repeat(1000);
        PDRectangle dims = new PDRectangle(612, 792);
        PageSummary result = classifier.classify(
            1, text, dims, Collections.emptyList()
        );
        assertThat(result.getClassification()).isEqualTo(PageClassification.DIGITAL);
    }

    @Test
    void shouldClassifyScannedPageWithHighRasterCoverage() {
        PDRectangle dims = new PDRectangle(612, 792);
        double pageArea = 612.0 * 792.0;
        EmbeddedImageInfo largeImage = EmbeddedImageInfo.builder()
                                                        .pageNumber(1)
                                                        .x(0).y(0)
                                                        .width((float) Math.sqrt(pageArea * 0.85))
                                                        .height((float) Math.sqrt(pageArea * 0.85))
                                                        .build();
        PageSummary result = classifier.classify(
            1, StringUtils.EMPTY, dims, List.of(largeImage)
        );
        assertThat(result.getClassification()).isEqualTo(PageClassification.SCANNED);
    }

    @Test
    void shouldClassifyMixedPage() {
        String text = "A".repeat(1000);
        PDRectangle dims = new PDRectangle(612, 792);
        double pageArea = 612.0 * 792.0;
        EmbeddedImageInfo mediumImage = EmbeddedImageInfo.builder()
                                                         .pageNumber(1)
                                                         .x(0).y(0)
                                                         .width((float) Math.sqrt(pageArea * 0.70))
                                                         .height((float) Math.sqrt(pageArea * 0.70))
                                                         .build();
        PageSummary result = classifier.classify(
            1, text, dims, List.of(mediumImage)
        );
        assertThat(result.getClassification()).isEqualTo(PageClassification.MIXED);
    }

    @Test
    void shouldClassifyBlankPageAsScanned() {
        PDRectangle dims = new PDRectangle(612, 792);
        PageSummary result = classifier.classify(
            1, StringUtils.EMPTY, dims, Collections.emptyList()
        );
        assertThat(result.getClassification()).isEqualTo(PageClassification.SCANNED);
    }

    @Test
    void shouldPopulateAllResultFields() {
        String text = "Hello World";
        PDRectangle dims = new PDRectangle(612, 792);
        PageSummary result = classifier.classify(
            5, text, dims, Collections.emptyList()
        );
        assertThat(result.getPageNumber()).isEqualTo(5);
        assertThat(result.getCharCount()).isEqualTo(11);
        assertThat(result.getPageWidth()).isEqualTo(612.0);
        assertThat(result.getPageHeight()).isEqualTo(792.0);
    }
}
