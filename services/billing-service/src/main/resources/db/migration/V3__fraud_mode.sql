ALTER TABLE billing_merchants
  ADD COLUMN fraud_mode varchar(32) NOT NULL DEFAULT 'INPAY_RISK';
