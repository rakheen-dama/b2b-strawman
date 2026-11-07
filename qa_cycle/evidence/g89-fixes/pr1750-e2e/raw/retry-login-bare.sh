#!/usr/bin/env bash
# G89-F46 retry (once) of the optional login-bare suite on stack G2
source /tmp/pr1750/lib.sh
echo "G2-login-bare attempt1 FAILED rc=1: G89-F46 portal webServer next build next/font/google 'Module not found' (0 tests ran); retrying once $(ts)" >> $TL
spacing G2-login-bare-retry
run_pw G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare-retry
echo RETRY-ALLDONE >> $TL
