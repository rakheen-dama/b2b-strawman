#!/usr/bin/env bash
# Phase 93 exit — the read-only database check of the take-on run (slice 660A).
#
#   bash qa_cycle/evidence/phase93-exit/psql-check.sh <cutover C yyyy-mm-dd> <first month end yyyy-mm-dd>
#
# Run by hand right after `pnpm test:e2e:take-on` on the same stack. SELECT only, every statement
# inside a read-only transaction (PGOPTIONS default_transaction_read_only=on) — QA never writes SQL
# (the phase92-exit/psql-check.sh pattern). Prints "## <name>" sections with '|'-separated rows and
# writes the same text to PSQL_OUTPUT=<file> (default psql-output.txt next to this script).
set -euo pipefail

C="${1:?usage: psql-check.sh <cutover yyyy-mm-dd> <first month end yyyy-mm-dd>}"
END="${2:?usage: psql-check.sh <cutover yyyy-mm-dd> <first month end yyyy-mm-dd>}"
for d in "$C" "$END"; do
  [[ "$d" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "bad date: $d" >&2; exit 2; }
done
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${PSQL_OUTPUT:-$HERE/psql-output.txt}"
CONTAINER="${E2E_POSTGRES_CONTAINER:-e2e-postgres}"

q() {
  docker exec -e PGOPTIONS='-c default_transaction_read_only=on' "$CONTAINER" \
    psql -U postgres -d app -v ON_ERROR_STOP=1 -X -A -t -F '|' -c "$1"
}

S="$(q "select schema_name from public.org_schema_mapping where external_org_id = 'e2e-test-org';")"
[[ "$S" =~ ^tenant_[a-z0-9_]+$ ]] || { echo "unexpected tenant schema: '$S'" >&2; exit 3; }
G="'Take-on Trust Banking Account'"

{
  echo "# Phase 93 exit — take-on read-only psql check ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  echo "# container $CONTAINER, cutover $C, first month end $END"
  echo "## schema"
  echo "$S"
  echo "## flyway (tenant, latest)"
  q "select version, description from $S.flyway_schema_history order by installed_rank desc limit 1;"

  echo "## trust-accounts"
  echo "# name|type|primary|opened|take_on_cutover_date|status"
  q "select account_name, account_type, is_primary, opened_date, take_on_cutover_date, status
       from $S.trust_accounts order by account_name;"

  echo "## take-on-batches"
  echo "# dataset|status|total|valid|error|duplicate|blocks|cutover"
  q "select dataset, status, total_rows, valid_rows, error_rows, duplicate_rows, block_count, cutover_date
       from $S.take_on_batches order by created_at;"

  echo "## take-on-rows (status per dataset)"
  q "select b.dataset, r.status, count(*) from $S.take_on_rows r
       join $S.take_on_batches b on b.id = r.batch_id
      group by b.dataset, r.status order by b.dataset, r.status;"

  echo "## customers taken on"
  echo "# lifecycle_status|count|with taken_on_at"
  q "select lifecycle_status, count(*), count(taken_on_at) from $S.customers
      where take_on_batch_id is not null group by lifecycle_status;"

  echo "## brought-forward fee notes"
  echo "# number|status|total|issue|due"
  q "select invoice_number, status, total, issue_date, due_date from $S.invoices
      where take_on_batch_id is not null order by invoice_number;"
  echo "# invoice counter next_number"
  q "select next_number from $S.invoice_counters;"

  echo "## G cashbook vs ledgers (posted rows dated <= $END)"
  echo "# sum(cashbook_effect)|sum(ledger_effect)|difference|rows"
  q "select coalesce(sum(t.cashbook_effect), 0.00), coalesce(sum(t.ledger_effect), 0.00),
            coalesce(sum(t.cashbook_effect), 0.00) - coalesce(sum(t.ledger_effect), 0.00), count(t.id)
       from $S.trust_accounts a
       join $S.trust_transactions t on t.trust_account_id = a.id and t.posted_at is not null
        and t.transaction_date <= date '$END'
      where a.account_name = $G;"

  echo "## G reconciliations"
  echo "# period_end|status|difference|notes"
  q "select r.period_end, r.status, r.difference, r.notes from $S.trust_reconciliations r
       join $S.trust_accounts a on a.id = r.trust_account_id where a.account_name = $G;"

  echo "## G monthly list"
  echo "# period_end|bank|adjusted_bank|cashbook|client_ledgers|fund|unidentified|inter_account|creditor_total|difference"
  q "select l.period_end, l.bank_balance, l.adjusted_bank_balance, l.cashbook_balance,
            l.client_ledger_total, l.fund_ledger_balance, l.unidentified_deposit_total,
            l.inter_account_balance, l.creditor_total, l.difference
       from $S.trust_monthly_lists l join $S.trust_accounts a on a.id = l.trust_account_id
      where a.account_name = $G and l.period_end = date '$END';"

  echo "## G statement lines"
  echo "# date|description|amount|status|classification"
  q "select l.transaction_date, l.description, l.amount, l.match_status, l.classification
       from $S.bank_statement_lines l
       join $S.bank_statements s on s.id = l.bank_statement_id
       join $S.trust_accounts a on a.id = s.trust_account_id
      where a.account_name = $G order by l.line_number;"

  echo "## take-on report files"
  echo "# format|file_size|sha256"
  q "select format, file_size, sha256 from $S.take_on_report_files order by format;"

  echo "## dormancy / collections"
  q "select 'collection_activities', count(*) from $S.collection_activities;"
  q "select 'collections_enabled', collections_enabled from $S.org_settings;"
} | tee "$OUT"
