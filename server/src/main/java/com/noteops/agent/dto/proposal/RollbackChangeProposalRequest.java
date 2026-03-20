package com.noteops.agent.dto.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RollbackChangeProposalRequest(
    @JsonProperty("user_id")
    String userId
) {
}
