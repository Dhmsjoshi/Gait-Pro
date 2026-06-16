package com.gait.biomedicaltwin.services.ingestion;

import com.gait.biomedicaltwin.dtos.RawSensorDto;

public interface IngestionService {

    void saveAndAnalyze(RawSensorDto dto);

    //  Test simulator ke liye explicitly session close karne ka contract
    void forceCloseSessionForTest(String userId);
}
