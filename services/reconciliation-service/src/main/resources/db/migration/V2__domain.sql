CREATE TABLE reconciliation_cases(id uuid PRIMARY KEY,merchant_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
