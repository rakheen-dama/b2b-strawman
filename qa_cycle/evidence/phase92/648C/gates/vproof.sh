#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 648C)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-648C
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/648C-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (648C-new / 648C-touched classes):"
unzip -l $J/app.jar | grep -o "[^ ]*trustaccounting/\(TrustAccountSection\|TrustAccountService[A-Za-z\$]*\|TrustAccount\|InvestmentBasis\)\.class"
(cd $J && unzip -q -o app.jar '*trustaccounting/TrustAccountSection.class' '*trustaccounting/TrustAccountService.class' '*trustaccounting/TrustAccountService$TrustAccountResponse.class' '*trustaccounting/TrustAccountService$CreateTrustAccountRequest.class' '*trustaccounting/TrustAccount.class' '*db/migration/tenant/V22__*')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== TrustAccountSection.class exists (648C new): $(find $J -name 'TrustAccountSection.class' | wc -l | tr -d ' ') ; contains S86_2/S86_3/S86_4: $(c TrustAccountSection.class S86_2)/$(c TrustAccountSection.class S86_3)/$(c TrustAccountSection.class S86_4)"
echo "== TrustAccountService.class contains 'Investment basis required': $(c TrustAccountService.class 'Investment basis required') ; 'Investment basis not allowed': $(c TrustAccountService.class 'Investment basis not allowed') ; 'investment_basis' (audit detail): $(c TrustAccountService.class investment_basis)"
echo "== TrustAccountService\$TrustAccountResponse.class contains 'investmentBasis': $(c 'TrustAccountService$TrustAccountResponse.class' investmentBasis) ; 'section': $(c 'TrustAccountService$TrustAccountResponse.class' section)"
echo "== TrustAccountService\$CreateTrustAccountRequest.class contains 'investmentBasis': $(c 'TrustAccountService$CreateTrustAccountRequest.class' investmentBasis)"
echo "== TrustAccount.class contains 'investment_basis': $(c TrustAccount.class investment_basis) ; 'getSection': $(c TrustAccount.class getSection)"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V1[5-9][^ ]*\|db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== V22 in jar contains 'investment_basis IS NOT NULL' (F-1 deviation): $(LC_ALL=C grep -a -c 'investment_basis IS NOT NULL' "$(find $J -name 'V22__*' | head -1)")"
echo "== frontend container: 648C strings in .next (excluding cache):"
for s in 'trust-account-basis-select' 'the firm invests surplus trust money' 'invested on a client' 'Choose the investment basis' 'Section 86(4) investment account' 'Invested at the firm' 'trust-account-section'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF \"$s\" /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select column_name, data_type, character_maximum_length, is_nullable from information_schema.columns where table_schema='$S' and table_name='trust_accounts' and column_name='investment_basis'"
docker exec e2e-postgres psql -U postgres -d app -c "select conname, pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='$S' and conname='chk_trust_account_investment_basis'"
docker exec e2e-postgres psql -U postgres -d app -c "select account_name, account_type, investment_basis, is_primary from $S.trust_accounts order by created_at"
done
rm -rf $J
} > $OUT 2>&1
