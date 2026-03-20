package com.noteops.agent.service.ai;

import com.noteops.agent.config.AiProperties;

public interface AiProviderClient {

    AiProvider provider();

    AiResponse analyze(AiRequest request, AiProperties.ResolvedRoute route);
}
