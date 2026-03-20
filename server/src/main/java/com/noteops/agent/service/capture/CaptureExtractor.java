package com.noteops.agent.service.capture;

import com.noteops.agent.model.capture.CaptureInputType;

import java.util.LinkedHashMap;
import java.util.Map;

public interface CaptureExtractor {

    ExtractionResult extract(ExtractionCommand command);

    record ExtractionCommand(
        CaptureInputType sourceType,
        String rawText,
        String sourceUrl,
        String titleHint
    ) {
    }

    record ExtractionResult(
        String rawText,
        String cleanText,
        String sourceUrl,
        String extractionMode,
        String pageTitle
    ) {

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("raw_text", rawText);
            payload.put("clean_text", cleanText);
            payload.put("source_url", sourceUrl);
            payload.put("extraction_mode", extractionMode);
            payload.put("page_title", pageTitle);
            return payload;
        }
    }
}
