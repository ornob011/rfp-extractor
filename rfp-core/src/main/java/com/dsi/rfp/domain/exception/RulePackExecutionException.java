package com.dsi.rfp.domain.exception;

public class RulePackExecutionException extends RuntimeException {

    public RulePackExecutionException(String message) {
        super(message);
    }

    public RulePackExecutionException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
