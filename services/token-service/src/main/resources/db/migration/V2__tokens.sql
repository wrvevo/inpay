CREATE TABLE instruments(token text PRIMARY KEY,merchant_id uuid NOT NULL,provider text NOT NULL,affiliation_id text NOT NULL,active boolean NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
-- Real provider bindings require verified ownership and KMS encryption; not enabled by this scaffold.
CREATE TABLE token_bindings(id uuid PRIMARY KEY,instrument_token text NOT NULL REFERENCES instruments(token),provider_token_ciphertext bytea NOT NULL,kms_key_id text NOT NULL);
