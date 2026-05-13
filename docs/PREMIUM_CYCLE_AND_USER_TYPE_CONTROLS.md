# Premium Cycle And User Type Controls

This document summarizes the backend behavior added for premium cycle visibility, admin user-type management, and persisted debit-expense paid state.

## User Types

- `Regular` users can see:
  - the current active cycle
  - the latest closed cycle
- `Premium` users can see:
  - the current active cycle
  - up to 12 closed cycles

The premium flag is stored in `app_user_financial_plan_settings.is_premium`.

Default behavior:

- new users default to `is_premium = false`
- deleting a tracker does not clear premium status, because tracker deletion removes cycle/history data but does not delete the settings row

## Retention Model

- Full active snapshots still live in `app_user_financial_plan_cycle`
- Older closed-cycle full snapshots are archived in `app_user_financial_plan_cycle_archive`
- Bank-balance history remains in `app_user_financial_plan_cycle_history`

The system retains enough closed-cycle snapshots to support premium visibility while preserving regular-user behavior.

## Admin API

Admin-only endpoints:

- `GET /api/financial-plan/users`
  - returns tracker-owner summaries used by the admin UI
  - includes premium status in each user summary
  - includes the signed-in admin's own account so admins can change themselves between `Regular` and `Premium`
- `PUT /api/financial-plan/users/{userSub}/premium`
  - request body: `{ "premium": true | false }`
  - updates the target user's premium status
- `GET /api/financial-plan/viewer?userSub=...&cycle=...`
  - returns the selected user's current or visible closed-cycle snapshot for the admin viewer flow
  - if the stored tracker data is encrypted, the response preserves the encrypted wrapper fields so the UI can require that user's Encryption Key before rendering any financial data

## Closed-Cycle Selection Versus Revert

- Closed-cycle selection supports opening any visible closed cycle.
- Revert does **not** restore an arbitrary selected closed cycle.
- Revert remains a rollback of the most recent close-cycle action only.

In practice, repeated reverts walk backward one close at a time until only a single active cycle remains.

## Debit Expense `Paid` Field

Debit expense rows now persist a `paid` flag in `ExpenseItem`.

Behavior:

- checking `Paid` in the UI forces that row's current-cycle debit amount to `0`
- unchecking `Paid` restores the row's current-cycle amount from the row's next-cycle amount
- on close-cycle carry-forward, new-cycle expense rows reset `paid` to `false`

Backward compatibility:

- older saved debit expense rows may not contain an explicit `paid` value
- when `paid` is missing, backend normalization infers it from the current amount
  - current amount `0` => paid
  - otherwise unpaid