package com.noteops.agent.service.idea;

import com.noteops.agent.model.idea.IdeaAssessmentResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StubIdeaAgent implements IdeaAgent {

    @Override
    // 使用确定性 stub 生成结构化 assessment，先稳定合同与主链路，不引入真实 provider 依赖。
    public IdeaAssessmentResult assess(AssessIdeaRequest request) {
        String focus = firstMeaningful(request.rawDescription(), request.noteSummary(), request.title(), "this idea");
        String conciseFocus = collapseSentence(focus);
        String targetUser = request.sourceNoteId() == null
            ? "Independent builders exploring a new execution workflow"
            : "People trying to turn notes into clear follow-up actions";

        List<String> mvpPath = new ArrayList<>();
        mvpPath.add("Clarify the smallest user problem behind: " + conciseFocus);
        if (request.sourceNoteId() != null && request.sourceNoteTitle() != null) {
            mvpPath.add("Review whether note context from \"" + request.sourceNoteTitle() + "\" supports the hypothesis");
        } else {
            mvpPath.add("Validate the core assumption with one real workflow");
        }

        List<String> nextActions = new ArrayList<>();
        nextActions.add("Write a one-sentence problem statement");
        nextActions.add("Interview 3 target users about the current workaround");
        if (request.noteKeyPoints() != null && !request.noteKeyPoints().isEmpty()) {
            nextActions.add("Turn the strongest note key point into a validation question");
        }

        List<String> risks = new ArrayList<>();
        risks.add("The value proposition may overlap with existing task tools");
        if (request.sourceNoteId() != null) {
            risks.add("The source note may capture an isolated insight instead of a repeatable workflow problem");
        }

        return new IdeaAssessmentResult(
            "Users struggle to move from captured insight to consistent execution around " + conciseFocus,
            targetUser,
            "If the workflow turns insight capture into explicit next steps, users will act on more of what they already know",
            mvpPath,
            nextActions,
            risks,
            "This assessment is generated from the current idea fields and optional source note context using the local stub agent"
        );
    }

    private String firstMeaningful(String... values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = collapseSentence(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "this idea";
    }

    private String collapseSentence(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120).trim() : normalized;
    }
}
