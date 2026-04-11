-- Update "Month End Balance" label to "Month End Balance minus Dues"
-- in all rows of app_user_financial_plan.
--
-- 1. Updates the label on the Chase checking-balance-month-end-chase balance item.
-- 2. Updates the monthEndBalanceLabel field on every income subsection entry.

BEGIN;

-- 1. Chase balance item label
UPDATE app_user_financial_plan
SET plan_data = jsonb_set(
    plan_data,
    '{balanceItems}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN item->>'id' = 'checking-balance-month-end-chase'
                     AND item->>'label' = 'Month End Balance'
                THEN jsonb_set(item, '{label}', '"Month End Balance minus Dues"')
                ELSE item
            END
        )
        FROM jsonb_array_elements(plan_data->'balanceItems') AS item
    )
)
WHERE plan_data->'balanceItems' IS NOT NULL;

-- 2. Income subsection monthEndBalanceLabel
UPDATE app_user_financial_plan
SET plan_data = jsonb_set(
    plan_data,
    '{incomeSubsections}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN subsection->>'monthEndBalanceLabel' = 'Month End Balance'
                THEN jsonb_set(subsection, '{monthEndBalanceLabel}', '"Month End Balance minus Dues"')
                ELSE subsection
            END
        )
        FROM jsonb_array_elements(plan_data->'incomeSubsections') AS subsection
    )
)
WHERE plan_data->'incomeSubsections' IS NOT NULL;

COMMIT;
