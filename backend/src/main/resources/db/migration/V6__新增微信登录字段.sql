ALTER TABLE customers ADD COLUMN openid VARCHAR(64);
ALTER TABLE customers ADD COLUMN session_key VARCHAR(128);

CREATE UNIQUE INDEX uk_customers_openid ON customers(openid);
