#!/usr/bin/env bash
# Codespaces/Linux only. Run with bash; never source or run with shell tracing.
set +x
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
umask 077
secret_file=.local/secrets/treasury-password
mkdir -p .local/secrets
chmod 700 .local/secrets
if [[ -e "$secret_file" ]]; then
  printf '%s\n' 'Password file already exists; retained unchanged.'
  exit 1
fi
trap 'unset treasury_password confirmation' EXIT
IFS= read -r -s -p 'Treasury wallet password: ' treasury_password </dev/tty
printf '\n' >/dev/tty
IFS= read -r -s -p 'Confirm treasury wallet password: ' confirmation </dev/tty
printf '\n' >/dev/tty
if [[ -z "$treasury_password" || "$treasury_password" != "$confirmation" ]]; then
  printf '%s\n' 'Empty password or confirmation mismatch; no file written.' >&2
  exit 1
fi
(set -o noclobber; printf '%s' "$treasury_password" > "$secret_file")
chmod 600 -- "$secret_file"
if [[ "$(stat -c '%a' -- "$secret_file")" != 600 ]]; then
  printf '%s\n' 'Password file permissions could not be restricted to 600; do not start TESTNET.' >&2
  exit 1
fi
printf '%s\n' 'Created ignored .local/secrets/treasury-password with owner-only permissions.'
printf '%s\n' 'No wallet unlocked, runtime changed, or transaction submitted.'
