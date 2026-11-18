CREATE TABLE provider_receipts(id uuid PRIMARY KEY,merchant_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
