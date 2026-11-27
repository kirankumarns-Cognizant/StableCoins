#!/usr/bin/env bash
# Read-only presenter queries. No seed, repair, queue receive, or payment command.
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd -- "$root"
phase="${1:-}"
case "$phase" in
  catalog|pending) ;;
  payment|preparation|approvals|execution|ledger|closure|all)
    [[ "${2:-}" =~ ^PAY-[A-Za-z0-9-]{1,40}$ ]] || {
      echo 'Supply the payment ID shown by the UI: bash scripts/demo/db-check.sh PHASE PAY-...' >&2; exit 2;
    } ;;
  *) echo 'Usage: bash scripts/demo/db-check.sh catalog|pending|payment|preparation|approvals|execution|ledger|closure|all [PAY-...]' >&2; exit 2 ;;
esac
customer="${DEMO_CUSTOMER_ID:-11111111-1111-1111-1111-111111111111}"
[[ "$customer" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || {
  echo 'DEMO_CUSTOMER_ID must be a UUID' >&2; exit 2;
}
run_query() {
  docker compose exec -T -e DEMO_CUSTOMER_ID="$customer" -e DEMO_PAYMENT_ID="${2:-}" postgres sh -c \
    'exec psql -X -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v customer_id="$DEMO_CUSTOMER_ID" -v payment_id="$DEMO_PAYMENT_ID"' \
    < "$root/scripts/demo/$1.sql"
}
if [[ "$phase" == all ]]; then
  for section in payment preparation approvals execution ledger closure; do run_query "$section" "$2"; done
else
  run_query "$phase" "${2:-}"
fi
