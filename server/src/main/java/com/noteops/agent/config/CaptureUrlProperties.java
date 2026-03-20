package com.noteops.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "noteops.capture.url")
public record CaptureUrlProperties(
    Duration connectTimeout,
    Duration readTimeout,
    int maxResponseBytes,
    int maxTextLength,
    String userAgent
) {

    public CaptureUrlProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        maxResponseBytes = maxResponseBytes <= 0 ? 200_000 : maxResponseBytes;
        maxTextLength = maxTextLength <= 0 ? 4_000 : maxTextLength;
        userAgent = userAgent == null || userAgent.isBlank()
            ? "NoteOps-Agent/0.0.1"
            : userAgent.trim();
    }
}
