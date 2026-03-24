package com.noteops.agent.repository.search;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SearchRepository {

    List<SearchCandidate> findByUserId(UUID userId);

    record SearchCandidate(
        UUID noteId,
        UUID userId,
        String title,
        String currentSummary,
        List<String> currentKeyPoints,
        List<String> currentTags,
        String sourceUri,
        String latestContentType,
        String latestContent,
        Instant updatedAt
    ) {
    }
}
