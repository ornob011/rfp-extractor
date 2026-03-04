package com.dsi.rfp.domain.exception;

public class DocumentCorruptException extends RuntimeException {

    public DocumentCorruptException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
