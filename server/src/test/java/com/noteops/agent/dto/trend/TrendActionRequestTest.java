package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrendActionRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesUserIdActionAndOperatorNote() throws Exception {
        TrendActionRequest request = new TrendActionRequest("11111111-1111-1111-1111-111111111111", "SAVE_AS_NOTE", "keep source lineage");

        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(payload.get("user_id").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(payload.get("action").asText()).isEqualTo("SAVE_AS_NOTE");
        assertThat(payload.get("operator_note").asText()).isEqualTo("keep source lineage");
    }
}
