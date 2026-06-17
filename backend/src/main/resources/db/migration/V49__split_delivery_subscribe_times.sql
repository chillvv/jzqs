ALTER TABLE admin_settings
    ADD COLUMN delivery_subscribe_lunch_time VARCHAR(5) NOT NULL DEFAULT '11:30',
    ADD COLUMN delivery_subscribe_dinner_time VARCHAR(5) NOT NULL DEFAULT '17:30';

UPDATE admin_settings
SET delivery_subscribe_lunch_time = CASE
        WHEN delivery_subscribe_trigger_time IS NULL OR delivery_subscribe_trigger_time = '' THEN '11:30'
        ELSE delivery_subscribe_trigger_time
    END,
    delivery_subscribe_dinner_time = CASE
        WHEN delivery_subscribe_trigger_time IS NULL OR delivery_subscribe_trigger_time = '' THEN '17:30'
        ELSE delivery_subscribe_trigger_time
    END
WHERE id = 1;
