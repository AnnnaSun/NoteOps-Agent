package com.noteops.agent.service.capture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CaptureAnalysisJsonSchema {

    private CaptureAnalysisJsonSchema() {
    }

    static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title_candidate", Map.of("type", "string"));
        properties.put("summary", Map.of("type", "string"));
        properties.put("key_points", Map.of(
            "type", "array",
            "items", Map.of("type", "string")
        ));
        properties.put("tags", Map.of(
            "type", "array",
            "items", Map.of("type", "string")
        ));
        properties.put("idea_candidate", Map.of(
            "type", List.of("string", "null")
        ));
        properties.put("confidence", Map.of("type", "number"));
        properties.put("language", Map.of(
            "type", List.of("string", "null")
        ));
        properties.put("warnings", Map.of(
            "type", "array",
            "items", Map.of("type", "string")
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of(
            "title_candidate",
            "summary",
            "key_points",
            "tags",
            "idea_candidate",
            "confidence",
            "language",
            "warnings"
        ));
        return schema;
    }

    static String systemPrompt() {
        return """
            You analyze captured text into a strict json object for NoteOps.
            Return only a valid json object.
            Never add markdown fences.
            Keep summaries concise and factual.
            key_points must be a short array of concrete bullet-like statements.
            tags must be short lower-case topical labels.
            confidence must be a number between 0 and 1.
            warnings should call out ambiguity, missing context, or extraction quality concerns.
            Example json format:
            {
              "title_candidate": "string",
              "summary": "string",
              "key_points": ["string"],
              "tags": ["string"],
              "idea_candidate": "string or null",
              "confidence": 0.0,
              "language": "string or null",
              "warnings": ["string"]
            }
            """;
    }

    static String userPrompt(CaptureAnalysisClient.AnalyzeRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("source_type: ").append(request.sourceType().name()).append('\n');
        if (request.sourceUrl() != null) {
            builder.append("source_url: ").append(request.sourceUrl()).append('\n');
        }
        if (request.titleHint() != null) {
            builder.append("title_hint: ").append(request.titleHint()).append('\n');
        }
        if (request.pageTitle() != null) {
            builder.append("page_title: ").append(request.pageTitle()).append('\n');
        }
        builder.append('\n');
        builder.append("captured_text:\n");
        builder.append(request.cleanText());
        return builder.toString();
    }
}
