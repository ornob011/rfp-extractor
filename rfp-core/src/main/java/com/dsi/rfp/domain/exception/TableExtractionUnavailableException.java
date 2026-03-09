package com.dsi.rfp.domain.exception;

public class TableExtractionUnavailableException extends RuntimeException {

    public TableExtractionUnavailableException(String message) {
        super(message);
    }

    public TableExtractionUnavailableException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}

