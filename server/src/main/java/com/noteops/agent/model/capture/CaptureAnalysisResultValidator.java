package com.noteops.agent.model.capture;

public final class CaptureAnalysisResultValidator {

    private static final int MAX_KEY_POINTS = 8;
    private static final int MAX_TAGS = 8;
    private static final int MAX_WARNINGS = 6;

    private CaptureAnalysisResultValidator() {
    }

    public static CaptureAnalysisResult validate(CaptureAnalysisResult value) {
        // 只允许结构完整、范围合理的分析结果继续流入后续落库链路。
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("analysis result must not be empty");
        }
        if (value.titleCandidate() == null) {
            throw new IllegalArgumentException("title_candidate must not be blank");
        }
        if (value.summary() == null) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (value.keyPoints().isEmpty()) {
            throw new IllegalArgumentException("key_points must contain at least one item");
        }
        if (value.keyPoints().size() > MAX_KEY_POINTS) {
            throw new IllegalArgumentException("key_points must contain at most 8 items");
        }
        if (value.tags().size() > MAX_TAGS) {
            throw new IllegalArgumentException("tags must contain at most 8 items");
        }
        if (value.warnings().size() > MAX_WARNINGS) {
            throw new IllegalArgumentException("warnings must contain at most 6 items");
        }
        if (value.confidence() == null) {
            throw new IllegalArgumentException("confidence must be present");
        }
        if (value.confidence() < 0 || value.confidence() > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        return value;
    }
}
