package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.ExtractionStateCheckpointEntity;
import com.dsi.rfp.adapter.persistence.repository.ExtractionStateCheckpointJpaRepository;
import com.dsi.rfp.agent.ExtractionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionStateCheckpointRepositoryTest {

    @Mock
    private ExtractionStateCheckpointJpaRepository jpaRepository;

    @InjectMocks
    private ExtractionStateCheckpointRepository repository;

    @Test
    void shouldSaveCheckpoint() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        when(jpaRepository.findTopByJobIdOrderByCheckpointSequenceDesc(1L))
            .thenReturn(Optional.empty());

        repository.save(1L, state);

        ArgumentCaptor<ExtractionStateCheckpointEntity> captor =
            ArgumentCaptor.forClass(ExtractionStateCheckpointEntity.class);

        verify(jpaRepository).save(captor.capture());

        ExtractionStateCheckpointEntity entity = captor.getValue();
        assertThat(entity.getJobId()).isEqualTo(1L);
        assertThat(entity.getCheckpointSequence()).isEqualTo(1);
        assertThat(entity.getStateJson()).containsEntry(
            "jobId",
            1L
        );
    }

    @Test
    void shouldReturnEmptyOptionalWhenCheckpointMissing() {
        when(jpaRepository.findTopByJobIdOrderByCheckpointSequenceDesc(99L))
            .thenReturn(Optional.empty());

        Optional<ExtractionState> result = repository.load(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldLoadAndDeserializeCheckpoint() {
        ExtractionStateCheckpointEntity entity =
            new ExtractionStateCheckpointEntity();

        entity.setJobId(1L);
        entity.setCheckpointSequence(1);
        entity.setStateJson(Map.of(
            "jobId",
            1L,
            "documentPath",
            "/tmp/x.pdf"
        ));

        when(jpaRepository.findTopByJobIdOrderByCheckpointSequenceDesc(1L))
            .thenReturn(Optional.of(entity));

        Optional<ExtractionState> result = repository.load(1L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().documentPath()).isEqualTo("/tmp/x.pdf");
    }
}
