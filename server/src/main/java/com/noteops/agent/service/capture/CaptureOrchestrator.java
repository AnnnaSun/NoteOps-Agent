package com.noteops.agent.service.capture;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.config.AiProperties;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import com.noteops.agent.model.capture.CaptureJobStatus;
import com.noteops.agent.repository.capture.CaptureJobRepository;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CaptureOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CaptureOrchestrator.class);

    private final CaptureJobRepository captureJobRepository;
    private final CaptureExtractor captureExtractor;
    private final CaptureAnalysisWorker captureAnalysisWorker;
    private final CaptureNoteConsolidator captureNoteConsolidator;
    private final AiProperties aiProperties;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;

    public CaptureOrchestrator(CaptureJobRepository captureJobRepository,
                               CaptureExtractor captureExtractor,
                               CaptureAnalysisWorker captureAnalysisWorker,
                               CaptureNoteConsolidator captureNoteConsolidator,
                               AiProperties aiProperties,
                               AgentTraceRepository agentTraceRepository,
                               ToolInvocationLogRepository toolInvocationLogRepository,
                               UserActionEventRepository userActionEventRepository) {
        this.captureJobRepository = captureJobRepository;
        this.captureExtractor = captureExtractor;
        this.captureAnalysisWorker = captureAnalysisWorker;
        this.captureNoteConsolidator = captureNoteConsolidator;
        this.aiProperties = aiProperties;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
    }

    // Capture 主编排入口：完成抽取、分析、落库和 trace/event 写入。
    public UUID capture(CaptureCommand command) {
        UUID captureJobId = command.captureJobId();
        captureJobRepository.createReceived(
            captureJobId,
            command.userId(),
            command.sourceType(),
            command.sourceUrl(),
            command.rawText()
        );
        log.info(
            "module=CaptureOrchestrator action=capture_job_created result=SUCCESS user_id={} capture_job_id={} source_type={} trace_id=null",
            command.userId(),
            captureJobId,
            command.sourceType()
        );

        UUID traceId = agentTraceRepository.create(
            command.userId(),
            "CAPTURE",
            "Capture " + command.sourceType().name() + " into Note",
            "CAPTURE_JOB",
            captureJobId,
            List.of("capture-extractor", "capture-analysis-worker", "capture-note-consolidator"),
            initialTraceState(captureJobId, command)
        );
        userActionEventRepository.append(
            command.userId(),
            "CAPTURE_SUBMITTED",
            "CAPTURE_JOB",
            captureJobId,
            traceId,
            eventPayload(
                "source_type", command.sourceType().name(),
                "title_hint", command.titleHint()
            )
        );

        try {
            CaptureExtractor.ExtractionResult extractionResult = extract(captureJobId, traceId, command);
            CaptureAnalysisWorker.AnalysisOutcome analysisOutcome = analyze(traceId, command, extractionResult);
            UUID noteId = consolidate(captureJobId, traceId, command, extractionResult, analysisOutcome);
            captureJobRepository.markCompleted(captureJobId);
            log.info(
                "module=CaptureOrchestrator action=capture_job_completed result=SUCCESS user_id={} capture_job_id={} trace_id={} note_id={}",
                command.userId(),
                captureJobId,
                traceId,
                noteId
            );
            agentTraceRepository.markCompleted(
                traceId,
                "Capture completed and created note " + noteId,
                completedTraceState(captureJobId, command, analysisOutcome, noteId)
            );
            log.info(
                "module=CaptureOrchestrator action=trace_write_completed result=SUCCESS user_id={} capture_job_id={} trace_id={} note_id={}",
                command.userId(),
                captureJobId,
                traceId,
                noteId
            );
            return captureJobId;
        } catch (CapturePipelineException exception) {
            failCapture(captureJobId, traceId, command, exception.failureReason(), exception.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, exception.failureReason().name(), exception.getMessage(), traceId.toString());
        } catch (RuntimeException exception) {
            failCapture(captureJobId, traceId, command, CaptureFailureReason.CONSOLIDATION_FAILED, exception.getMessage());
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                CaptureFailureReason.CONSOLIDATION_FAILED.name(),
                exception.getMessage(),
                traceId.toString()
            );
        }
    }

    // 抽取原始内容，成功后写 extraction 结果和工具日志。
    private CaptureExtractor.ExtractionResult extract(UUID captureJobId, UUID traceId, CaptureCommand command) {
        captureJobRepository.markExtracting(captureJobId);
        log.info(
            "module=CaptureOrchestrator action=capture_stage_start result=RUNNING user_id={} capture_job_id={} trace_id={} stage={}",
            command.userId(),
            captureJobId,
            traceId,
            CaptureJobStatus.EXTRACTING
        );
        long startedAt = System.nanoTime();
        try {
            CaptureExtractor.ExtractionResult extractionResult = captureExtractor.extract(
                new CaptureExtractor.ExtractionCommand(
                    command.sourceType(),
                    command.rawText(),
                    command.sourceUrl(),
                    command.titleHint()
                )
            );
            captureJobRepository.saveExtractionResult(captureJobId, extractionResult.toMap());
            int durationMs = durationMs(startedAt);
            toolInvocationLogRepository.append(
                command.userId(),
                traceId,
                extractionToolName(command.sourceType()),
                "SUCCESS",
                eventPayload(
                    "request_type", "CAPTURE_EXTRACTION",
                    "source_type", command.sourceType().name(),
                    "source_url", command.sourceUrl()
                ),
                eventPayload(
                    "result", "SUCCESS",
                    "page_title", extractionResult.pageTitle(),
                    "clean_text_excerpt", excerpt(extractionResult.cleanText())
                ),
                durationMs,
                null,
                null
            );
            log.info(
                "module=CaptureOrchestrator action=capture_stage_end result=SUCCESS user_id={} capture_job_id={} trace_id={} stage={} duration_ms={}",
                command.userId(),
                captureJobId,
                traceId,
                CaptureJobStatus.EXTRACTING,
                durationMs
            );
            return extractionResult;
        } catch (CapturePipelineException exception) {
            int durationMs = durationMs(startedAt);
            toolInvocationLogRepository.append(
                command.userId(),
                traceId,
                extractionToolName(command.sourceType()),
                "FAILED",
                eventPayload(
                    "request_type", "CAPTURE_EXTRACTION",
                    "source_type", command.sourceType().name(),
                    "source_url", command.sourceUrl()
                ),
                Map.of("result", "FAILED"),
                durationMs,
                exception.failureReason().name(),
                exception.getMessage()
            );
            log.warn(
                "module=CaptureOrchestrator action=capture_stage_end result=FAILED user_id={} capture_job_id={} trace_id={} stage={} duration_ms={} error_code={} error_message={}",
                command.userId(),
                captureJobId,
                traceId,
                CaptureJobStatus.EXTRACTING,
                durationMs,
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        }
    }

    // 调用 AI 分析并保存结构化结果，失败时直接上抛。
    private CaptureAnalysisWorker.AnalysisOutcome analyze(UUID traceId,
                                                          CaptureCommand command,
                                                          CaptureExtractor.ExtractionResult extractionResult) {
        captureJobRepository.markAnalyzing(command.captureJobId());
        log.info(
            "module=CaptureOrchestrator action=capture_stage_start result=RUNNING user_id={} capture_job_id={} trace_id={} stage={}",
            command.userId(),
            command.captureJobId(),
            traceId,
            CaptureJobStatus.ANALYZING
        );
        try {
            CaptureAnalysisWorker.AnalysisOutcome analysisOutcome = captureAnalysisWorker.analyze(
                new CaptureAnalysisClient.AnalyzeRequest(
                    command.userId(),
                    traceId,
                    command.sourceType(),
                    command.sourceUrl(),
                    command.titleHint(),
                    extractionResult.pageTitle(),
                    extractionResult.cleanText()
                )
            );
            captureJobRepository.saveAnalysisResult(command.captureJobId(), analysisOutcome.analysisResult());
            log.info(
                "module=CaptureOrchestrator action=capture_stage_end result=SUCCESS user_id={} capture_job_id={} trace_id={} stage={} duration_ms={} provider={} model={}",
                command.userId(),
                command.captureJobId(),
                traceId,
                CaptureJobStatus.ANALYZING,
                analysisOutcome.durationMs(),
                analysisOutcome.provider(),
                analysisOutcome.model()
            );
            return analysisOutcome;
        } catch (CapturePipelineException exception) {
            log.warn(
                "module=CaptureOrchestrator action=capture_stage_end result=FAILED user_id={} capture_job_id={} trace_id={} stage={} error_code={} error_message={}",
                command.userId(),
                command.captureJobId(),
                traceId,
                CaptureJobStatus.ANALYZING,
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        }
    }

    // 将抽取结果和分析结果合并写入 Note。
    private UUID consolidate(UUID captureJobId,
                             UUID traceId,
                             CaptureCommand command,
                             CaptureExtractor.ExtractionResult extractionResult,
                             CaptureAnalysisWorker.AnalysisOutcome analysisOutcome) {
        captureJobRepository.markConsolidating(captureJobId);
        log.info(
            "module=CaptureOrchestrator action=capture_stage_start result=RUNNING user_id={} capture_job_id={} trace_id={} stage={}",
            command.userId(),
            captureJobId,
            traceId,
            CaptureJobStatus.CONSOLIDATING
        );
        long startedAt = System.nanoTime();
        try {
            NoteRepository.NoteCreationResult noteCreationResult = captureNoteConsolidator.consolidate(
                command.userId(),
                command.sourceType(),
                command.sourceUrl(),
                command.titleHint(),
                extractionResult,
                analysisOutcome.analysisResult()
            );
            Map<String, Object> consolidationResult = new LinkedHashMap<>();
            consolidationResult.put("note_id", noteCreationResult.noteId());
            consolidationResult.put("note_content_id", noteCreationResult.contentId());
            captureJobRepository.saveConsolidationResult(captureJobId, consolidationResult);
            int durationMs = durationMs(startedAt);
            toolInvocationLogRepository.append(
                command.userId(),
                traceId,
                "capture.note-consolidator",
                "SUCCESS",
                Map.of(
                    "request_type", "CAPTURE_CONSOLIDATION",
                    "capture_job_id", captureJobId
                ),
                eventPayload(
                    "result", "SUCCESS",
                    "note_id", noteCreationResult.noteId(),
                    "note_content_id", noteCreationResult.contentId()
                ),
                durationMs,
                null,
                null
            );
            userActionEventRepository.append(
                command.userId(),
                "NOTE_CREATED_FROM_CAPTURE",
                "NOTE",
                noteCreationResult.noteId(),
                traceId,
                eventPayload(
                    "capture_job_id", captureJobId,
                    "source_type", command.sourceType().name()
                )
            );
            log.info(
                "module=CaptureOrchestrator action=note_created_from_capture result=SUCCESS user_id={} capture_job_id={} trace_id={} note_id={} duration_ms={}",
                command.userId(),
                captureJobId,
                traceId,
                noteCreationResult.noteId(),
                durationMs
            );
            log.info(
                "module=CaptureOrchestrator action=capture_stage_end result=SUCCESS user_id={} capture_job_id={} trace_id={} stage={} duration_ms={}",
                command.userId(),
                captureJobId,
                traceId,
                CaptureJobStatus.CONSOLIDATING,
                durationMs
            );
            return noteCreationResult.noteId();
        } catch (CapturePipelineException exception) {
            int durationMs = durationMs(startedAt);
            toolInvocationLogRepository.append(
                command.userId(),
                traceId,
                "capture.note-consolidator",
                "FAILED",
                Map.of(
                    "request_type", "CAPTURE_CONSOLIDATION",
                    "capture_job_id", captureJobId
                ),
                Map.of("result", "FAILED"),
                durationMs,
                exception.failureReason().name(),
                exception.getMessage()
            );
            log.warn(
                "module=CaptureOrchestrator action=capture_stage_end result=FAILED user_id={} capture_job_id={} trace_id={} stage={} duration_ms={} error_code={} error_message={}",
                command.userId(),
                captureJobId,
                traceId,
                CaptureJobStatus.CONSOLIDATING,
                durationMs,
                exception.failureReason().name(),
                exception.getMessage()
            );
            throw exception;
        }
    }

    private void failCapture(UUID captureJobId,
                             UUID traceId,
                             CaptureCommand command,
                             CaptureFailureReason failureReason,
                             String errorMessage) {
        captureJobRepository.markFailed(captureJobId, failureReason, errorMessage);
        agentTraceRepository.markFailed(
            traceId,
            errorMessage,
            failedTraceState(captureJobId, command, failureReason)
        );
        log.info(
            "module=CaptureOrchestrator action=trace_write_completed result=FAILED user_id={} capture_job_id={} trace_id={} error_code={}",
            command.userId(),
            captureJobId,
            traceId,
            failureReason.name()
        );
        log.warn(
            "module=CaptureOrchestrator action=capture_job_failed result=FAILED user_id={} capture_job_id={} trace_id={} error_code={} error_message={}",
            command.userId(),
            captureJobId,
            traceId,
            failureReason.name(),
            errorMessage
        );
    }

    private Map<String, Object> initialTraceState(UUID captureJobId, CaptureCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("capture_job_id", captureJobId);
        value.put("source_type", command.sourceType().name());
        value.put("input_summary", command.sourceType() == CaptureInputType.TEXT ? excerpt(command.rawText()) : command.sourceUrl());
        value.put("provider", configuredRoute().provider().name());
        value.put("model", configuredRoute().model());
        return value;
    }

    private Map<String, Object> completedTraceState(UUID captureJobId,
                                                    CaptureCommand command,
                                                    CaptureAnalysisWorker.AnalysisOutcome analysisOutcome,
                                                    UUID noteId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("capture_job_id", captureJobId);
        value.put("source_type", command.sourceType().name());
        value.put("provider", analysisOutcome.provider());
        value.put("model", analysisOutcome.model());
        value.put("result_note_id", noteId);
        value.put("result", "COMPLETED");
        return value;
    }

    private Map<String, Object> failedTraceState(UUID captureJobId,
                                                 CaptureCommand command,
                                                 CaptureFailureReason failureReason) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("capture_job_id", captureJobId);
        value.put("source_type", command.sourceType().name());
        value.put("input_summary", command.sourceType() == CaptureInputType.TEXT ? excerpt(command.rawText()) : command.sourceUrl());
        value.put("provider", configuredRoute().provider().name());
        value.put("model", configuredRoute().model());
        value.put("failure_reason", failureReason.name());
        value.put("result", "FAILED");
        return value;
    }

    private AiProperties.ResolvedRoute configuredRoute() {
        return aiProperties.resolveRoute(CaptureAnalysisClient.ROUTE_KEY, null);
    }

    private String extractionToolName(CaptureInputType sourceType) {
        return sourceType == CaptureInputType.TEXT ? "capture.text-input-normalizer" : "capture.url-http-extractor";
    }

    private int durationMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String excerpt(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private Map<String, Object> eventPayload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object value = keyValues[index + 1];
            if (value != null) {
                payload.put(String.valueOf(keyValues[index]), value);
            }
        }
        return payload;
    }

    public record CaptureCommand(
        UUID captureJobId,
        UUID userId,
        CaptureInputType sourceType,
        String rawText,
        String sourceUrl,
        String titleHint
    ) {
    }
}
