package com.noteops.agent.service.ai;

import java.util.Map;
import java.util.UUID;

public record AiRequest(
    UUID userId,
    UUID traceId,
    String routeKey,
    String requestType,
    String toolName,
    String systemPrompt,
    String userPrompt,
    AiResponseMode responseMode,
    Map<String, Object> responseSchema,
    Map<String, Object> inputMetadata,
    AiProvider providerOverride,
    String modelOverride
) {

    public AiRequest {
        responseMode = responseMode == null ? AiResponseMode.TEXT : responseMode;
        responseSchema = responseSchema == null ? Map.of() : Map.copyOf(responseSchema);
        inputMetadata = inputMetadata == null ? Map.of() : Map.copyOf(inputMetadata);
        routeKey = blankToNull(routeKey);
        requestType = blankToNull(requestType);
        toolName = blankToNull(toolName);
        systemPrompt = blankToNull(systemPrompt);
        userPrompt = blankToNull(userPrompt);
        modelOverride = blankToNull(modelOverride);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
