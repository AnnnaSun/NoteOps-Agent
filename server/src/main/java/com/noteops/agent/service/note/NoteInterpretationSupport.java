package com.noteops.agent.service.note;

import java.util.List;

public final class NoteInterpretationSupport {

    private NoteInterpretationSupport() {
    }

    public static List<String> extractKeyPoints(String text) {
        return List.of(text.split("(?<=[.!?。！？])\\s+"))
            .stream()
            .map(String::trim)
            .filter(segment -> !segment.isBlank())
            .limit(3)
            .toList();
    }

    public static String summarize(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220) + "...";
    }
}
