package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.adapter.rest.JobStatusResponse;
import com.dsi.rfp.adapter.rest.RepairEventDto;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.RepairLogEntry;
import com.dsi.rfp.domain.model.UserRole;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.dsi.rfp.domain.port.out.ResultPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RfpJobService {

    private static final Set<UserRole> PRIVILEGED_ROLES = Set.of(UserRole.ADMIN, UserRole.AUDITOR);

    private final JobStatePort jobStatePort;
    private final ResultPersistencePort resultPersistencePort;
    private final ExtractionStateCheckpointRepository checkpointRepository;

    public RfpJobService(
        JobStatePort jobStatePort,
        ResultPersistencePort resultPersistencePort,
        ExtractionStateCheckpointRepository checkpointRepository
    ) {
        this.jobStatePort = jobStatePort;
        this.resultPersistencePort = resultPersistencePort;
        this.checkpointRepository = checkpointRepository;
    }

    public Optional<JobStatusResponse> findById(Long jobId) {
        return jobStatePort.findById(jobId)
            .map(job -> toResponse(job, jobId));
    }

    public Optional<JobStatusResponse> findById(
        Long jobId,
        String username,
        Set<UserRole> roles
    ) {
        return jobStatePort.findById(jobId)
            .map(job -> {
                checkOwnership(
                    job,
                    username,
                    roles
                );
                return toResponse(job, jobId);
            });
    }

    public List<JobStatusResponse> findAll() {
        return jobStatePort.findAll().stream()
            .map(job -> toResponse(job, job.getJobId()))
            .toList();
    }

    public List<JobStatusResponse> findAll(
        String username,
        Set<UserRole> roles
    ) {
        return jobStatePort.findAll().stream()
            .filter(job -> isAccessible(
                job,
                username,
                roles
            ))
            .map(job -> toResponse(job, job.getJobId()))
            .toList();
    }

    public JsonNode getSectionsJson(Long jobId) {
        return jobStatePort.findById(jobId)
            .map(ExtractionJob::getSectionsJson)
            .orElseThrow(() -> new EntityNotFoundException(
                String.format("Job not found: %s", jobId)
            ));
    }

    public Optional<JsonNode> getResult(Long jobId) {
        return resultPersistencePort.findResult(jobId);
    }

    private void checkOwnership(
        ExtractionJob job,
        String username,
        Set<UserRole> roles
    ) {
        if (hasPrivilegedRole(roles)) {
            return;
        }

        if (username.equals(job.getSubmittedByUsername())) {
            return;
        }

        throw new AccessDeniedException(
            "You do not have permission to access this job"
        );
    }

    private boolean isAccessible(
        ExtractionJob job,
        String username,
        Set<UserRole> roles
    ) {
        if (hasPrivilegedRole(roles)) {
            return true;
        }

        return username.equals(job.getSubmittedByUsername());
    }

    private boolean hasPrivilegedRole(Set<UserRole> roles) {
        return roles.stream().anyMatch(PRIVILEGED_ROLES::contains);
    }

    private JobStatusResponse toResponse(
        ExtractionJob job,
        Long jobId
    ) {
        Optional<ExtractionState> checkpoint = checkpointRepository.load(jobId);

        return JobStatusResponse.builder()
            .jobId(job.getJobId())
            .status(job.getStatus())
            .progress(job.getProgress())
            .submittedAt(job.getSubmittedAt())
            .completedAt(job.getCompletedAt())
            .errorMessage(job.getErrorMessage())
            .originalFilename(job.getOriginalFilename())
            .pageCount(job.getPageCount())
            .repairEvents(checkpoint.map(this::mapRepairEvents).orElse(List.of()))
            .totalRepairIterations(checkpoint.map(ExtractionState::totalRepairIterations).orElse(0))
            .lowConfidenceQueueSize(checkpoint.map(state -> state.lowConfidenceQueue().size()).orElse(0))
            .build();
    }

    private List<RepairEventDto> mapRepairEvents(ExtractionState state) {
        return state.repairLog().stream()
            .map(this::toRepairEvent)
            .toList();
    }

    private RepairEventDto toRepairEvent(RepairLogEntry entry) {
        return new RepairEventDto(
            entry.getComponentId(),
            entry.getAttemptNumber(),
            entry.getStrategy(),
            entry.getOutcome()
        );
    }
}
