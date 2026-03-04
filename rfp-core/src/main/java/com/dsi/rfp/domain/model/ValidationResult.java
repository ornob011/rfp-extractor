package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationResult {

    private final boolean valid;
    private final ValidationErrorCode errorCode;
    private final String errorMessage;

    public static ValidationResult ok() {
        return ValidationResult.builder()
                               .valid(true)
                               .build();
    }

    public static ValidationResult fail(
        ValidationErrorCode errorCode,
        String errorMessage
    ) {
        return ValidationResult.builder()
                               .valid(false)
                               .errorCode(errorCode)
                               .errorMessage(errorMessage)
                               .build();
    }
}
