package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.rest.JobStatusResponse;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.out.JobStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfpJobServiceTest {

    @Mock
    private JobStatePort jobStatePort;

    private RfpJobService service;

    @BeforeEach
    void setUp() {
        service = new RfpJobService(jobStatePort);
    }

    @Test
    void shouldReturnEmptyWhenJobNotFound() {
        Long jobId = 1L;
        when(jobStatePort.findById(jobId))
            .thenReturn(Optional.empty());
        Optional<JobStatusResponse> result = service.findById(jobId);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldMapJobToResponse() {
        Long jobId = 42L;
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.RUNNING)
                                         .progress(50)
                                         .submittedAt(Instant.now())
                                         .originalFilename("test.pdf")
                                         .pageCount(10)
                                         .build();
        when(jobStatePort.findById(jobId))
            .thenReturn(Optional.of(job));

        Optional<JobStatusResponse> result = service.findById(jobId);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(result.get().getProgress()).isEqualTo(50);
        assertThat(result.get().getOriginalFilename())
            .isEqualTo("test.pdf");
    }

    @Test
    void shouldReturnAllJobs() {
        when(jobStatePort.findAll())
            .thenReturn(Collections.emptyList());
        List<JobStatusResponse> result = service.findAll();
        assertThat(result).isEmpty();
    }
}
