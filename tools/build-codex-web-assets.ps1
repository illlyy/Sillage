[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$SkipInstall,
    [switch]$SkipBuild,
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$sourceRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "vendor\codex-web"))
$outputRoot = [System.IO.Path]::GetFullPath((Join-Path $sourceRoot "scratch\asar\webview"))
$destinationRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "app\src\main\assets\codex-desktop"))
$tempRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ".tmp\codex-web-assets"))

function Assert-PathUnderRepo([string]$Path, [string]$Label) {
    $repoPrefix = $repoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $Path.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label is outside the repository: $Path"
    }
}

Assert-PathUnderRepo $sourceRoot "Source"
Assert-PathUnderRepo $outputRoot "Build output"
Assert-PathUnderRepo $destinationRoot "Android asset destination"
Assert-PathUnderRepo $tempRoot "Temporary path"

if (-not (Test-Path -LiteralPath (Join-Path $sourceRoot "package.json") -PathType Leaf)) {
    throw "Vendored codex-web source is missing: $sourceRoot"
}

Push-Location $sourceRoot
try {
    if (-not $SkipBuild) {
        if (-not $SkipInstall) {
            $npm = Get-Command npm -ErrorAction Stop
            & $npm.Source ci --ignore-scripts
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE" }
        }

        $bash = Get-Command bash -ErrorAction Stop
        $command = 'export PATH="$PWD/node_modules/.bin:$PATH"; ./scripts/prepare && npm run build:browser && npm run build:server'
        & $bash.Source -lc $command
        if ($LASTEXITCODE -ne 0) { throw "codex-web build failed with exit code $LASTEXITCODE" }
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath (Join-Path $outputRoot "index.html") -PathType Leaf)) {
    throw "Generated WebView output is missing: $outputRoot"
}

$outputFiles = Get-ChildItem -LiteralPath $outputRoot -Recurse -File
$outputBytes = ($outputFiles | Measure-Object -Property Length -Sum).Sum
Write-Host "Generated WebView: $($outputFiles.Count) files / $outputBytes bytes"

if (-not $Apply) {
    Write-Host "Build complete. Android assets were not changed. Re-run with -Apply after reviewing the output."
    exit 0
}

if (-not $PSCmdlet.ShouldProcess($destinationRoot, "replace Android Codex Desktop asset snapshot")) {
    exit 0
}

if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
$stageRoot = Join-Path $tempRoot "new"
Copy-Item -LiteralPath $outputRoot -Destination $stageRoot -Recurse -Force

$backupRoot = Join-Path $tempRoot ("previous-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
try {
    if (Test-Path -LiteralPath $destinationRoot) {
        Move-Item -LiteralPath $destinationRoot -Destination $backupRoot
    }
    Move-Item -LiteralPath $stageRoot -Destination $destinationRoot
} catch {
    if (-not (Test-Path -LiteralPath $destinationRoot) -and (Test-Path -LiteralPath $backupRoot)) {
        Move-Item -LiteralPath $backupRoot -Destination $destinationRoot
    }
    throw
}

Write-Host "Android WebView assets updated: $destinationRoot"
if (Test-Path -LiteralPath $backupRoot) {
    Write-Host "Previous snapshot retained temporarily at: $backupRoot"
}
Write-Warning "Run the Android WebView bundle tests. The previous snapshot contained Android-specific bundle changes that may not be reproduced automatically."