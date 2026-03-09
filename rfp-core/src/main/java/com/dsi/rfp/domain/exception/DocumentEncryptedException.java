package com.dsi.rfp.domain.exception;

public class DocumentEncryptedException extends RuntimeException {

    public DocumentEncryptedException(String message) {
        super(message);
    }

    public DocumentEncryptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
