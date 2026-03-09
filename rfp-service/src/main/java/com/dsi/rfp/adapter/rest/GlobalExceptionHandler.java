package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.NoSuchFileException;
import java.util.concurrent.CompletionException;

@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail handleLlmUnavailable(LlmUnavailableException ex) {
        log.warn(
            "event=llm.unavailable component=GlobalExceptionHandler status=503 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.getMessage()
        );
    }

    @ExceptionHandler(LlmResponseParseException.class)
    ProblemDetail handleLlmResponseParse(LlmResponseParseException ex) {
        log.warn(
            "event=llm.parse.failed component=GlobalExceptionHandler status=502 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DocumentEncryptedException.class)
    ProblemDetail handleEncrypted(DocumentEncryptedException ex) {
        log.warn(
            "event=document.encrypted component=GlobalExceptionHandler status=422 message={}",
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DocumentXfaException.class)
    ProblemDetail handleXfa(DocumentXfaException ex) {
        log.warn(
            "event=document.xfa component=GlobalExceptionHandler status=422 message={}",
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DocumentUnsupportedTypeException.class)
    ProblemDetail handleUnsupportedType(DocumentUnsupportedTypeException ex) {
        log.warn(
            "event=document.unsupported component=GlobalExceptionHandler status=415 message={}",
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DocumentValidationException.class)
    ProblemDetail handleValidation(DocumentValidationException ex) {
        log.warn(
            "event=document.validation component=GlobalExceptionHandler status=422 errorCode={} message={}",
            ex.getErrorCode(),
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.getMessage()
        );
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    ProblemDetail handleFileSizeLimit(FileSizeLimitExceededException ex) {
        log.warn(
            "event=file.too.large component=GlobalExceptionHandler status=413 message={}",
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DocumentCorruptException.class)
    ProblemDetail handleCorrupt(DocumentCorruptException ex) {
        log.warn(
            "event=document.corrupt component=GlobalExceptionHandler status=422 message={}",
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.getMessage()
        );
    }

    @ExceptionHandler(NoSuchFileException.class)
    ProblemDetail handleNoSuchFile(NoSuchFileException ex) {
        log.warn(
            "event=file.not.found component=GlobalExceptionHandler status=404 file={}",
            ex.getMessage()
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            String.format("File not found: %s", ex.getMessage())
        );
    }

    @ExceptionHandler(CompletionException.class)
    ProblemDetail handleCompletionException(CompletionException ex) {
        log.warn(
            "event=async.fail component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }
}
