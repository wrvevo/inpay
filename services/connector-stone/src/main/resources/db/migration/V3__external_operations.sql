CREATE TABLE external_operations (
    merchant_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    provider_id VARCHAR(128),
    status VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN',
    code VARCHAR(100) NOT NULL DEFAULT 'IN_PROGRESS_OR_INTERRUPTED',
    http_status INTEGER NOT NULL DEFAULT 0,
    uncertain BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (merchant_id, operation_id)
);
