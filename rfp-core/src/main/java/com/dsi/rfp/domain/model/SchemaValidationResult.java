package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SchemaValidationResult {

    @Builder.Default
    private final List<String> errors = new java.util.ArrayList<>();
    private boolean valid;

    public static SchemaValidationResult ok() {
        return SchemaValidationResult.builder()
                                     .valid(true)
                                     .build();
    }

    public static SchemaValidationResult fail(List<String> errors) {
        return SchemaValidationResult.builder()
                                     .valid(false)
                                     .errors(errors)
                                     .build();
    }
}
