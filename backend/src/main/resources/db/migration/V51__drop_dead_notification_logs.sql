DROP TABLE IF EXISTS notification_logs;

DELETE FROM maintenance_cleanup_settings
WHERE module_key = 'NOTIFICATION_LOG';
