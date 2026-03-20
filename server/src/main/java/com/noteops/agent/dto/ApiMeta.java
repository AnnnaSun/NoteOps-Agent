package com.noteops.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ApiMeta(@JsonProperty("server_time") Instant serverTime) {
}
