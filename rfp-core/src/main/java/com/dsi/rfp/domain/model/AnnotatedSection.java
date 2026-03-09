package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnnotatedSection {

    @Builder.Default
    private final List<AnnotatedSection> children = new java.util.ArrayList<>();
    private String title;
    private int level;
    private int pageStart;
    private int pageEnd;
}
