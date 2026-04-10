package com.noteops.agent.service.idea;

import com.noteops.agent.repository.idea.IdeaRepository;
import com.noteops.agent.model.idea.IdeaAssessmentResult;
import com.noteops.agent.model.idea.IdeaSourceMode;
import com.noteops.agent.model.idea.IdeaStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeaQueryServiceTest {

    @Test
    void listsIdeasForRequestedUser() {
        UUID userId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.ideas.add(new IdeaRepository.IdeaRecord(
            UUID.randomUUID(),
            userId,
            IdeaSourceMode.FROM_NOTE,
            noteId,
            "First idea",
            "First description",
            IdeaStatus.ASSESSED,
            IdeaAssessmentResult.empty(),
            Instant.parse("2026-04-09T08:00:00Z"),
            Instant.parse("2026-04-09T10:00:00Z")
        ));
        ideaRepository.ideas.add(new IdeaRepository.IdeaRecord(
            UUID.randomUUID(),
            userId,
            IdeaSourceMode.MANUAL,
            null,
            "Second idea",
            "Second description",
            IdeaStatus.CAPTURED,
            IdeaAssessmentResult.empty(),
            Instant.parse("2026-04-09T07:00:00Z"),
            Instant.parse("2026-04-09T09:00:00Z")
        ));
        ideaRepository.ideas.add(new IdeaRepository.IdeaRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            IdeaSourceMode.MANUAL,
            null,
            "Other user idea",
            null,
            IdeaStatus.CAPTURED,
            IdeaAssessmentResult.empty(),
            Instant.parse("2026-04-09T07:00:00Z"),
            Instant.parse("2026-04-09T11:00:00Z")
        ));

        IdeaQueryService service = new IdeaQueryService(ideaRepository);

        List<IdeaQueryService.IdeaSummaryView> ideas = service.list(userId.toString());

        assertThat(ideas).hasSize(2);
        assertThat(ideas).extracting(IdeaQueryService.IdeaSummaryView::title)
            .containsExactly("First idea", "Second idea");
        assertThat(ideas.getFirst().sourceNoteId()).isEqualTo(noteId);
    }

    @Test
    void getsIdeaDetailForRequestedUser() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        InMemoryIdeaRepository ideaRepository = new InMemoryIdeaRepository();
        ideaRepository.detail = Optional.of(new IdeaRepository.IdeaRecord(
            ideaId,
            userId,
            IdeaSourceMode.MANUAL,
            null,
            "Detailed idea",
            "A richer description",
            IdeaStatus.PLANNED,
            new IdeaAssessmentResult(
                "Problem statement",
                "Target user",
                "Core hypothesis",
                List.of("Validation path"),
                List.of("Next action"),
                List.of("Risk"),
                "Reasoning summary"
            ),
            Instant.parse("2026-04-09T08:00:00Z"),
            Instant.parse("2026-04-09T09:00:00Z")
        ));

        IdeaQueryService service = new IdeaQueryService(ideaRepository);

        IdeaQueryService.IdeaDetailView detail = service.get(ideaId.toString(), userId.toString());

        assertThat(detail.id()).isEqualTo(ideaId);
        assertThat(detail.title()).isEqualTo("Detailed idea");
        assertThat(detail.status()).isEqualTo(IdeaStatus.PLANNED);
        assertThat(detail.assessmentResult().problemStatement()).isEqualTo("Problem statement");
    }

    @Test
    void rejectsInvalidUserIdWhenListingIdeas() {
        IdeaQueryService service = new IdeaQueryService(new InMemoryIdeaRepository());

        assertThatThrownBy(() -> service.list("bad-user-id"))
            .hasMessage("user_id must be a valid UUID");
    }

    @Test
    void rejectsMissingIdeaForRequestedUser() {
        UUID userId = UUID.randomUUID();
        UUID ideaId = UUID.randomUUID();
        IdeaQueryService service = new IdeaQueryService(new InMemoryIdeaRepository());

        assertThatThrownBy(() -> service.get(ideaId.toString(), userId.toString()))
            .hasMessage("idea not found");
    }

    private static final class InMemoryIdeaRepository implements IdeaRepository {

        private final List<IdeaRecord> ideas = new ArrayList<>();
        private Optional<IdeaRecord> detail = Optional.empty();

        @Override
        public IdeaRecord create(UUID userId,
                                 IdeaSourceMode sourceMode,
                                 UUID sourceNoteId,
                                 String title,
                                 String rawDescription,
                                 IdeaStatus status,
                                 IdeaAssessmentResult assessmentResult) {
            throw new UnsupportedOperationException("create is not used in query tests");
        }

        @Override
        public Optional<IdeaRecord> findByIdAndUserId(UUID ideaId, UUID userId) {
            return detail.filter(record -> record.id().equals(ideaId) && record.userId().equals(userId));
        }

        @Override
        public List<IdeaRecord> findAllByUserId(UUID userId) {
            return ideas.stream()
                .filter(record -> record.userId().equals(userId))
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .toList();
        }

        @Override
        public IdeaRecord updateAssessment(UUID ideaId, UUID userId, IdeaAssessmentResult assessmentResult, IdeaStatus status) {
            throw new UnsupportedOperationException("updateAssessment is not used in query tests");
        }

        @Override
        public IdeaRecord updateStatus(UUID ideaId, UUID userId, IdeaStatus status) {
            throw new UnsupportedOperationException("updateStatus is not used in query tests");
        }

        @Override
        public Optional<IdeaRecord> updateStatusIfCurrent(UUID ideaId,
                                                          UUID userId,
                                                          IdeaStatus currentStatus,
                                                          IdeaStatus targetStatus) {
            throw new UnsupportedOperationException("updateStatusIfCurrent is not used in query tests");
        }
    }
}
