package com.noteops.agent.application.ai;

public interface AiProviderClient {

    AiProvider provider();

    AiResponse analyze(AiRequest request, AiProperties.ResolvedRoute route);
}
