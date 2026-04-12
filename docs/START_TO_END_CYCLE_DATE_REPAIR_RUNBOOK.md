# Start-To-End Cycle Date Repair Runbook

This runbook documents the rerunnable admin repair endpoint that corrects malformed stored cycle date ranges for users on the `START_TO_END` timeline.

## Problem

An older close-cycle bug generated malformed next-cycle dates such as:

- `2026-05-01` to `2026-05-30`
- `2026-05-31` to `2026-06-30`

For `START_TO_END`, the intended rule is always:

- cycle start = first day of month
- cycle end = last day of same month

The code bug is fixed for future cycles. This endpoint repairs already-saved bad rows.

## Endpoint

- Method: `POST`
- Path: `/api/admin/repair-start-to-end-cycle-dates`

## Access

The endpoint uses the same admin access rule as other maintenance APIs.

- property: `app.admin.email`
- env var: `APP_ADMIN_EMAIL`
- default: `naudiyal@gmail.com`

## What It Repairs

The endpoint repairs malformed rows in:

- `app_user_financial_plan_cycle`
- `app_user_financial_plan_cycle_history`

For each affected `START_TO_END` row, it recalculates:

- `cycle_start_date = first day of cycle_end_date month`
- `cycle_end_date = last day of cycle_end_date month`

History rows are rewritten safely by inserting or updating the corrected primary key row and then deleting the malformed original row.

## Local Use

With the backend running locally and an authenticated admin browser session, run this in DevTools Console:

```javascript
fetch("http://localhost:8080/api/admin/repair-start-to-end-cycle-dates", {
  method: "POST",
  credentials: "include"
}).then(async (response) => {
  console.log("status:", response.status);
  console.log("body:", await response.text());
});
```

Expected success response:

- `status: 200`
- body like `Repaired N malformed start-to-end cycle rows.`

## Verification Queries

Check live rows:

```sql
select user_sub, cycle_slot, cycle_start_date, cycle_end_date
from app_user_financial_plan_cycle
order by user_sub, cycle_slot;
```

Check archived rows:

```sql
select user_sub, timeline_type, cycle_start_date, cycle_end_date
from app_user_financial_plan_cycle_history
order by user_sub, cycle_end_date desc;
```

Any `START_TO_END` rows should now use full month ranges.