package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.exception.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(TableExtractionUnavailableException.class)
    ProblemDetail handleTableExtractionUnavailable(TableExtractionUnavailableException ex) {
        log.warn(
            "event=table.extraction.unavailable component=GlobalExceptionHandler status=503 message={}",
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

    @ExceptionHandler(RulePackLoadException.class)
    ProblemDetail handleRulePackLoad(RulePackLoadException ex) {
        log.error(
            "event=rulepack.load.failed component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }

    @ExceptionHandler(RulePackEvaluationException.class)
    ProblemDetail handleRulePackEvaluation(RulePackEvaluationException ex) {
        log.error(
            "event=rulepack.evaluation.failed component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }

    @ExceptionHandler(RulePackExecutionException.class)
    ProblemDetail handleRulePackExecution(RulePackExecutionException ex) {
        log.error(
            "event=rulepack.execution.failed component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DocumentEncryptedException.class)
    ProblemDetail handleEncrypted(DocumentEncryptedException ex) {
        log.warn(
            "event=document.encrypted component=GlobalExceptionHandler status=422 message={}",
            ex.getMessage(),
            ex
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
            ex.getMessage(),
            ex
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
            ex.getMessage(),
            ex
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
            ex.getMessage(),
            ex
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
            ex.getMessage(),
            ex
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
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            String.format("File not found: %s", ex.getMessage())
        );
    }

    @ExceptionHandler(RfpSchemaLoadException.class)
    ProblemDetail handleSchemaLoad(RfpSchemaLoadException ex) {
        log.error(
            "event=schema.load.failed component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn(
            "event=resource.not.found component=GlobalExceptionHandler status=404 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        log.warn(
            "event=entity.not.found component=GlobalExceptionHandler status=404 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
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

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn(
            "event=access.denied component=GlobalExceptionHandler status=403 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            ex.getMessage()
        );
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn(
            "event=auth.failed component=GlobalExceptionHandler status=401 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            ex.getMessage()
        );
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ProblemDetail handleUsernameExists(UsernameAlreadyExistsException ex) {
        log.warn(
            "event=username.conflict component=GlobalExceptionHandler status=409 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.getMessage()
        );
    }

    @ExceptionHandler(JwtValidationException.class)
    ProblemDetail handleJwtValidation(JwtValidationException ex) {
        log.warn(
            "event=jwt.invalid component=GlobalExceptionHandler status=401 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn(
            "event=validation.failed component=GlobalExceptionHandler status=400 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
    }

    @ExceptionHandler(SystemIoException.class)
    ProblemDetail handleSystemIo(SystemIoException ex) {
        log.error(
            "event=system.io component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityException ex) {
        log.error(
            "event=data.integrity component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleException(Exception ex) {
        log.error(
            "event=exception component=GlobalExceptionHandler status=500 message={}",
            ex.getMessage(),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }
}
