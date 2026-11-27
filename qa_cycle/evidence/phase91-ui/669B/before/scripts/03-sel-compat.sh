#!/usr/bin/env bash
# 669B SEL-COMPAT suites (brief §9): `pnpm test:e2e:navigation` and
# `pnpm test:e2e e2e/tests/auth/rbac-capabilities.spec.ts`, unchanged specs, against the running
# e2e mock-auth stack (:3001). Logs + Playwright JSON → /tmp/669B-$PHASE-raw/; a summary
# (expected / passed / failed / flaky / skipped per suite, failing titles) → $RAW/suites.json,
# which 02-assemble.cjs folds into ../../$PHASE/sel-compat.json. Restores the tracked artefacts the
# suites rewrite (frontend/test-results, documentation/screenshots) afterwards.
# Usage (from anywhere): PHASE=before|after bash 03-sel-compat.sh
set -u
PHASE="${PHASE:-before}"
HERE="$(cd "$(dirname "$0")" && pwd)"
W="$(cd "$HERE/../../../../../.." && pwd)"
RAW="/tmp/669B-$PHASE-raw"
mkdir -p "$RAW"
source "$HOME/.nvm/nvm.sh" >/dev/null && nvm use 20 >/dev/null
export SHELL=/bin/bash NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001
P=/opt/homebrew/bin/pnpm
cd "$W/frontend" || exit 1
run() {
  local name="$1"; shift
  PLAYWRIGHT_JSON_OUTPUT_NAME="$RAW/suite-$name.json" "$P" "$@" --reporter=list,json > "$RAW/suite-$name.log" 2>&1
  echo "$name exit $?" | tee -a "$RAW/suite-exits.txt"
}
: > "$RAW/suite-exits.txt"
run navigation test:e2e:navigation
run rbac-capabilities test:e2e e2e/tests/auth/rbac-capabilities.spec.ts
node -e '
const fs = require("fs"); const raw = process.argv[1]; const out = { at: new Date().toISOString(), env: "E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001", suites: {} };
const exits = Object.fromEntries(fs.readFileSync(raw + "/suite-exits.txt", "utf8").trim().split("\n").map((l) => { const [n, , c] = l.split(" "); return [n, +c]; }));
for (const n of ["navigation", "rbac-capabilities"]) {
  const f = raw + "/suite-" + n + ".json"; if (!fs.existsSync(f)) { out.suites[n] = { error: "no json", exit: exits[n] }; continue; }
  const j = JSON.parse(fs.readFileSync(f, "utf8")); const failed = []; const files = new Set();
  const walk = (s, file) => { for (const sp of s.specs || []) { files.add(sp.file || file); for (const t of sp.tests || []) if (t.status === "unexpected") failed.push(`${sp.file}:${sp.line} ${sp.title}`); } for (const c of s.suites || []) walk(c, s.file || file); };
  for (const s of j.suites || []) walk(s, s.file);
  out.suites[n] = { exit: exits[n], command: n === "navigation" ? "pnpm test:e2e:navigation" : "pnpm test:e2e e2e/tests/auth/rbac-capabilities.spec.ts", stats: j.stats, files: [...files], failed };
}
fs.writeFileSync(raw + "/suites.json", JSON.stringify(out)); console.log(JSON.stringify(out));
' "$RAW"
cd "$W" && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null
git -C "$W" status --short
