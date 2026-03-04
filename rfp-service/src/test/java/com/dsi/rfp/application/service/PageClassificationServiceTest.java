package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PageClassifier;
import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.domain.model.EmbeddedImageInfo;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageClassificationResult;
import com.dsi.rfp.domain.port.out.JobStatePort;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageClassificationServiceTest {

    @Mock
    private PdfDocumentLoader pdfLoader;

    @Mock
    private PageClassifier pageClassifier;

    @Mock
    private JobStatePort jobStatePort;

    private PageClassificationService service;

    @BeforeEach
    void setUp() {
        service = new PageClassificationService(
            pdfLoader, pageClassifier, jobStatePort
        );
    }

    @Test
    void shouldReturnOneResultPerPage() throws IOException {
        Long jobId = 1L;
        Path path = Path.of("/tmp/test.pdf");
        when(pdfLoader.getPageCount(path)).thenReturn(3);
        when(pdfLoader.loadPageText(eq(path), anyInt()))
            .thenReturn("text");
        when(pdfLoader.getPageDimensions(eq(path), anyInt()))
            .thenReturn(new PDRectangle(612, 792));
        when(pdfLoader.loadPageImages(eq(path), anyInt()))
            .thenReturn(Collections.emptyList());
        when(pageClassifier.classify(
            anyInt(), anyString(), any(), anyList()
        )).thenReturn(PageClassificationResult.builder()
                                              .classification(PageClassification.DIGITAL)
                                              .build());

        List<PageClassificationResult> results =
            service.classifyPages(jobId, path);

        assertThat(results).hasSize(3);
    }

    @Test
    void shouldUpdateProgressForEachPage() throws IOException {
        Long jobId = 2L;
        Path path = Path.of("/tmp/test.pdf");
        when(pdfLoader.getPageCount(path)).thenReturn(2);
        when(pdfLoader.loadPageText(eq(path), anyInt()))
            .thenReturn("text");
        when(pdfLoader.getPageDimensions(eq(path), anyInt()))
            .thenReturn(new PDRectangle(612, 792));
        when(pdfLoader.loadPageImages(eq(path), anyInt()))
            .thenReturn(Collections.emptyList());
        when(pageClassifier.classify(
            anyInt(), anyString(), any(), anyList()
        )).thenReturn(PageClassificationResult.builder()
                                              .classification(PageClassification.DIGITAL)
                                              .build());

        service.classifyPages(jobId, path);

        verify(jobStatePort).updateProgress(jobId, 50);
        verify(jobStatePort).updateProgress(jobId, 100);
    }

    @Test
    void shouldDelegateToPageClassifier() throws IOException {
        Long jobId = 3L;
        Path path = Path.of("/tmp/test.pdf");
        when(pdfLoader.getPageCount(path)).thenReturn(1);
        when(pdfLoader.loadPageText(path, 1)).thenReturn("hello");
        PDRectangle dims = new PDRectangle(612, 792);
        when(pdfLoader.getPageDimensions(path, 1)).thenReturn(dims);
        List<EmbeddedImageInfo> images = Collections.emptyList();
        when(pdfLoader.loadPageImages(path, 1)).thenReturn(images);
        when(pageClassifier.classify(1, "hello", dims, images))
            .thenReturn(PageClassificationResult.builder()
                                                .classification(PageClassification.DIGITAL)
                                                .build());

        service.classifyPages(jobId, path);

        verify(pageClassifier).classify(1, "hello", dims, images);
    }
}
