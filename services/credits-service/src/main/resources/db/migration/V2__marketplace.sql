-- Este banco pertence exclusivamente ao marketplace; nenhuma FK para o gateway.
CREATE TABLE marketplace_state (tenant uuid PRIMARY KEY, state jsonb NOT NULL, version bigint NOT NULL DEFAULT 0);
CREATE TABLE marketplace_journal (id uuid PRIMARY KEY, tenant uuid NOT NULL, owner text NOT NULL, payload jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX marketplace_journal_owner ON marketplace_journal(tenant,owner,created_at);
CREATE FUNCTION reject_journal_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Marketplace journal is append-only'; END; $$;
CREATE TRIGGER immutable_journal BEFORE UPDATE OR DELETE ON marketplace_journal FOR EACH ROW EXECUTE FUNCTION reject_journal_change();
