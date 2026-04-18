package com.noteops.agent.dto.proposal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RejectChangeProposalRequest(
    @JsonProperty("user_id")
    String userId
) {
}
