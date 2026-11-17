#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 647C)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-647C
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/647C-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (647C-touched classes):"
unzip -l $J/app.jar | grep -o "[^ ]*\(parser/CsvBankStatementParser\|parser/ParsedStatementLine\|creditor/TrustCreditorLedgerController\|creditor/TrustCreditorLedgerService\|transaction/CreditorLedgerRow\|transaction/TrustLedgerQueryRepository\|reconciliation/TrustReconciliationService\)\.class"
(cd $J && unzip -q -o app.jar '*reconciliation/parser/CsvBankStatementParser.class' '*reconciliation/parser/ParsedStatementLine.class' '*creditor/TrustCreditorLedgerController.class' '*creditor/TrustCreditorLedgerService.class' '*transaction/CreditorLedgerRow.class' '*transaction/TrustLedgerQueryRepository.class' '*reconciliation/TrustReconciliationService.class')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== CsvBankStatementParser.class: 647C keyword logic"
for s in suggestClassification containsAny LPFF FIDELITY COMMISSION REFUND REVERSAL INTEREST SERVICE; do
  echo "   contains '$s': $(c CsvBankStatementParser.class "$s")"
done
echo "== ParsedStatementLine.class contains 'suggestedClassification' (647C): $(c ParsedStatementLine.class suggestedClassification)"
echo "== TrustCreditorLedgerController.class contains '/api/trust-creditor-accounts/{creditorAccountId}/transactions' (647C): $(c TrustCreditorLedgerController.class '/api/trust-creditor-accounts/{creditorAccountId}/transactions')"
echo "== TrustCreditorLedgerService.class contains 'listCreditorTransactions' (647C): $(c TrustCreditorLedgerService.class listCreditorTransactions)"
echo "== CreditorLedgerRow.class exists (647C): $(find $J -name CreditorLedgerRow.class | wc -l | tr -d ' ') ; contains 'getChargeVatAmount': $(c CreditorLedgerRow.class getChargeVatAmount)"
echo "== TrustLedgerQueryRepository.class contains 'creditorLedgerRows' (647C): $(c TrustLedgerQueryRepository.class creditorLedgerRows)"
echo "== TrustReconciliationService.class contains 'setSuggestedClassification' (647C): $(c TrustReconciliationService.class setSuggestedClassification)"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V1[5-9][^ ]*\|db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 647C strings in .next (excluding cache):"
for s in 'fund-ledger-table' 'classification-suggested-note' 'Go to the first page' 'Awaiting approval' 'fund-ledger-page-out-of-range' "Suggested from the line"; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF '$s' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 4"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.bank_statements) statements, (select count(*) from $S.trust_reconciliations) recons"
docker exec e2e-postgres psql -U postgres -d app -c "select column_name, data_type from information_schema.columns where table_schema='$S' and table_name='bank_statement_lines' and column_name='suggested_classification'"
done
rm -rf $J
} > $OUT 2>&1
