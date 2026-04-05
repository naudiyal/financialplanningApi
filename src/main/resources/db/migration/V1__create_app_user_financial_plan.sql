CREATE TABLE IF NOT EXISTS app_user_financial_plan (
    user_sub VARCHAR(255) PRIMARY KEY,
    email VARCHAR(320),
    display_name VARCHAR(255),
    plan_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_user_financial_plan_email
    ON app_user_financial_plan (email);