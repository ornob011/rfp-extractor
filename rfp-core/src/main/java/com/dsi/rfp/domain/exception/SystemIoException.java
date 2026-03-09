package com.dsi.rfp.domain.exception;

public class SystemIoException extends RuntimeException {

    public SystemIoException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}
