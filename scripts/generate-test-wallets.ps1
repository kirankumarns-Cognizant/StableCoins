#Requires -Version 5.1
<#
.SYNOPSIS
    Generates two disposable EVM test wallets for the StableCoinsDemo POC.

.DESCRIPTION
    Wrapper script for the Java wallet-generation utility.

    Wallets generated are for Base Sepolia TESTNET ONLY.
    Never fund these wallets with mainnet ETH, production USDC,
    or any other asset with real financial value.

    SAFETY RULES:
      - This script does NOT print private keys.
      - This script does NOT print passwords.
      - This script does NOT read wallet file contents back to the console.
      - This script does NOT connect to any blockchain network.
      - This script does NOT submit any transactions.
      - If wallet files already exist, generation is aborted.

.NOTES
    Prerequisites:
      - Java 21 (checks JAVA_HOME, then PATH, then C:\Users\884412\tools\jdk-21)
      - Run from an interactive terminal (passwords are entered without echo)
      - Run from the repository root so wallet files land in .local\wallets\

    Wallet files are written to:
      .local\wallets\treasury-wallet.json
      .local\wallets\beneficiary-wallet.json

    Both files are excluded from Git via .gitignore.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Locate repository root (parent of the scripts/ directory)
# ---------------------------------------------------------------------------
$repoRoot = Split-Path $PSScriptRoot -Parent
$utilityDir = Join-Path $PSScriptRoot "generate-test-wallets"

Write-Host ""
Write-Host "StableCoinsDemo Test Wallet Generator"
Write-Host "======================================"
Write-Host "Network: Base Sepolia TESTNET ONLY"
Write-Host ""

# ---------------------------------------------------------------------------
# Locate Java 21
# ---------------------------------------------------------------------------
function Find-Java21 {
    # 1. Use JAVA_HOME if set
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    # 2. Try known project JDK location
    $knownJdk = "C:\Users\884412\tools\jdk-21\bin\java.exe"
    if (Test-Path $knownJdk) {
        return $knownJdk
    }

    # 3. Fall back to system java
    try {
        $null = Get-Command java -ErrorAction Stop
        return "java"
    } catch {
        return $null
    }
}

function Get-JavaMajorVersion([string]$javaExe) {
    # java -version writes to stderr. PowerShell's 2>&1 turns stderr lines into
    # ErrorRecord objects. Force each object to its string representation before
    # joining so the regex matches the raw version line regardless of object type.
    #
    # $ErrorActionPreference = 'Continue' (function-local) prevents the outer
    # 'Stop' preference from turning ErrorRecord pipeline objects into terminating
    # errors before ForEach-Object can call .ToString() on them.
    $local:ErrorActionPreference = 'Continue'
    try {
        $versionOutput = & $javaExe -version 2>&1 | ForEach-Object { $_.ToString() } | Out-String
        if ($versionOutput -match '"(\d+)(?:[._]\d+)*"') {
            $major = [int]$Matches[1]
            # Java 8 and earlier report as 1.x (e.g. "1.8.0_392")
            if ($major -eq 1) {
                if ($versionOutput -match '"1[._](\d+)') { return [int]$Matches[1] }
            }
            return $major
        }
        # Version line found but pattern did not match — report raw output for diagnosis
        Write-Host "WARNING: Could not parse Java version from output:"
        Write-Host $versionOutput
        return 0
    } catch {
        Write-Host "WARNING: Java executable could not be run: $_"
        return 0
    }
}

$javaExe = Find-Java21
if (-not $javaExe) {
    Write-Error "Java executable not found. Install Java 21 or set JAVA_HOME to a Java 21 installation."
    exit 1
}

# Validate JAVA_HOME points to a usable JDK (java.exe + javac.exe both required)
if ($env:JAVA_HOME) {
    $javaExeCheck  = Join-Path $env:JAVA_HOME "bin\java.exe"
    $javacExeCheck = Join-Path $env:JAVA_HOME "bin\javac.exe"
    if (-not (Test-Path $javaExeCheck)) {
        Write-Error "JAVA_HOME is set to '$($env:JAVA_HOME)' but bin\java.exe was not found there."
        exit 1
    }
    if (-not (Test-Path $javacExeCheck)) {
        Write-Error "JAVA_HOME is set to '$($env:JAVA_HOME)' but bin\javac.exe was not found. Ensure JAVA_HOME points to a full JDK, not a JRE."
        exit 1
    }
}

$majorVersion = Get-JavaMajorVersion $javaExe
if ($majorVersion -ne 21) {
    Write-Error "Java 21 is required (found major version: $majorVersion). Set JAVA_HOME to a Java 21 installation."
    exit 1
}

Write-Host "Java 21 found: $javaExe"
Write-Host ""

# ---------------------------------------------------------------------------
# Build the utility fat jar (tests skipped for fast startup)
# ---------------------------------------------------------------------------
$mvnCmd = Join-Path $repoRoot "services\mvnw.cmd"
if (-not (Test-Path $mvnCmd)) {
    Write-Error "Maven wrapper not found at: $mvnCmd"
    exit 1
}

Write-Host "Building wallet generator utility..."
$env:JAVA_HOME = Split-Path (Split-Path $javaExe -Parent) -Parent
Push-Location $utilityDir
try {
    & $mvnCmd package --no-transfer-progress -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed (exit code $LASTEXITCODE)."
        exit 1
    }
} finally {
    Pop-Location
}

Write-Host "Build complete."
Write-Host ""

# ---------------------------------------------------------------------------
# Run the wallet generator from the repository root
# (so .local\wallets\ resolves relative to the repo)
# ---------------------------------------------------------------------------
$jarPath = Join-Path $utilityDir "target\generate-test-wallets.jar"
if (-not (Test-Path $jarPath)) {
    Write-Error "Jar not found after build: $jarPath"
    exit 1
}

Push-Location $repoRoot
try {
    & $javaExe -jar $jarPath
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exitCode -eq 0) {
    Write-Host ""
    Write-Host "Wallet generation completed successfully."
    Write-Host "Backup your wallet files and passwords securely before continuing."
} elseif ($exitCode -eq 2) {
    Write-Host ""
    Write-Host "Generation aborted: wallet files already exist."
    exit 2
} else {
    Write-Host ""
    Write-Error "Wallet generation failed (exit code $exitCode)."
    exit $exitCode
}
