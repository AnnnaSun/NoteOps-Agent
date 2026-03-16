package com.noteops.agent.api.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateChangeProposalRequest(
    @JsonProperty("user_id")
    String userId
) {
}
