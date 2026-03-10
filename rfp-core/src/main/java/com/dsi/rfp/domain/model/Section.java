package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class Section implements Serializable {

    @Builder.Default
    private final List<Section> children = new java.util.ArrayList<>();
    private UUID id;
    private String title;
    private int level;
    private int pageStart;
    private int pageEnd;
    private SectionConfidence confidence;
}
