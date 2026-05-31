CREATE TABLE maintenance_job_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_type VARCHAR(64) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    time_range_label VARCHAR(255) NOT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    duration_ms BIGINT NULL,
    scanned_count INT NOT NULL DEFAULT 0,
    deleted_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    message VARCHAR(255) NOT NULL,
    error_detail TEXT NULL,
    operator_name VARCHAR(64) NULL,
    metadata_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_job_logs_type_started
    ON maintenance_job_logs (job_type, started_at DESC);
