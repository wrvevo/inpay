CREATE TABLE payment_journeys (
 id uuid PRIMARY KEY,
 tenant uuid NOT NULL,
 request_key varchar(128) NOT NULL,
 request_hash varchar(64) NOT NULL,
 merchant_order varchar(128) NOT NULL,
 amount bigint NOT NULL CHECK(amount > 0),
 method varchar(30) NOT NULL,
 provider_id varchar(128),
 status varchar(30) NOT NULL,
 artifacts jsonb NOT NULL DEFAULT '{}',
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tenant,request_key)
);
CREATE INDEX payment_journey_tenant ON payment_journeys(tenant,created_at);
