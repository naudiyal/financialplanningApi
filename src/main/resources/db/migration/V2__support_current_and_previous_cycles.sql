CREATE TABLE IF NOT EXISTS app_user_financial_plan_cycle (
    user_sub VARCHAR(255) NOT NULL,
    cycle_slot VARCHAR(16) NOT NULL,
    email VARCHAR(320),
    display_name VARCHAR(255),
    cycle_start_date DATE NOT NULL,
    cycle_end_date DATE NOT NULL,
    plan_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT app_user_financial_plan_cycle_pkey PRIMARY KEY (user_sub, cycle_slot)
);

CREATE INDEX IF NOT EXISTS idx_app_user_financial_plan_cycle_email
    ON app_user_financial_plan_cycle (email);

CREATE INDEX IF NOT EXISTS idx_app_user_financial_plan_cycle_user_cycle
    ON app_user_financial_plan_cycle (user_sub, cycle_slot);

WITH current_cycle AS (
    SELECT
        CASE
            WHEN EXTRACT(DAY FROM CURRENT_DATE) >= 16
                THEN (date_trunc('month', CURRENT_DATE)::date + 15)
            ELSE ((date_trunc('month', CURRENT_DATE) - INTERVAL '1 month')::date + 15)
        END AS cycle_start_date
)
INSERT INTO app_user_financial_plan_cycle (
    user_sub,
    cycle_slot,
    email,
    display_name,
    cycle_start_date,
    cycle_end_date,
    plan_data,
    created_at,
    updated_at
)
SELECT
    existing.user_sub,
    'CURRENT',
    existing.email,
    existing.display_name,
    current_cycle.cycle_start_date,
    (current_cycle.cycle_start_date + INTERVAL '1 month' - INTERVAL '1 day')::date,
    existing.plan_data,
    existing.created_at,
    existing.updated_at
FROM app_user_financial_plan existing
CROSS JOIN current_cycle
ON CONFLICT (user_sub, cycle_slot) DO NOTHING;