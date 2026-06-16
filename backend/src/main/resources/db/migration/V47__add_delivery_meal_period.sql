ALTER TABLE meal_slot_orders
    ADD COLUMN delivery_meal_period VARCHAR(16) NULL AFTER meal_period;

UPDATE meal_slot_orders
SET delivery_meal_period = meal_period
WHERE delivery_meal_period IS NULL OR delivery_meal_period = '';

ALTER TABLE meal_slot_orders
    MODIFY COLUMN delivery_meal_period VARCHAR(16) NOT NULL;

ALTER TABLE subscription_rules
    ADD COLUMN lunch_delivery_meal_period VARCHAR(16) NULL AFTER lunch_quantity,
    ADD COLUMN dinner_delivery_meal_period VARCHAR(16) NULL AFTER dinner_quantity;

UPDATE subscription_rules
SET lunch_delivery_meal_period = 'LUNCH'
WHERE lunch_enabled = TRUE
  AND (lunch_delivery_meal_period IS NULL OR lunch_delivery_meal_period = '');

UPDATE subscription_rules
SET dinner_delivery_meal_period = 'DINNER'
WHERE dinner_enabled = TRUE
  AND (dinner_delivery_meal_period IS NULL OR dinner_delivery_meal_period = '');

UPDATE subscription_rules
SET lunch_delivery_meal_period = 'LUNCH'
WHERE lunch_delivery_meal_period IS NULL OR lunch_delivery_meal_period = '';

UPDATE subscription_rules
SET dinner_delivery_meal_period = 'DINNER'
WHERE dinner_delivery_meal_period IS NULL OR dinner_delivery_meal_period = '';

ALTER TABLE subscription_rules
    MODIFY COLUMN lunch_delivery_meal_period VARCHAR(16) NOT NULL DEFAULT 'LUNCH',
    MODIFY COLUMN dinner_delivery_meal_period VARCHAR(16) NOT NULL DEFAULT 'DINNER';
