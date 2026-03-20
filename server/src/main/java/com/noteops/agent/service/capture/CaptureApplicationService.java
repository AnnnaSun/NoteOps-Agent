package com.noteops.agent.service.capture;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.capture.CaptureAnalysisResult;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import com.noteops.agent.model.capture.CaptureJobStatus;
import com.noteops.agent.repository.capture.CaptureJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.UUID;

@Service
public class CaptureApplicationService {

    private final CaptureJobRepository captureJobRepository;
    private final CaptureOrchestrator captureOrchestrator;

    public CaptureApplicationService(CaptureJobRepository captureJobRepository,
                                     CaptureOrchestrator captureOrchestrator) {
        this.captureJobRepository = captureJobRepository;
        this.captureOrchestrator = captureOrchestrator;
    }

    // 创建 Capture：做参数归一化、输入校验，然后进入编排流程。
    public CaptureView create(CreateCaptureCommand command) {
        UUID userId = parseUuid(command.userId(), "INVALID_USER_ID", "user_id must be a valid UUID");
        CaptureInputType sourceType = parseSourceType(command.sourceType());
        String rawText = blankToNull(command.rawText());
        String sourceUrl = blankToNull(command.sourceUrl());
        String titleHint = blankToNull(command.titleHint());
        validateInput(sourceType, rawText, sourceUrl);

        UUID captureJobId = captureOrchestrator.capture(
            new CaptureOrchestrator.CaptureCommand(
                UUID.randomUUID(),
                userId,
                sourceType,
                rawText,
                sourceUrl,
                titleHint
            )
        );
        return get(captureJobId.toString(), userId.toString());
    }

    // 根据 capture id 和 user id 读取最新的 Capture 视图。
    public CaptureView get(String captureIdRaw, String userIdRaw) {
        UUID captureId = parseUuid(captureIdRaw, "INVALID_CAPTURE_ID", "capture_job_id must be a valid UUID");
        UUID userId = parseUuid(userIdRaw, "INVALID_USER_ID", "user_id must be a valid UUID");
        return captureJobRepository.findByIdAndUserId(captureId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAPTURE_NOT_FOUND", "capture not found"));
    }

    private void validateInput(CaptureInputType sourceType, String rawText, String sourceUrl) {
        if (sourceType == CaptureInputType.TEXT && rawText == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_RAW_TEXT", "raw_text is required for TEXT capture");
        }
        if (sourceType == CaptureInputType.URL) {
            if (sourceUrl == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_SOURCE_URL", "source_url is required for URL capture");
            }
            try {
                new URI(sourceUrl);
            } catch (URISyntaxException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL", "source_url must be a valid URI");
            }
        }
    }

    private CaptureInputType parseSourceType(String rawValue) {
        try {
            return CaptureInputType.valueOf(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_TYPE", "source_type must be TEXT or URL");
        }
    }

    private UUID parseUuid(String rawValue, String errorCode, String message) {
        try {
            return UUID.fromString(rawValue);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CreateCaptureCommand(
        String userId,
        String sourceType,
        String rawText,
        String sourceUrl,
        String titleHint
    ) {
    }

    public record CaptureView(
        UUID captureJobId,
        CaptureInputType sourceType,
        CaptureJobStatus status,
        UUID noteId,
        CaptureFailureReason failureReason,
        CaptureAnalysisResult analysisPreview,
        Instant createdAt,
        Instant updatedAt,
        UUID traceId
    ) {
    }
}
