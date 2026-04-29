param(
    [string]$TomcatHome = "C:\TomCat",
    [string]$AppName = "smart-campus-api",
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

function Log([string]$msg) {
    Write-Host "[smart-campus] $msg"
}

function Require-Tool([string]$tool) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "Required tool not found in PATH: $tool"
    }
}

function Wait-Http([string]$url, [int]$timeoutSeconds = 45) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 750
        }
    } while ((Get-Date) -lt $deadline)
    return $false
}

$webapps = Join-Path $TomcatHome "webapps"
$confHost = Join-Path $TomcatHome "conf\Catalina\localhost"
$workHost = Join-Path $TomcatHome "work\Catalina\localhost"
$logsDir = Join-Path $TomcatHome "logs"

$appWar = Join-Path $webapps "$AppName.war"
$appDir = Join-Path $webapps $AppName
$appCtx = Join-Path $confHost "$AppName.xml"
$appWork = Join-Path $workHost $AppName

Log "Project root: $ProjectRoot"
Log "Tomcat home:  $TomcatHome"

if (-not (Test-Path $TomcatHome)) {
    throw "Tomcat directory not found: $TomcatHome"
}

Require-Tool "mvn"

Log "Stopping Tomcat (if running)..."
& (Join-Path $TomcatHome "bin\shutdown.bat") | Out-Null
Start-Sleep -Seconds 2

Log "Cleaning previous deployment artifacts..."
Remove-Item -Recurse -Force $appDir -ErrorAction SilentlyContinue
Remove-Item -Force $appWar -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force $appWork -ErrorAction SilentlyContinue
Remove-Item -Force $appCtx -ErrorAction SilentlyContinue

Push-Location $ProjectRoot
try {
    Log "Building WAR with Maven..."
    mvn clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$builtWar = Join-Path $ProjectRoot "target\$AppName.war"
if (-not (Test-Path $builtWar)) {
    throw "WAR not found after build: $builtWar"
}

Log "Copying WAR to Tomcat webapps..."
Copy-Item $builtWar $appWar -Force

Log "Starting Tomcat..."
& (Join-Path $TomcatHome "bin\startup.bat") | Out-Null

$baseUrl = "http://localhost:8080/$AppName/api/v1"
Log "Waiting for API startup: $baseUrl"
if (-not (Wait-Http -url $baseUrl -timeoutSeconds 60)) {
    Log "API did not become ready in time."
    Log "Recent localhost log:"
    Get-Content -Tail 120 (Join-Path $logsDir "localhost.$((Get-Date).ToString('yyyy-MM-dd')).log") -ErrorAction SilentlyContinue
    throw "Deployment failed or API not reachable."
}

Log "API is reachable."

Log "Running quick smoke tests..."
$discovery = Invoke-RestMethod -Uri $baseUrl -Method Get
if (-not $discovery.version) {
    throw "Discovery payload missing 'version'."
}

$rooms = Invoke-RestMethod -Uri "$baseUrl/rooms" -Method Get
if ($rooms -eq $null) {
    throw "Rooms endpoint returned null."
}

$sensors = Invoke-RestMethod -Uri "$baseUrl/sensors" -Method Get
if ($sensors -eq $null) {
    throw "Sensors endpoint returned null."
}

Log "Smoke tests passed."
Log "Deployment complete."
Write-Host ""
Write-Host "Base URL: $baseUrl"
