CREATE TABLE payments (
 id uuid PRIMARY KEY,merchant_id uuid NOT NULL,merchant_order_id varchar(128) NOT NULL,
 amount bigint NOT NULL CHECK(amount>0),currency char(3) NOT NULL CHECK(currency='BRL'),
 instrument_token varchar(128) NOT NULL,status text NOT NULL,
 captured bigint NOT NULL DEFAULT 0 CHECK(captured>=0 AND captured<=amount),
 refunded bigint NOT NULL DEFAULT 0 CHECK(refunded>=0 AND refunded<=captured),
 uncertain boolean NOT NULL DEFAULT false,version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX payments_tenant_created ON payments(merchant_id,created_at);
CREATE TABLE payment_idempotency (
 merchant_id uuid NOT NULL,key varchar(128) NOT NULL,request_hash char(64) NOT NULL,
 payment_id uuid NOT NULL REFERENCES payments(id),PRIMARY KEY(merchant_id,key)
);
CREATE TABLE operations (
 id uuid PRIMARY KEY,payment_id uuid NOT NULL REFERENCES payments(id),type text NOT NULL,
 state text NOT NULL CHECK(state IN ('PLANNED','SENT','UNKNOWN','SUCCEEDED','FAILED')),
 provider text NOT NULL,affiliation_id text NOT NULL,amount bigint NOT NULL CHECK(amount>0),
 provider_reference text,created_at timestamptz NOT NULL DEFAULT now()
);
