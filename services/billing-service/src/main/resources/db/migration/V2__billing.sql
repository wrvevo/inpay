CREATE TABLE billing_merchants (
 id uuid PRIMARY KEY, tenant uuid NOT NULL UNIQUE, name varchar(200) NOT NULL,
 plan varchar(30) NOT NULL, status varchar(20) NOT NULL CHECK(status IN ('PENDING','APPROVED','BLOCKED')),
 requested_by text NOT NULL, approved_by text, evidence text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE billing_commands (kind text NOT NULL, key uuid NOT NULL, actor text NOT NULL, request jsonb NOT NULL, result jsonb NOT NULL, PRIMARY KEY(kind,key));
CREATE TABLE billing_invoices (
 id uuid PRIMARY KEY, merchant_id uuid NOT NULL REFERENCES billing_merchants(id), customer_ref varchar(128) NOT NULL,
 amount bigint NOT NULL CHECK(amount>0), method varchar(20) NOT NULL, due_date date NOT NULL,
 conditions jsonb NOT NULL, status varchar(30) NOT NULL DEFAULT 'OPEN', created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX billing_invoices_due ON billing_invoices(status,due_date,merchant_id);
CREATE TABLE billing_subscriptions (
 id uuid PRIMARY KEY, merchant_id uuid NOT NULL REFERENCES billing_merchants(id), customer_ref varchar(128) NOT NULL,
 amount bigint NOT NULL CHECK(amount>0), method varchar(20) NOT NULL, anchor_day int NOT NULL CHECK(anchor_day BETWEEN 1 AND 31),
 next_on date NOT NULL, active boolean NOT NULL DEFAULT true, conditions jsonb NOT NULL);
CREATE INDEX billing_subscriptions_due ON billing_subscriptions(next_on) WHERE active;
CREATE TABLE billing_cycles (subscription_id uuid NOT NULL REFERENCES billing_subscriptions(id), cycle date NOT NULL,
 invoice_id uuid NOT NULL REFERENCES billing_invoices(id), PRIMARY KEY(subscription_id,cycle));
CREATE TABLE billing_costs (
 id uuid PRIMARY KEY, invoice_id uuid NOT NULL UNIQUE REFERENCES billing_invoices(id), provider text NOT NULL,
 provider_operation text NOT NULL, gross bigint NOT NULL, provider_cost bigint NOT NULL,
 gateway_fee bigint NOT NULL, plan text NOT NULL, settled_at timestamptz NOT NULL, deposit_reference text NOT NULL,
 UNIQUE(provider,provider_operation));
CREATE TABLE billing_events (provider text NOT NULL,event_id text NOT NULL,payload jsonb NOT NULL,PRIMARY KEY(provider,event_id));
CREATE TABLE billing_payables (id uuid PRIMARY KEY,merchant_id uuid NOT NULL REFERENCES billing_merchants(id),
 description varchar(200) NOT NULL, amount bigint NOT NULL CHECK(amount>0),due_date date NOT NULL,status text NOT NULL DEFAULT 'PLANNED');
CREATE TABLE billing_notifications (id uuid PRIMARY KEY,invoice_id uuid NOT NULL REFERENCES billing_invoices(id),
 channel text NOT NULL,recipient_reference text NOT NULL,template text NOT NULL,consent_reference text NOT NULL,
 scheduled_on date NOT NULL,status text NOT NULL DEFAULT 'PENDING_PROVIDER',UNIQUE(invoice_id,channel,scheduled_on,template));
CREATE TABLE billing_audit (id uuid PRIMARY KEY,actor text NOT NULL,action text NOT NULL,resource text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE FUNCTION reject_billing_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Immutable billing ledger'; END; $$;
CREATE TRIGGER billing_cost_immutable BEFORE UPDATE OR DELETE ON billing_costs FOR EACH ROW EXECUTE FUNCTION reject_billing_update();
CREATE TRIGGER billing_audit_immutable BEFORE UPDATE OR DELETE ON billing_audit FOR EACH ROW EXECUTE FUNCTION reject_billing_update();
