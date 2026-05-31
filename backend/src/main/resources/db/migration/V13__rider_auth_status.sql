ALTER TABLE rider_profiles
    ADD COLUMN current_openid VARCHAR(128);

ALTER TABLE rider_profiles
    ADD COLUMN auth_status VARCHAR(32) NOT NULL DEFAULT 'UNASSIGNED';

ALTER TABLE rider_profiles
    ADD COLUMN display_name VARCHAR(64);

ALTER TABLE rider_profiles
    ADD COLUMN first_login_at TIMESTAMP;

ALTER TABLE rider_profiles
    ADD COLUMN last_login_at TIMESTAMP;

ALTER TABLE rider_profiles
    ADD COLUMN assigned_at TIMESTAMP;

ALTER TABLE rider_profiles
    ADD COLUMN assigned_by VARCHAR(64);

UPDATE rider_profiles
SET auth_status = 'ACTIVE',
    display_name = COALESCE(display_name, rider_name)
WHERE auth_status = 'UNASSIGNED';
