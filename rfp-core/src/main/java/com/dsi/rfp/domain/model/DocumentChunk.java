package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentChunk {

    int chunkIndex;

    @Builder.Default
    List<Section> sections = List.of();

    String rawText;

    int tokenEstimate;

    String contextHeader;
}
