param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

$jdkBin = "C:\Program Files\Java\jdk-21\bin"
$javac = Join-Path $jdkBin "javac.exe"
$jarTool = Join-Path $jdkBin "jar.exe"

if (-not (Test-Path $javac)) {
    throw "javac.exe not found at $javac"
}
if (-not (Test-Path $jarTool)) {
    throw "jar.exe not found at $jarTool"
}

$existingAppDir = Join-Path $ProjectRoot "target/smart-campus-api"
$existingLibDir = Join-Path $existingAppDir "WEB-INF/lib"

if (-not (Test-Path $existingLibDir)) {
    throw "Missing dependency jars at $existingLibDir. Build once with Maven on another machine first."
}

$stageDir = Join-Path $ProjectRoot "target/manual-war-stage"
$classesDir = Join-Path $stageDir "WEB-INF/classes"
$stageLibDir = Join-Path $stageDir "WEB-INF/lib"

if (Test-Path $stageDir) {
    Remove-Item -Recurse -Force $stageDir
}

New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $stageLibDir -Force | Out-Null

Copy-Item -Recurse -Force (Join-Path $ProjectRoot "src/main/webapp/*") $stageDir
Copy-Item -Recurse -Force (Join-Path $existingLibDir "*") $stageLibDir

$srcFiles = Get-ChildItem (Join-Path $ProjectRoot "src/main/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$cp = (Get-ChildItem $stageLibDir -Filter *.jar | ForEach-Object { $_.FullName }) -join ";"

& $javac -cp $cp -d $classesDir $srcFiles
if ($LASTEXITCODE -ne 0) {
    throw "Compilation failed with exit code $LASTEXITCODE"
}

$warPath = Join-Path $ProjectRoot "target/smart-campus-api.war"
if (Test-Path $warPath) {
    Remove-Item -Force $warPath
}

Push-Location $stageDir
try {
    & $jarTool -cf $warPath *
    if ($LASTEXITCODE -ne 0) {
        throw "WAR creation failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host "WAR built: $warPath"
Get-Item $warPath | Select-Object FullName, Length, LastWriteTime
