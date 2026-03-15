package com.noteops.agent.persistence.trace;

import java.util.Map;
import java.util.UUID;

public interface ToolInvocationLogRepository {

    void append(UUID userId,
                UUID traceId,
                String toolName,
                String status,
                Map<String, Object> inputDigest,
                Map<String, Object> outputDigest,
                Integer latencyMs,
                String errorCode,
                String errorMessage);
}
