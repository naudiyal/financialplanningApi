ALTER TABLE app_user_financial_plan_settings
    ADD COLUMN IF NOT EXISTS is_premium BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS app_user_financial_plan_cycle_archive (
    user_sub VARCHAR(255) NOT NULL,
    timeline_type VARCHAR(32) NOT NULL,
    cycle_start_date DATE NOT NULL,
    cycle_end_date DATE NOT NULL,
    email VARCHAR(320),
    display_name VARCHAR(255),
    plan_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT app_user_financial_plan_cycle_archive_pkey PRIMARY KEY (user_sub, timeline_type, cycle_start_date, cycle_end_date)
);

CREATE INDEX IF NOT EXISTS idx_app_user_financial_plan_cycle_archive_user_timeline
    ON app_user_financial_plan_cycle_archive (user_sub, timeline_type, cycle_end_date DESC, cycle_start_date DESC);