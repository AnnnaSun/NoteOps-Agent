package com.noteops.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ApiEnvelope<T>(
    boolean success,
    @JsonProperty("trace_id")
    String traceId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    T data,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    ApiError error,
    ApiMeta meta
) {

    public static <T> ApiEnvelope<T> success(String traceId, T data) {
        return new ApiEnvelope<>(true, traceId, data, null, new ApiMeta(Instant.now()));
    }

    public static <T> ApiEnvelope<T> error(String traceId, String code, String message) {
        return new ApiEnvelope<>(false, traceId, null, new ApiError(code, message), new ApiMeta(Instant.now()));
    }
}
