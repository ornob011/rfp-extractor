package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.AnalysisResultEntity;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.adapter.persistence.repository.AnalysisResultRepository;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.port.out.ResultPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaResultRepository implements ResultPersistencePort {

    private final AnalysisResultRepository resultRepository;
    private final AnalysisJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveResult(Long jobId, JsonNode resultJson) {
        var jobEntity = findJobOrThrow(jobId);
        persistResult(jobEntity, resultJson.toString());

        log.info(
            "event=result.saved component=JpaResultRepository jobId={}",
            jobId
        );
    }

    private AnalysisJobEntity findJobOrThrow(Long jobId) {
        return jobRepository.findById(jobId)
                            .orElseThrow(() -> new EntityNotFoundException(
                                String.format("Job not found: %s", jobId)
                            ));
    }

    private void persistResult(
        AnalysisJobEntity jobEntity,
        String jsonString
    ) {
        resultRepository.findByAnalysisJobId(jobEntity.getId())
                        .ifPresentOrElse(
                            entity -> entity.setResultJson(jsonString),
                            () -> resultRepository.save(
                                AnalysisResultEntity.builder()
                                                    .analysisJob(jobEntity)
                                                    .resultJson(jsonString)
                                                    .build()
                            )
                        );
    }

    @Override
    public Optional<JsonNode> findResult(Long jobId) {
        return resultRepository.findByAnalysisJobId(jobId)
                               .map(entity -> parseJson(entity.getResultJson()));
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new SystemIoException(
                "Failed to parse persisted result JSON",
                exception
            );
        }
    }
}
