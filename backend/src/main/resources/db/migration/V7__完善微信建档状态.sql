ALTER TABLE customers ADD COLUMN profile_completed BOOLEAN DEFAULT FALSE;

UPDATE customers
SET profile_completed = CASE
    WHEN source = 'MINIAPP' AND openid IS NOT NULL THEN TRUE
    ELSE FALSE
END;

CREATE UNIQUE INDEX uk_customers_name_active ON customers(name, active);
