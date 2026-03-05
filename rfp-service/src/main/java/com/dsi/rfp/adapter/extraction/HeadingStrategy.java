package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.HeadingCandidate;
import com.dsi.rfp.domain.model.HeadingDetectionMethod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface HeadingStrategy {

    List<HeadingCandidate> detectHeadings(
        Path pdfPath,
        PdfDocumentLoader loader
    ) throws IOException;

    HeadingDetectionMethod strategyName();
}
