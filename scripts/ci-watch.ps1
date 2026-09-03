<#
.SYNOPSIS
    Watch GitHub Actions CI status for the jzqs repo.

.DESCRIPTION
    Zero-dependency: calls the GitHub REST API directly.
    The repo is public, so anonymous read works (no gh CLI, no token needed).
    If GITHUB_TOKEN / GH_TOKEN is set it will be used to raise the rate limit
    (and keeps working if the repo ever becomes private).

    By default it watches the workflow run for the current HEAD and blocks
    until it finishes:
      - all jobs green      -> exit code 0
      - any job failed      -> downloads failed job logs, prints tail, exit 1
      - timeout / no run    -> exit code 2

    NOTE: this file is intentionally pure ASCII. Windows PowerShell 5.1 reads
    BOM-less .ps1 files as ANSI (GBK), which corrupts any non-ASCII bytes.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ci-watch.ps1
    Watch current HEAD, block until CI finishes.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ci-watch.ps1 -NoWait
    Query once and return immediately.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\ci-watch.ps1 -Sha 271a9f5 -TailLines 200
    Watch a specific commit, print 200 lines of failed job logs.
#>
[CmdletBinding()]
param(
    [string]$Sha,
    [switch]$NoWait,
    [int]$TimeoutMinutes = 30,
    [int]$IntervalSeconds = 20,
    [int]$TailLines = 60
)

$ErrorActionPreference = 'Stop'

# Show UTF-8 log content correctly on the Windows console.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

# ---------- repo / commit ----------
function Get-RepoSlug {
    $url = (git remote get-url origin 2>$null)
    if (-not $url) { throw 'Cannot read git remote origin. Run this inside the jzqs repo.' }
    $url = $url.Trim()
    if ($url -match 'github\.com[:/](.+?)/(.+?)(\.git)?$') {
        return "$($Matches[1])/$($Matches[2])"
    }
    throw "Cannot parse GitHub repo from remote: $url"
}

function Get-HeadSha {
    $s = (git rev-parse HEAD 2>$null)
    if (-not $s) { throw 'Cannot read HEAD. Run this inside a git repo.' }
    return $s.Trim()
}

$repo    = Get-RepoSlug
$sha     = if ($Sha) { $Sha.Trim() } else { Get-HeadSha }
$apiBase = "https://api.github.com/repos/$repo"

$headers = @{
    'User-Agent' = 'jzqs-ci-watch'
    'Accept'     = 'application/vnd.github+json'
}
$token = if ($env:GITHUB_TOKEN) { $env:GITHUB_TOKEN } elseif ($env:GH_TOKEN) { $env:GH_TOKEN } else { $null }
if ($token) { $headers['Authorization'] = "Bearer $token" }

# ---------- helpers ----------
function Invoke-GitHub {
    param([string]$Uri)
    for ($i = 1; $i -le 3; $i++) {
        try {
            return Invoke-RestMethod -Uri $Uri -Headers $headers -TimeoutSec 30
        } catch {
            if ($i -eq 3) { throw }
            Write-Host "  [retry $i/3] request failed: $($_.Exception.Message)" -ForegroundColor DarkYellow
            Start-Sleep -Seconds 5
        }
    }
}

function Convert-UtcToLocal([string]$utc) {
    if (-not $utc) { return '-' }
    try { return ([DateTime]::Parse($utc).ToLocalTime()).ToString('MM-dd HH:mm:ss') }
    catch { return $utc }
}

function Get-ConclusionColor([string]$c) {
    switch ($c) {
        'success'   { 'Green' }
        'failure'   { 'Red' }
        'cancelled' { 'DarkYellow' }
        'skipped'   { 'DarkGray' }
        'timed_out' { 'Red' }
        default     { 'Yellow' }
    }
}

function Get-LatestRun {
    $res = Invoke-GitHub "$apiBase/actions/runs?head_sha=$sha&per_page=1"
    if ($res.total_count -eq 0) { return $null }
    return $res.workflow_runs[0]
}

# Download the run log zip and print the tail of each failed job.
function Show-FailureLogs {
    param($RunId, $FailedJobNames)

    $tmp = Join-Path $env:TEMP "jzqs-ci-logs/$RunId"
    $zip = Join-Path $tmp 'logs.zip'
    Write-Host ''
    Write-Host "Downloading CI logs -> $tmp" -ForegroundColor DarkCyan
    try {
        New-Item -ItemType Directory -Path $tmp -Force | Out-Null
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri "$apiBase/actions/runs/$RunId/logs" -Headers $headers `
            -OutFile $zip -MaximumRedirection 5 -TimeoutSec 120
        Expand-Archive -Path $zip -DestinationPath $tmp -Force
    } catch {
        Write-Host "Log download failed (anonymous download is limited; set GITHUB_TOKEN to fix): $($_.Exception.Message)" -ForegroundColor DarkYellow
        return
    }

    $files = Get-ChildItem -Path $tmp -Filter '*.txt' -File | Sort-Object Name
    if (-not $files) {
        Write-Host 'Log archive is empty, cannot extract failure details.' -ForegroundColor DarkYellow
        return
    }

    foreach ($name in $FailedJobNames) {
        # GitHub log zip entry format: <index>_<job name>.txt
        $safe  = ($name -replace '[\\/:*?"<>|]', '_')
        $match = $files | Where-Object { $_.Name -like "*_$safe.txt" } | Select-Object -First 1
        if (-not $match) {
            $match = $files | Where-Object { $_.Name -like "*$safe*" } | Select-Object -First 1
        }
        if (-not $match) { continue }

        Write-Host ''
        Write-Host "===== FAILED JOB LOG: $name =====" -ForegroundColor Red
        $lines = Get-Content -Path $match.FullName
        $start = [Math]::Max(0, $lines.Count - $TailLines)
        $lines[$start..($lines.Count - 1)] | ForEach-Object { Write-Host $_ }
        Write-Host "----- full log: $($match.FullName) -----" -ForegroundColor DarkGray
    }
}

# ---------- main ----------
Write-Host "Repo:   $repo" -ForegroundColor DarkCyan
Write-Host "Commit: $sha" -ForegroundColor DarkCyan

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$run = $null

while ($true) {
    $run = Get-LatestRun
    if ($run) { break }

    if ($NoWait) {
        Write-Host 'No CI run found for this commit yet (GitHub takes a few seconds after push).' -ForegroundColor Yellow
        exit 2
    }
    if ((Get-Date) -gt $deadline) {
        Write-Host "Timeout: no CI run for this commit within $TimeoutMinutes min. Did you push?" -ForegroundColor Red
        exit 2
    }
    Write-Host "Waiting for GitHub to create the run... $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor DarkGray
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "Workflow: $($run.name)" -ForegroundColor DarkCyan
Write-Host "Started:  $(Convert-UtcToLocal $run.created_at)" -ForegroundColor DarkCyan
Write-Host "Run URL:  $($run.html_url)" -ForegroundColor DarkCyan
Write-Host ''

while ($run.status -ne 'completed') {
    if ($NoWait) {
        Write-Host "CI still running (status=$($run.status)). Run this script again later." -ForegroundColor Yellow
        exit 2
    }
    if ((Get-Date) -gt $deadline) {
        Write-Host "Timeout: CI did not finish within $TimeoutMinutes min. See $($run.html_url)" -ForegroundColor Red
        exit 2
    }
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] status=$($run.status), waiting..." -ForegroundColor DarkGray
    Start-Sleep -Seconds $IntervalSeconds
    $run = Get-LatestRun
}

$jobsRes = Invoke-GitHub "$apiBase/actions/runs/$($run.id)/jobs"

Write-Host '========== CI RESULT ==========' -ForegroundColor Cyan
foreach ($j in $jobsRes.jobs) {
    $color = Get-ConclusionColor $j.conclusion
    Write-Host ("  {0,-24} {1}" -f $j.name, $j.conclusion) -ForegroundColor $color
}
Write-Host '===============================' -ForegroundColor Cyan
Write-Host "Finished: $(Convert-UtcToLocal $run.updated_at)" -ForegroundColor DarkCyan

$failed = @($jobsRes.jobs | Where-Object { $_.conclusion -in @('failure', 'cancelled', 'timed_out') })

if ($failed.Count -gt 0) {
    Write-Host ''
    Write-Host "CI FAILED: $repo @ $sha" -ForegroundColor Red
    Write-Host "Run URL: $($run.html_url)" -ForegroundColor Red
    Show-FailureLogs -RunId $run.id -FailedJobNames $failed.name
    exit 1
}

Write-Host ''
Write-Host 'CI PASSED (all jobs green).' -ForegroundColor Green
exit 0
