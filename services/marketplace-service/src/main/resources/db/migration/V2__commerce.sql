CREATE TABLE api_resources (
 tenant uuid NOT NULL, kind varchar(40) NOT NULL, id varchar(80) NOT NULL,
 operation_key varchar(128) NOT NULL, request jsonb NOT NULL, payload jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(tenant,kind,id),
 UNIQUE(tenant,kind,operation_key));
CREATE INDEX api_resources_page ON api_resources(tenant,kind,created_at DESC,id);
