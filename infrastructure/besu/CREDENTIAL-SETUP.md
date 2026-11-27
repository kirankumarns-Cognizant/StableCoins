# Dedicated Besu credentials: setup and verification

The BESU_RPC_URL, BESU_KEYSTORE_FILE and BESU_PASSWORD_FILE settings are already
implemented in the private runtime. Settings do not generate wallets or passwords.

Initially observed in this Windows checkout on 2026-11-26: dedicated Besu treasury and
beneficiary keystores and the dedicated treasury password file were absent.
Windows interactive setup subsequently completed with BESU_LOCAL_CREDENTIALS_VERIFIED; see current activity status for evidence. The existing public Base Sepolia wallets were not opened, replaced or reused.
Ignored files on another machine/Codespace must be checked there independently.

## Codespaces/Linux setup

Run at the repository root. This setup is part of Codespaces acceptance, after
the complete implementation has been published deliberately. It does not mean
the Besu application payment flow is complete now.

~~~bash
cd /workspaces/StableCoins
node infrastructure/besu/credential-status.cjs
~~~

The status tool checks existence/permissions only. It never reads wallet or
password contents, contacts RPC or claims a password is correct. Windows ACLs
are not inferred from Unix modes; ownerOnly is null on Windows. With all files present, Windows reports BESU_CREDENTIAL_FILES_PRESENT_ACL_CHECK_REQUIRED; the PowerShell wrapper performs the independent ACL checks.

If either dedicated wallet already exists, preserve the directory and investigate
the original setup; do not rerun creation to overwrite it. When neither exists:

~~~bash
bash services/mvnw -f scripts/generate-test-wallets/pom.xml -DskipTests package
java --class-path scripts/generate-test-wallets/target/generate-test-wallets.jar \
  infrastructure/besu/CreateBesuWalletPair.java
~~~

Enter and confirm two passwords privately, then re-enter both to unlock the saved
files. Save the passwords privately with their roles. Expect BESU_PAIR_VERIFIED.
The launcher creates .local/besu-wallets/treasury-wallet.json and
.local/besu-wallets/beneficiary-wallet.json with mode 600 on Linux.
Do not create empty JSON placeholders. Keep the public wallet files unchanged.

Prepare the dedicated treasury password file:

~~~bash
java --class-path scripts/generate-test-wallets/target/generate-test-wallets.jar \
  infrastructure/besu/PrepareBesuPassword.java
node infrastructure/besu/credential-status.cjs
git check-ignore -- .local/besu-wallets/treasury-wallet.json \
  .local/besu-wallets/beneficiary-wallet.json .local/secrets/besu-treasury-password
~~~

Expect BESU_TREASURY_PASSWORD_VERIFIED_AND_SAVED, followed by
BESU_CREDENTIAL_FILES_PRESENT. The helper actually unlocks the saved Besu treasury
before creating .local/secrets/besu-treasury-password. It uses exact UTF-8 password
bytes without a trailing newline, mode 600 and an owner-only secrets directory.
Existing files are never overwritten; failures preserve any existing material.
Never paste password contents into chat, shell arguments, source or logs.

The beneficiary password is needed for future beneficiary signing, not receiving.
It is not required by the treasury runtime and no beneficiary password file is
created by this procedure.

## Runtime references

After the private chain has been initialized, started, genesis-pinned and DEMOUSD
deployed according to README.md, runtime-context.cjs exports validated public
metadata and the host RPC URL. It does not enable execution or export secrets.

~~~bash
node infrastructure/besu/runtime-context.cjs
source .local/besu-network/readiness.env
export BESU_KEYSTORE_FILE="$PWD/.local/besu-wallets/treasury-wallet.json"
export BESU_PASSWORD_FILE="$PWD/.local/secrets/besu-treasury-password"
~~~

BESU_RPC_URL is an endpoint string, not a file. For the Java process on the
Codespace host it is http://127.0.0.1:8547. A blockchain-service container needs a
separate tested network attachment, in-container paths and read-only secret
mounts; host loopback and paths cannot simply be reused inside it.
The final application Compose overlay and end-to-end acceptance remain gates.

Preserve .local/besu-wallets and .local/besu-network across restarts. Keep encrypted
wallets and password backups private; Git ignores these local paths. Generating
credentials is an explicit operator action and never an application startup side
effect. Production AWS credential storage/deployment is a later stage.
## Windows interactive setup

From the project root in an ordinary interactive PowerShell terminal:

~~~powershell
& '.\infrastructure\besu\setup-credentials.ps1'
~~~

The script uses Java 21 from your user tools directory and the built wallet JAR.
On a fresh checkout it creates a dedicated pair, confirms both passwords and
unlocks both saved files. If both Besu wallets already exist it preserves them
and continues with the missing treasury password file. A partial pair or an
existing password file stops setup without overwriting anything.

Enter the existing Besu treasury password privately when prompted. The helper
unlocks the saved keystore before writing exact UTF-8 password bytes. It applies
current-user-only Windows ACLs, and the wrapper independently checks all three
files before printing BESU_LOCAL_CREDENTIALS_VERIFIED. Keep the beneficiary
password in your private password manager; no beneficiary password file is needed
to receive tokens.

Do not create another pair to recover from a password-helper failure after
BESU_PAIR_VERIFIED. Rerun this script to resume the password step. The setup
requires an interactive console; it never receives passwords through chat,
command arguments, logs or environment variables.

The same Java helpers work in Codespaces with owner-only POSIX permissions;
use the Linux commands above. A new independent Codespace setup generates a new
pair. To retain the same identities, securely transfer the encrypted keystores
and provision the matching password privately instead; never publish them in Git.
