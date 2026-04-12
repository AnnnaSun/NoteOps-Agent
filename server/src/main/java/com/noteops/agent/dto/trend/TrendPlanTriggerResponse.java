package com.noteops.agent.dto.trend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.noteops.agent.service.trend.TrendPlanApplicationService;
import com.noteops.agent.service.trend.TrendSourceRegistry;

import java.util.List;

public record TrendPlanTriggerResponse(
    @JsonProperty("plan_key")
    String planKey,
    boolean enabled,
    @JsonProperty("resolved_sources")
    List<ResolvedTrendSourceResponse> resolvedSources,
    @JsonProperty("fetch_limit_per_source")
    int fetchLimitPerSource,
    String schedule,
    @JsonProperty("keyword_bias")
    List<String> keywordBias,
    @JsonProperty("auto_ingest")
    boolean autoIngest,
    @JsonProperty("auto_convert")
    boolean autoConvert,
    @JsonProperty("trigger_mode")
    String triggerMode,
    @JsonProperty("fetched_count")
    int fetchedCount,
    @JsonProperty("inserted_count")
    int insertedCount,
    @JsonProperty("deduped_count")
    int dedupedCount,
    @JsonProperty("source_results")
    List<SourceResultResponse> sourceResults,
    String result
) {

    public static TrendPlanTriggerResponse from(TrendPlanApplicationService.TriggerResult result) {
        return new TrendPlanTriggerResponse(
            result.planKey(),
            result.enabled(),
            result.resolvedSources().stream().map(ResolvedTrendSourceResponse::from).toList(),
            result.fetchLimitPerSource(),
            result.schedule(),
            result.keywordBias(),
            result.autoIngest(),
            result.autoConvert(),
            result.triggerMode(),
            result.fetchedCount(),
            result.insertedCount(),
            result.dedupedCount(),
            result.sourceResults().stream().map(SourceResultResponse::from).toList(),
            result.result()
        );
    }

    public record ResolvedTrendSourceResponse(
        @JsonProperty("source_type")
        String sourceType,
        @JsonProperty("display_name")
        String displayName
    ) {

        public static ResolvedTrendSourceResponse from(TrendSourceRegistry.ResolvedTrendSource source) {
            return new ResolvedTrendSourceResponse(source.sourceType().name(), source.displayName());
        }
    }

    public record SourceResultResponse(
        @JsonProperty("source_type")
        String sourceType,
        @JsonProperty("display_name")
        String displayName,
        @JsonProperty("fetched_count")
        int fetchedCount,
        @JsonProperty("inserted_count")
        int insertedCount,
        @JsonProperty("deduped_count")
        int dedupedCount
    ) {

        public static SourceResultResponse from(TrendPlanApplicationService.SourceResult result) {
            return new SourceResultResponse(
                result.sourceType(),
                result.displayName(),
                result.fetchedCount(),
                result.insertedCount(),
                result.dedupedCount()
            );
        }
    }
}
