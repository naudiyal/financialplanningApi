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
  "$matches = Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Where-Object { $_.CommandLine -like '*FinancialPlanningApi*' -or $_.CommandLine -like '*target\classes*' -or $_.CommandLine -like '*financial-planning-api-0.0.1-SNAPSHOT.jar*' -or $_.CommandLine -like '*com.naudi.financialplanningapi.FinancialPlanningApiApplication*' }; if ($matches) { $matches | Select-Object ProcessId, Name, CommandLine | Format-List; $matches | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } } else { Write-Host 'No matching FinancialPlanning API Java processes found.' }"
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
endlocal