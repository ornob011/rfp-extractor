package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RepairableComponent {

    String componentId;

    RepairComponentType componentType;

    ConfidenceSource confidenceSource;

    double currentConfidence;
}
