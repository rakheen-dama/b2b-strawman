#!/usr/bin/env bash
source /tmp/pr1750/lib.sh
echo "G2-login-bare attempt3 FAILED rc=1 (clean .next, same 24 font errors); standalone portal build 22:14 rc=0 0 font errors -> fetch recovered; attempt4 $(ts)" >> $TL
spacing G2-login-bare-attempt4
run_pw G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare-attempt4
echo RETRY3-ALLDONE >> $TL
