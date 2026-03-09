package com.dsi.rfp.domain.exception;

import lombok.Getter;

@Getter
public class FileSizeLimitExceededException extends RuntimeException {

    private final long actualBytes;
    private final long limitBytes;

    public FileSizeLimitExceededException(long actualBytes, long limitBytes) {
        super(String.format(
            "File size %d bytes exceeds limit of %d bytes",
            actualBytes,
            limitBytes
        ));
        this.actualBytes = actualBytes;
        this.limitBytes = limitBytes;
    }
}
