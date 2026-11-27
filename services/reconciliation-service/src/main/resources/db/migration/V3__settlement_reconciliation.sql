ALTER TABLE reconciliation_cases
    ADD COLUMN provider varchar(64),
    ADD COLUMN business_date date,
    ADD COLUMN status varchar(16) CHECK (status IN ('NO_DATA', 'MATCHED', 'DIFFERENCES')),
    ADD COLUMN reference_count bigint CHECK (reference_count >= 0),
    ADD COLUMN difference_count bigint CHECK (difference_count BETWEEN 0 AND reference_count),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT reconciliation_case_scope UNIQUE (merchant_id, provider, business_date);

CREATE TABLE reconciliation_entries (
    merchant_id uuid NOT NULL,
    provider varchar(64) NOT NULL,
    business_date date NOT NULL,
    reference varchar(150) NOT NULL,
    source varchar(8) NOT NULL CHECK (source IN ('EXPECTED', 'SETTLED')),
    amount_minor bigint NOT NULL,
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (merchant_id, provider, business_date, source, reference)
);
