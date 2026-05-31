SET @add_visible_to_customer = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'meal_slot_orders'
              AND COLUMN_NAME = 'visible_to_customer'
        ),
        'SELECT 1',
        'ALTER TABLE meal_slot_orders ADD COLUMN visible_to_customer BOOLEAN DEFAULT TRUE'
    )
);

PREPARE add_visible_to_customer_stmt FROM @add_visible_to_customer;
EXECUTE add_visible_to_customer_stmt;
DEALLOCATE PREPARE add_visible_to_customer_stmt;

SET @add_related_order_id = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'wallet_transactions'
              AND COLUMN_NAME = 'related_order_id'
        ),
        'SELECT 1',
        'ALTER TABLE wallet_transactions ADD COLUMN related_order_id BIGINT'
    )
);

PREPARE add_related_order_id_stmt FROM @add_related_order_id;
EXECUTE add_related_order_id_stmt;
DEALLOCATE PREPARE add_related_order_id_stmt;
