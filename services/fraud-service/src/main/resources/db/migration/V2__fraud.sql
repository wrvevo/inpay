CREATE TABLE assessments(id uuid PRIMARY KEY,merchant_id uuid NOT NULL,payment_id uuid NOT NULL,score int NOT NULL CHECK(score BETWEEN 0 AND 100),decision text NOT NULL,policy_version text NOT NULL,reasons text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX assessments_payment ON assessments(merchant_id,payment_id);
CREATE TABLE velocity_buckets(merchant_id uuid NOT NULL,subject_hmac text NOT NULL,window_start timestamptz NOT NULL,attempts int NOT NULL,PRIMARY KEY(merchant_id,subject_hmac,window_start));
