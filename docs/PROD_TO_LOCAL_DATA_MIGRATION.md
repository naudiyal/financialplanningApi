# Production To Local Data Migration

This document records the successful workflow used to copy production PostgreSQL data from AWS RDS into the local PostgreSQL database for the Financial Planning application.

## Goal

Move production data from AWS RDS into the local `financial_planning` database so the local backend can run against real application data.

## Active Table

The backend currently reads and writes the `app_user_financial_plan_cycle` table.

This is important because older scripts in the repository were written around the legacy `app_user_financial_plan` table. For the current application flow, the production export and local import should target `app_user_financial_plan_cycle`.

## Prerequisites

- Local PostgreSQL is installed and running on `localhost:5432`.
- Local database `financial_planning` exists.
- Local application user `financial_app` exists and can connect.
- Local schema has already been created by the backend and Flyway migrations.
- PostgreSQL client tools are installed on the laptop.
- The laptop can reach the production AWS RDS instance on port `5432`.

Related references:

- `LOCAL_RUN_INSTRUCTIONS.md`
- `PROD_POSTGRES_CREDENTIALS.md`
- `FinancialPlanningApi/docs/AWS_RDS_EXECUTION_SUMMARY.md`

## Production Connection Values Used

- Database name: `financial_planning`
- Database user: `financial_app`
- Port: `5432`
- RDS endpoint: `financial-planning-prod-db.c6lmucoisg8z.us-east-1.rds.amazonaws.com`

Keep the production password in `PROD_POSTGRES_CREDENTIALS.md`. Do not duplicate the secret into more files than necessary.

## Local Connection Values Used

- Host: `localhost`
- Port: `5432`
- Database name: `financial_planning`
- Database user: `financial_app`

Keep the local password in `LOCAL_RUN_INSTRUCTIONS.md` and related local credential notes.

## Environment Issues Resolved During Migration

### 1. `pg_dump` was not on PATH

The laptop had PostgreSQL installed, but PowerShell could not find `pg_dump` by command name.

Verified location:

```text
C:\Program Files\PostgreSQL\16\bin\pg_dump.exe
```

Solution used: run `pg_dump.exe` by full path.

### 2. `psql` was not on PATH

The laptop had the PostgreSQL client tools installed, but PowerShell could not find `psql` by command name.

Solution used: run `psql.exe` by full path.

### 3. Production connectivity initially timed out

Initial `pg_dump` attempts failed with a network timeout when connecting to the RDS endpoint.

Root cause: the production RDS instance was not reachable from the current laptop IP.

Solution used:

- determine the current public IP address
- add an inbound security group rule in AWS for PostgreSQL `5432`
- allow source `<public-ip>/32`

The public IP used during this successful migration was:

```text
99.6.76.164/32
```

## Step 1. Verify Local Schema Exists

Before importing production data, ensure the local backend has already created the current schema through Flyway.

The relevant migration creates `app_user_financial_plan_cycle` and supporting indexes.

If needed, start the backend first so Flyway can create the schema before importing data.

## Step 2. Export Production Data To An Insert SQL File

Run this from PowerShell in any folder where you want the export file to be written.

In the successful run, the command was executed from the workspace root.

```powershell
$env:PGPASSWORD="<prod-password>"

& "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" `
  -U financial_app `
  -h financial-planning-prod-db.c6lmucoisg8z.us-east-1.rds.amazonaws.com `
  -p 5432 `
  -d financial_planning `
  --data-only `
  --inserts `
  --table=app_user_financial_plan_cycle `
  --file=app_user_financial_plan_cycle_data.sql
```

Expected result:

- command finishes without error
- file `app_user_financial_plan_cycle_data.sql` is created in the current folder

Optional verification:

```powershell
Get-ChildItem .\app_user_financial_plan_cycle_data.sql
```

## Step 3. Import The Production Export Into Local PostgreSQL

Run the import against local PostgreSQL using the local application user.

```powershell
$env:PGPASSWORD="<local-password>"

& "C:\Program Files\PostgreSQL\16\bin\psql.exe" `
  -v ON_ERROR_STOP=1 `
  -U financial_app `
  -h localhost `
  -p 5432 `
  -d financial_planning `
  -f .\app_user_financial_plan_cycle_data.sql
```

Observed successful output pattern:

```text
INSERT 0 1
INSERT 0 1
...
```

The successful import inserted 10 rows.

## Step 4. Verify The Imported Data Locally

Run this verification query after import:

```powershell
$env:PGPASSWORD="<local-password>"

& "C:\Program Files\PostgreSQL\16\bin\psql.exe" `
  -U financial_app `
  -h localhost `
  -p 5432 `
  -d financial_planning `
  -c "SELECT user_sub, cycle_slot, email, updated_at FROM app_user_financial_plan_cycle ORDER BY updated_at DESC;"
```

Observed result from the successful migration:

- 10 rows present in `app_user_financial_plan_cycle`
- one user had both `CURRENT` and `PREVIOUS` rows
- sample plan row was present

Representative imported emails included:

- `madhur.march17@gmail.com`
- `cinthyasloggett75@gmail.com`
- `naudiyal@gmail.com`
- `letsomesenseprevail@gmail.com`
- `digitalitdirector@gmail.com`
- `ishanaudiyal@gmail.com`
- `rianaudiyal@gmail.com`
- `innaudiyal@gmail.com`
- `sample@mybetterbudget.com`

## If You Need To Re-Import

If local rows already exist and need to be replaced, truncate the table before loading the exported file.

```powershell
$env:PGPASSWORD="<local-password>"

& "C:\Program Files\PostgreSQL\16\bin\psql.exe" `
  -v ON_ERROR_STOP=1 `
  -U financial_app `
  -h localhost `
  -p 5432 `
  -d financial_planning `
  -c "TRUNCATE TABLE app_user_financial_plan_cycle;"
```

Then rerun the import command.

## Troubleshooting

### `pg_dump` is not recognized

Use the full executable path:

```text
C:\Program Files\PostgreSQL\16\bin\pg_dump.exe
```

### `psql` is not recognized

Use the full executable path:

```text
C:\Program Files\PostgreSQL\16\bin\psql.exe
```

### `Connection timed out`

This is usually a network or AWS security group issue, not a password problem.

Check:

- RDS endpoint is correct
- port `5432` is open for the current laptop IP
- the RDS instance is reachable from the internet or the expected network path

### `No such file or directory` for the SQL file

The export file was not created yet, or PowerShell is running in a different folder than the file location.

Generate the export first, then either:

- run the import from the same directory
- or use an absolute path to the SQL file

## Notes

- Do not import `flyway_schema_history` from production into local.
- Do not rely on the legacy `app_user_financial_plan` table for current application behavior.
- This workflow only copies database data. It does not change local OAuth, frontend, or backend environment settings.