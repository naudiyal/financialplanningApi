# Production To Local Data Migration

This document describes how to copy production PostgreSQL data from AWS RDS into the local PostgreSQL database.

Last updated: May 30, 2026.

## Automated Sync Script

Run from the workspace root:

```powershell
.\sync-aws-db-to-local.bat
```

### What it does

1. **Exports** 4 tables from AWS RDS to CSV files:
   - `app_user_financial_plan_cycle` — current and previous cycle data
   - `app_user_financial_plan_settings` — user timeline type and premium status
   - `app_user_financial_plan_cycle_history` — bank balance history
   - `app_user_financial_plan_cycle_archive` — archived closed cycles

2. **Truncates** all 4 local tables (full replacement, not merge).

3. **Imports** the CSV data into local PostgreSQL.

4. **Verifies** row counts for all 4 tables.

### Prerequisites

- Local PostgreSQL installed and running on `localhost:5432`
- Database `financial_planning` exists
- User `financial_app` exists with password `password`
- Schema created by Flyway migrations (start the API once first)
- Laptop can reach AWS RDS on port `5432`
- Production DB password available (prompted if `PROD_PGPASSWORD` env var not set)

### Configuration

Defaults can be overridden via environment variables:

| Variable | Default |
|----------|---------|
| `AWS_DB_HOST` | `financial-planning-prod-db.c6lmucoisg8z.us-east-1.rds.amazonaws.com` |
| `AWS_DB_PORT` | `5432` |
| `AWS_DB_NAME` | `financial_planning` |
| `AWS_DB_USER` | `financial_app` |
| `AWS_DB_SSLMODE` | `require` |
| `LOCAL_DB_HOST` | `localhost` |
| `LOCAL_DB_PORT` | `5432` |
| `LOCAL_DB_NAME` | `financial_planning` |
| `LOCAL_DB_USER` | `financial_app` |
| `LOCAL_PGPASSWORD` | `password` |
| `PROD_PGPASSWORD` | (prompted) |
| `PSQL` | `C:\Program Files\PostgreSQL\16\bin\psql.exe` |

## Notes

- The script **replaces all local data** — it truncates tables before importing.
- CSV export files are saved to `FinancialPlanning\artifacts\db-sync\`.
- The `flyway_schema_history` table is NOT synced (managed by Flyway migrations).
- Production DB password belongs in `PROD_SECRETS.local.md`. Do not duplicate.
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