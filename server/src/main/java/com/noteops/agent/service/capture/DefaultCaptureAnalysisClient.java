package com.noteops.agent.service.capture;

import com.noteops.agent.service.ai.AiClient;
import com.noteops.agent.service.ai.AiRequest;
import com.noteops.agent.service.ai.AiResponse;
import com.noteops.agent.service.ai.AiResponseMode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultCaptureAnalysisClient implements CaptureAnalysisClient {

    private final AiClient aiClient;

    public DefaultCaptureAnalysisClient(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    // 组装 Capture 分析请求并交给统一 AI 路由。
    public AnalyzeResponse analyze(AnalyzeRequest request) {
        AiResponse response = aiClient.analyze(new AiRequest(
            request.userId(),
            request.traceId(),
            ROUTE_KEY,
            REQUEST_TYPE,
            TOOL_NAME,
            CaptureAnalysisJsonSchema.systemPrompt(),
            CaptureAnalysisJsonSchema.userPrompt(request),
            AiResponseMode.JSON_OBJECT,
            CaptureAnalysisJsonSchema.schema(),
            inputMetadata(request),
            null
        ));
        return new AnalyzeResponse(response.provider(), response.model(), response.rawText(), response.durationMs());
    }

    private Map<String, Object> inputMetadata(AnalyzeRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_type", request.sourceType().name());
        if (request.sourceUrl() != null) {
            metadata.put("source_url", request.sourceUrl());
        }
        return metadata;
    }
}
