package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Clause {

    String clauseId;

    String sectionId;

    String text;

    int pageNumber;
}
