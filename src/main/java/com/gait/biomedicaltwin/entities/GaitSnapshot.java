package com.gait.biomedicaltwin.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "gait_snapshots")
public class GaitSnapshot extends BaseAuditModel{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "VARCHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "VARCHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private GaitSession session;

    private Double distanceInterval; // 250, 500, 750, 1000

    @Enumerated(EnumType.STRING)
    private FootSide footSide;

    @Column(columnDefinition = "TEXT")
    private String trajectoryJson;

    private Double avgRosParity;     // Aggregated ROS
    private Double avgSymmetryIndex; // Aggregated Symmetry
    private Double fatigueIndex;     // Aggregated Fatigue Score




}
