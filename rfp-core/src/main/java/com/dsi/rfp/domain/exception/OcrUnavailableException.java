package com.dsi.rfp.domain.exception;

public class OcrUnavailableException extends RuntimeException {

    public OcrUnavailableException(String message) {
        super(message);
    }

    public OcrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
