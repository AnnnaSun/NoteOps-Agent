package com.noteops.agent.repository.proposal;

import com.noteops.agent.service.proposal.ChangeProposalApplicationService;
import com.noteops.agent.model.proposal.ChangeProposalRiskLevel;
import com.noteops.agent.model.proposal.ChangeProposalStatus;
import com.noteops.agent.model.proposal.ChangeProposalTargetLayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ChangeProposalRepository {

    ChangeProposalApplicationService.ChangeProposalView create(UUID userId,
                                                              UUID noteId,
                                                              UUID traceId,
                                                              String proposalType,
                                                              ChangeProposalTargetLayer targetLayer,
                                                              ChangeProposalRiskLevel riskLevel,
                                                              String diffSummary,
                                                              Map<String, Object> beforeSnapshot,
                                                              Map<String, Object> afterSnapshot,
                                                              List<Map<String, Object>> sourceRefs);

    List<ChangeProposalApplicationService.ChangeProposalView> findByNoteIdAndUserId(UUID noteId, UUID userId);

    Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndUserId(UUID proposalId, UUID userId);

    Optional<ChangeProposalApplicationService.ChangeProposalView> findByIdAndNoteIdAndUserId(UUID proposalId, UUID noteId, UUID userId);

    void updateStatus(UUID proposalId, ChangeProposalStatus status, String rollbackToken);
}
