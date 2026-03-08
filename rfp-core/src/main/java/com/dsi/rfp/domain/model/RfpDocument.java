package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class RfpDocument {

    Long jobId;

    @Builder.Default
    List<Section> sections = List.of();

    RfpEntities entities;

    @Builder.Default
    List<TableExtractionResult> tables = List.of();

    @Builder.Default
    Map<String, Double> confidenceMap = Map.of();

    @Builder.Default
    List<Clause> clauses = List.of();

    @Builder.Default
    List<PageSummary> pageClassifications = List.of();

    @Builder.Default
    Map<Integer, Double> pageConfidences = Map.of();

    @Builder.Default
    Map<Integer, PageExtractionMethod> pageExtractionMethods = Map.of();
}
