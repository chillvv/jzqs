CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_users_phone ON users (phone);
CREATE UNIQUE INDEX uk_users_username ON users (username);

INSERT INTO users (username, phone, display_name, role, status, password_hash, created_at, updated_at)
SELECT 'owner', '17671863805', '商家后台', 'OWNER', 'ENABLED',
       CONCAT('{sha256}', SHA2(CONCAT('17671863805', ':', '17671863805'), 256)),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE phone = '17671863805'
);

UPDATE users
SET password_hash = CONCAT('{sha256}', SHA2(CONCAT('17671863805', ':', '17671863805'), 256)),
    role = CASE
        WHEN role IS NULL OR role = '' THEN 'OWNER'
        ELSE role
    END,
    status = 'ENABLED',
    display_name = CASE
        WHEN display_name IS NULL OR display_name = '' THEN '商家后台'
        ELSE display_name
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE phone = '17671863805';
