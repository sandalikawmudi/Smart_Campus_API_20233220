param(
    [string]$TomcatHome = "C:\TomCat",
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

Write-Host "[smart-campus] Building WAR (no Maven)..."
powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "build-war-no-maven.ps1")

$war = Join-Path $ProjectRoot "target\smart-campus-api.war"
if (-not (Test-Path $war)) {
    throw "WAR not found at $war"
}

$destWar = Join-Path $TomcatHome "webapps\smart-campus-api.war"
Write-Host "[smart-campus] Copying WAR to $destWar"
Copy-Item -Force $war $destWar

Write-Host "[smart-campus] Waiting for deployment..."
Start-Sleep -Seconds 6

$url = "http://localhost:8080/smart-campus-api/api/v1"
try {
    $status = (Invoke-WebRequest -UseBasicParsing $url).StatusCode
    Write-Host "[smart-campus] API status: $status at $url"
} catch {
    Write-Host "[smart-campus] API check failed: $($_.Exception.Message)"
    throw
}
