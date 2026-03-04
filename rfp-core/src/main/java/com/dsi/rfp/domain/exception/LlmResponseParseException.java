package com.dsi.rfp.domain.exception;

public class LlmResponseParseException extends RuntimeException {

    private final String rawResponse;

    public LlmResponseParseException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    public LlmResponseParseException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
