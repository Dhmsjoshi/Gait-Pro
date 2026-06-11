package com.gait.biomedicaltwin.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "gait_data_points", indexes = {
        @Index(name = "idx_session_timestamp", columnList = "session_id, timestamp")
})
public class GaitDataPoint extends BaseAuditModel{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id",  nullable = false)
    @JsonIgnore
    private GaitSession session;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    //Duel shoe Mapping & Asymmetry Tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "foot_side",nullable = false, length = 10)
    private FootSide footSide;

    //--- RAW SENSOR METRICS ---
    @Column(name = "impact_shockwave_z", columnDefinition = "DECIMAL(5,2)")
    private Double impactShockWaveZ;

    @Column(name = "foot_roll_angle_x",columnDefinition = "DECIMAL(5,2)")
    private Double footRollAngleX;

    @Column(name = "pitch_angle_y",columnDefinition = "DECIMAL(5,2)")
    private Double pitchAngleY;

    @Column(name = "temperature_c", columnDefinition = "DECIMAL(4,2)")
    private Double temperatureC;

    @Column(name = "humidity_rh",columnDefinition = "DECIMAL(4,2)")
    private Double humidityRh;

    @Column(name = "step_id")
    private UUID stepId; // for grouping

    // --- CALCULATED BIO-MECHANICAL METRICS ---

    @Column(name = "stance_phase_duration_ms")
    private Long stancePhaseDurationMs;

    @Column(name = "effective_flex_length", columnDefinition = "DECIMAL(5,2)")
    private Double effectiveFlexLength;

    @Column(name = "current_cadence")
    private Integer currentCadence;

    @Column(name = "step_interval_ms")
    private Long stepIntervalMs;

    @Column(name = "symmetry_index", columnDefinition = "DECIMAL(5,2)")
    private Double symmetryIndex;

    @Column(name = "roll_over_parity", columnDefinition = "DECIMAL(5,2)")
    private Double rollOverParity;

    @Column(name = "trajectory_x", columnDefinition = "DECIMAL(10,4)")
    private Double trajectoryX;

    @Column(name = "trajectory_z", columnDefinition = "DECIMAL(10,4)")
    private Double trajectoryZ;

    @Column(name = "trajectory_y", columnDefinition = "DECIMAL(10,4)")
    private Double trajectoryY;


    // --- FLAGS ---
    @Column(name = "is_faulty_step", nullable = false)
    private Boolean isFaultyStep = false;

    @Column(name = "is_fatigued", nullable = false)
    private Boolean isFatigued = false;

    @Column(name = "is_swing_phase")
    private Boolean isSwingPhase = false;


    @Transient
    public UUID getSessionId() {
        return session != null ? session.getId() : null;
    }

    public String toCsvLine() {
        return String.format("%s,%s,%s,%s,%s,%s,%s,%b\n",
                this.timestamp,
                this.trajectoryX,
                this.trajectoryY,
                this.trajectoryZ,
                this.rollOverParity,
                this.pitchAngleY,           // Aapki entity mein hai
                this.footRollAngleX,        // Aapki entity mein ye naam hai
                this.isSwingPhase);         // Aapki entity mein hai
    }
}
