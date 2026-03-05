package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.adapter.extraction.SectionSegmenter;
import com.dsi.rfp.domain.model.*;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtractionPipelineServiceTest {

    @Mock
    private PageClassificationService classificationService;
    @Mock
    private JobStatePort jobStatePort;
    @Mock
    private PdfDocumentLoader pdfLoader;
    @Mock
    private SectionSegmenter sectionSegmenter;

    private ExtractionPipelineService service;

    @BeforeEach
    void setUp() {
        service = new ExtractionPipelineService(
            classificationService,
            jobStatePort,
            pdfLoader,
            sectionSegmenter,
            new ObjectMapper()
        );
    }

    @Test
    void shouldRunClassificationAndSetCompleted() throws IOException {
        Long jobId = 1L;
        Path path = Path.of("/tmp/test.pdf");
        when(classificationService.classifyPages(jobId, path))
            .thenReturn(List.of());
        when(pdfLoader.getPageCount(path)).thenReturn(5);
        when(sectionSegmenter.segment(eq(path), eq(pdfLoader), eq(5)))
            .thenReturn(List.of());
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
        when(sectionSegmenter.segment(eq(path), eq(pdfLoader), eq(1)))
            .thenReturn(List.of());
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

    @Test
    void shouldSegmentSectionsAndPersistJson() throws IOException {
        Long jobId = 3L;
        Path path = Path.of("/tmp/test.pdf");
        when(classificationService.classifyPages(jobId, path))
            .thenReturn(List.of());
        when(pdfLoader.getPageCount(path)).thenReturn(10);

        Section section = Section.builder()
                                 .id(UUID.randomUUID())
                                 .title("Test Section").level(1).pageStart(0).pageEnd(9)
                                 .confidence(SectionConfidence.builder().score(0.9).method(HeadingDetectionMethod.TOC).build())
                                 .build();
        when(sectionSegmenter.segment(eq(path), eq(pdfLoader), eq(10)))
            .thenReturn(List.of(section));

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

        verify(jobStatePort).updateSectionsJson(eq(jobId), any(JsonNode.class));
    }
}
