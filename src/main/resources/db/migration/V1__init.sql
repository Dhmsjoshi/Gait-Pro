-- 1. USERS TABLE
CREATE TABLE users
(
    id                            BINARY(16)   NOT NULL,
    created_at                    DATETIME     NOT NULL,
    updated_at                    DATETIME     NULL,
    name                          VARCHAR(100) NOT NULL,
    height_cm                     DOUBLE       NULL,
    gait_type                     VARCHAR(20)  NOT NULL,
    target_cadence                INT          NOT NULL,
    body_weight_kg                DECIMAL(5, 2)NULL,
    max_allowed_weight_percentage DOUBLE       NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- 2. GAIT SESSIONS TABLE
CREATE TABLE gait_sessions
(
    id                   BINARY(16) NOT NULL,
    created_at           DATETIME   NOT NULL,
    updated_at           DATETIME   NULL,
    user_id              BINARY(16) NOT NULL,
    start_time           DATETIME   NOT NULL,
    end_time             DATETIME   NULL,
    overall_health_score DOUBLE     NULL,
    is_archived          BIT(1)     NOT NULL DEFAULT 0,
    CONSTRAINT pk_gait_sessions PRIMARY KEY (id)
);

-- 3. GAIT DATA POINTS TABLE
CREATE TABLE gait_data_points
(
    id                       BIGINT AUTO_INCREMENT NOT NULL,
    created_at               DATETIME    NOT NULL,
    updated_at               DATETIME    NULL,
    session_id               BINARY(16)  NOT NULL,
    timestamp                DATETIME    NOT NULL,
    foot_side                VARCHAR(10) NOT NULL,
    impact_shockwave_z       DECIMAL(5, 2) NULL,
    foot_roll_angle_x        DECIMAL(5, 2) NULL,
    pitch_angle_y            DECIMAL(5, 2) NULL,
    temperature_c            DECIMAL(4, 2) NULL,
    humidity_rh              DECIMAL(4, 2) NULL,
    step_id                  BINARY(16)  NULL,
    stance_phase_duration_ms BIGINT      NULL,
    effective_flex_length    DECIMAL(5, 2) NULL,
    current_cadence          INT         NULL,
    step_interval_ms         BIGINT      NULL,
    symmetry_index           DECIMAL(5, 2) NULL,
    roll_over_parity         DECIMAL(5, 2) NULL,
    trajectory_x             DECIMAL(10, 4) NULL,
    trajectory_z             DECIMAL(10, 4) NULL,
    trajectory_y             DECIMAL(10, 4) NULL,
    is_faulty_step           BIT(1)      NOT NULL DEFAULT 0,
    is_fatigued              BIT(1)      NOT NULL DEFAULT 0,
    is_swing_phase           BIT(1)      NOT NULL DEFAULT 0,
    CONSTRAINT pk_gait_data_points PRIMARY KEY (id)
);

-- 4. GAIT SNAPSHOTS TABLE
CREATE TABLE gait_snapshots
(
    id                 BINARY(16)   NOT NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NULL,
    session_id         BINARY(16)   NOT NULL,
    distance_interval  DOUBLE       NULL,
    foot_side          VARCHAR(10)  NULL,
    trajectory_json    TEXT         NULL,
    avg_ros_parity     DOUBLE       NULL,
    avg_symmetry_index DOUBLE       NULL,
    fatigue_index      DOUBLE       NULL,
    CONSTRAINT pk_gait_snapshots PRIMARY KEY (id)
);

-- --- INDEXES FOR PERFORMANCE OPTIMIZATION ---

-- Data point lookup Optimization (Aapki Entity ke Index se matched)
CREATE INDEX idx_session_timestamp ON gait_data_points (session_id, timestamp);

-- User session history lookup Optimization (Aapki Entity ke Index se matched)
CREATE INDEX idx_user_sessions ON gait_sessions (user_id);

-- Snapshot fetch performance Optimization
CREATE INDEX idx_snapshot_session ON gait_snapshots (session_id);


-- --- FOREIGN KEY CONSTRAINTS ---

ALTER TABLE gait_data_points
    ADD CONSTRAINT FK_GAIT_DATA_POINTS_ON_SESSION FOREIGN KEY (session_id) REFERENCES gait_sessions (id);

ALTER TABLE gait_sessions
    ADD CONSTRAINT FK_GAIT_SESSIONS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE gait_snapshots
    ADD CONSTRAINT FK_GAIT_SNAPSHOTS_ON_SESSION FOREIGN KEY (session_id) REFERENCES gait_sessions (id);