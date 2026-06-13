package com.gait.biomedicaltwin.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseAuditModel{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "VARCHAR(36)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "height_cm")
    private Double heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "gait_type", nullable = false, length = 20)
    private GaitType gaitType;

    @Column(name = "target_cadence",nullable = false)
    private Integer targetCadence;

    //Medical weight limit validation
    @Column(name = "body_weight_kg", columnDefinition = "DECIMAL(5,2)")
    private Double bodyWeightKg;

    //Doctor's safe Threshold in %
    @Column(name = "max_allowed_weight_percentage")
    private Double maxAllowedWeightPercentage;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GaitSession> sessions;

}
