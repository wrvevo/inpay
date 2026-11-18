CREATE TABLE checkout_sessions(id uuid PRIMARY KEY,merchant_id uuid NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
