package com.gait.biomedicaltwin.controllers;

import com.gait.biomedicaltwin.dtos.RawSensorDto;
import com.gait.biomedicaltwin.services.ingestion.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;


    @PostMapping("/sensor-data")
    public ResponseEntity<String> receiveSensorData(@Valid @RequestBody RawSensorDto dto) {
        try {
            ingestionService.saveAndAnalyze(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Data processed and saved successfully");
        } catch (RuntimeException e) {
            // Agar User nahi mila ya koi aur logic error hua
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error: " + e.getMessage());
        }
    }





}
