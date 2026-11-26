CREATE TABLE onboarding_case (
  tenant uuid NOT NULL,
  id uuid NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant,id)
);
CREATE TABLE onboarding_document (
  tenant uuid NOT NULL,
  case_id uuid NOT NULL,
  id uuid NOT NULL,
  kind varchar(32) NOT NULL,
  version integer NOT NULL,
  mime varchar(64) NOT NULL,
  sha256 char(64) NOT NULL,
  key_id varchar(64) NOT NULL,
  nonce bytea,
  ciphertext bytea,
  uploaded_by varchar(256) NOT NULL,
  scan_source varchar(32) NOT NULL,
  decision varchar(16) NOT NULL DEFAULT 'PENDING',
  reviewed_by varchar(256),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (tenant,id),
  UNIQUE (tenant,case_id,kind,version),
  FOREIGN KEY (tenant,case_id) REFERENCES onboarding_case(tenant,id)
);
CREATE INDEX onboarding_document_expiry ON onboarding_document(expires_at) WHERE ciphertext IS NOT NULL;
CREATE TABLE onboarding_audit (
  id bigserial PRIMARY KEY,
  tenant uuid NOT NULL,
  case_id uuid NOT NULL,
  actor varchar(256) NOT NULL,
  event varchar(80) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant,case_id) REFERENCES onboarding_case(tenant,id)
);
CREATE INDEX onboarding_audit_case ON onboarding_audit(tenant,case_id,id);
