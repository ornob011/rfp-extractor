package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PageClassifier;
import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PageClassificationService {

    private final PdfDocumentLoader pdfLoader;
    private final PageClassifier pageClassifier;
    private final JobStatePort jobStatePort;

    public PageClassificationService(
        PdfDocumentLoader pdfLoader,
        PageClassifier pageClassifier,
        JobStatePort jobStatePort
    ) {
        this.pdfLoader = pdfLoader;
        this.pageClassifier = pageClassifier;
        this.jobStatePort = jobStatePort;
    }

    public List<PageSummary> classifyPages(
        Long jobId,
        Path pdfPath
    ) throws IOException {
        int pageCount = pdfLoader.getPageCount(pdfPath);

        List<PageSummary> results = new ArrayList<>();

        for (int page = 1; page <= pageCount; page++) {
            String text = pdfLoader.loadPageText(
                pdfPath,
                page
            );

            PDRectangle dims = pdfLoader.getPageDimensions(
                pdfPath,
                page
            );

            List<EmbeddedImageInfo> images = pdfLoader.loadPageImages(
                pdfPath,
                page
            );

            PageSummary result = pageClassifier.classify(
                page,
                text,
                dims,
                images
            );

            results.add(result);

            int progress = (page * 100) / pageCount;

            jobStatePort.updateProgress(
                jobId,
                progress
            );
        }

        log.info(
            "event=pages.classified component=PageClassificationService jobId={} pages={}",
            jobId,
            pageCount
        );

        return results;
    }
}
