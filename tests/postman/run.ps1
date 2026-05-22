<#
.SYNOPSIS
  Newman test runner for Dumble.Backend services.

.DESCRIPTION
  Assumes the docker-compose stack is running. If the gateway is not answering
  on the initial probe, the script will `docker compose up -d` once and wait
  for health, then run the tests. The stack is NEVER torn down by this script —
  it's meant to simulate a real QA environment that stays up between runs.

  Produces per-service HTML + JSON reports in tests/postman/reports/.

.PARAMETER Only
  Comma-separated collection basenames to run (e.g. "auth,gym").
  If omitted, every *.postman_collection.json in collections/ runs.

.PARAMETER EnvFile
  Path to the Postman environment file.
  Default: environments/local.postman_environment.json.

.PARAMETER GatewayUrl
  Base URL the gateway is expected to answer on.
  Default: http://localhost:8080.

.PARAMETER NoUp
  Skip the auto-bring-up step. If the gateway is not healthy, fail fast.

.PARAMETER WaitSeconds
  How long to wait for the gateway to become healthy after `docker compose up -d`.
  Default: 180.

.EXAMPLE
  .\run.ps1
  .\run.ps1 -Only auth
  .\run.ps1 -Only auth,gym -NoUp
#>

[CmdletBinding()]
param(
  [string]$Only = "",
  [string]$EnvFile = "environments/local.postman_environment.json",
  [string]$GatewayUrl = "",
  [switch]$NoUp,
  [int]$WaitSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$repoRoot    = (Resolve-Path "..\..").Path
$composeFile = Join-Path $repoRoot "release\docker-compose.yml"
$dotenvFile  = Join-Path $repoRoot "release\.env"

# Resolve gateway URL: explicit param > value pulled from the Postman env file
# > fall-back default. Pulling from the env file means a local port remap (e.g.
# 18080 when 8080 is in use) is honored without passing -GatewayUrl every run.
if ([string]::IsNullOrWhiteSpace($GatewayUrl) -and (Test-Path $EnvFile)) {
  try {
    $envJson = Get-Content $EnvFile -Raw | ConvertFrom-Json
    $entry = $envJson.values | Where-Object { $_.key -eq 'gateway_url' } | Select-Object -First 1
    if ($entry -and -not [string]::IsNullOrWhiteSpace($entry.value)) {
      $GatewayUrl = $entry.value
    }
  } catch { }
}
if ([string]::IsNullOrWhiteSpace($GatewayUrl)) { $GatewayUrl = 'http://localhost:8080' }
$healthUrl = "$GatewayUrl/actuator/health"

function Test-GatewayHealthy {
  param([int]$TimeoutSec = 3)
  try {
    $r = Invoke-WebRequest -Uri $healthUrl -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
    return $r.StatusCode -eq 200
  } catch {
    return $false
  }
}

function Wait-ForGateway {
  param([int]$Seconds)
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-GatewayHealthy) { return $true }
    Start-Sleep -Seconds 3
  }
  return $false
}

# ── 1. ensure the stack is up (idempotent) ──────────────────────────────────
Write-Host "Probing $healthUrl ..." -ForegroundColor Cyan
if (Test-GatewayHealthy) {
  Write-Host "Gateway already healthy." -ForegroundColor Green
} elseif ($NoUp) {
  Write-Host "ERROR: gateway not healthy and -NoUp was set." -ForegroundColor Red
  exit 3
} else {
  Write-Host "Gateway not responding. Bringing stack up..." -ForegroundColor Yellow
  if (-not (Test-Path $composeFile)) {
    Write-Host "ERROR: $composeFile not found" -ForegroundColor Red
    exit 2
  }
  if (Test-Path $dotenvFile) {
    docker compose -f $composeFile --env-file $dotenvFile up -d
  } else {
    Write-Host "WARN: release/.env not found; using compose defaults." -ForegroundColor Yellow
    docker compose -f $composeFile up -d
  }
  if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: docker compose up failed (exit $LASTEXITCODE)" -ForegroundColor Red
    exit 4
  }
  Write-Host "Waiting up to ${WaitSeconds}s for gateway health..." -ForegroundColor Cyan
  if (-not (Wait-ForGateway -Seconds $WaitSeconds)) {
    Write-Host "ERROR: gateway did not become healthy within ${WaitSeconds}s." -ForegroundColor Red
    Write-Host "Inspect logs with: docker compose -f release\docker-compose.yml logs --tail=200" -ForegroundColor Yellow
    exit 5
  }
  Write-Host "Gateway healthy." -ForegroundColor Green
}

# ── 2. resolve which collections to run ─────────────────────────────────────
$onlyList = @()
if ($Only) {
  $onlyList = $Only.Split(",") | ForEach-Object { $_.Trim().ToLower() }
}

$collections = Get-ChildItem "collections" -Filter "*.postman_collection.json" -ErrorAction SilentlyContinue | Sort-Object Name
if (-not $collections -or $collections.Count -eq 0) {
  Write-Host "ERROR: no collections found in tests/postman/collections/" -ForegroundColor Red
  Write-Host "Collections are generated on demand by Claude; ask it to test a service." -ForegroundColor Yellow
  exit 6
}

New-Item -ItemType Directory -Force -Path "reports" | Out-Null

# ── 3a. inject runtime secrets from release/.env (admin creds for ban tests).
# Newman never persists these; they live in the in-memory environment only.
$runtimeArgs = @()
if (Test-Path $dotenvFile) {
  $envContent = Get-Content $dotenvFile -ErrorAction SilentlyContinue
  $adminEmail = (
    ($envContent | Where-Object { $_ -match '^ADMIN_EMAIL=' } | Select-Object -First 1) -replace '^ADMIN_EMAIL=', ''
  )
  $adminPassword = (
    ($envContent | Where-Object { $_ -match '^ADMIN_PASSWORD=' } | Select-Object -First 1) -replace '^ADMIN_PASSWORD=', ''
  )
  if ([string]::IsNullOrWhiteSpace($adminEmail))    { $adminEmail = 'admin@dumble.local' }
  if (-not [string]::IsNullOrWhiteSpace($adminPassword)) {
    $runtimeArgs += @('--env-var', "admin_email=$adminEmail", '--env-var', "admin_password=$adminPassword")
  }
}

# ── 3a.1 reset rate-limit counters in Redis so the suite starts from zero.
# Without this, a previous failed/partial run leaves keys with up to 60s of TTL,
# and the first login in this run trips the 429 cap immediately. Test isolation
# matters more than preserving someone else's counters during a CI run.
try {
  $redisContainer = (docker ps --filter "name=redis" --format "{{.Names}}" | Select-Object -First 1)
  if ($redisContainer) {
    docker exec $redisContainer sh -c 'redis-cli --scan --pattern "ratelimit:auth:*" | xargs -r redis-cli DEL' 2>$null | Out-Null
    Write-Host "Cleared Redis rate-limit keys." -ForegroundColor DarkGray
  }
} catch {
  Write-Host "WARN: could not clear Redis rate-limit keys (continuing)" -ForegroundColor Yellow
}

# ── 3b. run newman per collection ───────────────────────────────────────────
$results = @()
foreach ($coll in $collections) {
  $name = $coll.BaseName -replace "\.postman_collection$",""
  if ($onlyList -and ($onlyList -notcontains $name.ToLower())) { continue }

  $jsonReport = Join-Path "reports" "$name.json"
  $htmlReport = Join-Path "reports" "$name.html"

  Write-Host "`n=== $name ===" -ForegroundColor Cyan
  newman run $coll.FullName `
    -e $EnvFile `
    @runtimeArgs `
    --reporters cli,json,htmlextra `
    --reporter-json-export $jsonReport `
    --reporter-htmlextra-export $htmlReport `
    --color on
  $exitCode = $LASTEXITCODE

  if (Test-Path $jsonReport) {
    $j = Get-Content $jsonReport -Raw | ConvertFrom-Json
    $startMs = [int64]$j.run.timings.started
    $endMs   = [int64]$j.run.timings.completed
    $results += [PSCustomObject]@{
      Service     = $name
      Scenarios   = $j.run.stats.iterations.total
      Requests    = $j.run.stats.requests.total
      ReqFailed   = $j.run.stats.requests.failed
      Assertions  = $j.run.stats.assertions.total
      AssertFail  = $j.run.stats.assertions.failed
      Errors      = $j.run.failures.Count
      DurationSec = [math]::Round(($endMs - $startMs) / 1000.0, 1)
      ExitCode    = $exitCode
    }
  } else {
    $results += [PSCustomObject]@{
      Service     = $name
      Scenarios   = 0
      Requests    = 0
      ReqFailed   = 0
      Assertions  = 0
      AssertFail  = 0
      Errors      = "n/a"
      DurationSec = 0
      ExitCode    = $exitCode
    }
  }
}

# ── 4. summary ──────────────────────────────────────────────────────────────
Write-Host "`n========== SUMMARY ==========" -ForegroundColor Yellow
$results | Format-Table -AutoSize

$totalAssertFail = ($results | Where-Object { $_.AssertFail -is [int] } | Measure-Object -Property AssertFail -Sum).Sum
$totalErr        = ($results | Where-Object { $_.Errors     -is [int] } | Measure-Object -Property Errors     -Sum).Sum

if ($totalAssertFail -gt 0 -or $totalErr -gt 0) {
  Write-Host "Failed assertions: $totalAssertFail" -ForegroundColor Red
  Write-Host "Request errors:    $totalErr"        -ForegroundColor Red
} else {
  Write-Host "All collections passed." -ForegroundColor Green
}
Write-Host "Reports: tests/postman/reports/" -ForegroundColor Cyan
Write-Host "Stack left running (use 'docker compose -f release\docker-compose.yml down' to stop)." -ForegroundColor Cyan

exit ($(if ($totalAssertFail -gt 0 -or $totalErr -gt 0) { 1 } else { 0 }))
