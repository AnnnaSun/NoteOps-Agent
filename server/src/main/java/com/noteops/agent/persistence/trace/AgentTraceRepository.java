package com.noteops.agent.persistence.trace;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AgentTraceRepository {

    UUID create(UUID userId, String goal, UUID captureId, List<String> workerSequence);

    void markCompleted(UUID traceId, String resultSummary, Map<String, Object> orchestratorState);

    void markFailed(UUID traceId, String resultSummary, Map<String, Object> orchestratorState);
}
