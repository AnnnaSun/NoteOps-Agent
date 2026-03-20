package com.noteops.agent.dto.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplyChangeProposalRequest(
    @JsonProperty("user_id")
    String userId
) {
}
