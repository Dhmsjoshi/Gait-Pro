-- =========================================================================
-- 1. GAIT SESSIONS UPDATES
-- =========================================================================
-- is_archived pehle se V1 mein hai, toh sirf missing is_processed add karenge
ALTER TABLE gait_sessions ADD is_processed BIT(1) NOT NULL DEFAULT 0;

-- =========================================================================
-- 2. GAIT SNAPSHOTS UPDATES
-- =========================================================================
-- foot_side ko VARCHAR(10) se badhakar VARCHAR(255) kar rahe hain for flexible alignment
ALTER TABLE gait_snapshots MODIFY foot_side VARCHAR(255);