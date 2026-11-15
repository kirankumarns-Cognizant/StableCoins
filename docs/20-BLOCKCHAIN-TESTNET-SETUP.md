# Blockchain Testnet Setup — Base Sepolia

> Step 4 update: the existing blockchain execution controller is default-off.
> `BLOCKCHAIN_EXECUTION_API_ENABLED=true` is required only for a deliberately
> protected internal testnet setup; it does not provide authentication.
> Current signing remains the local environment-key provider. Secrets Manager/KMS
> signing and an actual transfer integration test are still required before AWS
> execution. Readiness uses buffered RPC gas plus an explicit allowance, not a
> measured complete Base data-fee quote. See
> [treasury/execution readiness](25-TREASURY-AND-EXECUTION-READINESS.md).


## Purpose

Records externally verified configuration for the REAL Base Sepolia testnet
integration in `blockchain-service`. All values below were verified from
official sources before Iteration 5 implementation began.

---

## Verified Configuration

| Parameter | Value | Source |
|-----------|-------|--------|
| Network name | Base Sepolia | docs.base.org |
| Chain ID | **84532** | docs.base.org/get-started/connect-to-base |
| Public RPC URL | `https://sepolia.base.org` | docs.base.org/get-started/connect-to-base |
| Test USDC available | **Yes** | developers.circle.com |
| Test USDC contract | `0x036CbD53842c5426634e7929541eC2318f3dCF7e` | developers.circle.com/stablecoins/usdc-contract-addresses |
| Token symbol | USDC | — |
| Token decimals | **6** | ERC-20 standard / Circle docs |
| Block explorer | `https://sepolia.basescan.org` | docs.base.org/chain/block-explorers |
| web3j version | **6.1.0** | github.com/LFDT-web3j/web3j releases |
| Java requirement | Java 21+ | github.com/LFDT-web3j/web3j (binaries compiled with Java 21) |

---

## Obtaining Test Funds

### Base Sepolia ETH (gas)

Primary: **Coinbase Developer Platform Faucet**
- Provides up to 0.1 ETH per 24 hours
- Also provides USDC, EURC, cbBTC testnet tokens
- Source: docs.base.org/get-started/get-funds

Additional faucets: thirdweb, Alchemy (free account required), QuickNode, Chainstack

### Test USDC

Primary: **Coinbase Developer Platform Faucet** (same URL as above — also distributes USDC)

Secondary: **Circle Testnet Faucet**
- Navigate to developers.circle.com and select Base Sepolia

---

## Environment Variables Required for REAL_TESTNET Mode

Both flags must be set to activate real blockchain submission:

```bash
BLOCKCHAIN_MODE=REAL_TESTNET
TESTNET_ENABLED=true
```

Network / token configuration:

```bash
BLOCKCHAIN_NETWORK=base-sepolia
BLOCKCHAIN_CHAIN_ID=84532
BLOCKCHAIN_RPC_URL=https://sepolia.base.org
BLOCKCHAIN_TOKEN_CONTRACT=0x036CbD53842c5426634e7929541eC2318f3dCF7e
BLOCKCHAIN_TOKEN_SYMBOL=USDC
BLOCKCHAIN_TOKEN_DECIMALS=6
BLOCKCHAIN_EXPLORER_BASE_URL=https://sepolia.basescan.org
BLOCKCHAIN_CONFIRMATIONS_REQUIRED=1
```

Wallet (never commit private key):

```bash
BLOCKCHAIN_WALLET_ADDRESS=<your testnet wallet address>
BLOCKCHAIN_WALLET_PRIVATE_KEY=<your testnet private key — env var only, never git>
```

---

## Security Constraints

- Private key is loaded exclusively from the `BLOCKCHAIN_WALLET_PRIVATE_KEY` environment variable.
- Private key is **never** stored in PostgreSQL, logged, or included in any event payload.
- Signed raw transaction bytes are **never** surfaced in API responses or events.
- Only the derived `txHash` and wallet address appear in outbound data.

---

## Mode Guard

`blockchain-service` defaults to `BLOCKCHAIN_MODE=MOCK`.
Real testnet execution requires **both** conditions simultaneously:

```
BLOCKCHAIN_MODE=REAL_TESTNET   AND   TESTNET_ENABLED=true
```

If either flag is absent or incorrect the service falls back to `MockBlockchainAdapter`.

---

## Chain ID Validation

On startup (and before every real submission) the service calls
`eth_chainId` on the configured RPC and compares the result against
`BLOCKCHAIN_CHAIN_ID`. A mismatch causes an immediate startup failure
(for startup check) or `BlockchainConfigurationException` (for pre-submission check).

---

## Testnet Explorer Links

After a real transaction is submitted, the explorer URL is:

```
https://sepolia.basescan.org/tx/<txHash>
```

---

## Generating Disposable Test Wallets

### Purpose

The StableCoinsDemo POC requires two EVM wallets for testnet exercises:

| Role | Wallet name |
|------|-------------|
| Sends USDC | StableCoinsDemo-Treasury |
| Receives USDC | StableCoinsDemo-Beneficiary |

> **These wallets are disposable Base Sepolia TESTNET wallets and must never be
> funded with mainnet ETH, production USDC, or other assets having real
> financial value.**

---

### Prerequisites

- **Java 21** — required by web3j 6.1.0 (the same JDK used for all services)
- An interactive terminal (passwords are entered without echo)
- Run from the **repository root** so wallet files land under `.local/wallets/`

---

### Command

```powershell
# From repository root:
scripts\generate-test-wallets.ps1
```

The script:
1. Verifies Java 21 is available.
2. Builds `scripts/generate-test-wallets/` into a self-contained fat jar.
3. Runs the jar; prompts interactively for two wallet passwords (no echo).
4. Writes two encrypted Ethereum Keystore V3 JSON files.

---

### Wallet file location

```
.local/wallets/
    treasury-wallet.json
    beneficiary-wallet.json
```

Both files are **excluded from Git** via `.gitignore` (`.local/` rule).  
Run `git status` after generation to confirm neither file appears as tracked.

---

### Password handling

- Passwords are entered interactively; they are **never echoed** to the terminal.
- Passwords are **never** stored in files, environment variables, or logs.
- If a password is lost the wallet file **cannot be recovered** — keep a secure
  offline backup.
- Each wallet has an independent password.

---

### Git exclusion

The following `.gitignore` entries prevent wallet files from being committed:

```
.local/
.local/wallets/
*.wallet.json
```

Verify with `git status` immediately after generation — no wallet files should
appear as staged or untracked in the Git output.

---

### Testnet-only restriction

These wallets exist **only** for Base Sepolia (chain ID 84532) testnet exercises.

Do not:
- Import them into a mainnet wallet application.
- Fund them with real ETH or production USDC.
- Reuse them beyond the POC demo lifecycle.

---

### Safety gate for existing files

If either `.local/wallets/treasury-wallet.json` or
`.local/wallets/beneficiary-wallet.json` already exists, the generator **stops
immediately** without overwriting anything.  To regenerate, rename or delete the
existing files first.

---

### Backup implications

`.local/` is git-ignored.  The wallet files exist only on the local machine.
If the machine is lost or `.local/` is deleted, the wallets cannot be recovered
without the original private key.  Since these are testnet-only disposable
wallets, the correct response to loss is to generate a fresh pair and re-fund
from a testnet faucet — not to attempt recovery.

---

## Verification Date

Externally verified: 2026-11-11.
Sources: docs.base.org, developers.circle.com, github.com/LFDT-web3j/web3j.
