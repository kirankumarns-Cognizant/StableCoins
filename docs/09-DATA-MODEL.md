# Data Model

## Schema Model

One Aurora PostgreSQL cluster, one database, one shared schema.

Logical table ownership defines which service may write to each table.
See ADR-007.

A service may read from tables it does not own.
A service may not INSERT, UPDATE or DELETE rows in another service's tables.

---

## payment-service

### CUSTOMER

| Column | Type | Notes |
|---|---|---|
| customer_id | UUID PK | |
| name | VARCHAR | |
| customer_type | VARCHAR | CORPORATE, INDIVIDUAL |
| status | VARCHAR | ACTIVE, SUSPENDED |
| created_at | TIMESTAMPTZ | |

### ACCOUNT

Customer payment account. Not to be confused with LEDGER_ACCOUNT.
See ADR-012.

| Column | Type | Notes |
|---|---|---|
| account_id | UUID PK | |
| customer_id | UUID FK → CUSTOMER | |
| currency | VARCHAR | |
| account_type | VARCHAR | |
| balance | NUMERIC(18,2) | Simulated balance for POC |
| status | VARCHAR | ACTIVE, SUSPENDED |

### BENEFICIARY

| Column | Type | Notes |
|---|---|---|
| beneficiary_id | UUID PK | |
| customer_id | UUID FK → CUSTOMER | |
| name | VARCHAR | |
| country | VARCHAR | |
| currency | VARCHAR | |
| wallet_address | VARCHAR | Destination blockchain wallet |
| status | VARCHAR | ACTIVE, SUSPENDED |

### PAYMENT

| Column | Type | Notes |
|---|---|---|
| payment_id | VARCHAR PK | Format: PAY-xxxxx |
| correlation_id | VARCHAR UNIQUE | Format: CORR-xxxxx |
| sender_account_id | UUID FK → ACCOUNT | |
| beneficiary_id | UUID FK → BENEFICIARY | |
| amount | NUMERIC(18,2) | |
| source_currency | VARCHAR | |
| destination_currency | VARCHAR | |
| payment_rail | VARCHAR | AUTO, STABLECOIN, VISA, STABLECOIN_PLUS_VISA |
| status | VARCHAR | See payment state model in AGENTS.md |
| idempotency_key | VARCHAR UNIQUE | From Idempotency-Key request header |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

### PAYMENT_STATUS_HISTORY

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| old_status | VARCHAR | |
| new_status | VARCHAR | |
| reason | VARCHAR | |
| actor_service | VARCHAR | Which service caused transition |
| timestamp | TIMESTAMPTZ | |

---

## compliance-quote-service

### COMPLIANCE_CHECK

| Column | Type | Notes |
|---|---|---|
| check_id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| check_type | VARCHAR | KYC, KYB, AML, SANCTIONS, WALLET_SCREENING |
| result | VARCHAR | PASS, FAIL, MANUAL_REVIEW |
| reason | VARCHAR | |
| checked_at | TIMESTAMPTZ | |

### FX_QUOTE

Simulated in the POC. No real FX provider.

| Column | Type | Notes |
|---|---|---|
| quote_id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| source_currency | VARCHAR | |
| destination_currency | VARCHAR | |
| source_amount | NUMERIC(18,2) | |
| destination_amount | NUMERIC(18,6) | |
| rate | NUMERIC(18,8) | |
| fees | NUMERIC(18,2) | |
| expires_at | TIMESTAMPTZ | |
| status | VARCHAR | PENDING, ACCEPTED, EXPIRED |

---

## blockchain-service

### WALLET

Private keys must NOT be stored in this table or anywhere in PostgreSQL.

| Column | Type | Notes |
|---|---|---|
| wallet_id | UUID PK | |
| customer_id | UUID FK → CUSTOMER | |
| blockchain | VARCHAR | e.g. ETHEREUM |
| network | VARCHAR | e.g. base-sepolia |
| wallet_address | VARCHAR | Public address only |
| status | VARCHAR | ACTIVE, SUSPENDED |

### BLOCKCHAIN_TRANSACTION

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| correlation_id | VARCHAR | |
| chain_id | BIGINT | 84532 for Base Sepolia |
| network | VARCHAR | |
| token_contract | VARCHAR | USDC contract address |
| sender_address | VARCHAR | |
| recipient_address | VARCHAR | |
| amount | NUMERIC(36,18) | Full ERC-20 precision |
| token_decimals | INT | |
| tx_hash | VARCHAR UNIQUE | Real testnet transaction hash |
| block_number | BIGINT | |
| gas_used | BIGINT | |
| status | VARCHAR | SUBMITTED, CONFIRMED, FAILED |
| submitted_at | TIMESTAMPTZ | |
| confirmed_at | TIMESTAMPTZ | |

---

## settlement-ledger-service

### LEDGER_ACCOUNT

The chart of accounts. Not to be confused with customer ACCOUNT.
See ADR-012.

Seeded by Flyway migration with the approved chart of accounts.

| Column | Type | Notes |
|---|---|---|
| ledger_account_id | UUID PK | |
| account_code | VARCHAR UNIQUE | e.g. 110001 |
| account_name | VARCHAR | |
| account_type | VARCHAR | ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE |
| asset | VARCHAR | USD, USDC, ETH |
| is_active | BOOLEAN | |

### JOURNAL

A complete, balanced accounting transaction.
One journal contains one or more LEDGER_ENTRY rows.
SUM(debits) must equal SUM(credits) per asset before a journal can be POSTED.

| Column | Type | Notes |
|---|---|---|
| journal_id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| correlation_id | VARCHAR | |
| description | VARCHAR | Human-readable description |
| posted_at | TIMESTAMPTZ | |
| status | VARCHAR | PENDING, POSTED, REVERSED |
| reversed_by_journal_id | UUID FK → JOURNAL | Nullable; set when reversed |

### LEDGER_ENTRY

Individual debit or credit lines within a JOURNAL.
Rows are immutable once JOURNAL.status = POSTED.

| Column | Type | Notes |
|---|---|---|
| entry_id | UUID PK | |
| journal_id | UUID FK → JOURNAL | |
| ledger_account_id | UUID FK → LEDGER_ACCOUNT | |
| payment_id | VARCHAR | Denormalised for query convenience |
| asset | VARCHAR | USD, USDC, ETH |
| debit | NUMERIC(18,6) | 0 if credit entry |
| credit | NUMERIC(18,6) | 0 if debit entry |
| posted_at | TIMESTAMPTZ | |
| reversal_of | UUID FK → LEDGER_ENTRY | Nullable; set on reversal entries |

### SETTLEMENT

| Column | Type | Notes |
|---|---|---|
| settlement_id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| journal_id | UUID FK → JOURNAL | |
| settlement_type | VARCHAR | BLOCKCHAIN, VISA |
| asset | VARCHAR | |
| amount | NUMERIC(18,6) | |
| status | VARCHAR | PENDING, COMPLETED, FAILED |
| settled_at | TIMESTAMPTZ | |

---

## visa-simulator

### VISA_TRANSACTION

All records represent simulated Visa transactions. No real Visa network is involved.

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| correlation_id | VARCHAR | |
| simulator_reference | VARCHAR | Visa simulator internal ref |
| source_asset | VARCHAR | |
| source_amount | NUMERIC(18,2) | |
| destination_currency | VARCHAR | |
| beneficiary_id | VARCHAR | |
| status | VARCHAR | ACCEPTED, PROCESSING, SENT, COMPLETED, DECLINED, FAILED, TIMEOUT |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

---

## reconciliation-service

### RECONCILIATION

| Column | Type | Notes |
|---|---|---|
| reconciliation_id | UUID PK | |
| payment_id | VARCHAR FK → PAYMENT | |
| status | VARCHAR | MATCHED, PARTIALLY_MATCHED, MISMATCH, PENDING |
| payment_amount | NUMERIC(18,6) | |
| ledger_amount | NUMERIC(18,6) | |
| blockchain_amount | NUMERIC(18,6) | |
| visa_amount | NUMERIC(18,2) | |
| reason | VARCHAR | Populated on MISMATCH |
| reconciled_at | TIMESTAMPTZ | |

### RECONCILIATION_DETAIL

One row per reconciled leg. Provides per-leg breakdown for the UI.

| Column | Type | Notes |
|---|---|---|
| detail_id | UUID PK | |
| reconciliation_id | UUID FK → RECONCILIATION | |
| leg | VARCHAR | PAYMENT, LEDGER, BLOCKCHAIN, VISA |
| expected_amount | NUMERIC(18,6) | |
| actual_amount | NUMERIC(18,6) | |
| asset | VARCHAR | |
| status | VARCHAR | MATCHED, MISMATCH, MISSING |
| notes | VARCHAR | |

## Step 8 owner migrations

payment V12 adds payment_closure. blockchain V6 adds actual receipt fee components,
completeness/reason and confirmation depth. ledger V8 binds settlement to
correlation, transaction hash and fee journal. visa V2 adds visa_transaction and
visa_payout_audit. reconciliation V1 creates reconciliation and
reconciliation_detail with immutable reports/source fingerprints. Every service
retains its own Flyway history and logical write ownership under ADR-007/008.
Legacy missing fee evidence remains incomplete; conflicting historical uniqueness
must be reviewed rather than deleting or rewriting posted financial records.
