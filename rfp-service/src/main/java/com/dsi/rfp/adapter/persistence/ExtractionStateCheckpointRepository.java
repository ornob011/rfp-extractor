package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.ExtractionStateCheckpointEntity;
import com.dsi.rfp.adapter.persistence.repository.ExtractionStateCheckpointJpaRepository;
import com.dsi.rfp.agent.ExtractionState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class ExtractionStateCheckpointRepository {
    private final ExtractionStateCheckpointJpaRepository jpaRepository;

    public ExtractionStateCheckpointRepository(
        ExtractionStateCheckpointJpaRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

    public void save(
        Long jobId,
        ExtractionState state
    ) {
        int nextSequence = nextSequence(jobId);

        ExtractionStateCheckpointEntity entity = new ExtractionStateCheckpointEntity();
        entity.setJobId(jobId);
        entity.setCheckpointSequence(nextSequence);
        entity.setStateJson(state.data());

        jpaRepository.save(entity);
    }

    public Optional<ExtractionState> load(Long jobId) {
        return jpaRepository.findTopByJobIdOrderByCheckpointSequenceDesc(jobId)
                            .map(ExtractionStateCheckpointEntity::getStateJson)
                            .map(ExtractionState::new);
    }

    @Transactional
    public void deleteByJobId(Long jobId) {
        jpaRepository.deleteByJobId(jobId);
    }

    private int nextSequence(Long jobId) {
        return jpaRepository.findTopByJobIdOrderByCheckpointSequenceDesc(jobId)
                            .map(entity -> entity.getCheckpointSequence() + 1)
                            .orElse(1);
    }
}
