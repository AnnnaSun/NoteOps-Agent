package com.noteops.agent.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String traceId;

    public ApiException(HttpStatus httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, null);
    }

    public ApiException(HttpStatus httpStatus, String errorCode, String message, String traceId) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public String traceId() {
        return traceId;
    }
}
