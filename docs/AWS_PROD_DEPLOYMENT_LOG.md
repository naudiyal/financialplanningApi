# AWS Production Deployment Log

This document captures the production deployment work for the Financial Planning application on AWS. It includes:

1. your questions
2. the guidance provided
3. the actions you took
4. the issues encountered
5. the explanation of those issues
6. how each issue was resolved
7. the final production outcome

Historical note: this log includes earlier file-based storage steps and references to `financial-plan.json`. The current application stores plan data in PostgreSQL and does not depend on `APP_STORAGE_PATH` for normal runtime.

## 1. Initial Production Hosting Question

### Your question

"how can I move this to a production server so that I can use my applicaiton over internet?"

### My answer

I recommended a simple production architecture that matched the current app design:

1. Host both frontend and backend on a single server.
2. Use Nginx to serve the React frontend.
3. Use Nginx to reverse-proxy API and OAuth routes to Spring Boot.
4. Use one public domain for both frontend and backend.
5. Use HTTPS.

I explained that same-origin hosting was the cleanest option because the backend already managed Google OAuth and sessions.

### Relevant app facts considered

These current files mattered to the production plan:

- [FinancialPlanningApi/src/main/resources/application.properties](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/resources/application.properties)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/WebConfig.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/WebConfig.java)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/SecurityConfig.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/SecurityConfig.java)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/service/FinancialPlanStorageService.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/service/FinancialPlanStorageService.java)

### What you did next

You decided to use AWS.

## 2. Choosing AWS

### Your question

"I want to use AWS"

### My answer

I recommended:

1. AWS EC2 for the server
2. Nginx on the EC2 instance
3. Spring Boot JAR as a `systemd` service
4. Vite frontend build served by Nginx
5. Route 53 and HTTPS later

I explicitly advised against overcomplicating the first deployment with ECS, EKS, or Lambda because the app was currently:

1. one Spring Boot service
2. one static frontend
3. one JSON storage file

### What you did next

You asked whether AWS-provided hostnames could be used instead of buying a domain immediately.

## 3. Domain Question Before Buying One

### Your question

"I have An AWS account. Do not have a domain name yet but for now can we use whatever aws provides. I can create Google OAuth client ID and secret for prod application Repo is working locally"

### My answer

I explained that while a raw EC2 IP or AWS-provided hostname could be used for rough smoke tests, it was not a good production path for Google OAuth because:

1. Google OAuth production flows should use a stable public domain.
2. HTTPS should be used.
3. The app uses session cookies and backend-managed redirects.
4. Same-origin deployment with a real domain is much cleaner and less fragile.

### What you did next

You asked for domain suggestions.

## 4. Domain Name Selection

### Your questions

You asked for:

1. general domain suggestions
2. names similar to `planmymoney.com`
3. a recommendation between `mybetterbudget.com` and `mygoodbudget.com`

### My answer

I recommended `mybetterbudget.com` because it sounded stronger, clearer, and more product-like.

### What you did next

You requested `mybetterbudget.com` and then continued with AWS setup while the domain was processing.

## 5. Beginning The EC2 Deployment

### Your question

"Guide me through each step"

### My answer

I provided a phased AWS deployment plan:

1. Launch an EC2 instance.
2. Attach an Elastic IP.
3. Build frontend and backend locally.
4. Upload the artifacts to the server.
5. Configure backend environment variables.
6. Create a `systemd` service for Spring Boot.
7. Configure Nginx.
8. Add HTTPS with Certbot.
9. Point DNS to the server.
10. Test Google OAuth on the production domain.

### What you did next

You started the EC2 launch flow and asked several setup questions.

## 6. EC2 Launch Configuration Questions

### Your questions

You asked about:

1. key pair type: `RSA` or `ED25519`
2. whether SSH should allow `Anywhere`
3. what `HTTP` and `HTTPS` mean in AWS security group rules
4. whether the default `8 GB` storage was enough

### My answers

I recommended:

1. `ED25519` as the better modern SSH key type
2. `Anywhere` for SSH temporarily if needed for convenience, with the note that it should be hardened later
3. that `HTTP` means port `80` and `HTTPS` means port `443`
4. `16 GB` or `20 GB` as a comfortable disk size, though `8 GB` would work initially

### What you did next

You selected `16 GB` storage and launched the instance.

## 7. Elastic IP And First SSH Failure

### What happened

You attempted SSH using:

- `44.198.94.78`

but got a timeout.

### Your question

You reported that the SSH connection timed out.

### My answer

I checked reachability and explained that this looked like a network or addressing problem rather than a key problem.

### Checks performed

1. Confirmed the key file existed locally.
2. Confirmed port `22` was not reachable on `44.198.94.78` at that time.

### Root cause

The Elastic IP had not yet been correctly associated to the running instance.

### What you found

You later reported that the instance public IP was actually:

- `44.200.101.240`

not `44.198.94.78`.

### Explanation

That meant:

1. the instance had received an auto-assigned public IP
2. the Elastic IP was not yet attached to the instance

### Resolution

You associated `44.198.94.78` to the instance, including selecting the instance’s private IP during association.

### What happened next

SSH worked after the network addressing was corrected.

## 8. Local Builds And Upload Preparation

### Your questions

You asked:

1. whether `npm install` needed to be run again
2. why `npm run build` was needed if the app was already running locally
3. where the `scp` commands should be run from

### My answers

I explained:

1. `npm install` is only needed again if dependencies are missing or changed
2. `npm run build` is required to create a production frontend bundle in `dist`
3. `mvn clean package` is required to create the backend JAR
4. the `scp` commands must be run from your local machine because the source paths are on your Windows machine

### What you did next

You built the backend and frontend locally and started uploading files to the server.

## 9. Creating Server Directories And Uploading Files

### What happened

You created the deployment directories on the Ubuntu server and then used `scp` from your local machine to upload:

1. frontend build files
2. backend JAR
3. `financial-plan.json`

### What you confirmed

You confirmed that SSH was working and directories had been created, which meant file upload could proceed.

## 10. Backend Environment File Creation

### Your question

You asked for help creating the production backend environment file.

### My answer

I guided you to create:

- `/etc/financial-planning/api.env`

with:

```bash
GOOGLE_CLIENT_ID=your-production-google-client-id
GOOGLE_CLIENT_SECRET=your-production-google-client-secret
APP_UI_URL=https://mybetterbudget.com
APP_STORAGE_PATH=/opt/financial-planning/data/financial-plan.json
```

### Issue encountered

When you tried to inspect the file afterward, `cat /etc/financial-planning/api.env` returned `No such file or directory`.

### Explanation

The likely causes were:

1. the directory did not exist when the file was first created
2. the file was not actually saved in `nano`
3. the filename may have been changed accidentally in the save prompt

### Resolution

I instructed you to:

1. explicitly create `/etc/financial-planning`
2. recreate the file with `sudo nano`
3. save it using `Ctrl+O`, `Enter`, `Ctrl+X`
4. verify it with `sudo cat /etc/financial-planning/api.env`

### Outcome

You confirmed it looked good afterward.

## 11. Creating The Backend Service

### Your action

You created the `systemd` service file:

- `/etc/systemd/system/financial-planning-api.service`

### My guidance

I provided the service definition to:

1. run the Spring Boot JAR from `/opt/financial-planning/api`
2. load environment variables from `/etc/financial-planning/api.env`
3. restart automatically on failure

### What happened

When you ran `sudo systemctl status financial-planning-api`, the status output filled the terminal and you did not see the normal shell prompt.

### Explanation

That happened because `systemctl status` usually opens its output in a pager like `less`.

### Resolution

I told you to press `q` to exit the pager without stopping the app.

### Outcome

You confirmed the app appeared to be running on the Ubuntu server.

## 12. First Nginx Check And Default Welcome Page

### Your observation

When you opened:

- `http://44.198.94.78`

you saw:

- `Welcome to nginx!`

### Explanation

That meant:

1. Nginx itself was installed and working
2. the default Nginx site was still being served
3. your custom site was not yet active for the public requests

### Resolution

I instructed you to:

1. remove `/etc/nginx/sites-enabled/default`
2. enable `/etc/nginx/sites-available/financial-planning`
3. run `sudo nginx -t`
4. reload Nginx

### What you confirmed

You confirmed the frontend files existed under `/opt/financial-planning/ui`, including `index.html` and the `assets` folder.

## 13. First Internal Server Error From Nginx

### Your observation

After enabling your site, `http://44.198.94.78` returned an internal server error.

### Investigation

I checked:

1. backend service status
2. backend logs
3. Nginx error log
4. Nginx site configuration

### Findings

The backend was healthy and running on port `8080`.

The Nginx error log showed repeated permission failures such as:

`stat() "/opt/financial-planning/ui/index.html" failed (13: Permission denied)`

### Explanation

Nginx could not read the frontend files because the file and directory permissions under `/opt/financial-planning/ui` were too restrictive for the Nginx worker user.

### Resolution

I instructed you to grant read and traversal permissions:

```bash
sudo chmod 755 /opt
sudo chmod 755 /opt/financial-planning
sudo chmod -R 755 /opt/financial-planning/ui
sudo find /opt/financial-planning/ui -type f -exec chmod 644 {} \;
sudo systemctl reload nginx
```

### Outcome

The React app began to load instead of showing the internal server error.

## 14. Authentication Service Unavailable In Production UI

### Your observation

The React app loaded and showed the sign-in page, but also displayed a message saying the authentication service was unavailable.

### Investigation

I inspected the frontend code and found this line in [FinancialPlanningUI/src/App.tsx](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningUI/src/App.tsx):

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
```

### Explanation

This was correct for local development, but wrong for production because the production frontend bundle was still trying to call `http://localhost:8080` from the browser.

In a production browser, `localhost` means the user’s own machine, not the EC2 instance.

### Resolution

I changed the frontend so it uses:

1. `http://localhost:8080` only during development
2. same-origin requests in production

Updated line:

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')
```

Then I rebuilt the frontend locally and instructed you to re-upload the frontend build.

### Outcome

That removed the production frontend API-base misconfiguration.

## 15. Second Internal Server Error After Re-upload

### Your observation

After re-uploading the rebuilt frontend, you saw an internal server error again on `http://44.198.94.78`.

### Investigation

I checked the Nginx logs and directory permissions again.

### Findings

The `scp` upload had reset directory permissions on the UI folders. In particular:

- `/opt/financial-planning/ui` was `drwx------`
- `/opt/financial-planning/ui/assets` was `drwx------`

### Explanation

That meant only the `ubuntu` user could enter those directories. Nginx still could not traverse them.

### Resolution

I instructed you to reset the directory and file permissions again using:

```bash
sudo chmod 755 /opt/financial-planning/ui
sudo chmod 755 /opt/financial-planning/ui/assets
sudo find /opt/financial-planning/ui -type d -exec chmod 755 {} \;
sudo find /opt/financial-planning/ui -type f -exec chmod 644 {} \;
sudo systemctl reload nginx
```

### Outcome

The internal server error was resolved.

## 16. Google OAuth Error On Raw IP Address

### Your observation

When you clicked `Sign in with Google` while testing on:

- `http://44.198.94.78`

Google returned:

- `Access blocked: Authorization Error`
- `Error 400: invalid_request`

### Explanation

This was expected because the production Google OAuth client was configured for:

1. `https://mybetterbudget.com`
2. `https://www.mybetterbudget.com`

but not for the raw IP address.

Google OAuth requires the origin and redirect URI to match the configured production values exactly.

### Resolution

I explained that raw-IP login was not the correct production test and that the next steps had to be:

1. point the domain to the server
2. add HTTPS
3. test login on the real domain

## 17. Production Google OAuth Client Setup

### Your action

You created the production Google OAuth client with these values:

Authorized JavaScript origins:

1. `https://mybetterbudget.com`
2. `https://www.mybetterbudget.com`

Authorized redirect URIs:

1. `https://mybetterbudget.com/login/oauth2/code/google`
2. `https://www.mybetterbudget.com/login/oauth2/code/google`

### My guidance

I confirmed that this setup was correct and told you the server env file did not need those values directly, only the client ID and secret.

## 18. Forwarded Headers Production Fix

### What happened

Before final production rollout, I added this line to [FinancialPlanningApi/src/main/resources/application.properties](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/resources/application.properties):

```properties
server.forward-headers-strategy=framework
```

### Explanation

This helps Spring Boot correctly interpret Nginx’s forwarded HTTPS headers so redirects and callback URLs behave correctly behind the reverse proxy.

### What you did next

You rebuilt the backend JAR locally and re-uploaded it to the server.

## 19. DNS And Certbot Failure

### Your observation

When you first ran:

```bash
sudo certbot --nginx -d mybetterbudget.com -d www.mybetterbudget.com
```

Certbot failed with:

1. no valid `A` record for `mybetterbudget.com`
2. `NXDOMAIN` for `www.mybetterbudget.com`

### Explanation

This was a DNS problem, not a Certbot problem. Let’s Encrypt could not verify the domains because public DNS did not yet resolve them to your server.

### Resolution

I instructed you to create DNS records:

1. `A` record for `mybetterbudget.com` -> `44.198.94.78`
2. `CNAME` for `www.mybetterbudget.com` -> `mybetterbudget.com`

### What you confirmed

You later showed successful DNS lookups on the server.

## 20. Successful DNS Resolution

### Your observation

On the Ubuntu server, `nslookup` showed:

1. `mybetterbudget.com` resolving to `44.198.94.78`
2. `www.mybetterbudget.com` resolving as a CNAME to `mybetterbudget.com`

### My answer

I confirmed that the DNS setup was correct and told you to rerun Certbot.

## 21. Successful HTTPS Certificate Setup

### Your observation

When you reran Certbot, it succeeded.

### What Certbot reported

1. certificate issued successfully
2. certificate deployed to the Nginx site
3. HTTPS enabled on:
   - `https://mybetterbudget.com`
   - `https://www.mybetterbudget.com`

### Meaning

At that point, the app had:

1. public DNS
2. working Nginx site
3. working backend service
4. valid HTTPS certificate

## 22. Local DNS Resolver Problem On Your Windows Machine

### Your observation

Despite public DNS being configured, your browser on Windows showed:

- `DNS_PROBE_FINISHED_NXDOMAIN`

and local `nslookup` using your default DNS server at `10.0.0.1` did not show the expected address correctly.

### Investigation

I had you query public DNS directly using:

1. `8.8.8.8`
2. `1.1.1.1`

### Findings

Both public resolvers correctly returned:

- `mybetterbudget.com` -> `44.198.94.78`
- `www.mybetterbudget.com` -> alias to `mybetterbudget.com`

### Explanation

That meant:

1. public DNS was correct
2. the deployment was reachable on the internet
3. only your local network DNS cache or router resolver was stale

### Resolution

I instructed you to run:

```powershell
ipconfig /flushdns
```

### Outcome

After flushing DNS, you were able to proceed.

## 23. Final Production Login Success

### Your statement

"Login worked"

### Meaning

This confirmed the full production deployment was successful.

The following were all working together:

1. AWS EC2 hosting
2. Elastic IP
3. Spring Boot backend as a service
4. React frontend served by Nginx
5. Nginx reverse proxy
6. production DNS
7. HTTPS certificate
8. production Google OAuth client
9. Google login on the live domain

## 24. Final Production State

By the end of deployment, the live production application was available at:

- `https://mybetterbudget.com`

and Google login worked successfully there.

### Current production architecture

1. Frontend served from `/opt/financial-planning/ui`
2. Backend JAR served from `/opt/financial-planning/api`
3. JSON data stored at `/opt/financial-planning/data/financial-plan.json`
4. Spring Boot managed by `systemd`
5. Nginx handling static frontend and reverse proxy
6. Let’s Encrypt certificate managed by Certbot

## 25. Issues Encountered Summary

Here is the concise issue list and resolution summary.

### Issue 1

SSH timed out.

Cause:
Elastic IP not properly associated and wrong public IP used.

Resolution:
Associated the Elastic IP to the instance and retried SSH with the correct public address.

### Issue 2

Environment file appeared missing.

Cause:
The file was not saved correctly or the directory did not exist yet.

Resolution:
Created the directory explicitly, recreated the file, saved it properly, and verified with `sudo cat`.

### Issue 3

Nginx showed the default welcome page.

Cause:
The default site was still enabled instead of the app site.

Resolution:
Removed `/etc/nginx/sites-enabled/default`, enabled the custom site, tested config, and reloaded Nginx.

### Issue 4

Internal server error from Nginx.

Cause:
Frontend files existed but Nginx did not have permission to read them.

Resolution:
Adjusted directory and file permissions to allow Nginx access.

### Issue 5

Frontend showed `authentication service unavailable`.

Cause:
Production frontend bundle still defaulted API calls to `http://localhost:8080`.

Resolution:
Changed frontend default API base URL to same-origin in production, rebuilt, and re-uploaded the frontend.

### Issue 6

Internal server error returned again after frontend re-upload.

Cause:
`scp` upload reset restrictive directory permissions on the UI folders.

Resolution:
Reapplied executable permissions to the uploaded directories and read permissions to the files.

### Issue 7

Google login failed on the raw IP.

Cause:
The production OAuth client was configured for the production domain, not the EC2 IP.

Resolution:
Stopped testing login on the IP and moved to domain + HTTPS setup.

### Issue 8

Certbot failed.

Cause:
Public DNS records for the domain and `www` were not set up yet.

Resolution:
Created the DNS records, verified resolution, and reran Certbot successfully.

### Issue 9

Browser showed `DNS_PROBE_FINISHED_NXDOMAIN` even after DNS was set.

Cause:
Local router or local DNS cache was stale.

Resolution:
Verified public DNS with Google and Cloudflare resolvers, then flushed local DNS cache.

## 26. Production-Specific Code Changes That Mattered

### Frontend change

In [FinancialPlanningUI/src/App.tsx](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningUI/src/App.tsx), the frontend was updated so production defaults to same-origin API calls instead of `http://localhost:8080`.

### Backend change

In [FinancialPlanningApi/src/main/resources/application.properties](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/resources/application.properties), this production-relevant property was added:

```properties
server.forward-headers-strategy=framework
```

This ensures Spring Boot correctly respects Nginx’s forwarded HTTPS headers.

## 27. Final Outcome

The production deployment succeeded.

You now have:

1. a live AWS-hosted application
2. working HTTPS
3. working Google login on the production domain
4. frontend and backend correctly integrated behind Nginx
5. a production-ready first deployment for the current shared-JSON architecture

## 28. Suggested Next Improvements

After the successful production deployment, the most natural next improvements are:

1. Restrict allowed Google accounts if you do not want open sign-in.
2. Move from shared JSON storage to a real database or per-user data model.
3. Add backup and recovery for the JSON data file.
4. Commit all production-safe code changes if not already committed.
5. Add deployment notes and operational runbooks for future updates.
