ALTER TABLE wallet_transactions
    ADD COLUMN related_transaction_id BIGINT NULL,
    ADD COLUMN refunded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN refund_reason_code VARCHAR(64) NULL,
    ADD COLUMN refund_reason_text VARCHAR(255) NULL;

CREATE INDEX idx_wallet_transactions_related_transaction
    ON wallet_transactions (related_transaction_id);

CREATE INDEX idx_wallet_transactions_related_aftersale
    ON wallet_transactions (related_aftersale_id);
