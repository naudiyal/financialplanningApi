ALTER TABLE app_user_financial_plan_settings
    ADD COLUMN IF NOT EXISTS allow_admin_edit BOOLEAN NOT NULL DEFAULT FALSE;
