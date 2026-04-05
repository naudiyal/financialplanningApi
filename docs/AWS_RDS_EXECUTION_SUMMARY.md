# AWS RDS Execution Summary

This document records the detailed sequence of work completed after the AWS PostgreSQL instance was created for production.

Secrets are intentionally redacted in this log. Hostnames, security group IDs, commands, file paths, and operational issues are included because they were necessary to complete the cutover.

## 1. RDS Creation Details

The production database was created in AWS RDS with the following configuration decisions.

Chosen settings:

1. engine: PostgreSQL 16.3
2. deployment: Single-AZ
3. credentials management: self-managed username and password
4. authentication: password authentication
5. instance family: burstable
6. instance class: `db.t4g.micro`
7. storage type: `gp3`
8. allocated storage: 20 GiB
9. VPC: default VPC
10. public access: disabled
11. network type: IPv4
12. DB subnet group: default
13. RDS Proxy: disabled
14. CloudWatch log exports: disabled
15. performance/insights: minimal cost-oriented settings
16. RDS security group: `financial-planning-rds-sg`

Important operational detail:

1. an early configuration path showed an estimated cost above $400 per month
2. that happened because a much larger instance class was selected in the AWS console
3. the cost was corrected by switching to `db.t4g.micro`
4. after the correction the estimate dropped to roughly $15.44 per month

Known production identifiers after creation:

1. RDS endpoint: `financial-planning-prod-db.c6lmucoisg8z.us-east-1.rds.amazonaws.com`
2. RDS security group ID: `sg-00d906d11379c5412`
3. VPC ID: `vpc-0fc8763ae3bbe4b9b`

## 2. Production EC2 And RDS Relationship

The application architecture remained:

1. Nginx on EC2 serving the frontend and reverse proxying the API
2. Spring Boot running on EC2 as a `systemd` service
3. Amazon RDS PostgreSQL replacing JSON-backed persistence for production data

The EC2 security group used by the application host was identified as:

1. name: `launch-wizard-1`
2. security group ID: `sg-0853be18110e20523`

## 3. Updating Production Environment Variables

The production backend environment file on EC2 was updated at:

1. `/etc/financial-planning/api.env`

The effective settings included:

1. `APP_UI_URL=https://mybetterbudget.com`
2. `APP_STORAGE_PATH=/opt/financial-planning/data/financial-plan.json`
3. `APP_DATASOURCE_URL=jdbc:postgresql://financial-planning-prod-db.c6lmucoisg8z.us-east-1.rds.amazonaws.com:5432/financial_planning`
4. `APP_DATASOURCE_USERNAME=financial_app`
5. `APP_DATASOURCE_PASSWORD=<redacted>`
6. `GOOGLE_CLIENT_ID=<redacted>`
7. `GOOGLE_CLIENT_SECRET=<redacted>`

Important detail:

1. the old JSON path remained in the env file
2. that was acceptable because the backend was being transitioned to RDS-backed storage, while the JSON file was still preserved and later backed up for rollback safety

## 4. First Connectivity Test From EC2 To RDS

To verify that EC2 could reach RDS, PostgreSQL client access was tested from the EC2 machine.

The first connection attempt timed out.

Observed error:

1. connection to the RDS endpoint on port `5432` failed with `Connection timed out`

What that meant:

1. DNS resolution worked
2. the problem was not the database password
3. the likely issue was VPC or security group access

Root cause confirmed:

1. the RDS security group did not yet allow inbound PostgreSQL traffic from the EC2 instance security group

Resolution steps:

1. identified the EC2 security group in use by the instance
2. edited the inbound rules on `financial-planning-rds-sg`
3. added a PostgreSQL rule for port `5432`
4. used the EC2 security group as the source rather than opening the port to the internet

Result:

1. `psql` from EC2 to RDS succeeded after the security group rule was added

## 5. Verifying The Initial RDS Database State

After connectivity worked, the database list on the RDS instance was inspected.

Observed databases:

1. `postgres`
2. `rdsadmin`
3. `template0`
4. `template1`

Important finding:

1. the application database `financial_planning` did not exist yet

Action taken:

1. connected to the default `postgres` database
2. created the `financial_planning` database manually

## 6. `psql` Interaction Issues During Database Creation

While working inside `psql`, there were a few shell interaction issues that had to be cleaned up.

Observed symptoms:

1. pager behavior after `\l`
2. accidental entry into the `postgres->` continuation prompt

Meaning of `postgres->`:

1. it indicates incomplete SQL input inside `psql`
2. it does not indicate a successful connection state

Recovery actions used:

1. `q` to exit the pager when needed
2. `\r` to reset the current query buffer
3. `Ctrl+C` as an alternative to cancel unfinished input
4. `\q` to exit `psql`

Final verification of DB creation:

1. reconnected directly to `financial_planning`
2. confirmed the prompt changed to `financial_planning=>`

## 7. Local Artifact Build For Deployment

The updated application needed to be rebuilt locally before deploying to EC2.

Frontend build:

1. command: `npm run build`
2. result: successful Vite production build
3. notable warning: the main JavaScript bundle exceeded 500 kB after minification
4. warning impact: not a blocker for deployment

Backend build:

1. first attempt used `mvn clean package`
2. this failed because the local `target` directory was locked by a running or previously running local Java process
3. the workaround was to run `mvn package` without `clean`
4. result: backend JAR built successfully

Generated backend artifact:

1. `FinancialPlanningApi/target/financial-planning-api-0.0.1-SNAPSHOT.jar`

Generated frontend artifact:

1. `FinancialPlanningUI/dist`

## 8. Learning The Correct SSH And SCP Usage

There were several command-line mistakes during the upload phase that were resolved one by one.

Issues encountered:

1. using `ssh` instead of `scp` to try to upload the JAR
2. uncertainty about which hostname to use for EC2
3. uncertainty about the SSH username
4. uncertainty about whether the domain name or EC2 public DNS should be used

Resolved operational values:

1. EC2 public DNS: `ec2-44-198-94-78.compute-1.amazonaws.com`
2. SSH user: `ubuntu`
3. local key file: `mybetterbudget-key.pem`

Correct upload pattern established:

1. use `scp`, not `ssh`, for file transfer
2. use the same username and host that work for SSH login

## 9. Upload Problems And Fixes

Multiple upload issues occurred during deployment.

### 9.1. Remote path permission problem

Initial attempt:

1. uploaded directly into `/opt/financial-planning/deploy/api/`

Observed failure:

1. `Permission denied`

Root cause:

1. the `ubuntu` user did not have direct write permission into that root-owned path

Resolution:

1. upload artifacts to the EC2 home directory first
2. move or copy them into `/opt/financial-planning/...` later using `sudo`

### 9.2. Wrong local path for the SQL export file

Observed failure:

1. `No such file or directory` for the local SQL export file path

Root cause:

1. the command referenced `FinancialPlanningApi` again even when already inside that folder
2. the export file also did not exist yet at that moment

Resolution:

1. generate the export file first
2. use the correct relative path depending on the current folder

### 9.3. Copy-pasting the PowerShell prompt text

Observed failure:

1. PowerShell interpreted the copied `PS C:\...>` prompt text as a command
2. this produced misleading errors such as ambiguous `-i` handling and failed process lookups

Resolution:

1. paste only the command itself
2. do not paste the prompt prefix shown by PowerShell

### 9.4. Host authenticity confirmation

During the first `scp` attempt, OpenSSH prompted for host authenticity confirmation.

Observed text:

1. the host key could not be established yet for the EC2 public DNS name
2. the fingerprint was shown
3. the host was already known by IP and domain aliases in `known_hosts`

Resolution:

1. accepted the host key by answering `yes`

This was expected behavior when connecting by a hostname that had not yet been recorded in `known_hosts`.

## 10. Successful Artifact Uploads

Once the correct patterns were established, the following transfers succeeded from the Windows machine to the EC2 home directory.

Backend JAR upload:

1. `scp -i ".\mybetterbudget-key.pem" .\FinancialPlanningApi\target\financial-planning-api-0.0.1-SNAPSHOT.jar ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com:~/`

Frontend upload:

1. `scp -i ".\mybetterbudget-key.pem" -r .\FinancialPlanningUI\dist ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com:~/`

## 11. Copying Files Into Production Locations On EC2

After the uploads, files were copied into the server-owned deployment locations.

Important directories involved:

1. `/opt/financial-planning/deploy/api`
2. `/opt/financial-planning/deploy/ui`
3. `/opt/financial-planning/api`
4. `/opt/financial-planning/ui`

Key actions:

1. created deployment directories with `sudo mkdir -p`
2. copied the uploaded JAR into the deploy folder
3. copied frontend `dist` contents into the deploy folder
4. copied deployed frontend files into `/opt/financial-planning/ui`
5. restarted the Spring Boot `systemd` service
6. validated Nginx configuration and reloaded Nginx

## 12. Initial Service Restart Looked Healthy

After the first restart, `systemctl status` showed:

1. `financial-planning-api.service` was active and running
2. Java started successfully
3. Tomcat started on port `8080`

Important nuance:

1. the service being up did not prove Flyway had executed
2. it only proved that the application could start with the current configuration

## 13. First Data Import Attempt Failed

Before the schema problem was fully understood, data import into RDS was attempted.

Preparation actions:

1. local development PostgreSQL data was exported using `scripts/export_app_user_financial_plan.ps1`
2. this generated:
3. `artifacts/db-export/app_user_financial_plan_schema.sql`
4. `artifacts/db-export/app_user_financial_plan_data.sql`
5. the data file was uploaded to the EC2 home directory
6. the old production JSON file was backed up on EC2 before import

Observed import failure:

1. `ERROR: unrecognized configuration parameter "transaction_timeout"`

Root cause:

1. the SQL export file contained `SET transaction_timeout`
2. that setting came from newer local PostgreSQL tooling
3. the RDS PostgreSQL version did not recognize that configuration parameter

Compounding issue discovered immediately after:

1. querying `app_user_financial_plan` failed with `relation "app_user_financial_plan" does not exist`
2. this proved the target schema had not been created yet in RDS

## 14. Discovering That The Wrong JAR Was Still In Use

The production service definition was inspected directly.

Service file path:

1. `/etc/systemd/system/financial-planning-api.service`

Critical `ExecStart` line:

1. `/usr/bin/java -jar /opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar`

Why this mattered:

1. an earlier copy command had placed the new build at `/opt/financial-planning/api/financial-planning-api.jar`
2. but `systemd` was actually launching `/opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar`
3. this meant the restarted service was still using the older versioned JAR, not the newly copied generic filename

Resolution:

1. replaced the versioned JAR at the exact path used by `ExecStart`
2. restarted the service again

## 15. Discovering The Flyway Packaging Problem

Even after correcting the JAR path, the RDS database still showed no tables.

Observed verification command result:

1. `\dt` returned `Did not find any relations.`

At that point the datasource environment variables were verified and found to be correct.

That narrowed the issue to the packaged application itself.

Root cause identified in the codebase:

1. `spring.flyway.enabled=true` was present in `application.properties`
2. `flyway-core` was present in `pom.xml`
3. but the PostgreSQL-specific Flyway module was missing

Code change applied locally:

1. added `org.flywaydb:flyway-database-postgresql` to `FinancialPlanningApi/pom.xml`

Then:

1. rebuilt the backend JAR with `mvn package`
2. uploaded the corrected JAR to EC2
3. copied it over `/opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar`
4. restarted the service

## 16. Verifying Flyway Finally Ran

After the corrected JAR was deployed, the schema was verified directly in RDS.

Observed relations:

1. `public.app_user_financial_plan`
2. `public.flyway_schema_history`

This confirmed:

1. Flyway was active
2. the migration file ran successfully against RDS
3. the database schema now matched the application expectation

## 17. Correcting And Re-running The Data Import

With the schema now present, the uploaded SQL data file was fixed in place on EC2.

Command used:

1. `sed -i '/transaction_timeout/d' ~/app_user_financial_plan_data.sql`

Then the import was rerun from EC2 into RDS using `psql`.

Result:

1. data import succeeded

## 18. Final Database Verification

After the corrected import, the final verification query returned three rows.

Rows present in `app_user_financial_plan`:

1. `117286134050139874787 | naudiyal@gmail.com | Devin Naudiyal`
2. `100350673248008311192 | innaudiyal@gmail.com | Devin Naudiyal`
3. `sample-mybetterbudget-com | sample@mybetterbudget.com | Sample Plan`

Meaning:

1. the primary production user record was imported
2. the secondary user record was imported
3. the sample tracker row was imported

## 19. Files Created Or Updated During The Cutover Work

These repo-side files were created or updated to support the RDS promotion:

1. `FinancialPlanningApi/src/main/resources/db/migration/V1__create_app_user_financial_plan.sql`
2. `FinancialPlanningApi/scripts/export_app_user_financial_plan.ps1`
3. `FinancialPlanningApi/scripts/import_app_user_financial_plan_to_rds.ps1`
4. `FinancialPlanningApi/docs/AWS_RDS_PROMOTION_PLAN.md`
5. `FinancialPlanningApi/docs/AWS_RDS_CUTOVER_RUNBOOK.md`
6. `FinancialPlanningApi/docs/AWS_RDS_EXECUTION_SUMMARY.md`
7. `FinancialPlanningApi/.gitignore`
8. `FinancialPlanningApi/pom.xml`

Important code fixes made during this process:

1. export script parameter name was changed from `Host` to `DbHost` to avoid collision with PowerShell's built-in `$Host`
2. Flyway PostgreSQL support was added through `flyway-database-postgresql`

## 20. Current Production State At The End Of The Cutover

At the end of the work completed so far:

1. EC2 is still serving the app
2. Nginx is still fronting the site
3. Spring Boot is running as `financial-planning-api.service`
4. production persistence is on Amazon RDS PostgreSQL
5. the RDS schema is managed by Flyway
6. production data was imported from local development PostgreSQL
7. the old production JSON file was backed up on EC2
8. the RDS database contains the expected three rows

## 21. Remaining Manual Verification

The remaining step after this log is browser-based smoke testing.

Recommended verification flow:

1. sign in at `https://mybetterbudget.com`
2. confirm saved tracker data appears immediately
3. make a small change and save it
4. refresh and confirm the saved change persists
5. open Sample Tracker and confirm it loads the sample data
6. edit the sample data and confirm those edits remain local-only

## 22. Lessons Learned During This Cutover

Operational lessons from this cutover:

1. a successful Spring Boot startup does not prove Flyway migrations executed
2. for RDS in a private VPC setup, security group to security group access is the important connectivity check
3. deployment commands must match the exact JAR path referenced by `systemd`
4. PostgreSQL export files created by newer client tools can include settings that older target versions reject
5. when uploading to EC2, copying to the home directory first is the simplest pattern when `/opt/...` is root-owned
6. when troubleshooting interactive shells, distinguishing between pager mode, query continuation mode, and shell mode saves time