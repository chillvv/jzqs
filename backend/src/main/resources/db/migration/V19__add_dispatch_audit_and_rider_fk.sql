ALTER TABLE dispatch_assignments
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE dispatch_assignments
    ADD COLUMN rider_profile_id BIGINT NULL,
    ADD CONSTRAINT fk_dispatch_assignments_rider FOREIGN KEY (rider_profile_id) REFERENCES rider_profiles(id);

ALTER TABLE delivery_receipts
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
