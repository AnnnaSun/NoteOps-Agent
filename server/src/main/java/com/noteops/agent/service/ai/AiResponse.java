package com.noteops.agent.service.ai;

public record AiResponse(
    AiProvider provider,
    String model,
    String rawText,
    int durationMs
) {
}
