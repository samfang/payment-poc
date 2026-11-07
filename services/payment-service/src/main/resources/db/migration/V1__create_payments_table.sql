CREATE TABLE payments (
    id           UUID                     NOT NULL,
    owner_id     UUID                     NOT NULL,
    amount       NUMERIC(19, 4)           NOT NULL,
    currency_code VARCHAR(3)              NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_payments_owner_created ON payments (owner_id, created_at, id);
