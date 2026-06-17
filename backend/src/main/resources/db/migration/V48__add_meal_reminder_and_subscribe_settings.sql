ALTER TABLE admin_settings
    ADD COLUMN meal_reminder_popup_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN delivery_subscribe_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN delivery_subscribe_trigger_time VARCHAR(5) NOT NULL DEFAULT '17:30';

UPDATE admin_settings
SET meal_reminder_popup_enabled = TRUE,
    delivery_subscribe_enabled = TRUE,
    delivery_subscribe_trigger_time = '17:30'
WHERE id = 1;
