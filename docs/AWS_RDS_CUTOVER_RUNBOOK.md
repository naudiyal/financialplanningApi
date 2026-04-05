# AWS RDS Cutover Runbook

This runbook is for the current production strategy:

1. EC2 + Nginx stays in place
2. Spring Boot stays on EC2
3. Amazon RDS PostgreSQL becomes the production data store
4. local development PostgreSQL is the source of truth
5. existing production JSON is backed up and then ignored

## 1. Export Local Development Data

From the API repo on the Windows development machine:

```powershell
Set-Location 'C:\Users\naudi\OneDrive\workspace\FinancialPlanning\FinancialPlanningApi'
.\scripts\export_app_user_financial_plan.ps1
```

This writes:

1. `artifacts/db-export/app_user_financial_plan_schema.sql`
2. `artifacts/db-export/app_user_financial_plan_data.sql`

## 2. Create and Configure RDS

Create the RDS PostgreSQL instance and database:

1. instance type: small is fine for now
2. DB name: `financial_planning`
3. app user: `financial_app`
4. security group: allow `5432` only from the EC2 security group

## 3. Update Production Backend Environment

On EC2, update the backend env file with:

```bash
APP_UI_URL=https://mybetterbudget.com
APP_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/financial_planning
APP_DATASOURCE_USERNAME=financial_app
APP_DATASOURCE_PASSWORD=<rds-password>
GOOGLE_CLIENT_ID=<prod-google-client-id>
GOOGLE_CLIENT_SECRET=<prod-google-client-secret>
```

## 4. Back Up Old Production JSON

Before restarting the app, back up the old JSON file:

```bash
cp /opt/financial-planning/data/financial-plan.json /opt/financial-planning/data/financial-plan.backup-$(date +%F-%H%M%S).json
```

## 5. Deploy the Updated Backend

Deploy the backend that includes Flyway and restart the service.

On first startup against an empty RDS database, Flyway will create `app_user_financial_plan` automatically.

## 6. Import Local Data Into RDS

From the Windows development machine:

```powershell
Set-Location 'C:\Users\naudi\OneDrive\workspace\FinancialPlanning\FinancialPlanningApi'
.\scripts\import_app_user_financial_plan_to_rds.ps1 -RdsHost '<rds-endpoint>' -Password '<rds-password>'
```

If you need to replace rows already imported into RDS:

```powershell
.\scripts\import_app_user_financial_plan_to_rds.ps1 -RdsHost '<rds-endpoint>' -Password '<rds-password>' -TruncateBeforeImport
```

## 7. Verify Production Behavior

After import, verify:

1. login works
2. your saved tracker data appears
3. save works
4. sample tracker works
5. sample edits remain local-only

## 8. Rollback

If the cutover fails and you need a quick rollback:

1. point the backend env back to the old storage approach only if you still have a compatible app version
2. or restore the previous deployed backend and use the JSON backup

For the current plan, the simpler rollback is to keep the production JSON backup and the previous deployed JAR until the RDS version is verified.