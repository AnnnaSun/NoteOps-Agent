package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.trend.TrendActionType;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.model.trend.TrendSourceType;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.note.NoteRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import com.noteops.agent.service.note.NoteInterpretationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrendNoteConversionService {

    private static final Logger log = LoggerFactory.getLogger(TrendNoteConversionService.class);
    private static final String TOOL_NAME = "trend.note.convert";

    private final TrendItemRepository trendItemRepository;
    private final NoteRepository noteRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final TransactionTemplate transactionTemplate;

    public TrendNoteConversionService(TrendItemRepository trendItemRepository,
                                      NoteRepository noteRepository,
                                      AgentTraceRepository agentTraceRepository,
                                      ToolInvocationLogRepository toolInvocationLogRepository,
                                      UserActionEventRepository userActionEventRepository,
                                      PlatformTransactionManager transactionManager) {
        this.trendItemRepository = trendItemRepository;
        this.noteRepository = noteRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public SaveAsNoteResult saveAsNote(SaveAsNoteCommand command) {
        long startedAt = System.nanoTime();
        try {
            SaveAsNoteResult result = transactionTemplate.execute(status -> doSaveAsNote(command, startedAt));
            if (result == null) {
                throw new IllegalStateException("trend note conversion returned null");
            }
            return result;
        } catch (ApiException exception) {
            log.warn(
                "module=TrendNoteConversionService action=trend_note_convert_fail trace_id={} user_id={} trend_item_id={} result=FAILED error_code={} error_message={}",
                command.traceId(),
                command.userId(),
                command.trendItemId(),
                exception.errorCode(),
                exception.getMessage()
            );
            throw exception;
        } catch (Exception exception) {
            ApiException apiException = new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TREND_NOTE_CONVERSION_FAILED",
                exception.getMessage() == null ? "trend note conversion failed" : exception.getMessage(),
                command.traceId().toString()
            );
            log.warn(
                "module=TrendNoteConversionService action=trend_note_convert_fail trace_id={} user_id={} trend_item_id={} result=FAILED error_code={} error_message={}",
                command.traceId(),
                command.userId(),
                command.trendItemId(),
                apiException.errorCode(),
                apiException.getMessage()
            );
            throw apiException;
        }
    }

    private SaveAsNoteResult doSaveAsNote(SaveAsNoteCommand command, long startedAt) {
        TrendItemRepository.TrendItemRecord trendItem = trendItemRepository.findByIdAndUserId(command.trendItemId(), command.userId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "TREND_NOTE_CONVERSION_FAILED",
                "trend item not found",
                command.traceId().toString()
            ));
        validateActionable(trendItem, command.traceId());

        TrendAnalysisPayload analysisPayload = trendItem.analysisPayload() == null
            ? TrendAnalysisPayload.empty()
            : trendItem.analysisPayload();
        List<String> topicTags = normalizeTags(analysisPayload.topicTags());
        List<String> keyPoints = buildKeyPoints(analysisPayload, topicTags);
        String currentSummary = NoteInterpretationSupport.summarize(firstNonBlank(
            analysisPayload.summary(),
            trendItem.summary(),
            trendItem.title(),
            "Trend candidate"
        ));
        String title = firstNonBlank(trendItem.title(), analysisPayload.summary(), "Trend candidate");
        String rawText = buildRawText(trendItem, analysisPayload, topicTags);
        String cleanText = NoteInterpretationSupport.summarize(buildCleanText(trendItem, analysisPayload, topicTags));
        Map<String, Object> sourceSnapshot = buildSourceSnapshot(trendItem, command, analysisPayload, topicTags);
        Map<String, Object> analysisResult = buildAnalysisResult(trendItem, command, analysisPayload, topicTags);

        agentTraceRepository.create(
            command.traceId(),
            command.userId(),
            "TREND_ACTION",
            "Convert trend item to note",
            "TREND_ITEM",
            trendItem.id(),
            List.of("trend-note-conversion-worker"),
            new LinkedHashMap<>(sourceSnapshot)
        );

        Map<String, Object> inputDigest = buildToolInputDigest(trendItem, command, topicTags);
        toolInvocationLogRepository.append(
            command.userId(),
            command.traceId(),
            TOOL_NAME,
            "STARTED",
            inputDigest,
            Map.of(),
            null,
            null,
            null
        );

        NoteRepository.NoteCreationResult noteCreationResult = noteRepository.create(
            command.userId(),
            title,
            currentSummary,
            keyPoints,
            topicTags,
            trendItem.url(),
            rawText,
            cleanText,
            sourceSnapshot,
            analysisResult
        );

        TrendItemRepository.TrendItemRecord updatedTrendItem = trendItemRepository.updateStatus(
            trendItem.id(),
            command.userId(),
            TrendItemStatus.SAVED_AS_NOTE,
            trendItem.suggestedAction(),
            noteCreationResult.noteId(),
            null
        );

        Map<String, Object> outputDigest = buildToolOutputDigest(updatedTrendItem, noteCreationResult);
        toolInvocationLogRepository.append(
            command.userId(),
            command.traceId(),
            TOOL_NAME,
            "COMPLETED",
            inputDigest,
            outputDigest,
            durationMs(startedAt),
            null,
            null
        );
        userActionEventRepository.append(
            command.userId(),
            "TREND_SAVED_AS_NOTE",
            "TREND_ITEM",
            updatedTrendItem.id(),
            command.traceId(),
            buildEventPayload(updatedTrendItem, noteCreationResult, command, analysisPayload, topicTags)
        );
        agentTraceRepository.markCompleted(
            command.traceId(),
            "Trend item saved as note " + noteCreationResult.noteId(),
            buildCompletedState(updatedTrendItem, noteCreationResult, command, analysisPayload, topicTags)
        );
        log.info(
            "module=TrendNoteConversionService action=trend_note_convert_success trace_id={} user_id={} trend_item_id={} note_id={} result=COMPLETED duration_ms={}",
            command.traceId(),
            command.userId(),
            updatedTrendItem.id(),
            noteCreationResult.noteId(),
            durationMs(startedAt)
        );
        return new SaveAsNoteResult(command.traceId().toString(), noteCreationResult, updatedTrendItem);
    }

    private void validateActionable(TrendItemRepository.TrendItemRecord trendItem, UUID traceId) {
        if (trendItem.status() != TrendItemStatus.ANALYZED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "TREND_NOTE_CONVERSION_FAILED",
                "trend item is not in actionable inbox state",
                traceId.toString()
            );
        }
    }

    private Map<String, Object> buildSourceSnapshot(TrendItemRepository.TrendItemRecord trendItem,
                                                     SaveAsNoteCommand command,
                                                     TrendAnalysisPayload analysisPayload,
                                                     List<String> topicTags) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("conversion_type", "SAVE_AS_NOTE");
        snapshot.put("trace_id", command.traceId().toString());
        snapshot.put("operator_note", blankToNull(command.operatorNote()));
        snapshot.put("trend_item_id", trendItem.id().toString());
        snapshot.put("source_type", trendItem.sourceType().name());
        snapshot.put("source_item_key", trendItem.sourceItemKey());
        snapshot.put("source_url", trendItem.url());
        snapshot.put("title", trendItem.title());
        snapshot.put("summary", trendItem.summary());
        snapshot.put("normalized_score", trendItem.normalizedScore());
        snapshot.put("status", trendItem.status().name());
        snapshot.put("suggested_action", trendItem.suggestedAction() == null ? null : trendItem.suggestedAction().name());
        snapshot.put("analysis_summary", analysisPayload.summary());
        snapshot.put("analysis_why_it_matters", analysisPayload.whyItMatters());
        snapshot.put("analysis_topic_tags", topicTags);
        snapshot.put("analysis_signal_type", analysisPayload.signalType());
        snapshot.put("analysis_reasoning_summary", analysisPayload.reasoningSummary());
        return snapshot;
    }

    private Map<String, Object> buildAnalysisResult(TrendItemRepository.TrendItemRecord trendItem,
                                                    SaveAsNoteCommand command,
                                                    TrendAnalysisPayload analysisPayload,
                                                    List<String> topicTags) {
        Map<String, Object> result = new LinkedHashMap<>(analysisPayload.toMap());
        result.put("conversion_type", "SAVE_AS_NOTE");
        result.put("trend_item_id", trendItem.id().toString());
        result.put("source_type", trendItem.sourceType().name());
        result.put("source_item_key", trendItem.sourceItemKey());
        result.put("source_url", trendItem.url());
        result.put("source_title", trendItem.title());
        result.put("trace_id", command.traceId().toString());
        result.put("operator_note", blankToNull(command.operatorNote()));
        result.put("topic_tags", topicTags);
        return result;
    }

    private Map<String, Object> buildToolInputDigest(TrendItemRepository.TrendItemRecord trendItem,
                                                     SaveAsNoteCommand command,
                                                     List<String> topicTags) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("trend_item_id", trendItem.id().toString());
        digest.put("user_id", command.userId().toString());
        digest.put("action", TrendActionType.SAVE_AS_NOTE.name());
        digest.put("operator_note", blankToNull(command.operatorNote()));
        digest.put("source_type", trendItem.sourceType().name());
        digest.put("source_item_key", trendItem.sourceItemKey());
        digest.put("topic_tags", topicTags);
        return digest;
    }

    private Map<String, Object> buildToolOutputDigest(TrendItemRepository.TrendItemRecord trendItem,
                                                      NoteRepository.NoteCreationResult noteCreationResult) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("trend_item_id", trendItem.id().toString());
        digest.put("note_id", noteCreationResult.noteId().toString());
        digest.put("content_id", noteCreationResult.contentId().toString());
        digest.put("status", trendItem.status().name());
        digest.put("converted_note_id", trendItem.convertedNoteId() == null ? null : trendItem.convertedNoteId().toString());
        return digest;
    }

    private Map<String, Object> buildEventPayload(TrendItemRepository.TrendItemRecord trendItem,
                                                  NoteRepository.NoteCreationResult noteCreationResult,
                                                  SaveAsNoteCommand command,
                                                  TrendAnalysisPayload analysisPayload,
                                                  List<String> topicTags) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trend_item_id", trendItem.id().toString());
        payload.put("note_id", noteCreationResult.noteId().toString());
        payload.put("content_id", noteCreationResult.contentId().toString());
        payload.put("action", TrendActionType.SAVE_AS_NOTE.name());
        payload.put("status", trendItem.status().name());
        payload.put("source_type", trendItem.sourceType().name());
        payload.put("source_item_key", trendItem.sourceItemKey());
        payload.put("source_url", trendItem.url());
        payload.put("operator_note", blankToNull(command.operatorNote()));
        payload.put("suggested_action", trendItem.suggestedAction() == null ? null : trendItem.suggestedAction().name());
        payload.put("topic_tags", topicTags);
        payload.put("analysis_summary", analysisPayload.summary());
        return payload;
    }

    private Map<String, Object> buildCompletedState(TrendItemRepository.TrendItemRecord trendItem,
                                                    NoteRepository.NoteCreationResult noteCreationResult,
                                                    SaveAsNoteCommand command,
                                                    TrendAnalysisPayload analysisPayload,
                                                    List<String> topicTags) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("trend_item_id", trendItem.id().toString());
        state.put("note_id", noteCreationResult.noteId().toString());
        state.put("content_id", noteCreationResult.contentId().toString());
        state.put("result", "SAVED_AS_NOTE");
        state.put("status", trendItem.status().name());
        state.put("source_type", trendItem.sourceType().name());
        state.put("source_item_key", trendItem.sourceItemKey());
        state.put("operator_note", blankToNull(command.operatorNote()));
        state.put("analysis_summary", analysisPayload.summary());
        state.put("topic_tags", topicTags);
        return state;
    }

    private List<String> buildKeyPoints(TrendAnalysisPayload analysisPayload, List<String> topicTags) {
        LinkedHashSet<String> keyPoints = new LinkedHashSet<>();
        addIfPresent(keyPoints, analysisPayload.whyItMatters());
        addIfPresent(keyPoints, analysisPayload.signalType() == null ? null : "Signal: " + analysisPayload.signalType().trim());
        if (!topicTags.isEmpty()) {
            keyPoints.add("Tags: " + String.join(", ", topicTags));
        }
        addIfPresent(keyPoints, analysisPayload.reasoningSummary());
        if (keyPoints.isEmpty()) {
            addIfPresent(keyPoints, analysisPayload.summary());
        }
        return keyPoints.stream().limit(4).toList();
    }

    private List<String> normalizeTags(List<String> topicTags) {
        if (topicTags == null || topicTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : topicTags) {
            String value = blankToNull(tag);
            if (value != null) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String buildRawText(TrendItemRepository.TrendItemRecord trendItem,
                                TrendAnalysisPayload analysisPayload,
                                List<String> topicTags) {
        List<String> lines = new ArrayList<>();
        lines.add("trend_item_id: " + trendItem.id());
        lines.add("source_type: " + trendItem.sourceType().name());
        lines.add("source_item_key: " + trendItem.sourceItemKey());
        lines.add("title: " + firstNonBlank(trendItem.title(), "Trend candidate"));
        lines.add("source_url: " + trendItem.url());
        addIfPresent(lines, "trend_summary: ", trendItem.summary());
        addIfPresent(lines, "analysis_summary: ", analysisPayload.summary());
        addIfPresent(lines, "why_it_matters: ", analysisPayload.whyItMatters());
        if (!topicTags.isEmpty()) {
            lines.add("topic_tags: " + String.join(", ", topicTags));
        }
        addIfPresent(lines, "signal_type: ", analysisPayload.signalType());
        addIfPresent(lines, "reasoning_summary: ", analysisPayload.reasoningSummary());
        return String.join("\n", lines);
    }

    private String buildCleanText(TrendItemRepository.TrendItemRecord trendItem,
                                  TrendAnalysisPayload analysisPayload,
                                  List<String> topicTags) {
        List<String> fragments = new ArrayList<>();
        addIfPresent(fragments, trendItem.summary());
        addIfPresent(fragments, analysisPayload.summary());
        addIfPresent(fragments, analysisPayload.whyItMatters());
        if (!topicTags.isEmpty()) {
            fragments.add("tags: " + String.join(", ", topicTags));
        }
        addIfPresent(fragments, analysisPayload.reasoningSummary());
        if (fragments.isEmpty()) {
            fragments.add(firstNonBlank(trendItem.title(), "Trend candidate"));
        }
        return String.join(" | ", fragments);
    }

    private void addIfPresent(List<String> items, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            items.add(normalized);
        }
    }

    private void addIfPresent(LinkedHashSet<String> items, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            items.add(normalized);
        }
    }

    private void addIfPresent(List<String> items, String prefix, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            items.add(prefix + normalized);
        }
    }

    private void addIfPresent(Map<String, Object> items, String key, String value) {
        if (value != null && !value.isBlank()) {
            items.put(key, value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public record SaveAsNoteCommand(
        UUID trendItemId,
        UUID userId,
        UUID traceId,
        String operatorNote
    ) {
    }

    public record SaveAsNoteResult(
        String traceId,
        NoteRepository.NoteCreationResult noteCreationResult,
        TrendItemRepository.TrendItemRecord trendItem
    ) {
    }
}
