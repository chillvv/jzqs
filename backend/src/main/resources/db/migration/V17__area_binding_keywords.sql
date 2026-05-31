ALTER TABLE dispatch_area_bindings ADD COLUMN keywords VARCHAR(255);
UPDATE dispatch_area_bindings SET keywords = area_code;