package com.dsi.rfp.agent.checkpoint;

import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.ExtractionState;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.HashMap;
import java.util.Map;

public class CheckpointingNodeAction implements NodeAction<ExtractionState> {

    private final NodeAction<ExtractionState> delegate;
    private final ExtractionStateCheckpointRepository checkpointRepository;

    public CheckpointingNodeAction(
        NodeAction<ExtractionState> delegate,
        ExtractionStateCheckpointRepository checkpointRepository
    ) {
        this.delegate = delegate;
        this.checkpointRepository = checkpointRepository;
    }

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception {
        Map<String, Object> updates = delegate.apply(state);
        checkpointRepository.save(
            state.jobId(),
            mergeState(
                state,
                updates
            )
        );

        return updates;
    }

    private ExtractionState mergeState(
        ExtractionState state,
        Map<String, Object> updates
    ) {
        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(updates);

        return new ExtractionState(merged);
    }
}
