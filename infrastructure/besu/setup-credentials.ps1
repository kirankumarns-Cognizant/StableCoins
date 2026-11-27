# Interactive setup only. No secret values enter arguments, logs, Git or application configuration.
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Set-Location -LiteralPath $projectRoot
$java = Join-Path $env:USERPROFILE 'tools/jdk-21/bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Java 21 required at tools/jdk-21/bin/java.exe' }
$jar = 'scripts/generate-test-wallets/target/generate-test-wallets.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Build the wallet utility JAR first; see CREDENTIAL-SETUP.md' }
$treasury = Test-Path -LiteralPath '.local/besu-wallets/treasury-wallet.json' -PathType Leaf
$beneficiary = Test-Path -LiteralPath '.local/besu-wallets/beneficiary-wallet.json' -PathType Leaf
if ($treasury -ne $beneficiary) { throw 'Partial Besu pair exists; preserve it and investigate.' }
$ErrorActionPreference = 'Continue'
if (-not $treasury) {
    & $java --class-path $jar 'infrastructure/besu/CreateBesuWalletPair.java'
    if ($LASTEXITCODE -ne 0) { throw 'Wallet pair not verified; existing files preserved.' }
}
if (Test-Path -LiteralPath '.local/secrets/besu-treasury-password') {
    throw 'Existing password preserved. Setup will not replace it; verify the existing credentials separately.'
}
& $java --class-path $jar 'infrastructure/besu/PrepareBesuPassword.java'
if ($LASTEXITCODE -ne 0) { throw 'Password setup not verified; preserve the wallet files.' }
$ErrorActionPreference = 'Stop'
$paths = @('.local/besu-wallets/treasury-wallet.json', '.local/besu-wallets/beneficiary-wallet.json', '.local/secrets/besu-treasury-password')
foreach ($path in $paths) {
    $item = Get-Item -LiteralPath $path
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Linked credential path rejected' }
    $acl = Get-Acl -LiteralPath $path
    $allow = @($acl.Access | Where-Object { $_.AccessControlType -eq 'Allow' })
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if ($allow.Count -ne 1 -or $allow[0].IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -ne $identity) {
        throw 'Owner-only Windows credential ACL required'
    }
}
New-Item -ItemType Directory -Path '.local/poc-status' -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $projectRoot '.local/poc-status/besu-credential-setup-result.txt'),
    ('BESU_LOCAL_CREDENTIALS_VERIFIED ' + [DateTimeOffset]::Now.ToString('o')), (New-Object Text.UTF8Encoding($false)))
Write-Host 'BESU_LOCAL_CREDENTIALS_VERIFIED. No chain started or transaction submitted.'