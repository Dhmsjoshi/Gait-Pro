package com.gait.biomedicaltwin.controllers;

import com.gait.biomedicaltwin.entities.GaitDataPoint;
import com.gait.biomedicaltwin.repositories.GaitDataPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final GaitDataPointRepository dataPointRepository;

    // SCADA Dashboard ke liye Latest 20 Data Points ka stream
    @GetMapping("/stream/{userId}")
    public ResponseEntity<List<GaitDataPoint>> getLatestDataStream(@PathVariable UUID userId) {
        List<GaitDataPoint> latestData = dataPointRepository
                .findTop20BySessionUserIdOrderByTimestampDesc(userId);

        if (latestData.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(latestData);
    }
}
