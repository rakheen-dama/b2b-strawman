# Phase 14 — Handoff Notes for Next Agent

## Current State (2026-02-18 ~21:35 SAST)

**Phase 14 is paused.** Resume from **103B**.

### Completed Slices (7/17)
| Slice | PR | Status |
|-------|----|--------|
| 100A | #208 | Merged |
| 100B | #209 | Merged |
| 101A | #210 | Merged |
| 101B | #211 | Merged |
| 102A | #212 | Merged |
| 102B | #213 | Merged |
| 103A | #214 | Merged |

### Remaining (10 slices)
103B → 104A → 104B → 105A → 105B → 106A → 106B → 107A → 107B → 108A

### Resume Command
```bash
./scripts/run-phase.sh 14 103B
```

## Build Optimizations Applied This Session

1. **Tiered build strategy** in `epic_v2` skill:
   - Tier 1: `./mvnw compile test-compile -q` (~30s) — compile check
   - Tier 2: `./mvnw test -Dtest={NEW_TESTS} -q` (~2-3min) — targeted tests
   - Tier 3: `./mvnw clean verify -q` (~10-15min) — full verify once before commit

2. **Test log level set to ERROR** in `application-test.yml`:
   - Suppresses Flyway/Hibernate INFO/DEBUG noise (was 2-4MB per build)
   - To debug failures, pass `-Dlogging.level.root=INFO` on that specific run

## Known Issues & Fixes

### 1. API 500 errors kill agents mid-execution
- Happened on 101B — agent died during review phase
- **Fix**: Check `gh pr list --state open`, review with sonnet agent, merge, mark Done, resume

### 2. Done marker inconsistency
- Phase script checks the **Implementation Order table** rows for `**Done**`
- Agents sometimes mark Done in other tables but NOT the Implementation Order table
- **Fix**: `grep '^\| \*\*{SLICE}\*\*' tasks/phase14-customer-compliance-lifecycle.md` — if last column is empty, add `**Done** (PR #{N})`

### 3. Stale processes from previous runs
- If the phase script dies mid-slice, the `claude -p` child process becomes an orphan and keeps running
- Before restarting, **always check** `ps aux | grep "claude -p" | grep -v grep`
- Kill stale agents before launching a new run, otherwise two agents fight over the same worktree
- 103A took ~83 min (vs ~45 min typical) partly because a stale process from a previous run was competing

### 4. Flyway "Connection is closed" during tests
- Transient Testcontainers + HikariCP issue — connections time out under pool pressure when many tenant schemas are provisioned
- Agent retries the build and it passes on the next attempt
- Not a code bug — just test infrastructure flakiness

### 5. Monitoring a running agent
```bash
# Is agent alive?
ps aux | grep "claude -p" | grep -v grep

# Build progress?
wc -l /tmp/mvn-epic-{SLICE}.log

# Git progress?
git -C /Users/rakheendama/Projects/2026/worktree-epic-{SLICE} log --oneline -3
git -C /Users/rakheendama/Projects/2026/worktree-epic-{SLICE} status --short

# Phase log?
tail -20 tasks/.phase-14-progress.log
```

## What 103A Delivered
- **New package**: `compliance/` (CompliancePackDefinition, CompliancePackSeeder, RetentionPolicy, RetentionPolicyRepository)
- **3 shipped packs**: `generic-onboarding`, `sa-fica-company`, `sa-fica-individual` (JSON in `compliance-packs/`)
- **Integration**: CompliancePackSeeder called during tenant provisioning (TenantProvisioningService)
- **Tests**: CompliancePackSeederTest, updated FieldPackSeederIntegrationTest and TenantProvisioningServiceTest
- **+1014 / -13 lines**

## Task File
`tasks/phase14-customer-compliance-lifecycle.md`
