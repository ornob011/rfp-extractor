package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
@Builder
public class RepairableComponent implements Serializable {

    String componentId;

    RepairComponentType componentType;

    ConfidenceSource confidenceSource;

    double currentConfidence;
}
