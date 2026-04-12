package com.noteops.agent.service.trend;

import com.noteops.agent.common.ApiException;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaStatus;
import com.noteops.agent.model.trend.TrendAnalysisPayload;
import com.noteops.agent.model.trend.TrendItemStatus;
import com.noteops.agent.repository.event.UserActionEventRepository;
import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.repository.trace.AgentTraceRepository;
import com.noteops.agent.repository.trace.ToolInvocationLogRepository;
import com.noteops.agent.repository.trend.TrendItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrendIdeaConversionService {

    private static final Logger log = LoggerFactory.getLogger(TrendIdeaConversionService.class);
    private static final String TOOL_NAME = "trend.idea.convert";
    private static final String EVENT_TYPE = "TREND_PROMOTED_TO_IDEA";

    private final TrendItemRepository trendItemRepository;
    private final IdeaRepository ideaRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final ToolInvocationLogRepository toolInvocationLogRepository;
    private final UserActionEventRepository userActionEventRepository;
    private final TransactionTemplate transactionTemplate;

    public TrendIdeaConversionService(TrendItemRepository trendItemRepository,
                                      IdeaRepository ideaRepository,
                                      AgentTraceRepository agentTraceRepository,
                                      ToolInvocationLogRepository toolInvocationLogRepository,
                                      UserActionEventRepository userActionEventRepository,
                                      PlatformTransactionManager transactionManager) {
        this.trendItemRepository = trendItemRepository;
        this.ideaRepository = ideaRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.toolInvocationLogRepository = toolInvocationLogRepository;
        this.userActionEventRepository = userActionEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PromoteToIdeaResult promoteToIdea(PromoteToIdeaCommand command) {
        long startedAt = System.nanoTime();
        try {
            PromoteToIdeaResult result = transactionTemplate.execute(status -> doPromoteToIdea(command, startedAt));
            if (result == null) {
                throw new IllegalStateException("trend idea conversion returned null");
            }
            return result;
        } catch (ApiException exception) {
            log.warn(
                "module=TrendIdeaConversionService action=trend_idea_convert_fail trace_id={} user_id={} trend_item_id={} result=FAILED error_code={} error_message={}",
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
                "TREND_IDEA_CONVERSION_FAILED",
                exception.getMessage() == null ? "trend idea conversion failed" : exception.getMessage(),
                command.traceId().toString()
            );
            log.warn(
                "module=TrendIdeaConversionService action=trend_idea_convert_fail trace_id={} user_id={} trend_item_id={} result=FAILED error_code={} error_message={}",
                command.traceId(),
                command.userId(),
                command.trendItemId(),
                apiException.errorCode(),
                apiException.getMessage()
            );
            throw apiException;
        }
    }

    private PromoteToIdeaResult doPromoteToIdea(PromoteToIdeaCommand command, long startedAt) {
        TrendItemRepository.TrendItemRecord trendItem = trendItemRepository.findByIdAndUserId(command.trendItemId(), command.userId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "TREND_IDEA_CONVERSION_FAILED",
                "trend item not found",
                command.traceId().toString()
            ));
        validateActionable(trendItem, command.traceId());

        TrendAnalysisPayload analysisPayload = trendItem.analysisPayload() == null
            ? TrendAnalysisPayload.empty()
            : trendItem.analysisPayload();

        String title = firstNonBlank(trendItem.title(), analysisPayload.summary(), "Trend idea");
        String rawDescription = buildRawDescription(trendItem, analysisPayload, command.operatorNote());

        Map<String, Object> traceState = buildTraceState(trendItem, analysisPayload, command);
        agentTraceRepository.create(
            command.traceId(),
            command.userId(),
            "TREND_ACTION",
            "Convert trend item to idea",
            "TREND_ITEM",
            trendItem.id(),
            List.of("trend-idea-conversion-worker"),
            traceState
        );

        Map<String, Object> inputDigest = buildToolInputDigest(trendItem, command);
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

        IdeaRepository.IdeaRecord idea = ideaRepository.createFromTrend(
            command.userId(),
            trendItem.id(),
            title,
            rawDescription,
            IdeaStatus.CAPTURED,
            IdeaAssessmentResult.empty()
        );

        TrendItemRepository.TrendItemRecord updatedTrendItem = trendItemRepository.updateStatus(
            trendItem.id(),
            command.userId(),
            TrendItemStatus.PROMOTED_TO_IDEA,
            trendItem.suggestedAction(),
            trendItem.convertedNoteId(),
            idea.id()
        );

        Map<String, Object> outputDigest = buildToolOutputDigest(updatedTrendItem, idea);
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
            EVENT_TYPE,
            "TREND_ITEM",
            updatedTrendItem.id(),
            command.traceId(),
            buildEventPayload(updatedTrendItem, idea, analysisPayload, command)
        );

        agentTraceRepository.markCompleted(
            command.traceId(),
            "Trend item promoted to idea " + idea.id(),
            buildCompletedState(updatedTrendItem, idea, command)
        );

        log.info(
            "module=TrendIdeaConversionService action=trend_idea_convert_success trace_id={} user_id={} trend_item_id={} idea_id={} result=COMPLETED duration_ms={}",
            command.traceId(),
            command.userId(),
            updatedTrendItem.id(),
            idea.id(),
            durationMs(startedAt)
        );

        return new PromoteToIdeaResult(command.traceId().toString(), idea, updatedTrendItem);
    }

    private void validateActionable(TrendItemRepository.TrendItemRecord trendItem, UUID traceId) {
        if (trendItem.status() != TrendItemStatus.ANALYZED) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "TREND_IDEA_CONVERSION_FAILED",
                "trend item is not in actionable inbox state",
                traceId.toString()
            );
        }
    }

    private Map<String, Object> buildTraceState(TrendItemRepository.TrendItemRecord trendItem,
                                                TrendAnalysisPayload analysisPayload,
                                                PromoteToIdeaCommand command) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("conversion_type", "PROMOTE_TO_IDEA");
        state.put("trace_id", command.traceId().toString());
        state.put("user_id", command.userId().toString());
        state.put("trend_item_id", trendItem.id().toString());
        state.put("source_type", trendItem.sourceType().name());
        state.put("source_item_key", trendItem.sourceItemKey());
        state.put("title", trendItem.title());
        state.put("url", trendItem.url());
        state.put("normalized_score", trendItem.normalizedScore());
        state.put("status", trendItem.status().name());
        state.put("suggested_action", trendItem.suggestedAction() == null ? null : trendItem.suggestedAction().name());
        state.put("analysis_summary", analysisPayload.summary());
        state.put("analysis_why_it_matters", analysisPayload.whyItMatters());
        state.put("analysis_topic_tags", analysisPayload.topicTags());
        state.put("operator_note", command.operatorNote());
        return state;
    }

    private Map<String, Object> buildToolInputDigest(TrendItemRepository.TrendItemRecord trendItem, PromoteToIdeaCommand command) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("trend_item_id", trendItem.id().toString());
        digest.put("user_id", command.userId().toString());
        digest.put("action", "PROMOTE_TO_IDEA");
        digest.put("url", trendItem.url());
        digest.put("title", trendItem.title());
        digest.put("operator_note", command.operatorNote());
        return digest;
    }

    private Map<String, Object> buildToolOutputDigest(TrendItemRepository.TrendItemRecord updatedTrendItem,
                                                      IdeaRepository.IdeaRecord idea) {
        Map<String, Object> digest = new LinkedHashMap<>();
        digest.put("trend_item_id", updatedTrendItem.id().toString());
        digest.put("status", updatedTrendItem.status().name());
        digest.put("converted_idea_id", idea.id().toString());
        return digest;
    }

    private Map<String, Object> buildEventPayload(TrendItemRepository.TrendItemRecord updatedTrendItem,
                                                  IdeaRepository.IdeaRecord idea,
                                                  TrendAnalysisPayload analysisPayload,
                                                  PromoteToIdeaCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trend_item_id", updatedTrendItem.id().toString());
        payload.put("action", "PROMOTE_TO_IDEA");
        payload.put("status", updatedTrendItem.status().name());
        payload.put("converted_idea_id", idea.id().toString());
        payload.put("converted_note_id", updatedTrendItem.convertedNoteId() == null ? null : updatedTrendItem.convertedNoteId().toString());
        payload.put("source_type", updatedTrendItem.sourceType().name());
        payload.put("source_item_key", updatedTrendItem.sourceItemKey());
        payload.put("url", updatedTrendItem.url());
        payload.put("title", updatedTrendItem.title());
        payload.put("analysis_summary", analysisPayload.summary());
        payload.put("analysis_why_it_matters", analysisPayload.whyItMatters());
        payload.put("analysis_topic_tags", analysisPayload.topicTags());
        payload.put("operator_note", command.operatorNote());
        return payload;
    }

    private Map<String, Object> buildCompletedState(TrendItemRepository.TrendItemRecord updatedTrendItem,
                                                    IdeaRepository.IdeaRecord idea,
                                                    PromoteToIdeaCommand command) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("conversion_type", "PROMOTE_TO_IDEA");
        state.put("trace_id", command.traceId().toString());
        state.put("trend_item_id", updatedTrendItem.id().toString());
        state.put("status", updatedTrendItem.status().name());
        state.put("converted_idea_id", idea.id().toString());
        return state;
    }

    private String buildRawDescription(TrendItemRepository.TrendItemRecord trendItem,
                                       TrendAnalysisPayload analysisPayload,
                                       String operatorNote) {
        StringBuilder builder = new StringBuilder();
        builder.append("Source: ").append(trendItem.url()).append("\n");
        if (trendItem.summary() != null && !trendItem.summary().isBlank()) {
            builder.append("\nTrend summary:\n").append(trendItem.summary().trim()).append("\n");
        }
        if (analysisPayload.summary() != null && !analysisPayload.summary().isBlank()) {
            builder.append("\nAnalysis summary:\n").append(analysisPayload.summary().trim()).append("\n");
        }
        if (analysisPayload.whyItMatters() != null && !analysisPayload.whyItMatters().isBlank()) {
            builder.append("\nWhy it matters:\n").append(analysisPayload.whyItMatters().trim()).append("\n");
        }
        if (analysisPayload.topicTags() != null && !analysisPayload.topicTags().isEmpty()) {
            builder.append("\nTopic tags: ").append(String.join(", ", analysisPayload.topicTags())).append("\n");
        }
        if (operatorNote != null && !operatorNote.isBlank()) {
            builder.append("\nOperator note:\n").append(operatorNote.trim()).append("\n");
        }
        return builder.toString().trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private int durationMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public record PromoteToIdeaCommand(
        UUID trendItemId,
        UUID userId,
        UUID traceId,
        String operatorNote
    ) {
    }

    public record PromoteToIdeaResult(
        String traceId,
        IdeaRepository.IdeaRecord idea,
        TrendItemRepository.TrendItemRecord trendItem
    ) {
    }
}

