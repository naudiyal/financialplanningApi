@echo off
setlocal

set "ROOT=C:\Users\naudi\OneDrive\workspace\FinancialPlanning"
set "API_DIR=%ROOT%\FinancialPlanningApi"
set "TARGET_DIR=%API_DIR%\target"
set "MAVEN=C:\Users\naudi\OneDrive\workspace\tools\apache-maven-3.9.14\bin\mvn.cmd"
set "POM=%API_DIR%\pom.xml"
set "PORT=8080"

if not exist "%MAVEN%" (
  echo Maven not found at "%MAVEN%"
  exit /b 1
)

if not exist "%POM%" (
  echo API pom.xml not found at "%POM%"
  exit /b 1
)

echo Checking for running FinancialPlanning API Java processes...
powershell -NoProfile -Command ^
  "$matches = Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Where-Object { $_.CommandLine -like '*FinancialPlanningApi*' -or $_.CommandLine -like '*target\\classes*' -or $_.CommandLine -like '*financial-planning-api-0.0.1-SNAPSHOT.jar*' -or $_.CommandLine -like '*com.naudi.financialplanningapi.FinancialPlanningApiApplication*' }; if ($matches) { $matches | Select-Object ProcessId, Name, CommandLine | Format-List; $matches | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } } else { Write-Host 'No matching FinancialPlanning API Java processes found.' }"
if errorlevel 1 exit /b 1

set "PID="
for /f %%P in ('powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess)"') do set "PID=%%P"

if defined PID (
  echo Found process listening on port %PORT% with PID %PID%.
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter 'ProcessId = %PID%' | Select-Object ProcessId, Name, CommandLine | Format-List"
  echo Stopping PID %PID% so port %PORT% is free...
  taskkill /PID %PID% /F
  if errorlevel 1 exit /b 1
) else (
  echo No process is listening on port %PORT%.
)

if exist "%TARGET_DIR%" (
  echo Attempting to clear "%TARGET_DIR%" before Maven clean...
  rmdir /s /q "%TARGET_DIR%"
  if exist "%TARGET_DIR%" (
    echo Target directory is still present. Maven clean will retry removal.
  ) else (
    echo Target directory cleared.
  )
)

echo Running Maven clean install for FinancialPlanningApi...
call "%MAVEN%" -f "%POM%" clean install
if errorlevel 1 exit /b 1

echo Clean install complete.

REM Auto-detect Google OAuth settings from client secret JSON if not already set
set "OAUTH_CLIENT_JSON="
set "LOCAL_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google"

if not defined GOOGLE_CLIENT_ID (
  for %%F in ("%ROOT%\client_secret_*.apps.googleusercontent.com.json") do (
    powershell -NoProfile -Command "$content = Get-Content -Raw '%%~fF'; if ($content -like '*%LOCAL_REDIRECT_URI%*') { exit 0 } else { exit 1 }" >nul 2>nul
    if not errorlevel 1 set "OAUTH_CLIENT_JSON=%%~fF"
  )
)

if not defined GOOGLE_CLIENT_ID if not defined OAUTH_CLIENT_JSON (
  for %%F in ("%ROOT%\client_secret_*.apps.googleusercontent.com.json") do (
    if not defined OAUTH_CLIENT_JSON set "OAUTH_CLIENT_JSON=%%~fF"
  )
)

if not defined GOOGLE_CLIENT_ID if defined OAUTH_CLIENT_JSON (
  for /f "usebackq delims=" %%V in (`powershell -NoProfile -Command "$json = Get-Content -Raw \"%OAUTH_CLIENT_JSON%\" | ConvertFrom-Json; $json.web.client_id"`) do set "GOOGLE_CLIENT_ID=%%V"
)

if not defined GOOGLE_CLIENT_SECRET if defined OAUTH_CLIENT_JSON (
  for /f "usebackq delims=" %%V in (`powershell -NoProfile -Command "$json = Get-Content -Raw \"%OAUTH_CLIENT_JSON%\" | ConvertFrom-Json; $json.web.client_secret"`) do set "GOOGLE_CLIENT_SECRET=%%V"
)

if not defined GOOGLE_CLIENT_ID (
  echo GOOGLE_CLIENT_ID is not set.
  echo Set it in this shell before running this script, or place a local Google client secret JSON file in the workspace root.
  exit /b 1
)

if not defined GOOGLE_CLIENT_SECRET (
  echo GOOGLE_CLIENT_SECRET is not set.
  echo Set it in this shell before running this script, or place a local Google client secret JSON file in the workspace root.
  exit /b 1
)

if defined OAUTH_CLIENT_JSON (
  echo Loaded Google OAuth settings from "%OAUTH_CLIENT_JSON%".
)

REM Set local database and UI defaults if not already set
if not defined APP_DATASOURCE_URL set "APP_DATASOURCE_URL=jdbc:postgresql://localhost:5432/financial_planning"
if not defined APP_DATASOURCE_USERNAME set "APP_DATASOURCE_USERNAME=financial_app"
if not defined APP_DATASOURCE_PASSWORD set "APP_DATASOURCE_PASSWORD=password"
if not defined APP_UI_URL set "APP_UI_URL=http://localhost:5173"

REM Run the Spring Boot API
echo Starting FinancialPlanning API...
call "%MAVEN%" -f "%POM%" spring-boot:run
if errorlevel 1 exit /b 1

endlocal
