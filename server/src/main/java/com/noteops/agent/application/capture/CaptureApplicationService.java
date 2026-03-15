package com.noteops.agent.application.capture;

import com.noteops.agent.api.ApiException;
import com.noteops.agent.domain.capture.CaptureInputType;
import com.noteops.agent.domain.capture.CaptureJobStatus;
import com.noteops.agent.persistence.capture.CaptureJobRepository;
import com.noteops.agent.persistence.event.UserActionEventRepository;
import com.noteops.agent.persistence.note.NoteRepository;
import com.noteops.agent.persistence.trace.AgentTraceRepository;
import com.noteops.agent.persistence.trace.ToolInvocationLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CaptureApplicationService {

    private final CaptureJobRepository captureJobRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    public CaptureApplicationService(CaptureJobRepository captureJobRepository,
                                     NoteRepository noteRepository,
                                     AgentTraceRepository agentTraceRepository,
                                     ToolInvocationLogRepository toolInvocationLogRepository,
                                     UserActionEventRepository userActionEventRepository) {
        this.captureJobRepository = captureJobRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    public CaptureView create(CreateCaptureCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        CaptureInputType inputType = parseInputType(command.inputType());
        validateInput(command, inputType);

        UUID captureId = UUID.randomUUID();
        captureJobRepository.createReceived(captureId, userId, inputType, blankToNull(command.sourceUri()), blankToNull(command.rawInput()));

        // Phase 1 先把治理链路打通：每次 capture 都先建 trace 和最小 user event。
        UUID traceId = agentTraceRepository.create(userId, "Capture " + inputType.name() + " into Note", captureId, List.of("capture-worker"));
        userActionEventRepository.append(
            userId,
            "CAPTURE_SUBMITTED",
            "CAPTURE_JOB",
            captureId,
            traceId,
            Map.of("input_type", inputType.name())
        );

        try {
            // 这里按 EXTRACTING -> ANALYZING -> CONSOLIDATING 推进状态机，暂不引入异步 worker。
            Map<String, Object> extraction = extract(command, inputType);
            captureJobRepository.updateExtraction(captureId, extraction);
            toolInvocationLogRepository.append(
                userId,
                traceId,
                extractionToolName(inputType),
                "COMPLETED",
                Map.of("input_type", inputType.name()),
                Map.of("excerpt", excerpt((String) extraction.get("clean_text"))),
                1,
                null,
                null
            );

            Map<String, Object> analysis = analyze(extraction, inputType);
            captureJobRepository.updateAnalysis(captureId, analysis);
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "capture.summary-analyzer",
                "COMPLETED",
                Map.of("title", analysis.get("title")),
                Map.of("key_point_count", ((List<?>) analysis.get("key_points")).size()),
                1,
                null,
                null
            );

            // Note 只保存当前解释层；原始输入和清洗结果继续落到 note_contents。
            NoteRepository.NoteCreationResult noteCreation = noteRepository.create(
                userId,
                (String) analysis.get("title"),
                (String) analysis.get("summary"),
                castKeyPoints(analysis.get("key_points")),
                blankToNull(command.sourceUri()),
                (String) extraction.get("raw_text"),
                (String) extraction.get("clean_text"),
                sourceSnapshot(inputType, command.sourceUri()),
                analysis
            );

            Map<String, Object> consolidation = new LinkedHashMap<>();
            consolidation.put("note_id", noteCreation.noteId());
            consolidation.put("note_content_id", noteCreation.contentId());
            consolidation.put("trace_id", traceId);
            captureJobRepository.updateConsolidation(captureId, consolidation);
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "capture.note-consolidator",
                "COMPLETED",
                Map.of("capture_job_id", captureId),
                Map.of("note_id", noteCreation.noteId()),
                1,
                null,
                null
            );

            userActionEventRepository.append(
                userId,
                "NOTE_CREATED_FROM_CAPTURE",
                "NOTE",
                noteCreation.noteId(),
                traceId,
                Map.of("capture_job_id", captureId)
            );
            agentTraceRepository.markCompleted(
                traceId,
                "Created note " + noteCreation.noteId(),
                Map.of("capture_job_id", captureId, "note_id", noteCreation.noteId(), "status", CaptureJobStatus.COMPLETED.name())
            );
            captureJobRepository.markCompleted(captureId);
        } catch (RuntimeException exception) {
            captureJobRepository.markFailed(captureId, "CAPTURE_PROCESSING_FAILED", exception.getMessage());
            agentTraceRepository.markFailed(
                traceId,
                exception.getMessage(),
                Map.of("capture_job_id", captureId, "status", CaptureJobStatus.FAILED.name())
            );
            toolInvocationLogRepository.append(
                userId,
                traceId,
                "capture.pipeline",
                "FAILED",
                Map.of("capture_job_id", captureId),
                Map.of(),
                1,
                "CAPTURE_PROCESSING_FAILED",
                exception.getMessage()
            );
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CAPTURE_PROCESSING_FAILED", exception.getMessage(), traceId.toString());
        }

        return get(captureId.toString(), userId.toString());
    }

    public CaptureView get(String captureIdRaw, String userIdRaw) {
        UUID captureId = parseUuid(captureIdRaw, "INVALID_CAPTURE_ID", "capture id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");

        return captureJobRepository.findByIdAndUserId(captureId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAPTURE_NOT_FOUND", "capture not found"));
    }

    private void validateInput(CreateCaptureCommand command, CaptureInputType inputType) {
        if (inputType == CaptureInputType.TEXT && isBlank(command.rawInput())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_RAW_INPUT", "raw_input is required for TEXT capture");
        }
        if (inputType == CaptureInputType.URL) {
            if (isBlank(command.sourceUri())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_SOURCE_URI", "source_uri is required for URL capture");
            }
            try {
                new URI(command.sourceUri());
            } catch (URISyntaxException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URI", "source_uri must be a valid URI");
            }
        }
    }

    private Map<String, Object> extract(CreateCaptureCommand command, CaptureInputType inputType) {
        if (inputType == CaptureInputType.TEXT) {
            String cleaned = command.rawInput().trim();
            // TEXT capture 直接复用原文，不做会改变原意的预处理。
            Map<String, Object> extracted = new LinkedHashMap<>();
            extracted.put("raw_text", command.rawInput());
            extracted.put("clean_text", cleaned);
            extracted.put("extraction_mode", "INLINE_TEXT");
            if (!isBlank(command.sourceUri())) {
                extracted.put("source_uri", blankToNull(command.sourceUri()));
            }
            return extracted;
        }

        // URL 在 M3 只要求真实状态推进和存储结构，提取质量后置。
        String placeholder = "Placeholder extraction for " + command.sourceUri()
            + ". Phase 1 keeps URL ingestion stateful and traceable while extraction quality remains deferred.";
        return Map.of(
            "raw_text", command.sourceUri(),
            "clean_text", placeholder,
            "source_uri", command.sourceUri(),
            "extraction_mode", "URL_PLACEHOLDER"
        );
    }

    private Map<String, Object> analyze(Map<String, Object> extraction, CaptureInputType inputType) {
        String cleanText = (String) extraction.get("clean_text");
        List<String> keyPoints = extractKeyPoints(cleanText);
        String title = deriveTitle(cleanText, inputType, (String) extraction.get("source_uri"));
        return Map.of(
            "title", title,
            "summary", summarize(cleanText),
            "key_points", keyPoints
        );
    }

    private List<String> extractKeyPoints(String text) {
        return List.of(text.split("(?<=[.!?。！？])\\s+"))
            .stream()
            .map(String::trim)
            .filter(segment -> !segment.isBlank())
            .limit(3)
            .toList();
    }

    private String summarize(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }

    private String deriveTitle(String cleanText, CaptureInputType inputType, String sourceUri) {
        String seed = cleanText.strip().replaceAll("\\s+", " ");
        if (seed.isEmpty() && inputType == CaptureInputType.URL) {
            return "Captured URL: " + sourceUri;
        }
        String title = seed.length() <= 80 ? seed : seed.substring(0, 80) + "...";
        if (inputType == CaptureInputType.URL && sourceUri != null && !title.startsWith("Captured URL")) {
            return "Captured URL: " + title;
        }
        return title;
    }

    private String extractionToolName(CaptureInputType inputType) {
        return inputType == CaptureInputType.TEXT ? "capture.text-input-normalizer" : "capture.url-placeholder-extractor";
    }

    @SuppressWarnings("unchecked")
    private List<String> castKeyPoints(Object keyPoints) {
        return (List<String>) keyPoints;
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private CaptureInputType parseInputType(String rawValue) {
        try {
            return CaptureInputType.valueOf(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT_TYPE", "input_type must be TEXT or URL");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= 64 ? text : text.substring(0, 64) + "...";
    }

    private Map<String, Object> sourceSnapshot(CaptureInputType inputType, String sourceUri) {
        // source_snapshot 只记录来源上下文，避免把解释层字段混进原始内容层。
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("capture_input_type", inputType.name());
        if (!isBlank(sourceUri)) {
            snapshot.put("source_uri", sourceUri);
        }
        return snapshot;
    }

    public record CreateCaptureCommand(String userId, String inputType, String rawInput, String sourceUri) {
    }

    public record CaptureView(
        UUID id,
        UUID userId,
        CaptureInputType inputType,
        String sourceUri,
        String rawInput,
        CaptureJobStatus status,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        String noteId,
        String traceId
    ) {
    }
}
