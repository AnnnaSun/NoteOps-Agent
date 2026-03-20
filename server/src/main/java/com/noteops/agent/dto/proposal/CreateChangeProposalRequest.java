package com.noteops.agent.dto.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateChangeProposalRequest(
    @JsonProperty("user_id")
    String userId
) {
}
