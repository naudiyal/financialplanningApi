-- One-time cleanup: drop the legacy pre-cycle storage table.
--
-- Safe to run only after confirming the application is using:
-- - app_user_financial_plan_cycle
-- - app_user_financial_plan_settings
--
-- This removes the old table that is no longer used by the current app.

BEGIN;

DROP TABLE IF EXISTS app_user_financial_plan;

COMMIT;