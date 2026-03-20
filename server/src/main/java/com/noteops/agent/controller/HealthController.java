package com.noteops.agent.controller;

import com.noteops.agent.dto.ApiEnvelope;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiEnvelope<Map<String, String>> health() {
        return ApiEnvelope.success(null, Map.of(
            "service", "noteops-agent-server",
            "status", "UP"
        ));
    }
}
