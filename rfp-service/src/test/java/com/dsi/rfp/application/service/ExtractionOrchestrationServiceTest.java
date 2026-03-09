package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.ExtractionGraph;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.port.out.JobStatePort;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtractionOrchestrationServiceTest {

    @Mock
    private ExtractionGraph extractionGraph;

    @Mock
    private JobStatePort jobStatePort;

    @Mock
    private ExtractionStateCheckpointRepository checkpointRepository;

    @Mock
    private CompiledGraph<ExtractionState> compiledGraph;

    private ExtractionOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new ExtractionOrchestrationService(
            extractionGraph,
            jobStatePort,
            checkpointRepository
        );
    }

    @Test
    void shouldSetRunningThenCompleted() throws Exception {
        when(checkpointRepository.load(42L)).thenReturn(Optional.empty());
        when(extractionGraph.compile()).thenReturn(compiledGraph);
        when(compiledGraph.invoke(argThat(this::isStateMap))).thenReturn(Optional.empty());

        service.runExtraction(42L, Path.of("/tmp/x.pdf"));

        InOrder inOrder = inOrder(jobStatePort);
        inOrder.verify(jobStatePort).updateStatus(42L, AnalysisStatus.RUNNING);
        inOrder.verify(jobStatePort).updateStatus(42L, AnalysisStatus.COMPLETED);
    }

    @Test
    void shouldPassInitialStateToGraph() throws Exception {
        when(checkpointRepository.load(42L)).thenReturn(Optional.empty());
        when(extractionGraph.compile()).thenReturn(compiledGraph);
        when(compiledGraph.invoke(argThat(this::isStateMap))).thenReturn(Optional.empty());

        service.runExtraction(42L, Path.of("/tmp/doc.pdf"));

        verify(compiledGraph).invoke(argThat(this::matchesInitialState));
    }

    private boolean isStateMap(Map<String, Object> state) {
        return state != null;
    }

    private boolean matchesInitialState(Map<String, Object> state) {
        assertThat(state.get(ExtractionState.Key.JOB_ID.value())).isEqualTo(42L);
        assertThat(state.get(ExtractionState.Key.DOCUMENT_PATH.value())).isEqualTo("/tmp/doc.pdf");

        return true;
    }
}
