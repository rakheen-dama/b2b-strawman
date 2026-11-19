#!/usr/bin/env bash
# Phase 92 exit month — step 21 (task 652.31): the read-only database check.
#
#   bash qa_cycle/evidence/phase92-exit/psql-check.sh <month end, yyyy-mm-dd>
#
# Called by the last step of frontend/e2e/tests/legal-lifecycle/trust-balanced-month.spec.ts (or by
# hand). SELECT only, every statement inside a read-only transaction (PGOPTIONS
# default_transaction_read_only=on) — QA never writes SQL. Prints machine-readable sections
# ("## <name>" then '|'-separated rows) and writes the same text to PSQL_OUTPUT=<file> (the spec
# passes its run's evidence dir; by hand the default is psql-output.txt next to this script).
#
# The three accounts of the exit month (ruling 652E D-1: G is the seeded primary account).
set -euo pipefail

END="${1:?usage: psql-check.sh <month end yyyy-mm-dd>}"
[[ "$END" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "bad month end: $END" >&2; exit 2; }
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${PSQL_OUTPUT:-$HERE/psql-output.txt}"
CONTAINER="${E2E_POSTGRES_CONTAINER:-e2e-postgres}"

q() {
  docker exec -e PGOPTIONS='-c default_transaction_read_only=on' "$CONTAINER" \
    psql -U postgres -d app -v ON_ERROR_STOP=1 -X -A -t -F '|' -c "$1"
}

S="$(q "select schema_name from public.org_schema_mapping where external_org_id = 'e2e-test-org';")"
[[ "$S" =~ ^tenant_[a-z0-9_]+$ ]] || { echo "unexpected tenant schema: '$S'" >&2; exit 3; }

ACCOUNTS="'Mathebula & Partners Trust Account','Section 86(4) investment — Ndlovu','Section 86(4) investment — Khumalo'"

{
  echo "# Phase 92 exit month — read-only psql check ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  echo "# container $CONTAINER, month end $END"
  echo "## schema"
  echo "$S"

  echo "## cashbook-vs-ledgers"
  echo "# account|sum(cashbook_effect)|sum(ledger_effect)|difference|posted rows — posted rows dated <= $END"
  q "select a.account_name,
            coalesce(sum(t.cashbook_effect), 0.00),
            coalesce(sum(t.ledger_effect), 0.00),
            coalesce(sum(t.cashbook_effect), 0.00) - coalesce(sum(t.ledger_effect), 0.00),
            count(t.id)
       from $S.trust_accounts a
       left join $S.trust_transactions t
         on t.trust_account_id = a.id and t.posted_at is not null
        and t.transaction_date <= date '$END'
      where a.account_name in ($ACCOUNTS)
      group by a.id, a.account_name
      order by a.account_name;"

  echo "## ledger-split"
  echo "# account|client|client_investment|fund|unidentified|inter_account"
  q "select a.account_name,
            coalesce(sum(t.ledger_effect) filter (where t.ledger = 'CLIENT'), 0.00),
            coalesce(sum(t.ledger_effect) filter (where t.ledger = 'CLIENT_INVESTMENT'), 0.00),
            coalesce(sum(t.ledger_effect) filter (where t.ledger = 'NON_CLIENT'
                     and ca.account_type = 'FUND_INTEREST_AND_CHARGES'), 0.00),
            coalesce(sum(t.ledger_effect) filter (where t.ledger = 'NON_CLIENT'
                     and ca.account_type = 'UNIDENTIFIED_DEPOSITS'), 0.00),
            coalesce(sum(t.ledger_effect) filter (where t.ledger = 'INTER_ACCOUNT'), 0.00)
       from $S.trust_accounts a
       join $S.trust_transactions t
         on t.trust_account_id = a.id and t.posted_at is not null
        and t.transaction_date <= date '$END'
       left join $S.trust_creditor_accounts ca on ca.id = t.creditor_account_id
      where a.account_name in ($ACCOUNTS)
      group by a.account_name
      order by a.account_name;"

  echo "## monthly-lists"
  echo "# account|period_end|bank|outstanding_deposits|outstanding_payments|adjusted_bank|cashbook|client_ledgers|investment_creditors|fund|unidentified|inter_account|creditor_total|difference"
  q "select a.account_name, l.period_end, l.bank_balance, l.outstanding_deposits,
            l.outstanding_payments, l.adjusted_bank_balance, l.cashbook_balance,
            l.client_ledger_total, l.investment_creditor_total, l.fund_ledger_balance,
            l.unidentified_deposit_total, l.inter_account_balance, l.creditor_total, l.difference
       from $S.trust_monthly_lists l
       join $S.trust_accounts a on a.id = l.trust_account_id
      where l.period_end = date '$END'
      order by a.account_name;"

  echo "## interest-entries"
  echo "# account|entry_date|gross|fund_share|net|presentation|status|fund_row"
  q "select a.account_name, i.entry_date, i.gross_amount, i.fund_share_amount, i.net_amount,
            i.presentation, i.status, i.fund_share_transaction_id is not null
       from $S.trust_investment_interest i
       join $S.trust_investments v on v.id = i.investment_id
       join $S.trust_accounts a on a.id = v.investment_account_id
      order by i.entry_date, a.account_name;"

  echo "## portal-trust-balances"
  echo "# client|current_balance|invested_balance"
  q "select c.name, b.current_balance, b.invested_balance
       from portal.portal_trust_balance b
       join $S.customers c on c.id = b.customer_id
      where c.name in ('Ndlovu Family Trust', 'Khumalo Holdings')
      order by c.name;"
} | tee "$OUT"
