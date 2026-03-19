package com.noteops.agent.application.capture;

import com.noteops.agent.domain.capture.CaptureAiProvider;

public interface CaptureProviderClient extends CaptureAnalysisClient {

    CaptureAiProvider provider();
}
