package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;

@Value
@Builder
public class Clause implements Serializable {

    String clauseId;

    String sectionId;

    String text;

    int pageNumber;
}
