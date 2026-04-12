# Stale Label Normalization Runbook

This runbook documents the stale-label backfill work that was added to the backend and the exact steps used to validate it locally and run it in AWS production.

Status update as of April 11, 2026:

- stale bank labels were verified fixed in production after deploying the updated backend and rerunning the admin normalization endpoint
- the remaining work after label normalization was a separate UI-only Bank Balance Movement graph issue

## Purpose

Older saved cycles in the database could retain outdated labels even though newer load and save operations were already normalizing labels in backend code.

The fix added an admin-only API endpoint that rewrites every stored cycle through the latest normalization logic so all saved plans pick up the latest labels.

This runbook also documents a second-pass fix that was required after the first production backfill: some stale labels inside `incomeSubsections` were still preserved because the original normalization logic only filled blank subsection labels instead of force-upgrading known legacy values.

## What Was Added

Backend changes were made in these files:

- `FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/controller/AdminController.java`
- `FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/service/FinancialPlanStorageService.java`
- `FinancialPlanningApi/src/main/resources/application.properties`

The first version of this work added the admin endpoint and bulk rewrite.

The second version extended normalization so legacy subsection label values are force-upgraded during load, save, and admin backfill.

### New Admin Endpoint

- Method: `POST`
- Path: `/api/admin/normalize-all-plans`

### Admin Access Rule

The endpoint is protected by an admin email check.

- Property name: `app.admin.email`
- Environment variable: `APP_ADMIN_EMAIL`
- Default value: `naudiyal@gmail.com`

### What The Endpoint Does

For every row in `app_user_financial_plan_cycle`:

1. Reads `plan_data`
2. Deserializes it into `FinancialPlanData`
3. Runs it through the latest normalization logic
4. Recalculates summary fields
5. Writes the normalized JSON back to the same row
6. Updates `updated_at`

## Labels Covered By Current Normalization

Examples of labels normalized by current backend logic include:

- `Savings Next Cycle`
- `First Pay Check`
- `Second Paycheck`
- `Month End Balance minus Dues`
- normalized credit/debit column labels such as `Payment Date` and `Credit Limit`

Examples of subsection labels now explicitly normalized include:

- `Account Balance`
- `Additional Payments`
- `Total Balance`
- `Additional Income`
- `Month End Balance minus Dues`

Legacy subsection values that are specifically upgraded include examples such as:

- `Month End Balance`
- `Checking account balance - primary bank`
- `Checking Account Balance - Chase`
- `Total balance - primary bank`
- `Total Balance - Chase`
- `Additional payments - primary bank`
- `Additional Payments - Chase`
- `Additional income - primary bank`
- `Additional Income - Chase`
- `Checking account balance month end - primary bank`
- `Checking Account Balance @Month End - Chase`
- `Checking account balance month end - Chase`

## Local Validation Guide

Use this flow first before running the backfill in AWS.

### Step 1: Start Local PostgreSQL

Make sure local PostgreSQL is running and the backend can connect to:

- `jdbc:postgresql://localhost:5432/financial_planning`

### Step 2: Start The Local Backend

Open PowerShell in:

- `C:\Users\naudi\OneDrive\workspace\FinancialPlanning\FinancialPlanningApi`

Run:

```powershell
$env:GOOGLE_CLIENT_ID="<your local Google client id>"
$env:GOOGLE_CLIENT_SECRET="<your local Google client secret>"
$env:APP_UI_URL="http://localhost:5173"
$env:APP_DATASOURCE_PASSWORD="password"
$env:APP_ADMIN_EMAIL="naudiyal@gmail.com"
& "C:\Users\naudi\OneDrive\workspace\tools\apache-maven-3.9.14\bin\mvn.cmd" spring-boot:run
```

Expected backend URL:

- `http://localhost:8080`

### Step 3: Verify The Local Backend Is Reachable

In a separate PowerShell window run:

```powershell
curl.exe -i http://localhost:8080/api/auth/me
```

Expected result when not logged in:

- HTTP `200`
- JSON with `"authenticated":false`

### Step 4: Start The Local UI

Open PowerShell in:

- `C:\Users\naudi\OneDrive\workspace\FinancialPlanning\FinancialPlanningUI`

Run:

```powershell
npm run dev
```

Expected UI URL:

- `http://localhost:5173`

### Step 5: Sign In Locally As Admin

Open:

- `http://localhost:5173`

Sign in with:

- `naudiyal@gmail.com`

### Step 6: Verify The Local Authenticated Session

In the browser DevTools Console on `http://localhost:5173`, run:

```javascript
fetch("http://localhost:8080/api/auth/me", {
  credentials: "include"
}).then(async (response) => {
  console.log("status:", response.status);
  console.log("body:", await response.text());
});
```

Expected result:

- `status: 200`
- body contains the authenticated user details

### Step 7: Run The Local Backfill

In the same DevTools Console, run:

```javascript
fetch("http://localhost:8080/api/admin/normalize-all-plans", {
  method: "POST",
  credentials: "include"
}).then(async (response) => {
  console.log("status:", response.status);
  console.log("body:", await response.text());
});
```

Expected result:

- `status: 200`
- body like `Normalized N stored cycles.`

### Step 8: Verify Local Data After The Rewrite

Refresh the UI and inspect older cycles. Confirm stale labels are now using the latest values.

If you are testing the second-pass subsection fix specifically, inspect users that still had old bank subsection labels such as `Month End Balance` before the rerun.

Recommended local verification targets:

- `incomeSubsections[].monthEndBalanceLabel`
- `incomeSubsections[].checkingBalanceLabel`
- `incomeSubsections[].additionalPaymentsLabel`
- `incomeSubsections[].totalBalanceLabel`
- `incomeSubsections[].additionalIncomeLabel`

Optional database verification:

```powershell
$env:PGPASSWORD="password"
& "C:/Program Files/PostgreSQL/16/bin/psql.exe" -h localhost -U financial_app -d financial_planning
```

Then in `psql`:

```sql
select user_sub, cycle_slot, updated_at
from app_user_financial_plan_cycle
order by updated_at desc
limit 20;
```

Inspect one record:

```sql
select jsonb_pretty(plan_data)
from app_user_financial_plan_cycle
where user_sub = '<some user_sub>' and cycle_slot = 'CURRENT';
```

If you want to confirm subsection labels directly in SQL, inspect the `incomeSubsections` part of `plan_data` and verify old values like `Month End Balance` have become `Month End Balance minus Dues`.

## AWS Production Guide

Production site:

- `https://mybetterbudget.com`

Production API base:

- `https://mybetterbudget.com/api`

EC2 host:

- `ec2-44-198-94-78.compute-1.amazonaws.com`

### Step 1: Build And Deploy The Updated Backend

Use the existing promotion script from the workspace root:

```powershell
.\promote-api-aws.bat
```

This script:

1. builds the API jar
2. uploads it to EC2
3. replaces the deployed jar
4. restarts the `financial-planning-api` systemd service
5. verifies `https://mybetterbudget.com/api/auth/me`

### Step 2: Open The Production Site

Open:

- `https://mybetterbudget.com`

### Step 3: Sign In As Admin

Sign in with:

- `naudiyal@gmail.com`

### Step 4: Verify Your Production Session

Open browser DevTools Console on `https://mybetterbudget.com` and run:

```javascript
fetch("/api/auth/me", {
  credentials: "include"
}).then(async (response) => {
  console.log("status:", response.status);
  console.log("body:", await response.text());
});
```

Expected result:

- `status: 200`
- authenticated user details in the response body

### Step 5: Run The Production Backfill

In the same DevTools Console, run:

```javascript
fetch("/api/admin/normalize-all-plans", {
  method: "POST",
  credentials: "include"
}).then(async (response) => {
  console.log("status:", response.status);
  console.log("body:", await response.text());
});
```

Expected result:

- `status: 200`
- body like `Normalized N stored cycles.`

### Step 5A: When A Second Backfill Run Is Required

If production was already backfilled once before the subsection-label normalization fix was added, you must deploy the newer backend and run the admin endpoint again.

That is because the first backfill only rewrote data using the older normalization logic.

The second run is what upgrades stale subsection labels that survived the first production rewrite.

### Step 6: Verify Production Data

Spot-check older users and cycles in the application and confirm they now show the current labels.

Recommended labels to verify:

- `Savings Next Cycle`
- `First Pay Check`
- `Second Paycheck`
- `Month End Balance minus Dues`

Recommended subsection fields to verify when the issue affected another user:

- bank subsection month-end balance label
- bank subsection account balance label
- bank subsection additional payments label
- bank subsection total balance label
- bank subsection additional income label

If the stale label was visible in the Bank Accounts section for another user, perform a hard refresh after the rerun and then re-open that user’s cycle.

Confirmed production outcome after the final rerun:

- default bank labels such as `Additional Payments` and `Additional Income` were corrected in stored data
- other-bank labels such as `Month End Balance minus Dues` were corrected in stored data

### Step 7: Check API Logs If Needed

If the endpoint fails or returns `403` or `500`, inspect logs from your machine:

```powershell
ssh -i ".\mybetterbudget-key.pem" ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com "sudo journalctl -u financial-planning-api -n 100 --no-pager"
```

## Important URL Rule

Use `localhost` only for local development.

### Correct Local URLs

- `http://localhost:5173`
- `http://localhost:8080/api/auth/me`
- `http://localhost:8080/api/admin/normalize-all-plans`

### Correct Production URLs

- `https://mybetterbudget.com`
- `https://mybetterbudget.com/api/auth/me`
- `https://mybetterbudget.com/api/admin/normalize-all-plans`

Or, from the production site’s browser console, prefer same-origin paths:

- `/api/auth/me`
- `/api/admin/normalize-all-plans`

### Do Not Use In Production

Do not call `http://localhost:8080/...` from `https://mybetterbudget.com`.

That points to the browser machine itself, not the EC2 backend, and will fail with CORS and `403` or network errors.

## Troubleshooting

### Labels Are Correct But The Bank Balance Movement Graph Is Wrong

This is a separate UI problem, not a stale-label persistence problem.

One confirmed example for a default bank in production was:

- `Bi-monthly salary`: `1,631.66`
- `First Pay Check`: checked
- `Second Pay Check`: unchecked
- `Account Balance`: `3,276.42`
- `Additional payments`: `0`
- `Total Balance`: `4,908.08`
- `Additional income`: `0`
- `Month End Balance minus Dues`: `3,252.31`

In that scenario, the Bank Balance Movement chart incorrectly showed `After Dues = 1,620.65` even though it should have matched `Month End Balance minus Dues = 3,252.31`.

Root cause:

1. the chart was using a different starting-balance formula than the bank cards
2. the chart also grouped lines by displayed bank name, which could merge values unexpectedly
3. the chart also allowed zero-only legacy placeholder bank rows to appear in the tooltip

UI fixes that were applied:

1. aligned the chart starting balance with the same subsection/default-bank math used by the bank cards
2. removed line aggregation by displayed bank name and switched to stable per-row keys
3. filtered out zero-only placeholder bank groups such as legacy PNC rows
4. prevented generic names like `primary bank` or `secondary bank` from surfacing as chart bank names

Deployment rule for this issue:

1. backend deploy is required for stored-label normalization changes
2. the earlier Bank Balance Movement chart math fixes were UI-only
3. the newer Change in Bank Balance redesign is UI plus API because the UI reads previous-cycle and current-cycle plan snapshots from the API response
4. if both changed, deploy both, with API first and UI second

Production steps for the graph fix:

1. if you are deploying only the older math fix, run `./promote-ui-aws.bat`
2. if you are deploying the newer Change in Bank Balance redesign, run `./promote-api-aws.bat` and then `./promote-ui-aws.bat`
3. open `https://mybetterbudget.com`
4. hard refresh the page
5. recheck the affected user and bank in the chart

### Another User Still Shows Old Labels After A Successful Backfill

Check these in order:

1. confirm the stale label is coming from a stored field and not a cached browser view
2. hard refresh the browser and reload the same user and cycle
3. verify the deployed backend includes the second-pass subsection normalization fix
4. rerun `POST /api/admin/normalize-all-plans` after deploying the updated backend

This specific issue was caused by old `incomeSubsections` label values being non-blank, which meant the first normalization logic preserved them.

The updated backend fixes that by explicitly remapping known legacy subsection labels.

### `403 Forbidden` on `/api/admin/normalize-all-plans`

Check these in order:

1. you are signed in as `naudiyal@gmail.com`
2. the updated backend was actually deployed
3. `APP_ADMIN_EMAIL` is not set to some different value in production

### `401 Unauthorized`

Your browser session is missing or expired. Log in again and retry.

### `500 Internal Server Error`

Check backend logs on EC2 using `journalctl`.

### Backend Compiles But Endpoint Is Missing

The old jar is still deployed. Re-run:

```powershell
.\promote-api-aws.bat
```

### Endpoint Returns `200` But One User Still Shows Old Labels

This usually means one of these:

1. the browser is still showing cached data
2. the first-generation backfill ran before the subsection-label fix was deployed
3. you need to deploy the latest backend and rerun the admin endpoint one more time

Recommended fix:

1. deploy the newest API build with `.\promote-api-aws.bat`
2. sign in again as `naudiyal@gmail.com`
3. rerun `/api/admin/normalize-all-plans`
4. hard refresh and verify the affected user again

## Detailed Rerun Checklist

Use this exact checklist if stale labels remain after an earlier production backfill.

### Local Rerun Checklist

1. compile the backend locally
2. start the backend with the latest code
3. sign in locally as `naudiyal@gmail.com`
4. call `http://localhost:8080/api/admin/normalize-all-plans`
5. confirm HTTP `200`
6. refresh the UI and inspect the affected local user data
7. optionally inspect `plan_data` in PostgreSQL to confirm subsection labels changed

### Production Rerun Checklist

1. run `.\promote-api-aws.bat` from the workspace root
2. open `https://mybetterbudget.com`
3. sign in as `naudiyal@gmail.com`
4. verify `/api/auth/me` returns authenticated user info
5. call `/api/admin/normalize-all-plans`
6. confirm the endpoint returns HTTP `200`
7. hard refresh the browser
8. reopen the affected user and cycle
9. confirm the stale label is now updated

## Post-Run Options

After the backfill succeeds, choose one of these:

1. keep the admin endpoint for future label migrations
2. remove the admin endpoint and redeploy if you want to minimize maintenance surface area
