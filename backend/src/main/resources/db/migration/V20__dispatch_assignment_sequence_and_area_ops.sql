ALTER TABLE dispatch_assignments
    ADD COLUMN sequence_number INT NOT NULL DEFAULT 0;

CREATE INDEX idx_da_area_sequence
    ON dispatch_assignments (area_code, sequence_number);

ALTER TABLE rider_address_bindings
    MODIFY COLUMN rider_profile_id BIGINT NULL;
