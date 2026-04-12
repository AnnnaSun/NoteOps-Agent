package com.noteops.agent.service.trend;

import com.noteops.agent.model.trend.TrendAnalysisPayload;

public interface TrendAgent {

    TrendAnalysisPayload analyze(AnalyzeTrendRequest request);
}
