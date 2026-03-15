package com.noteops.agent.persistence.trace;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AgentTraceRepository {

    default UUID create(UUID userId, String goal, UUID captureId, List<String> workerSequence) {
        return create(
            userId,
            "CAPTURE",
            goal,
            "CAPTURE_JOB",
            captureId,
            workerSequence,
            Map.of("capture_job_id", captureId)
        );
    }

    UUID create(UUID userId,
                String entryType,
                String goal,
                String rootEntityType,
                UUID rootEntityId,
                List<String> workerSequence,
                Map<String, Object> orchestratorState);

    void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState);

    void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState);
}
