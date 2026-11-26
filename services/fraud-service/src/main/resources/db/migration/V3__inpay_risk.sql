ALTER TABLE assessments ADD COLUMN provider text NOT NULL DEFAULT 'INPAY_RISK';
ALTER TABLE assessments ADD COLUMN tokens int NOT NULL DEFAULT 0;
CREATE TABLE chargeback_lessons (
  merchant_id uuid NOT NULL,
  device_key text NOT NULL,
  confirmed int NOT NULL DEFAULT 0,
  legitimate int NOT NULL DEFAULT 0,
  PRIMARY KEY (merchant_id, device_key)
);
