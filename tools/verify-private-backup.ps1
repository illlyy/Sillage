[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
Push-Location $repoRoot
try {
    $failed = $false
    $tracked = @(git ls-files)
    if ($LASTEXITCODE -ne 0) { throw "git ls-files failed" }

    $forbidden = @(
        '^\.tmp/',
        '(^|/)local\.properties$',
        '\.(log|apk|aab|pem|p12|key|keystore)$'
    )
    $allowed = @('app/dev_keystore.jks')

    foreach ($path in $tracked) {
        if ($allowed -contains $path) { continue }
        foreach ($pattern in $forbidden) {
            if ($path -match $pattern) {
                Write-Error "Forbidden tracked file: $path" -ErrorAction Continue
                $failed = $true
                break
            }
        }

        $fullPath = Join-Path $repoRoot $path
        if ((Test-Path -LiteralPath $fullPath -PathType Leaf) -and (Get-Item -LiteralPath $fullPath).Length -ge 95MB) {
            Write-Error "Tracked file is at least 95 MB: $path" -ErrorAction Continue
            $failed = $true
        }
    }

    $nestedGit = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot "vendor") -Recurse -Force -Directory -Filter .git -ErrorAction SilentlyContinue)
    foreach ($item in $nestedGit) {
        Write-Error "Nested Git metadata found: $($item.FullName)" -ErrorAction Continue
        $failed = $true
    }

    git diff --check
    if ($LASTEXITCODE -ne 0) {
        Write-Error "git diff --check failed" -ErrorAction Continue
        $failed = $true
    }

    $gitleaks = Get-Command gitleaks -ErrorAction SilentlyContinue
    if ($null -ne $gitleaks) {
        & $gitleaks.Source git --redact --no-banner
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Gitleaks reported findings" -ErrorAction Continue
            $failed = $true
        }
    } else {
        Write-Warning "Gitleaks is not installed; run a full secret scan before making the repository public."
    }

    if ($failed) { throw "Private-backup verification failed." }
    Write-Host "Private-backup verification passed."
} finally {
    Pop-Location
}