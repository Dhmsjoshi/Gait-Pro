package com.gait.biomedicaltwin.services.analytics;

import com.gait.biomedicaltwin.entities.GaitDataPoint;

public interface AnalyticsService {

    void performBioMechanicalAnalysis(GaitDataPoint gaitDataPoint);
}
