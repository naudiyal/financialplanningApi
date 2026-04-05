param(
    [Parameter(Mandatory = $true)]
    [string]$RdsHost,

    [string]$Database = "financial_planning",
    [string]$Username = "financial_app",

    [Parameter(Mandatory = $true)]
    [string]$Password,

    [string]$DataFile = "artifacts/db-export/app_user_financial_plan_data.sql",
    [switch]$TruncateBeforeImport
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedDataFile = if ([System.IO.Path]::IsPathRooted($DataFile)) {
    $DataFile
} else {
    Join-Path $repoRoot $DataFile
}

if (-not (Test-Path $resolvedDataFile)) {
    throw "Data file not found: $resolvedDataFile"
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found in PATH. Install PostgreSQL client tools or add them to PATH."
}

$env:PGPASSWORD = $Password

if ($TruncateBeforeImport) {
    psql -v ON_ERROR_STOP=1 -U $Username -h $RdsHost -d $Database -c "TRUNCATE TABLE app_user_financial_plan;"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to truncate app_user_financial_plan before import."
    }
}

psql -v ON_ERROR_STOP=1 -U $Username -h $RdsHost -d $Database -f $resolvedDataFile
if ($LASTEXITCODE -ne 0) {
    throw "Import failed with exit code $LASTEXITCODE."
}

psql -v ON_ERROR_STOP=1 -U $Username -h $RdsHost -d $Database -c "SELECT user_sub, email, display_name, created_at, updated_at FROM app_user_financial_plan ORDER BY updated_at DESC;"
if ($LASTEXITCODE -ne 0) {
    throw "Verification query failed after import."
}