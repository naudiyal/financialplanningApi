WITH migrated_balance_items AS (
    SELECT
        plan.user_sub,
        jsonb_agg(
            CASE
                WHEN item.value->>'id' = 'net-balance-next-month-end' THEN
                    jsonb_set(
                        jsonb_set(item.value, '{id}', to_jsonb('savings-next-month'::text), true),
                        '{label}',
                        to_jsonb('Savings Next Month'::text),
                        true
                    )
                WHEN item.value->>'id' = 'savings-next-month'
                    AND COALESCE(item.value->>'label', '') IN ('Net Balance @Next Month End', 'Net balance next month end') THEN
                    jsonb_set(item.value, '{label}', to_jsonb('Savings Next Month'::text), true)
                ELSE item.value
            END
            ORDER BY item.ordinality
        ) AS balance_items
    FROM app_user_financial_plan AS plan
    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(plan.plan_data->'balanceItems', '[]'::jsonb)) WITH ORDINALITY AS item(value, ordinality)
    GROUP BY plan.user_sub
)
UPDATE app_user_financial_plan AS plan
SET
    plan_data = jsonb_set(plan.plan_data, '{balanceItems}', migrated_balance_items.balance_items, true),
    updated_at = NOW()
FROM migrated_balance_items
WHERE plan.user_sub = migrated_balance_items.user_sub
    AND EXISTS (
        SELECT 1
        FROM jsonb_array_elements(COALESCE(plan.plan_data->'balanceItems', '[]'::jsonb)) AS existing_item
        WHERE existing_item->>'id' = 'net-balance-next-month-end'
            OR (
                existing_item->>'id' = 'savings-next-month'
                AND COALESCE(existing_item->>'label', '') IN ('Net Balance @Next Month End', 'Net balance next month end')
            )
    );

SELECT
    user_sub,
    email,
    item->>'id' AS balance_item_id,
    item->>'label' AS balance_item_label
FROM app_user_financial_plan
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(plan_data->'balanceItems', '[]'::jsonb)) AS item
WHERE item->>'id' = 'savings-next-month'
ORDER BY email NULLS LAST, user_sub;