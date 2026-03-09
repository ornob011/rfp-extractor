package com.dsi.rfp.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfidenceRouter {

    private final ConfidenceScoringConfig scoringConfig;

    public boolean anyFieldBelowThreshold(ExtractionState state) {
        return state.confidenceMap()
                    .values()
                    .stream()
                    .anyMatch(score -> score < scoringConfig.lowConfidenceThreshold());
    }
}
