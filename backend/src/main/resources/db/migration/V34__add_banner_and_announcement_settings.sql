ALTER TABLE admin_settings
ADD COLUMN banner_images TEXT,
ADD COLUMN popup_announcement_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN popup_announcement_content TEXT;

UPDATE admin_settings
SET banner_images = '["../../assets/hero-new.jpg"]',
    popup_announcement_enabled = FALSE,
    popup_announcement_content = ''
WHERE id = 1;
