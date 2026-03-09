package com.dsi.rfp.domain.exception;

public class EntityMetadataContractException extends RuntimeException {

    public EntityMetadataContractException(String message) {
        super(message);
    }

    public EntityMetadataContractException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}
