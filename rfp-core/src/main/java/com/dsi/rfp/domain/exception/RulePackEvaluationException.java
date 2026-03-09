package com.dsi.rfp.domain.exception;

public class RulePackEvaluationException extends RuntimeException {

    public RulePackEvaluationException(String message) {
        super(message);
    }

    public RulePackEvaluationException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
