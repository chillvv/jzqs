ALTER TABLE aftersale_cases
    ADD COLUMN source_category VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN issue_param_summary VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN estimated_loss_meals INT NOT NULL DEFAULT 0,
    ADD COLUMN settled_loss_meals INT NOT NULL DEFAULT 0,
    ADD COLUMN gift_zero_meal_count INT NOT NULL DEFAULT 0,
    ADD COLUMN gift_veggie_juice_count INT NOT NULL DEFAULT 0;

UPDATE aftersale_cases
SET source_category = CASE
        WHEN source = 'AUTO_REFUND' THEN 'AUTO_REFUND'
        ELSE 'NORMAL'
    END,
    issue_param_summary = '',
    estimated_loss_meals = 0,
    settled_loss_meals = CASE WHEN status = 'COMPLETED' THEN wallet_delta ELSE 0 END,
    gift_zero_meal_count = 0,
    gift_veggie_juice_count = 0;

CREATE INDEX idx_aftersale_cases_requested_source_category
    ON aftersale_cases (requested_at, source_category);
