package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.out.JobStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionPipelineServiceTest {

    @Mock
    private PageClassificationService classificationService;
    @Mock
    private JobStatePort jobStatePort;
    @Mock
    private PdfDocumentLoader pdfLoader;

    private ExtractionPipelineService service;

    @BeforeEach
    void setUp() {
        service = new ExtractionPipelineService(
            classificationService, jobStatePort, pdfLoader
        );
    }

    @Test
    void shouldRunClassificationAndSetCompleted() throws IOException {
        Long jobId = 1L;
        Path path = Path.of("/tmp/test.pdf");
        when(classificationService.classifyPages(jobId, path))
            .thenReturn(List.of());
        when(pdfLoader.getPageCount(path)).thenReturn(5);
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.RUNNING)
                                         .originalFilename("test.pdf")
                                         .build();
        when(jobStatePort.findById(jobId))
            .thenReturn(Optional.of(job));
        when(jobStatePort.save(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        service.runAsync(jobId, path);

        var inOrder = inOrder(jobStatePort);
        inOrder.verify(jobStatePort)
               .updateStatus(jobId, AnalysisStatus.RUNNING);
        inOrder.verify(jobStatePort)
               .updateStatus(jobId, AnalysisStatus.COMPLETED);
    }

    @Test
    void shouldSetRunningBeforeClassification() throws IOException {
        Long jobId = 2L;
        Path path = Path.of("/tmp/test.pdf");
        when(classificationService.classifyPages(jobId, path))
            .thenReturn(List.of());
        when(pdfLoader.getPageCount(path)).thenReturn(1);
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.RUNNING)
                                         .originalFilename("test.pdf")
                                         .build();
        when(jobStatePort.findById(jobId))
            .thenReturn(Optional.of(job));
        when(jobStatePort.save(any()))
            .thenAnswer(inv -> inv.getArgument(0));

        service.runAsync(jobId, path);

        var inOrder = inOrder(jobStatePort, classificationService);
        inOrder.verify(jobStatePort)
               .updateStatus(jobId, AnalysisStatus.RUNNING);
        inOrder.verify(classificationService)
               .classifyPages(jobId, path);
    }
}
