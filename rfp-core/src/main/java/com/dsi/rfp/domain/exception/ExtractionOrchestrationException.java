package com.dsi.rfp.domain.exception;

public class ExtractionOrchestrationException extends RuntimeException {

    public ExtractionOrchestrationException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}

