ALTER TABLE gait_data_points
    ADD hardware_timestamp_ms BIGINT NULL;

ALTER TABLE users
DROP
COLUMN body_weight_kg;

ALTER TABLE users
    ADD body_weight_kg DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
DROP
COLUMN effective_flex_length;

ALTER TABLE gait_data_points
DROP
COLUMN foot_roll_angle_x;

ALTER TABLE gait_data_points
DROP
COLUMN humidity_rh;

ALTER TABLE gait_data_points
DROP
COLUMN impact_shockwave_z;

ALTER TABLE gait_data_points
DROP
COLUMN pitch_angle_y;

ALTER TABLE gait_data_points
DROP
COLUMN roll_over_parity;

ALTER TABLE gait_data_points
DROP
COLUMN step_id;

ALTER TABLE gait_data_points
DROP
COLUMN symmetry_index;

ALTER TABLE gait_data_points
DROP
COLUMN temperature_c;

ALTER TABLE gait_data_points
DROP
COLUMN trajectory_x;

ALTER TABLE gait_data_points
DROP
COLUMN trajectory_y;

ALTER TABLE gait_data_points
DROP
COLUMN trajectory_z;

ALTER TABLE gait_data_points
    ADD effective_flex_length DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
    ADD foot_roll_angle_x DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
    ADD humidity_rh DECIMAL(4, 2) NULL;

ALTER TABLE gait_data_points
    ADD impact_shockwave_z DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
    MODIFY is_swing_phase BIT (1) NULL;

ALTER TABLE gait_data_points
    ADD pitch_angle_y DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
    ADD roll_over_parity DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
    ADD step_id BIGINT NULL;

ALTER TABLE gait_data_points
    ADD symmetry_index DECIMAL(5, 2) NULL;

ALTER TABLE gait_data_points
    ADD temperature_c DECIMAL(4, 2) NULL;

ALTER TABLE gait_data_points
    ADD trajectory_x DECIMAL(10, 4) NULL;

ALTER TABLE gait_data_points
    ADD trajectory_y DECIMAL(10, 4) NULL;

ALTER TABLE gait_data_points
    ADD trajectory_z DECIMAL(10, 4) NULL;