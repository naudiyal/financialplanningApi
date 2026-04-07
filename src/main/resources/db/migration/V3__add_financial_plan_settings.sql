CREATE TABLE IF NOT EXISTS app_user_financial_plan_settings (
    user_sub VARCHAR(255) PRIMARY KEY,
    email VARCHAR(320),
    display_name VARCHAR(255),
    timeline_type VARCHAR(32) NOT NULL DEFAULT 'MID_TO_MID',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_user_financial_plan_settings_email
    ON app_user_financial_plan_settings (email);

INSERT INTO app_user_financial_plan_settings (
    user_sub,
    email,
    display_name,
    timeline_type,
    created_at,
    updated_at
)
SELECT
    cycle.user_sub,
    MAX(NULLIF(cycle.email, '')) AS email,
    MAX(NULLIF(cycle.display_name, '')) AS display_name,
    'MID_TO_MID',
    MIN(cycle.created_at) AS created_at,
    MAX(cycle.updated_at) AS updated_at
FROM app_user_financial_plan_cycle AS cycle
GROUP BY cycle.user_sub
ON CONFLICT (user_sub) DO NOTHING;