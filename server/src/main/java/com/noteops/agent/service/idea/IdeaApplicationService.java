package com.noteops.agent.service.idea;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IdeaApplicationService {

    private static final Logger log = LoggerFactory.getLogger(IdeaApplicationService.class);

    private final IdeaRepository ideaRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final UserActionEventRepository userActionEventRepository;

    @Autowired
    public IdeaApplicationService(IdeaRepository ideaRepository,
                                  NoteRepository noteRepository,
                                  AgentTraceRepository agentTraceRepository,
                                  UserActionEventRepository userActionEventRepository) {
        this.ideaRepository = ideaRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    @Transactional
    // 创建 Idea：校验来源模式、绑定 Note、落库并补齐最小 trace / event / log。
    public IdeaCommandResult create(CreateIdeaCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        IdeaSourceMode sourceMode = parseSourceMode(command.sourceMode());
        UUID sourceNoteId = resolveSourceNoteId(userId, sourceMode, command.sourceNoteId());
        String title = requireTitle(command.title());
        String rawDescription = blankToNull(command.rawDescription());

        log.info(
            "action=idea_create_start user_id={} source_mode={} source_note_id={} title={}",
            userId,
            sourceMode.name(),
            sourceNoteId,
            title
        );

        IdeaRepository.IdeaRecord idea = sourceMode == IdeaSourceMode.FROM_NOTE
            ? ideaRepository.createFromNote(
                userId,
                sourceNoteId,
                title,
                rawDescription,
                IdeaStatus.CAPTURED,
                IdeaAssessmentResult.empty()
            )
            : ideaRepository.createManual(
                userId,
                title,
                rawDescription,
                IdeaStatus.CAPTURED,
                IdeaAssessmentResult.empty()
            );

        Map<String, Object> traceState = new LinkedHashMap<>();
        traceState.put("idea_id", idea.id());
        traceState.put("source_mode", idea.sourceMode().name());
        traceState.put("status", idea.status().name());
        if (idea.sourceNoteId() != null) {
            traceState.put("source_note_id", idea.sourceNoteId());
        }

        UUID traceId = agentTraceRepository.create(
            userId,
            "IDEA_CREATE",
            "Create idea " + idea.id(),
            "IDEA",
            idea.id(),
            List.of("idea-worker"),
            traceState
        );

        String eventType = idea.sourceMode() == IdeaSourceMode.FROM_NOTE ? "IDEA_DERIVED_FROM_NOTE" : "IDEA_CREATED";
        userActionEventRepository.append(
            userId,
            eventType,
            "IDEA",
            idea.id(),
            traceId,
            traceState
        );

        agentTraceRepository.markCompleted(
            traceId,
            "Created idea " + idea.id(),
            Map.of(
                "idea_id", idea.id(),
                "source_mode", idea.sourceMode().name(),
                "status", idea.status().name()
            )
        );

        log.info(
            "action=idea_create_success trace_id={} user_id={} idea_id={} source_mode={} result=CREATED",
            traceId,
            userId,
            idea.id(),
            idea.sourceMode().name()
        );

        return new IdeaCommandResult(idea, traceId.toString());
    }

    private IdeaSourceMode parseSourceMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_MODE", "source_mode must be provided");
        }
        try {
            String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
            IdeaSourceMode sourceMode = IdeaSourceMode.valueOf(normalized);
            if (sourceMode == IdeaSourceMode.FROM_TREND) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_MODE_NOT_ALLOWED",
                    "source_mode FROM_TREND is internal-only"
                );
            }
            return sourceMode;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_MODE", "source_mode is invalid");
        }
    }

    private UUID resolveSourceNoteId(UUID userId, IdeaSourceMode sourceMode, String rawSourceNoteId) {
        String normalized = blankToNull(rawSourceNoteId);
        if (sourceMode == IdeaSourceMode.FROM_NOTE) {
            if (normalized == null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_NOTE_ID_REQUIRED",
                    "source_note_id is required when source_mode is FROM_NOTE"
                );
            }
            UUID noteId = parseUuid(normalized, "INVALID_SOURCE_NOTE_ID", "source_note_id must be a valid UUID");
            noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND", "note not found"));
            return noteId;
        }

        if (normalized != null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "SOURCE_NOTE_ID_NOT_ALLOWED",
                "source_note_id must be empty when source_mode is MANUAL"
            );
        }
        return null;
    }

    private String requireTitle(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED", "title must be provided");
        }
        return normalized;
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record CreateIdeaCommand(
        String userId,
        String sourceMode,
        String sourceNoteId,
        String title,
        String rawDescription
    ) {
    }

    public record IdeaCommandResult(
        IdeaRepository.IdeaRecord idea,
        String traceId
    ) {
    }
}
