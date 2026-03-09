package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AgentExecutionEntity;
import com.dsi.rfp.adapter.persistence.entity.AgentStepEntity;
import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.repository.AgentExecutionRepository;
import com.dsi.rfp.adapter.persistence.repository.AgentStepRepository;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.config.JpaConfig;
import com.dsi.rfp.domain.model.AgentStepType;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExecutionStatus;
import com.dsi.rfp.domain.model.StepOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AgentStepRepositoryTest {

    @Autowired
    private AgentStepRepository agentStepRepository;

    @Autowired
    private AgentExecutionRepository agentExecutionRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void shouldFindByExecutionIdOrderBySequence() {
        DocumentEntity doc =
            documentRepository.save(
                DocumentEntity.builder()
                              .originalFilename("test.pdf")
                              .contentType("application/pdf")
                              .fileSizeBytes(1024L)
                              .storagePath("/tmp/test.pdf")
                              .sha256Checksum("checksum-exec-test")
                              .build());

        AnalysisJobEntity job =
            analysisJobRepository.save(
                AnalysisJobEntity.builder()
                                 .document(doc)
                                 .status(AnalysisStatus.RUNNING)
                                 .progressPercent(0)
                                 .build());

        AgentExecutionEntity execution =
            agentExecutionRepository.save(
                AgentExecutionEntity.builder()
                                    .analysisJob(job)
                                    .status(ExecutionStatus.RUNNING)
                                    .build());

        agentStepRepository.save(
            AgentStepEntity.builder()
                           .execution(execution)
                           .stepType(AgentStepType.VALIDATE)
                           .sequence(2)
                           .outcome(StepOutcome.SUCCESS)
                           .build());

        agentStepRepository.save(
            AgentStepEntity.builder()
                           .execution(execution)
                           .stepType(AgentStepType.CLASSIFY_PAGES)
                           .sequence(0)
                           .outcome(StepOutcome.SUCCESS)
                           .build());

        agentStepRepository.save(
            AgentStepEntity.builder()
                           .execution(execution)
                           .stepType(AgentStepType.EXTRACT_TEXT)
                           .sequence(1)
                           .outcome(StepOutcome.SUCCESS)
                           .build());

        List<AgentStepEntity> steps =
            agentStepRepository.findByExecutionIdOrderBySequence(execution.getId());

        assertThat(steps).hasSize(3);
        assertThat(steps.get(0).getSequence()).isZero();
        assertThat(steps.get(1).getSequence()).isEqualTo(1);
        assertThat(steps.get(2).getSequence()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyListWhenNoStepsForExecution() {
        DocumentEntity doc =
            documentRepository.save(
                DocumentEntity.builder()
                              .originalFilename("empty.pdf")
                              .contentType("application/pdf")
                              .fileSizeBytes(512L)
                              .storagePath("/tmp/empty.pdf")
                              .sha256Checksum("checksum-empty-exec")
                              .build());

        AnalysisJobEntity job =
            analysisJobRepository.save(
                AnalysisJobEntity.builder()
                                 .document(doc)
                                 .status(AnalysisStatus.QUEUED)
                                 .progressPercent(0)
                                 .build());

        AgentExecutionEntity execution =
            agentExecutionRepository.save(
                AgentExecutionEntity.builder()
                                    .analysisJob(job)
                                    .status(ExecutionStatus.PENDING)
                                    .build());

        List<AgentStepEntity> steps =
            agentStepRepository.findByExecutionIdOrderBySequence(execution.getId());

        assertThat(steps).isEmpty();
    }
}
