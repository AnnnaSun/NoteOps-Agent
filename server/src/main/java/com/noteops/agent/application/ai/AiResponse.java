package com.noteops.agent.application.ai;

public record AiResponse(
    AiProvider provider,
    String model,
    String rawText,
    int durationMs
) {
}
