package com.noteops.agent.api.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RollbackChangeProposalRequest(
    @JsonProperty("user_id")
    String userId
) {
}
