package com.noteops.agent.service.note;

import com.noteops.agent.repository.note.NoteRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoteQueryServiceTest {

    @Test
    void listsNotesForRequestedUser() {
        UUID userId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.summaries.add(new NoteQueryService.NoteSummaryView(
            UUID.randomUUID(),
            userId,
            "First",
            "Summary 1",
            List.of("a"),
            UUID.randomUUID(),
            Instant.parse("2026-03-15T10:00:00Z")
        ));
        noteRepository.summaries.add(new NoteQueryService.NoteSummaryView(
            UUID.randomUUID(),
            userId,
            "Second",
            "Summary 2",
            List.of("b"),
            UUID.randomUUID(),
            Instant.parse("2026-03-15T09:00:00Z")
        ));

        NoteQueryService service = new NoteQueryService(noteRepository);

        List<NoteQueryService.NoteSummaryView> notes = service.list(userId.toString());

        assertThat(notes).hasSize(2);
        assertThat(notes).extracting(NoteQueryService.NoteSummaryView::title)
            .containsExactly("First", "Second");
    }

    @Test
    void getsNoteDetailWithEvidenceBlocks() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
        noteRepository.detail = Optional.of(new NoteQueryService.NoteDetailView(
            noteId,
            userId,
            "Detailed note",
            "Summary text",
            List.of("point-1"),
            contentId,
            "CAPTURE_RAW",
            "https://example.com",
            "raw text",
            "clean text",
            Instant.parse("2026-03-15T09:00:00Z"),
            Instant.parse("2026-03-15T10:00:00Z"),
            List.of()
        ));
        noteRepository.evidenceBlocks = List.of(
            new NoteQueryService.NoteEvidenceView(
                evidenceId,
                "EVIDENCE",
                "https://evidence.example.com",
                "外部补充来源",
                "背景补充",
                "这是证据摘要",
                Instant.parse("2026-03-15T11:00:00Z")
            )
        );

        NoteQueryService service = new NoteQueryService(noteRepository);

        NoteQueryService.NoteDetailView detail = service.get(noteId.toString(), userId.toString());

        assertThat(detail.evidenceBlocks()).hasSize(1);
        assertThat(detail.evidenceBlocks().getFirst().relationLabel()).isEqualTo("背景补充");
    }

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final List<NoteQueryService.NoteSummaryView> summaries = new ArrayList<>();
        private Optional<NoteQueryService.NoteDetailView> detail = Optional.empty();
        private List<NoteQueryService.NoteEvidenceView> evidenceBlocks = List.of();

        @Override
        public NoteCreationResult create(UUID userId,
                                         String title,
                                         String currentSummary,
                                         List<String> currentKeyPoints,
                                         String sourceUri,
                                         String rawText,
                                         String cleanText,
                                         Map<String, Object> sourceSnapshot,
                                         Map<String, Object> analysisResult) {
            return new NoteCreationResult(UUID.randomUUID(), UUID.randomUUID());
        }

        @Override
        public Optional<NoteQueryService.NoteDetailView> findByIdAndUserId(UUID noteId, UUID userId) {
            return detail;
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return summaries.stream()
                .filter(view -> view.userId().equals(userId))
                .toList();
        }

        @Override
        public List<NoteQueryService.NoteEvidenceView> findEvidenceByNoteIdAndUserId(UUID noteId, UUID userId) {
            return evidenceBlocks;
        }
    }
}
