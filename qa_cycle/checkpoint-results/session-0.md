# Session 0 Results — Stack startup & sanity

**Run**: Cycle 1, 2026-04-11
**Tester**: QA Agent (Playwright MCP)

## Summary
- Steps executed: 10/10
- PASS: 8
- FAIL: 0
- PARTIAL: 2 (pre-test state hygiene — see GAP-S0-01)
- Blockers: 0
- Gaps filed: GAP-S0-01

## Steps

### 0.1–0.3 — Stack running
- **Result**: PASS
- **Evidence**: Stack was already up (Infra Agent handoff). Backend, gateway, frontend, keycloak all running.

### 0.4 — Backend health
- **Result**: PASS
- **Evidence**: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

### 0.5 — Gateway health
- **Result**: PASS
- **Evidence**: `curl http://localhost:8443/actuator/health` → `{"status":"UP"}`

### 0.6 — Frontend health
- **Result**: PASS
- **Evidence**: `curl -o /dev/null -w "%{http_code}" http://localhost:3000/` → `200`

### 0.7 — Keycloak realm
- **Result**: PASS
- **Evidence**: `curl http://localhost:8180/realms/docteams` → returns realm JSON with public key

### 0.8 — Mailpit empty
- **Result**: PASS
- **Evidence**: `curl http://localhost:8025/api/v1/messages` → `"total":0,"unread":0`

### 0.9 — Only padmin in Keycloak
- **Result**: PARTIAL
- **Evidence**: Keycloak `docteams` realm has 5 users:
  - `padmin@docteams.local` (expected)
  - `alice@moyo-dlamini.local`, `bob@thornton-test.local`, `qatest@thornton-verify.local`, `thandi@thornton-test.local` (leftover from previous cycles)
- **Gap**: GAP-S0-01
- **Notes**: Stale users do NOT collide with the `mathebula-test.local` test data created by this run; not a blocker.

### 0.10 — No leftover tenant schemas
- **Result**: PARTIAL
- **Evidence**: `SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'` returns 3 schemas:
  - `tenant_4a171ca30392` (org: thornton-associates)
  - `tenant_62aa7c96ab38` (org: qa-verify-corp)
  - `tenant_555bfc30b94c` (org: moyo-dlamini-attorneys)
- **Gap**: GAP-S0-01 (same issue)
- **Notes**: No `mathebula` schema — no collision. Tenant schemas and org_schema_mapping are consistent.

## Gaps

### GAP-S0-01 — Environment pre-state not pristine (leftover users + tenant schemas)
- **Severity**: LOW
- **Description**: Test plan §Pre-test state requires "Only the platform admin is seeded in Keycloak" and no leftover tenant schemas. Actual state has 4 leftover users and 3 leftover tenant schemas from prior cycles.
- **Impact**: Does not block the current scenario because the Mathebula & Partners test data is uniquely named. But future test runs may flake on conflicting schema names or fail uniqueness checks.
- **Recommendation**: Infra Agent should teardown (`dev-down.sh --clean`) between QA cycles, or the `init-qa-phase` skill should enforce clean DB state.

## Checkpoints
- [x] All four services healthy
- [ ] `padmin@docteams.local` is the only Keycloak user (PARTIAL — 4 stale users remain, non-blocking)
- [x] Mailpit inbox is empty
