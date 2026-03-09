package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class RepairLogEntry {

    String fieldId;

    String previousValue;

    String newValue;

    String repairReason;

    int iterationNumber;

    Instant timestamp;
}
