param(
    [string]$OutputDir = "artifacts/db-export",
    [string]$DbHost = "localhost",
    [string]$Database = "financial_planning",
    [string]$Username = "financial_app",
    [string]$Password = "password"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir
} else {
    Join-Path $repoRoot $OutputDir
}

New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null

$schemaFile = Join-Path $resolvedOutputDir "app_user_financial_plan_schema.sql"
$dataFile = Join-Path $resolvedOutputDir "app_user_financial_plan_data.sql"

if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) {
    throw "pg_dump was not found in PATH. Install PostgreSQL client tools or add them to PATH."
}

$env:PGPASSWORD = $Password

pg_dump -U $Username -h $DbHost -d $Database --schema-only --table=app_user_financial_plan --file=$schemaFile
if ($LASTEXITCODE -ne 0) {
    throw "Schema export failed with exit code $LASTEXITCODE."
}

pg_dump -U $Username -h $DbHost -d $Database --data-only --inserts --table=app_user_financial_plan --file=$dataFile
if ($LASTEXITCODE -ne 0) {
    throw "Data export failed with exit code $LASTEXITCODE."
}

Write-Output "Schema export: $schemaFile"
Write-Output "Data export: $dataFile"