package com.dsi.rfp.domain.exception;

import com.dsi.rfp.domain.model.ValidationErrorCode;
import lombok.Getter;

@Getter
public class DocumentValidationException extends RuntimeException {

    private final ValidationErrorCode errorCode;

    public DocumentValidationException(
        ValidationErrorCode errorCode,
        String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }
}
