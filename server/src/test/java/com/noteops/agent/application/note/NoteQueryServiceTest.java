package com.noteops.agent.application.note;

import com.noteops.agent.persistence.note.NoteRepository;
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

    private static final class InMemoryNoteRepository implements NoteRepository {

        private final List<NoteQueryService.NoteSummaryView> summaries = new ArrayList<>();

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
            return Optional.empty();
        }

        @Override
        public List<NoteQueryService.NoteSummaryView> findAllByUserId(UUID userId) {
            return summaries.stream()
                .filter(view -> view.userId().equals(userId))
                .toList();
        }
    }
}
