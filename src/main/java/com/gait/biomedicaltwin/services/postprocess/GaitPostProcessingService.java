package com.gait.biomedicaltwin.services.postprocess;

import java.util.UUID;

public interface GaitPostProcessingService {
    void processSessionMetricsAsync(UUID sessionId);
}
