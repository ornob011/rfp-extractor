package com.dsi.rfp.domain.exception;

public class RulePackLoadException extends RuntimeException {

    public RulePackLoadException(String message) {
        super(message);
    }

    public RulePackLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
