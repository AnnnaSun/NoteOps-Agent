package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.service.note.NoteQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IdeaAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(IdeaAssessmentService.class);

    private final IdeaRepository ideaRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final IdeaAgent ideaAgent;
    private final Clock clock;

    @Autowired
    public IdeaAssessmentService(IdeaRepository ideaRepository,
                                 NoteRepository noteRepository,
                                 AgentTraceRepository agentTraceRepository,
                                 UserActionEventRepository userActionEventRepository,
                                 ToolInvocationLogRepository toolInvocationLogRepository,
                                 IdeaAgent ideaAgent) {
        this(
            ideaRepository,
            noteRepository,
            agentTraceRepository,
            userActionEventRepository,
            toolInvocationLogRepository,
            ideaAgent,
            Clock.systemUTC()
        );
    }

    IdeaAssessmentService(IdeaRepository ideaRepository,
                          NoteRepository noteRepository,
                          AgentTraceRepository agentTraceRepository,
                          UserActionEventRepository userActionEventRepository,
                          ToolInvocationLogRepository toolInvocationLogRepository,
                          IdeaAgent ideaAgent,
                          Clock clock) {
        this.ideaRepository = ideaRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.ideaAgent = ideaAgent;
        this.clock = clock;
    }

    @Transactional
    // 执行 Idea assess：校验状态、生成结构化 assessment、落库并补齐 trace / event / tool log。
    public IdeaAssessmentCommandResult assess(AssessIdeaCommand command) {
        UUID ideaId = parseUuid(command.ideaId(), "INVALID_IDEA_ID", "idea_id must be a valid UUID");
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        IdeaRepository.IdeaRecord idea = ideaRepository.findByIdAndUserId(ideaId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IDEA_NOT_FOUND", "idea not found"));
        validateAssessable(idea);
        NoteQueryService.NoteDetailView sourceNote = resolveSourceNote(idea);

        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("idea_id", idea.id());
        traceState.put("from_status", idea.status().name());
        traceState.put("to_status", IdeaStatus.ASSESSED.name());
        if (idea.sourceNoteId() != null) {
            traceState.put("source_note_id", idea.sourceNoteId());
        }

        UUID traceId = agentTraceRepository.create(
            userId,
            "IDEA_ASSESS",
            "Assess idea " + idea.id(),
            "IDEA",
            idea.id(),
            List.of("idea-assessment-worker"),
            traceState
        );
        long startedAtNanos = System.nanoTime();
        log.info(
            "module=IdeaAssessmentService action=idea_assess_start trace_id={} user_id={} idea_id={} source_note_id={} from_status={} result=RUNNING",
            traceId,
            userId,
            idea.id(),
            idea.sourceNoteId(),
            idea.status().name()
        );

        try {
            IdeaAssessmentResult assessmentResult = ideaAgent.assess(new AssessIdeaRequest(
                userId,
                traceId,
                idea.id(),
                idea.title(),
                idea.rawDescription(),
                idea.sourceNoteId(),
                sourceNote == null ? null : sourceNote.title(),
                sourceNote == null ? null : sourceNote.currentSummary(),
                sourceNote == null ? List.of() : sourceNote.currentKeyPoints()
            ));
            validateAssessmentResult(assessmentResult);

            IdeaRepository.IdeaRecord updated = ideaRepository.updateAssessment(
                idea.id(),
                userId,
                assessmentResult,
                IdeaStatus.ASSESSED
            );
            int durationMs = toLatencyMs((System.nanoTime() - startedAtNanos) / 1_000_000L);

            toolInvocationLogRepository.append(
                userId,
                traceId,
                "idea.assess",
                "COMPLETED",
                toolInputDigest(idea, sourceNote),
                toolOutputDigest(updated.assessmentResult()),
                durationMs,
                null,
                null
            );
            userActionEventRepository.append(
                userId,
                "IDEA_ASSESSED",
                "IDEA",
                updated.id(),
                traceId,
                Map.of(
                    "from_status", idea.status().name(),
                    "to_status", updated.status().name(),
                    "source_mode", updated.sourceMode().name()
                )
            );

            Map<String, Object> completedState = new LinkedHashMap<>(traceState);
            completedState.put("result", "COMPLETED");
            completedState.put("assessment_fields", List.of(
                "problem_statement",
                "target_user",
                "core_hypothesis",
                "mvp_validation_path",
                "next_actions"
            ));
            agentTraceRepository.markCompleted(traceId, "Assessed idea " + updated.id(), completedState);

            log.info(
                "module=IdeaAssessmentService action=idea_assess_success trace_id={} user_id={} idea_id={} to_status={} result=COMPLETED duration_ms={}",
                traceId,
                userId,
                updated.id(),
                updated.status().name(),
                durationMs
            );
            return new IdeaAssessmentCommandResult(updated, traceId.toString());
        } catch (Exception exception) {
            int durationMs = toLatencyMs((System.nanoTime() - startedAtNanos) / 1_000_000L);
            String errorCode = exception instanceof ApiException apiException ? apiException.errorCode() : "IDEA_ASSESS_FAILED";
            String errorMessage = exception.getMessage();
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "idea.assess",
                "FAILED",
                toolInputDigest(idea, sourceNote),
                Map.of(),
                durationMs,
                errorCode,
                errorMessage
            );
            userActionEventRepository.append(
                userId,
                "IDEA_ASSESS_FAILED",
                "IDEA",
                idea.id(),
                traceId,
                Map.of(
                    "from_status", idea.status().name(),
                    "error_code", errorCode,
                    "error_message", errorMessage
                )
            );
            Map<String, Object> failedState = new LinkedHashMap<>(traceState);
            failedState.put("result", "FAILED");
            failedState.put("error_code", errorCode);
            failedState.put("error_message", errorMessage);
            agentTraceRepository.markFailed(traceId, "Failed to assess idea " + idea.id(), failedState);
            log.warn(
                "module=IdeaAssessmentService action=idea_assess_fail trace_id={} user_id={} idea_id={} result=FAILED error_code={} error_message={} duration_ms={}",
                traceId,
                userId,
                idea.id(),
                errorCode,
                errorMessage,
                durationMs
            );
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "IDEA_ASSESS_FAILED", "idea assess failed");
        }
    }

    private void validateAssessable(IdeaRepository.IdeaRecord idea) {
        if (idea.status() != IdeaStatus.CAPTURED) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEA_STATUS_NOT_ASSESSABLE", "only CAPTURED ideas can be assessed");
        }
    }

    private NoteQueryService.NoteDetailView resolveSourceNote(IdeaRepository.IdeaRecord idea) {
        if (idea.sourceNoteId() == null) {
            return null;
        }
        return noteRepository.findByIdAndUserId(idea.sourceNoteId(), idea.userId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));
    }

    private void validateAssessmentResult(IdeaAssessmentResult result) {
        if (result == null
            || isBlank(result.problemStatement())
            || isBlank(result.targetUser())
            || isBlank(result.coreHypothesis())
            || result.mvpValidationPath() == null
            || result.mvpValidationPath().isEmpty()
            || result.nextActions() == null
            || result.nextActions().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "IDEA_ASSESSMENT_INVALID", "assessment_result is incomplete");
        }
    }

    private Map<String, Object> toolInputDigest(IdeaRepository.IdeaRecord idea, NoteQueryService.NoteDetailView sourceNote) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("idea_id", idea.id());
        value.put("title", idea.title());
        value.put("source_mode", idea.sourceMode().name());
        if (idea.rawDescription() != null) {
            value.put("raw_description_present", true);
        }
        if (sourceNote != null) {
            value.put("source_note_id", sourceNote.id());
            value.put("source_note_title", sourceNote.title());
            value.put("source_note_key_point_count", sourceNote.currentKeyPoints().size());
        }
        return value;
    }

    private Map<String, Object> toolOutputDigest(IdeaAssessmentResult result) {
        return Map.of(
            "problem_statement_present", !isBlank(result.problemStatement()),
            "target_user_present", !isBlank(result.targetUser()),
            "core_hypothesis_present", !isBlank(result.coreHypothesis()),
            "mvp_validation_path_count", result.mvpValidationPath().size(),
            "next_actions_count", result.nextActions().size()
        );
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int toLatencyMs(long durationMs) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs));
    }

    public record AssessIdeaCommand(
        String ideaId,
        String userId
    ) {
    }

    public record IdeaAssessmentCommandResult(
        IdeaRepository.IdeaRecord idea,
        String traceId
    ) {
    }
}
