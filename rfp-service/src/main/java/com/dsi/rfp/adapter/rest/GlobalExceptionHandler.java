package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.CompletionException;

@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail handleLlmUnavailable(LlmUnavailableException ex) {
        log.warn(
            String.format(
                "event=llm.unavailable component=GlobalExceptionHandler status=503 message=%s",
                ex.getMessage()
            ),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.getMessage()
        );
    }

    @ExceptionHandler(CompletionException.class)
    ProblemDetail handleCompletionException(CompletionException ex) {
        log.warn(
            String.format(
                "event=async.fail component=GlobalExceptionHandler status=500 message=%s",
                ex.getMessage()
            ),
            ex
        );

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getMessage()
        );
    }
}
