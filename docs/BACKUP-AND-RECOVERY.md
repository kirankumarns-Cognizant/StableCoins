# StableCoins whole-solution backup and recovery

Proposed operating design, 27 November 2026. Owner selected AWS S3 and us-west-2.
Implementation and restore acceptance: **D11 OPEN** in [the central backlog](40-IMPLEMENTATION-BACKLOG.md).
This document is a concrete recovery design, not evidence of a configured backup.
No bucket, credentials, schedule, snapshot or restore has been created by this documentation change.

## Storage and encryption

Use a dedicated private bucket named `stablecoins-recovery-<AWS-account-id>-us-west-2` (subject to name availability). Enable S3 Block Public Access, bucket-owner-enforced ownership, TLS-only access, server-side encryption, versioning and retention on infrastructure deletion. Use Restic client-side encryption before uploading any private project content. Windows and Codespaces use separate Restic repositories/prefixes so one cannot overwrite the other's state:

- `windows/restic/`: Windows source snapshot, all wallet generations and local configuration/state.
- `codespaces/restic/`: Codespaces source snapshot, database export and initialized private network state.
- Secret recovery uses a separately protected encrypted vault/repository with separate credentials, rather than placing readable passwords beside wallet files. Wallet passwords, API/RPC secrets and environment secret values must be inventoried privately; no secret values belong in this document or Git.

Store each backup encryption password in an independent approved password manager, with a separately secured offline recovery copy. Do not keep its only copy inside the S3 backup or Codespace. AWS access and the encryption password are both required to recover. Use short-lived AWS authentication where available; no root credentials or access keys in scripts/Git. Restrict clients to their own backup prefix and keep permanent-version deletion/retention administration separate from routine backup access. Restic requires coordination/lock permissions; validate least-privilege policies against actual operations rather than assuming write-only access is sufficient. Protect the AWS account with MFA and independent recovery arrangements.

Versioning is an additional recovery layer, not protection against an unrestricted administrator permanently deleting all versions. Consider independently administered/cross-account replication or a separate offline encrypted copy when the required resilience is defined. Do not apply S3 age-based expiration or archival transitions to active Restic objects: retained snapshots share data objects. Object Lock needs a separately tested design compatible with Restic locking/pruning before enabling it.

## Capture inventory

| Asset | Capture and recovery requirements |
| --- | --- |
| Source and build definitions | Correct StableCoins Git mirror with history/branches/tags, Git LFS objects if used, plus uncommitted/untracked work deliberately included in the encrypted source snapshot. Confirm the actual Git root and remote: the Windows project is inside an unrelated ancestor repository. Never mirror that ancestor as though it were StableCoins. Record commit, lockfiles, image digests and tool versions. |
| Wallets | Inventory every intentional original, replacement, beneficiary, treasury and Besu keystore in both environments. Preserve paths, permissions and checksums inside the encrypted manifest. No deduplication, replacement or deletion. Include personal recovery material outside the project only after identifying its location privately. |
| Secrets and environment | Recoverable private inventory covering .env variants, local secret files, runtime variables/configuration files, API/RPC credentials and Besu password files. Store secret values in the separate vault. Do not assume ignored files are in Git or that Codespaces and Windows have identical secrets. |
| PostgreSQL | Consistent PostgreSQL 16 custom-format pg_dump export, plus separately protected required roles/global settings or documented recreation. Include all business tables, journals, audit, approvals, reservations, signed-transaction/recovery and outbox records. Copying a live Docker volume is not the backup procedure. |
| Private Besu | Resolved BESU_STATE_DIR (default .local/besu-network), genesis and pinned identity, deployment/registry manifests, generated node configurations, node-1 through node-5 identity keys and cleanly stopped node data directories. Preserve exact deployed token/network identity and compatible Besu version. No init/reset/redeployment during recovery. |
| Messaging | Inventory LocalStack persistence, queues, DLQs, pending messages and in-flight processing. Establish and test a quiescent persistent-state export or owner-approved replay strategy that accounts for already-published outbox events. Database backup alone does not preserve queued messages. |
| Evidence | Preserve required private handovers, demo/receipt exports and a non-secret recovery report. Sensitive evidence stays encrypted; do not publish private handovers to GitHub. |

A Windows snapshot cannot capture a Docker volume or wallet that exists only in Codespaces. Back up each environment at its source. Missing required assets must fail the completeness check; do not label a partial source-only backup as whole-solution recovery.

## Coordinated full checkpoint

1. Preflight identity, expected AWS account/bucket/Region, free space, tool versions, path inventory and encryption-password recovery. Test access without printing secrets.
2. Enter an explicitly announced maintenance window. Block new payment/issuer submissions; let active work reach a recorded checkpoint. Quiesce producers, consumers and scheduled workers; record running services for later resumption. Never silently stop a live demo as part of an ordinary source backup.
3. Record unresolved execution IDs, transaction hashes, nonce/recovery state and queue/in-flight state. A public Base Sepolia transaction can continue confirming while this application is stopped; a local backup cannot roll back that chain.
4. With application writers stopped, export PostgreSQL. Stop private Besu nodes cleanly and capture their data/configuration/keys as part of the same checkpoint. Capture messaging persistence using its validated recovery procedure. Fail on dump/copy errors or missing required nodes/files.
5. Produce an encrypted checkpoint manifest: snapshot identifier/time, environment, commit, database/tool/image versions, chain/genesis/token identities and head block, asset inventory/checksums, pending work and restore dependencies. Staging must have restrictive permissions/protected storage and remain outside tracked files.
6. Upload the checkpoint through Restic to the correct environment repository; record the resulting snapshot ID and completion status. Unreadable-file/partial-backup exit statuses are failures of a full checkpoint. Perform repository integrity checking and verify the expected manifest/assets are recoverable.
7. Resume only services that were previously running, verify health and reconcile pending transaction evidence. Record any backup or restart failure explicitly. Never overwrite or remove original wallets during backup cleanup.

This checkpoint uses a controlled pause for a small PoC. PostgreSQL pg_dump itself supports online consistent exports, but this does not make independently changing database, queues and Besu directories a coordinated snapshot.

## Schedule and retention proposal

- Source/configuration/wallet backups after important changes; immediate capture after any new wallet, credential or deployment identity is created.
- Full coordinated checkpoint at the end of a demo session and before Codespace deletion/rebuild or disruptive maintenance. Add unattended scheduling only after maintenance/recovery behavior is accepted; an inactive Codespace cannot run its scheduled job.
- Initial retention proposal: 14 daily, 8 weekly and 12 monthly snapshots, managed by Restic's snapshot retention. Keep named milestone checkpoints and the intentional original wallet inventory. No automatic pruning until restore acceptance and operator review.
- Check latest successful backup age from outside the Codespace (with alerts); a local timer that disappears with its host is insufficient. Backups limit loss to the last successful snapshot; they do not eliminate changes made afterward.
- Review storage/transfer/requests and retained S3-version costs. Do not claim a price before measuring the private chain size and backup frequency.

## Restore drill and acceptance

Restore into a separate empty workspace/database/isolated private network, with payment workers and public transaction submission disabled. Never test by overwriting the functioning environment.

1. Recover AWS access and encryption passwords independently of the original machine/Codespace.
2. Restore source/version and private inventory; verify file checksums and restrictive permissions without printing file contents/passwords.
3. Restore PostgreSQL into an empty compatible instance; verify required roles, row counts, immutable journal totals, approvals, reservations and recovery/outbox evidence.
4. Restore Besu configuration, node identities and data using the recorded compatible version. Verify chain ID, genesis, validator identities, token address/code and balances; do not initialize a replacement chain.
5. Restore/test messaging according to the checkpoint procedure. Reconcile unresolved public transactions against the current public chain. Validate private pending transactions and signed payloads before enabling workers; do not blindly resubmit.
6. Verify application health, balances, accounting/reconciliation and duplicate-submission protection. Resume authorized operation only after recovery checks succeed.
7. Record elapsed recovery time, newest recovered checkpoint, failures and actual coverage. A successful upload or Restic integrity check alone does not establish a successful application restore.

AWS backup storage can be introduced before AWS application deployment. ECS/Aurora/EC2 migration remains the separate accepted deployment scope. This proposal adds no business microservice and changes no existing payment rail or runtime setting.

## Setup prerequisites still to establish

Region is confirmed as us-west-2. AWS account identity, permitted authentication/profile, bucket ownership/permissions, independent password storage, local tools and the Codespaces data inventory still require verification. Implementation scripts, infrastructure deployment and scheduling must be completed and exercised under D11; the prose above is not a runnable backup script.

## Sources

- [Restic repository preparation and S3 backend](https://restic.readthedocs.io/en/stable/030_preparing_a_new_repo.html)
- [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html)
- [PostgreSQL 16 pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html)
- [Besu backup and restore](https://docs.besu-eth.org/private-networks/how-to/backup)
- Project storage: [Besu Compose](../infrastructure/besu/compose.yml), [Besu network setup](../infrastructure/besu/network.cjs), [application Compose](../docker-compose.yml).
