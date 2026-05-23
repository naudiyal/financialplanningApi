# Google Login Handoff

## Overview

Google login was added to the Financial Planning application using Spring Boot on the backend and React on the frontend.

The backend now handles Google OAuth and session management. The frontend now requires authentication before loading the main application.

Current behavior:

1. A user opens the UI.
2. The UI checks `/api/auth/me`.
3. If the user is not authenticated, the UI shows a Google sign-in screen.
4. The user signs in through Google.
5. Spring Security creates a Google-backed browser session.
6. The frontend exchanges that session for a tab-scoped bearer token through `/api/auth/tab-token`.
7. The frontend stores that token in `sessionStorage` and uses it for authenticated API requests from that tab.

## What Changed

### Backend

Files:

- [FinancialPlanningApi/pom.xml](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/pom.xml)
- [FinancialPlanningApi/src/main/resources/application.properties](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/resources/application.properties)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/WebConfig.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/WebConfig.java)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/SecurityConfig.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/config/SecurityConfig.java)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/controller/AuthController.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/controller/AuthController.java)
- [FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/model/AuthUserResponse.java](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi/src/main/java/com/naudi/financialplanningapi/model/AuthUserResponse.java)

Summary:

1. Added Spring Security and OAuth2 client dependencies.
2. Added Google OAuth configuration through environment variables.
3. Protected financial-plan API endpoints.
4. Added `/api/auth/me` for auth status.
5. Added `/api/auth/logout` for sign-out.
6. Added `/api/auth/tab-token` to mint a signed tab-scoped bearer token from the current Google session.
7. Added bearer-token authentication for `/api/**` requests so different tabs can use different signed-in accounts.
8. Added `X-Expected-User-Sub` validation on personal write endpoints so stale tabs cannot save data into the wrong account.
9. Enabled CORS credentials for the frontend.

### Frontend

Files:

- [FinancialPlanningUI/src/App.tsx](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningUI/src/App.tsx)
- [FinancialPlanningUI/src/index.css](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningUI/src/index.css)

Summary:

1. Added a login gate.
2. Added a Google sign-in screen.
3. Added a tab-token bootstrap step after Google sign-in.
4. Added authenticated fetches that attach `Authorization: Bearer <tab token>`.
5. Added expected-user headers on personal write requests.
6. Added logout support.
7. Added signed-in user display in the UI.

## Per-Tab Auth Behavior

The original Google login integration relied only on the shared browser session cookie. That meant two tabs in the same browser profile could silently start using whichever account had most recently signed in, which was unsafe for encrypted personal saves.

The current flow keeps the Google session for login handoff, but each tab now receives its own signed bearer token and stores it in `sessionStorage`.

This means:

1. Different tabs or windows in the same browser profile can stay signed into different accounts at the same time.
2. Personal write endpoints are still guarded by the authenticated Google `sub` value to block stale or mismatched saves.
3. If the backend is restarted and no fixed tab-token secret is configured, previously issued tab tokens become invalid and the tab must refresh or sign in again.

Optional stability setting:

- Set `APP_AUTH_TAB_TOKEN_SECRET` for local or deployed environments if you want tab tokens to survive backend restarts instead of being invalidated on each process start.

## Google Cloud Setup That Was Done

1. Created or selected a Google Cloud project.
2. Configured the OAuth consent screen.
3. Chose app name `financialplanning`.
4. Created an OAuth client of type `Web application`.
5. Used local development URLs.

Local values used:

Authorized JavaScript origins:

- `http://localhost:5173`
- `http://127.0.0.1:5173`

Authorized redirect URI:

- `http://localhost:8080/login/oauth2/code/google`

## Local Run Instructions

### Backend

Run from [FinancialPlanningApi](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningApi):

```powershell
$env:GOOGLE_CLIENT_ID="your-client-id"
$env:GOOGLE_CLIENT_SECRET="your-client-secret"
$env:APP_UI_URL="http://localhost:5173"
$env:APP_AUTH_TAB_TOKEN_SECRET="replace-with-a-long-random-secret"
mvn spring-boot:run
```

Notes:

1. These environment variables are session-scoped in PowerShell.
2. If you open a new terminal, set them again unless you persisted them with `setx`.
3. `APP_AUTH_TAB_TOKEN_SECRET` is recommended so existing tab tokens remain valid across backend restarts.
4. If port `8080` is already in use, stop the stale Java process before starting the backend again.

### Frontend

Run from [FinancialPlanningUI](/c:/Users/naudi/workspace/FinancialPlanning/FinancialPlanningUI):

```powershell
npm run dev
```

Open:

- `http://localhost:5173`

## What Was Verified

1. Backend compile succeeded.
2. Frontend build succeeded.
3. Backend auth status endpoint returned success.
4. Google sign-in worked locally.
5. After login, the application loaded successfully.
6. Separate tabs could keep different signed-in accounts without sharing the same effective API identity.

## Current Limitations

1. Any Google user can currently sign in.
2. All authenticated users currently share the same JSON data file.
3. Environment variables are not yet permanently configured unless saved outside the current shell session.

## Recommended Next Steps

1. Restrict access to approved email addresses if needed.
2. Move from shared JSON storage to per-user storage.
3. Add production OAuth URLs and deployment configuration.
4. Persist local environment variables permanently if you do not want to set them each time.
