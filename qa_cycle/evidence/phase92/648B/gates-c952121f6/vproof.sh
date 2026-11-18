#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 648B @ c952121f6; adapted from 648E gates-6b4615074/vproof.sh)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-648B
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/648B-jar-c9
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (648B investment classes):"
unzip -l $J/app.jar | grep -o "[^ ]*trustaccounting/investment/\(TrustInvestmentInterest\|InterestPresentation\|TrustInvestmentInterestRepository\)\.class"
(cd $J && unzip -q -o app.jar '*investment/TrustInvestmentService.class' '*reconciliation/StatementLineClassification.class' '*reconciliation/TrustReconciliationService.class' '*report/InvestmentRegisterQuery.class')
c() { local f; f="$(find $J -name "$1" | head -1)"; [ -z "$f" ] && { echo "MISSING($1)"; return; }; LC_ALL=C grep -a -c "$2" "$f"; }
echo "== StatementLineClassification.class: INVESTMENT_INTEREST $(c StatementLineClassification.class INVESTMENT_INTEREST)"
echo "== TrustInvestmentService.class: FUND_INV_SHARE $(c TrustInvestmentService.class FUND_INV_SHARE) ; INV_INTEREST $(c TrustInvestmentService.class INV_INTEREST) ; interest_recorded $(c TrustInvestmentService.class interest_recorded)"
echo "== TrustReconciliationService.class: INVESTMENT_INTEREST $(c TrustReconciliationService.class INVESTMENT_INTEREST)"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 648B strings in .next (server + static):"
for s in 'Bank advice — no statement line' 'How did the bank credit it?' 'as the bank shows it' 'interest-line-select' 'Section 86(4) investment interest' 'investment-interest-fund-note' 'Net of the Fund' 'Gross interest' 'interest-bank-advice-note'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF \"$s\" /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select count(*) as interest_earned_cols from information_schema.columns where table_schema='$S' and table_name='trust_investments' and column_name='interest_earned'"
docker exec e2e-postgres psql -U postgres -d app -c "select count(*) as interest_table from information_schema.tables where table_schema='$S' and table_name='trust_investment_interest'"
done
rm -rf $J
} > $OUT 2>&1
