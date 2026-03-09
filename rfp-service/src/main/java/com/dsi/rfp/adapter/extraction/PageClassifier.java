package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PageClassifier {

    private static final double DIGITAL_CHAR_DENSITY_MIN = 0.001;
    private static final double SCANNED_CHAR_DENSITY_MAX = 0.0001;
    private static final double DIGITAL_RASTER_COVERAGE_MAX = 0.60;
    private static final double SCANNED_RASTER_COVERAGE_MIN = 0.80;

    public PageClassificationResult classify(
        int pageNumber,
        String pageText,
        PDRectangle pageDimensions,
        List<EmbeddedImageInfo> images
    ) {
        int charCount = pageText.trim().length();
        double pageWidth = pageDimensions.getWidth();
        double pageHeight = pageDimensions.getHeight();
        double pageArea = pageWidth * pageHeight;

        double totalImageArea = images.stream()
                                      .mapToDouble(EmbeddedImageInfo::area)
                                      .sum();

        double charDensity = computeCharDensity(charCount, pageArea);
        double rasterCoverage = computeRasterCoverage(
            totalImageArea, pageArea
        );

        PageClassification classification = determineClassification(
            charDensity, rasterCoverage
        );

        log.debug(
            "event=page.classified component=PageClassifier "
            + "page={} classification={} charDensity={} "
            + "rasterCoverage={} charCount={}",
            pageNumber,
            classification,
            charDensity,
            rasterCoverage,
            charCount
        );

        return PageClassificationResult.builder()
                                       .pageNumber(pageNumber)
                                       .classification(classification)
                                       .charDensity(charDensity)
                                       .rasterCoverage(rasterCoverage)
                                       .charCount(charCount)
                                       .pageWidth(pageWidth)
                                       .pageHeight(pageHeight)
                                       .pageAreaPixels(pageArea)
                                       .totalImageArea(totalImageArea)
                                       .build();
    }

    private double computeCharDensity(
        int charCount,
        double pageArea
    ) {
        if (pageArea <= 0) {
            return 0.0;
        }
        return charCount / pageArea;
    }

    private double computeRasterCoverage(
        double totalImageArea,
        double pageArea
    ) {
        if (pageArea <= 0) {
            return 0.0;
        }
        return Math.min(totalImageArea / pageArea, 1.0);
    }

    private PageClassification determineClassification(
        double charDensity,
        double rasterCoverage
    ) {
        if (charDensity > DIGITAL_CHAR_DENSITY_MIN
            && rasterCoverage < DIGITAL_RASTER_COVERAGE_MAX) {
            return PageClassification.DIGITAL;
        }

        if (charDensity < SCANNED_CHAR_DENSITY_MAX
            || rasterCoverage > SCANNED_RASTER_COVERAGE_MIN) {
            return PageClassification.SCANNED;
        }

        return PageClassification.MIXED;
    }
}
