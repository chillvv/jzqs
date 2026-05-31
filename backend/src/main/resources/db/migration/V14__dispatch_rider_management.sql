CREATE TABLE dispatch_area_bindings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    area_code VARCHAR(64) NOT NULL,
    default_rider_profile_id BIGINT NOT NULL,
    backup_rider_profile_id BIGINT,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_dispatch_area_bindings_area_code ON dispatch_area_bindings (area_code);

CREATE TABLE dispatch_reassignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reassign_level VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    from_rider_name VARCHAR(64),
    to_rider_name VARCHAR(64) NOT NULL,
    to_area_code VARCHAR(64),
    serve_date DATE NOT NULL,
    meal_period VARCHAR(16),
    sync_default_binding BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(255),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispatch_reassignments_serve_date ON dispatch_reassignments (serve_date, meal_period);

INSERT INTO dispatch_area_bindings (area_code, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at)
SELECT
    rp.default_area_code,
    rp.id,
    NULL,
    COALESCE(rp.assigned_by, '系统初始化'),
    COALESCE(rp.assigned_at, CURRENT_TIMESTAMP)
FROM rider_profiles rp
WHERE rp.default_area_code IS NOT NULL
  AND rp.default_area_code <> ''
  AND rp.auth_status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM dispatch_area_bindings dab
      WHERE dab.area_code = rp.default_area_code
  );
