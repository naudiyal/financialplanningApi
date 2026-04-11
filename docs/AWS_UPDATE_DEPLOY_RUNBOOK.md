# AWS Update Deploy Runbook

This runbook is the short version for pushing updated UI and API builds to the existing AWS EC2 deployment.

Verified on April 5, 2026:

- EC2 host: `ec2-44-198-94-78.compute-1.amazonaws.com`
- SSH user: `ubuntu`
- Backend service: `financial-planning-api`
- Backend JAR used by systemd: `/opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar`
- Frontend directory served by Nginx: `/opt/financial-planning/ui`

Verified local Windows tool paths on April 11, 2026:

- npm: `C:\Program Files\nodejs\npm.cmd`
- node: `C:\Program Files\nodejs\node.exe`
- Maven: `C:\Users\naudi\OneDrive\workspace\tools\apache-maven-3.9.14\bin\mvn.cmd`
- psql: `C:\Program Files\PostgreSQL\16\bin\psql.exe`

Reusable promotion scripts now exist in the workspace root:

- `promote-api-aws.bat`
- `promote-ui-aws.bat`
- `promote-sql-aws.bat`
- full guide: `DEPLOY_AND_VERIFY_AWS.md`

SQL handling rule:

- Flyway migrations under `src/main/resources/db/migration` are applied by the backend during API deployment.
- Manual one-off SQL scripts are not executed by `promote-api-aws.bat`; run them separately with `promote-sql-aws.bat`.

## 1. Build locally on Windows

From the workspace root:

```powershell
cd C:\Users\naudi\OneDrive\workspace\FinancialPlanning
```

Build the UI:

```powershell
$env:PATH = "C:\Program Files\nodejs;" + $env:PATH
& "C:\Program Files\nodejs\npm.cmd" --prefix .\FinancialPlanningUI run build
```

Build the API:

```powershell
& "C:\Users\naudi\OneDrive\workspace\tools\apache-maven-3.9.14\bin\mvn.cmd" -f .\FinancialPlanningApi\pom.xml package
```

Return to the workspace root:

```powershell
cd .
```

## 2. Upload the build artifacts to EC2

Upload the backend JAR to the EC2 home directory:

```powershell
scp -i ".\mybetterbudget-key.pem" .\FinancialPlanningApi\target\financial-planning-api-0.0.1-SNAPSHOT.jar ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com:~/
```

Upload the frontend `dist` folder to the EC2 home directory:

```powershell
scp -i ".\mybetterbudget-key.pem" -r .\FinancialPlanningUI\dist ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com:~/
```

## 3. SSH into EC2

```powershell
ssh -i ".\mybetterbudget-key.pem" ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com
```

After you connect, you are on an Ubuntu server, so the next commands run in the Linux shell on EC2, not in Windows PowerShell.

## 4. Deploy the backend on EC2

Copy the uploaded JAR over the exact file used by `systemd`:

```bash
cp ~/financial-planning-api-0.0.1-SNAPSHOT.jar /opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar
```

If you want to stay in Windows PowerShell and avoid opening an interactive SSH session, you can run the same remote commands like this:

```powershell
ssh -i ".\mybetterbudget-key.pem" ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com "cp ~/financial-planning-api-0.0.1-SNAPSHOT.jar /opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar"
```

Restart the backend service:

```bash
sudo systemctl restart financial-planning-api
sudo systemctl status financial-planning-api --no-pager
```

PowerShell one-liner version:

```powershell
ssh -i ".\mybetterbudget-key.pem" ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com "sudo systemctl restart financial-planning-api; sudo systemctl status financial-planning-api --no-pager"
```

Check recent backend logs if needed:

```bash
sudo journalctl -u financial-planning-api -n 100 --no-pager
```

PowerShell one-liner version:

```powershell
ssh -i ".\mybetterbudget-key.pem" ubuntu@ec2-44-198-94-78.compute-1.amazonaws.com "sudo journalctl -u financial-planning-api -n 100 --no-pager"
```

## 5. Deploy the frontend on EC2

Replace the current frontend files with the uploaded build output:

```bash
sudo mkdir -p /opt/financial-planning/ui
sudo rm -rf /opt/financial-planning/ui/*
sudo cp -r ~/dist/* /opt/financial-planning/ui/
```

Reset permissions so Nginx can read the files:

```bash
sudo find /opt/financial-planning/ui -type d -exec chmod 755 {} \;
sudo find /opt/financial-planning/ui -type f -exec chmod 644 {} \;
```

Validate and reload Nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 6. Quick verification on EC2

Check that the site is responding through the configured Nginx host routing on the server:

```bash
curl -I -H 'Host: mybetterbudget.com' http://127.0.0.1
curl -I https://mybetterbudget.com
```

`curl -I http://localhost` returns `404` on this server because the active Nginx site is bound to `mybetterbudget.com` and `www.mybetterbudget.com`, not `localhost`.

If you have an API endpoint you want to verify through Nginx, test that as well. If not, rely on `systemctl status` and `journalctl` for backend verification.

## 7. Important notes

- Do not deploy the backend JAR under `/opt/financial-planning/api/financial-planning-api.jar` unless you also change the `ExecStart` line in the service file.
- The currently active service file points to `/opt/financial-planning/api/financial-planning-api-0.0.1-SNAPSHOT.jar`.
- Run `scp` from your Windows machine, not from inside the EC2 SSH session.
- On EC2, commands run in the Ubuntu shell. If you prefer PowerShell, use `ssh "...commands..."` from Windows PowerShell to execute the remote Linux commands.
- If frontend uploads reset directory permissions again, rerun the permission commands in Step 5.

## 8. Current related docs

Longer deployment history and troubleshooting are also documented here:

- `DEPLOY_AND_VERIFY_AWS.md`
- `FinancialPlanningApi/docs/AWS_PROD_DEPLOYMENT_LOG.md`
- `FinancialPlanningApi/docs/AWS_RDS_EXECUTION_SUMMARY.md`
- `FinancialPlanningApi/docs/AWS_RDS_CUTOVER_RUNBOOK.md`