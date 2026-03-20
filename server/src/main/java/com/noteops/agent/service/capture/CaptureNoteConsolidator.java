package com.noteops.agent.service.capture;

import com.noteops.agent.model.capture.CaptureAnalysisResult;
import com.noteops.agent.model.capture.CaptureFailureReason;
import com.noteops.agent.model.capture.CaptureInputType;
import com.noteops.agent.repository.note.NoteRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CaptureNoteConsolidator {

    private final NoteRepository noteRepository;

    public CaptureNoteConsolidator(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    // 将 Capture 的抽取/分析结果写入 Note，失败统一包装为流程异常。
    public NoteRepository.NoteCreationResult consolidate(UUID userId,
                                                         CaptureInputType sourceType,
                                                         String sourceUrl,
                                                         String titleHint,
                                                         CaptureExtractor.ExtractionResult extractionResult,
                                                         CaptureAnalysisResult analysisResult) {
        try {
            return noteRepository.create(
                userId,
                analysisResult.titleCandidate(),
                analysisResult.summary(),
                analysisResult.keyPoints(),
                analysisResult.tags(),
                sourceUrl,
                extractionResult.rawText(),
                extractionResult.cleanText(),
                sourceSnapshot(sourceType, sourceUrl, titleHint, extractionResult),
                analysisResult.toMap()
            );
        } catch (RuntimeException exception) {
            throw new CapturePipelineException(
                CaptureFailureReason.CONSOLIDATION_FAILED,
                "capture consolidation failed: " + exception.getMessage(),
                exception
            );
        }
    }

    private Map<String, Object> sourceSnapshot(CaptureInputType sourceType,
                                               String sourceUrl,
                                               String titleHint,
                                               CaptureExtractor.ExtractionResult extractionResult) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source_type", sourceType.name());
        snapshot.put("source_url", sourceUrl);
        snapshot.put("title_hint", blankToNull(titleHint));
        snapshot.put("extraction_mode", extractionResult.extractionMode());
        snapshot.put("page_title", extractionResult.pageTitle());
        return snapshot;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
