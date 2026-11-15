#Requires -Version 5.1
# testnet-preflight.ps1 — Base Sepolia pre-flight check
# SAFETY: Read-only. No transactions submitted. No private keys printed.

param(
    [string]$RpcUrl          = $env:BLOCKCHAIN_RPC_URL,
    [long]  $ExpectedChainId = 84532,
    [string]$WalletAddress   = $env:BLOCKCHAIN_WALLET_ADDRESS,
    [string]$TokenContract   = $env:BLOCKCHAIN_TOKEN_CONTRACT
)

$ErrorActionPreference = "Continue"
$allPass = $true

function Pass  { param($label, $detail) Write-Host "  [PASS] $label$(if ($detail) {': ' + $detail})" -ForegroundColor Green }
function Fail  { param($label, $detail) Write-Host "  [FAIL] $label$(if ($detail) {': ' + $detail})" -ForegroundColor Red; $script:allPass = $false }
function Warn  { param($label, $detail) Write-Host "  [WARN] $label$(if ($detail) {': ' + $detail})" -ForegroundColor Yellow }
function Info  { param($label, $detail) Write-Host "  [INFO] $label$(if ($detail) {': ' + $detail})" -ForegroundColor Cyan }

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Base Sepolia Testnet Preflight Check" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 1. Java
Write-Host "--- Runtime ---"
try {
    $jv = (java -version 2>&1 | Select-Object -First 1).ToString()
    Info "Java" $jv
} catch { Fail "Java not found" "" }

# 2. Env vars (never check/print private key value)
Write-Host ""
Write-Host "--- Environment ---"
if ($env:BLOCKCHAIN_MODE -eq "REAL_TESTNET") { Pass "BLOCKCHAIN_MODE" "REAL_TESTNET" }
else { Fail "BLOCKCHAIN_MODE" "expected REAL_TESTNET, got '$($env:BLOCKCHAIN_MODE)'" }

if ($env:TESTNET_ENABLED -eq "true") { Pass "TESTNET_ENABLED" "true" }
else { Fail "TESTNET_ENABLED" "expected true, got '$($env:TESTNET_ENABLED)'" }

if ($RpcUrl)        { Pass "BLOCKCHAIN_RPC_URL set" "" }        else { Fail "BLOCKCHAIN_RPC_URL not set" "" }
if ($WalletAddress) { Pass "BLOCKCHAIN_WALLET_ADDRESS set" "" } else { Fail "BLOCKCHAIN_WALLET_ADDRESS not set" "" }
if ($TokenContract) { Pass "BLOCKCHAIN_TOKEN_CONTRACT set" "" } else { Fail "BLOCKCHAIN_TOKEN_CONTRACT not set" "" }

# Private key presence check (existence only — never print value)
if ($env:BLOCKCHAIN_WALLET_PRIVATE_KEY) { Pass "BLOCKCHAIN_WALLET_PRIVATE_KEY present (not printed)" "" }
else { Fail "BLOCKCHAIN_WALLET_PRIVATE_KEY not set" "" }

# 3. Token contract address
Write-Host ""
Write-Host "--- Token Contract ---"
$expectedContract = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"
if ($TokenContract -eq $expectedContract) { Pass "Token contract matches official Base Sepolia USDC" $TokenContract }
else { Fail "Token contract mismatch" "got=$TokenContract expected=$expectedContract" }

if (-not $RpcUrl) {
    Fail "RPC URL missing — cannot perform chain checks" ""
    Write-Host ""
    Write-Host "=== PREFLIGHT: FAIL (incomplete) ===" -ForegroundColor Red
    exit 1
}

# 4. Chain ID
Write-Host ""
Write-Host "--- RPC Connectivity ---"
try {
    $body = '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}'
    $resp = Invoke-RestMethod -Uri $RpcUrl -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10
    $chainHex = $resp.result
    $chainDec = [Convert]::ToInt64($chainHex.TrimStart('0x'), 16)
    if ($chainDec -eq $ExpectedChainId) { Pass "Chain ID" "$chainDec (Base Sepolia)" }
    else { Fail "Chain ID mismatch" "got=$chainDec expected=$ExpectedChainId — WRONG NETWORK" }
} catch {
    Fail "RPC call failed" $_.Exception.Message
}

# 5. ETH balance
Write-Host ""
Write-Host "--- Wallet Balances ---"
if ($WalletAddress) {
    try {
        $body = "{`"jsonrpc`":`"2.0`",`"method`":`"eth_getBalance`",`"params`":[`"$WalletAddress`",`"latest`"],`"id`":1}"
        $resp = Invoke-RestMethod -Uri $RpcUrl -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10
        $weiStr = $resp.result.TrimStart('0x')
        if ($weiStr -eq '' -or $weiStr -eq '0') { $ethVal = 0.0 }
        else {
            $wei = [System.Numerics.BigInteger]::Parse('0' + $weiStr, [System.Globalization.NumberStyles]::HexNumber)
            $ethVal = [double]$wei / 1e18
        }
        $ethRounded = [Math]::Round($ethVal, 6)
        if ($ethVal -gt 0.001)    { Pass "ETH balance" "$ethRounded ETH" }
        elseif ($ethVal -gt 0)    { Warn "ETH balance LOW" "$ethRounded ETH — may not cover gas. Get more from faucet." }
        else                      { Fail "ETH balance is 0" "Obtain Base Sepolia ETH from Coinbase Developer Platform faucet" }
    } catch { Fail "ETH balance check failed" $_.Exception.Message }
}

# 6. USDC balance
if ($WalletAddress -and $TokenContract) {
    try {
        $paddedAddr = $WalletAddress.TrimStart('0x').ToLower().PadLeft(64, '0')
        $data = "0x70a08231$paddedAddr"
        $body = "{`"jsonrpc`":`"2.0`",`"method`":`"eth_call`",`"params`":[{`"to`":`"$TokenContract`",`"data`":`"$data`"},`"latest`"],`"id`":1}"
        $resp = Invoke-RestMethod -Uri $RpcUrl -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10
        $rawHex = $resp.result.TrimStart('0x')
        if ($rawHex -eq '' -or $rawHex -match '^0+$') { $usdcVal = 0.0 }
        else {
            $raw = [System.Numerics.BigInteger]::Parse('0' + $rawHex, [System.Globalization.NumberStyles]::HexNumber)
            $usdcVal = [double]$raw / 1e6
        }
        $usdcRounded = [Math]::Round($usdcVal, 6)
        if ($usdcVal -gt 0) { Pass "USDC balance" "$usdcRounded USDC" }
        else { Fail "USDC balance is 0" "Obtain test USDC from Coinbase Developer Platform or Circle testnet faucet" }
    } catch { Fail "USDC balance check failed" $_.Exception.Message }
}

# Summary
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
if ($allPass) {
    Write-Host "  PREFLIGHT: PASS" -ForegroundColor Green
    Write-Host "  Await explicit owner instruction before submitting any transaction." -ForegroundColor Green
} else {
    Write-Host "  PREFLIGHT: FAIL" -ForegroundColor Red
    Write-Host "  Resolve the issues above before proceeding." -ForegroundColor Red
    exit 1
}
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
