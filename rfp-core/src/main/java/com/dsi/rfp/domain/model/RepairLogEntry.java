package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
@Builder
public class RepairLogEntry implements Serializable {

    String componentId;

    RepairComponentType componentType;

    int attemptNumber;

    RepairStrategy strategy;

    double beforeConfidence;

    double afterConfidence;

    RepairOutcome outcome;

    String reason;

    @Builder.Default
    Instant timestamp = Instant.now();
}
