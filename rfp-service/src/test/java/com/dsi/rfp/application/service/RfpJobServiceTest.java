package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.adapter.rest.JobStatusResponse;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.UserRole;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.dsi.rfp.domain.port.out.ResultPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfpJobServiceTest {

    @Mock
    private JobStatePort jobStatePort;

    @Mock
    private ResultPersistencePort resultPersistencePort;

    @Mock
    private ExtractionStateCheckpointRepository checkpointRepository;

    private RfpJobService service;

    @BeforeEach
    void setUp() {
        service = new RfpJobService(
            jobStatePort,
            resultPersistencePort,
            checkpointRepository
        );
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

    @Test
    void shouldReturnResultWhenPresent() {
        Long jobId = 42L;
        JsonNode mockResult = new ObjectMapper().createObjectNode().put("test", "value");
        when(resultPersistencePort.findResult(jobId))
            .thenReturn(Optional.of(mockResult));

        Optional<JsonNode> result = service.getResult(jobId);

        assertThat(result).isPresent();
        assertThat(result.get().get("test").asText()).isEqualTo("value");
    }

    @Test
    void shouldReturnEmptyResultWhenNotFound() {
        Long jobId = 99L;
        when(resultPersistencePort.findResult(jobId))
            .thenReturn(Optional.empty());

        Optional<JsonNode> result = service.getResult(jobId);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldAllowOwnerToAccessJob() {
        Long jobId = 1L;
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.COMPLETED)
                                         .submittedByUsername("analyst1")
                                         .originalFilename("test.pdf")
                                         .build();

        when(jobStatePort.findById(jobId)).thenReturn(Optional.of(job));

        Optional<JobStatusResponse> result = service.findById(
            jobId, "analyst1", Set.of(UserRole.ANALYST)
        );

        assertThat(result).isPresent();
    }

    @Test
    void shouldDenyNonOwnerAnalystAccess() {
        Long jobId = 1L;
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.COMPLETED)
                                         .submittedByUsername("analyst1")
                                         .originalFilename("test.pdf")
                                         .build();

        when(jobStatePort.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.findById(
            jobId, "analyst2", Set.of(UserRole.ANALYST)
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAllowAdminToAccessAnyJob() {
        Long jobId = 1L;
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.COMPLETED)
                                         .submittedByUsername("analyst1")
                                         .originalFilename("test.pdf")
                                         .build();

        when(jobStatePort.findById(jobId)).thenReturn(Optional.of(job));

        Optional<JobStatusResponse> result = service.findById(
            jobId, "admin", Set.of(UserRole.ADMIN)
        );

        assertThat(result).isPresent();
    }

    @Test
    void shouldAllowAuditorToAccessAnyJob() {
        Long jobId = 1L;
        ExtractionJob job = ExtractionJob.builder()
                                         .jobId(jobId)
                                         .status(AnalysisStatus.COMPLETED)
                                         .submittedByUsername("analyst1")
                                         .originalFilename("test.pdf")
                                         .build();

        when(jobStatePort.findById(jobId)).thenReturn(Optional.of(job));

        Optional<JobStatusResponse> result = service.findById(
            jobId, "auditor", Set.of(UserRole.AUDITOR)
        );

        assertThat(result).isPresent();
    }

    @Test
    void shouldFilterJobsByOwnerForAnalyst() {
        ExtractionJob job1 = ExtractionJob.builder()
                                          .jobId(1L)
                                          .status(AnalysisStatus.COMPLETED)
                                          .submittedByUsername("analyst1")
                                          .originalFilename("test1.pdf")
                                          .build();
        ExtractionJob job2 = ExtractionJob.builder()
                                          .jobId(2L)
                                          .status(AnalysisStatus.COMPLETED)
                                          .submittedByUsername("analyst2")
                                          .originalFilename("test2.pdf")
                                          .build();

        when(jobStatePort.findAll()).thenReturn(List.of(job1, job2));

        List<JobStatusResponse> result = service.findAll(
            "analyst1", Set.of(UserRole.ANALYST)
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getJobId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnAllJobsForAdmin() {
        ExtractionJob job1 = ExtractionJob.builder()
                                          .jobId(1L)
                                          .status(AnalysisStatus.COMPLETED)
                                          .submittedByUsername("analyst1")
                                          .originalFilename("test1.pdf")
                                          .build();
        ExtractionJob job2 = ExtractionJob.builder()
                                          .jobId(2L)
                                          .status(AnalysisStatus.COMPLETED)
                                          .submittedByUsername("analyst2")
                                          .originalFilename("test2.pdf")
                                          .build();

        when(jobStatePort.findAll()).thenReturn(List.of(job1, job2));

        List<JobStatusResponse> result = service.findAll(
            "admin", Set.of(UserRole.ADMIN)
        );

        assertThat(result).hasSize(2);
    }
}
