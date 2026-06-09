CREATE TABLE address_reference_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_address_id BIGINT NOT NULL,
    reference_image_url VARCHAR(1024) NOT NULL,
    source_order_id BIGINT NULL,
    updated_by_rider_name VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_address_reference_images_address UNIQUE (customer_address_id)
);

CREATE INDEX idx_address_reference_images_address
    ON address_reference_images (customer_address_id);
