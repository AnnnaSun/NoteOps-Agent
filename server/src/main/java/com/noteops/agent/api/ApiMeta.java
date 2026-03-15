package com.noteops.agent.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ApiMeta(@JsonProperty("server_time") Instant serverTime) {
}
