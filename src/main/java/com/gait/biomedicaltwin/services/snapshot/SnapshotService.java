package com.gait.biomedicaltwin.services.snapshot;

import com.gait.biomedicaltwin.entities.FootSide;
import com.gait.biomedicaltwin.entities.GaitSession;

import java.util.UUID;

public interface SnapshotService {
    void checkAndTriggerSnapshots(GaitSession session);
}
