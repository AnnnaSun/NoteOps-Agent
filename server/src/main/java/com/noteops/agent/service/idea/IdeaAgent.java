package com.noteops.agent.service.idea;

import com.noteops.agent.model.idea.IdeaAssessmentResult;

public interface IdeaAgent {

    IdeaAssessmentResult assess(AssessIdeaRequest request);
}
