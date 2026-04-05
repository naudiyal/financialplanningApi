# AWS RDS Promotion Plan

This plan assumes:

1. the existing AWS app is already running on EC2 with Nginx
2. the current production JSON data can be discarded after backup
3. the local development PostgreSQL database is the source of truth for production data
4. downtime during cutover is acceptable

Historical note: this promotion plan documents the move away from the old JSON file. The current application runtime uses PostgreSQL rather than `financial-plan.json`.

## Target Production Shape

1. frontend: Vite build served by Nginx on EC2
2. backend: Spring Boot JAR running on EC2 as a service
3. database: Amazon RDS PostgreSQL
4. auth: existing Google OAuth configuration

## Repo Support Added

The backend now includes Flyway so a fresh database can be initialized automatically.

Files involved:

1. `src/main/resources/db/migration/V1__create_app_user_financial_plan.sql`
2. `src/main/resources/application.properties`
3. `pom.xml`

`spring.flyway.baseline-on-migrate=true` is intentionally enabled so existing local databases with the already-created table can start cleanly without manual Flyway baselining.

## Cutover Rule

Use the local development PostgreSQL table `app_user_financial_plan` as the production source of truth.

That means:

1. back up the current production JSON file
2. create a fresh RDS database
3. import the local table rows into RDS
4. point the EC2 backend at RDS
5. stop using the old JSON file as live storage

## Step 1: Back Up Current Production JSON

On EC2, copy the current JSON file to a timestamped backup before changing anything.

Example:

```bash
cp /opt/financial-planning/data/financial-plan.json /opt/financial-planning/data/financial-plan.backup-$(date +%F-%H%M%S).json
```

## Step 2: Create RDS PostgreSQL

Recommended minimal setup:

1. engine: PostgreSQL
2. public access: `No`
3. security group: allow port `5432` only from the EC2 instance security group
4. automated backups: enabled

Create:

1. database: `financial_planning`
2. app user: `financial_app`

## Step 3: Export Local Dev Data

From the Windows development machine, export the live table data.

Schema-only export:

```powershell
$env:PGPASSWORD="password"
pg_dump -U financial_app -h localhost -d financial_planning --schema-only --table=app_user_financial_plan > app_user_financial_plan_schema.sql
```

Data-only export:

```powershell
$env:PGPASSWORD="password"
pg_dump -U financial_app -h localhost -d financial_planning --data-only --inserts --table=app_user_financial_plan > app_user_financial_plan_data.sql
```

If the local table already contains:

1. your personal user row
2. the sample row

then importing the full table data is enough.

## Step 4: Let Flyway Create the Table in AWS

Deploy the updated backend first and start it against the empty RDS database.

Set these environment variables on EC2:

```bash
APP_UI_URL=https://mybetterbudget.com
APP_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/financial_planning
APP_DATASOURCE_USERNAME=financial_app
APP_DATASOURCE_PASSWORD=<rds-password>
GOOGLE_CLIENT_ID=<prod-google-client-id>
GOOGLE_CLIENT_SECRET=<prod-google-client-secret>
```

When the application starts, Flyway will create `app_user_financial_plan` automatically on the fresh RDS database.

## Step 5: Import Local Data Into AWS

After the table exists, import the exported data file into RDS.

Example:

```bash
export PGPASSWORD='<rds-password>'
psql -U financial_app -h <rds-endpoint> -d financial_planning -f app_user_financial_plan_data.sql
```

If you prefer a one-command replacement import and the database is empty, you can also use a full table dump instead of separate schema and data files.

## Step 6: Verify Imported Rows

Run:

```bash
psql -U financial_app -h <rds-endpoint> -d financial_planning -c "select user_sub, email, display_name, created_at, updated_at from app_user_financial_plan order by updated_at desc;"
```

Confirm:

1. your user row exists
2. `sample@mybetterbudget.com` exists if you want the prebuilt sample row copied over from dev

If the sample row does not exist, the application can still recreate it later when sample mode is first requested.

## Step 7: Deploy the Application

Deploy both:

1. latest backend JAR
2. latest frontend `dist`

Then restart:

1. the Spring Boot service
2. Nginx if its config changed

## Step 8: Smoke Test Production

Verify:

1. Google login works
2. your tracker loads from the database
3. save works
4. sample tracker loads
5. sample edits are not persisted server-side
6. delete tracker works only for the personal tracker

## Recommended Follow-Up Hardening

After the first successful cutover:

1. move secrets from a plain env file to AWS Secrets Manager or SSM Parameter Store
2. add a small rollback note with the exact previous JSON backup path
3. add a second Flyway migration later if you want the sample row seeded automatically on empty environments