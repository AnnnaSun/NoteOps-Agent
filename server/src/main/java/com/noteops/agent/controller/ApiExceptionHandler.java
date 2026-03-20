package com.noteops.agent.controller;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.dto.ApiEnvelope;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleApiException(ApiException exception) {
        return ResponseEntity
            .status(exception.httpStatus())
            .body(ApiEnvelope.error(exception.traceId(), exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiEnvelope.error(null, "MISSING_REQUEST_PARAMETER", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnexpectedException(Exception exception) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiEnvelope.error(null, "INTERNAL_SERVER_ERROR", exception.getMessage()));
    }
}
