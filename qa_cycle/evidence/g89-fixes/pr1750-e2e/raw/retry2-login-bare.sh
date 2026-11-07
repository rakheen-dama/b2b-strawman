#!/usr/bin/env bash
# G89-F46: attempt 2 failed in 1.7s replaying the poisoned Turbopack font cache; clear portal/.next (gitignored build output) and run once more
source /tmp/pr1750/lib.sh
echo "G2-login-bare attempt2 FAILED rc=1 in 1.7s (same 24 font 'Module not found' errors, replayed from portal/.next cache); rm -rf portal/.next and attempt3 $(ts)" >> $TL
rm -rf $WT/portal/.next
spacing G2-login-bare-attempt3
run_pw G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare-attempt3
echo RETRY2-ALLDONE >> $TL
