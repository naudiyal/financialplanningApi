-- One-time backfill for legacy bank balance labels stored in plan_data JSONB.
--
-- Updates these legacy labels:
-- 1. "Checking account balance - primary bank" -> "Account Balance"
-- 2. "Total balance - primary bank" -> "Total Balance"
-- 3. "Checking account balance month end - primary bank" -> "Month End Balance minus Dues"
--
-- Applies to both:
-- - app_user_financial_plan_cycle (current storage)
-- - app_user_financial_plan (legacy storage, if still present)
--
-- Also updates matching incomeSubsections label fields so saved trackers stay consistent.

BEGIN;

-- Current storage table: balanceItems labels
UPDATE app_user_financial_plan_cycle AS plan
SET plan_data = jsonb_set(
    plan.plan_data,
    '{balanceItems}',
    COALESCE(
        (
            SELECT jsonb_agg(
                CASE
                    WHEN item->>'id' = 'checking-balance-chase'
                         AND item->>'label' IN ('Checking account balance - primary bank', 'Checking Account Balance - Chase')
                    THEN jsonb_set(item, '{label}', '"Account Balance"')
                    WHEN item->>'id' = 'total-balance-chase'
                         AND item->>'label' IN ('Total balance - primary bank', 'Total Balance - Chase')
                    THEN jsonb_set(item, '{label}', '"Total Balance"')
                    WHEN item->>'id' = 'checking-balance-month-end-chase'
                         AND item->>'label' IN (
                             'Checking account balance month end - primary bank',
                             'Checking Account Balance @Month End - Chase',
                             'Checking account balance month end - Chase'
                         )
                    THEN jsonb_set(item, '{label}', '"Month End Balance minus Dues"')
                    ELSE item
                END
            )
            FROM jsonb_array_elements(COALESCE(plan.plan_data->'balanceItems', '[]'::jsonb)) AS item
        ),
        '[]'::jsonb
    ),
    true
)
WHERE plan.plan_data->'balanceItems' IS NOT NULL;

-- Current storage table: incomeSubsections labels
UPDATE app_user_financial_plan_cycle AS plan
SET plan_data = jsonb_set(
    plan.plan_data,
    '{incomeSubsections}',
    COALESCE(
        (
            SELECT jsonb_agg(
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            subsection,
                            '{checkingBalanceLabel}',
                            CASE
                                WHEN subsection->>'checkingBalanceLabel' IN ('Checking account balance - primary bank', 'Checking Account Balance - Chase')
                                THEN '"Account Balance"'::jsonb
                                ELSE to_jsonb(subsection->>'checkingBalanceLabel')
                            END,
                            true
                        ),
                        '{totalBalanceLabel}',
                        CASE
                            WHEN subsection->>'totalBalanceLabel' IN ('Total balance - primary bank', 'Total Balance - Chase')
                            THEN '"Total Balance"'::jsonb
                            ELSE to_jsonb(subsection->>'totalBalanceLabel')
                        END,
                        true
                    ),
                    '{monthEndBalanceLabel}',
                    CASE
                        WHEN subsection->>'monthEndBalanceLabel' IN (
                            'Checking account balance month end - primary bank',
                            'Checking Account Balance @Month End - Chase',
                            'Checking account balance month end - Chase'
                        )
                        THEN '"Month End Balance minus Dues"'::jsonb
                        ELSE to_jsonb(subsection->>'monthEndBalanceLabel')
                    END,
                    true
                )
            )
            FROM jsonb_array_elements(COALESCE(plan.plan_data->'incomeSubsections', '[]'::jsonb)) AS subsection
        ),
        '[]'::jsonb
    ),
    true
)
WHERE plan.plan_data->'incomeSubsections' IS NOT NULL;

-- Legacy storage table: balanceItems labels
UPDATE app_user_financial_plan AS plan
SET plan_data = jsonb_set(
    plan.plan_data,
    '{balanceItems}',
    COALESCE(
        (
            SELECT jsonb_agg(
                CASE
                    WHEN item->>'id' = 'checking-balance-chase'
                         AND item->>'label' IN ('Checking account balance - primary bank', 'Checking Account Balance - Chase')
                    THEN jsonb_set(item, '{label}', '"Account Balance"')
                    WHEN item->>'id' = 'total-balance-chase'
                         AND item->>'label' IN ('Total balance - primary bank', 'Total Balance - Chase')
                    THEN jsonb_set(item, '{label}', '"Total Balance"')
                    WHEN item->>'id' = 'checking-balance-month-end-chase'
                         AND item->>'label' IN (
                             'Checking account balance month end - primary bank',
                             'Checking Account Balance @Month End - Chase',
                             'Checking account balance month end - Chase'
                         )
                    THEN jsonb_set(item, '{label}', '"Month End Balance minus Dues"')
                    ELSE item
                END
            )
            FROM jsonb_array_elements(COALESCE(plan.plan_data->'balanceItems', '[]'::jsonb)) AS item
        ),
        '[]'::jsonb
    ),
    true
)
WHERE plan.plan_data->'balanceItems' IS NOT NULL;

-- Legacy storage table: incomeSubsections labels
UPDATE app_user_financial_plan AS plan
SET plan_data = jsonb_set(
    plan.plan_data,
    '{incomeSubsections}',
    COALESCE(
        (
            SELECT jsonb_agg(
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            subsection,
                            '{checkingBalanceLabel}',
                            CASE
                                WHEN subsection->>'checkingBalanceLabel' IN ('Checking account balance - primary bank', 'Checking Account Balance - Chase')
                                THEN '"Account Balance"'::jsonb
                                ELSE to_jsonb(subsection->>'checkingBalanceLabel')
                            END,
                            true
                        ),
                        '{totalBalanceLabel}',
                        CASE
                            WHEN subsection->>'totalBalanceLabel' IN ('Total balance - primary bank', 'Total Balance - Chase')
                            THEN '"Total Balance"'::jsonb
                            ELSE to_jsonb(subsection->>'totalBalanceLabel')
                        END,
                        true
                    ),
                    '{monthEndBalanceLabel}',
                    CASE
                        WHEN subsection->>'monthEndBalanceLabel' IN (
                            'Checking account balance month end - primary bank',
                            'Checking Account Balance @Month End - Chase',
                            'Checking account balance month end - Chase'
                        )
                        THEN '"Month End Balance minus Dues"'::jsonb
                        ELSE to_jsonb(subsection->>'monthEndBalanceLabel')
                    END,
                    true
                )
            )
            FROM jsonb_array_elements(COALESCE(plan.plan_data->'incomeSubsections', '[]'::jsonb)) AS subsection
        ),
        '[]'::jsonb
    ),
    true
)
WHERE plan.plan_data->'incomeSubsections' IS NOT NULL;

COMMIT;