# Phase 92 — baseline statement (task 645.1, architecture §16.1.6)

Recorded on `main` **251100d54** (detached worktree `baseline-p92-main`), 2026-11-16, before any 645A code.
The exit gate (652F) compares against these numbers, not memory.

| Gate | Command | Result |
|---|---|---|
| BE-VERIFY | `cd backend && caffeinate -is ./mvnw verify -q` (see note 1) | **exit 0**, 20 min — **6 871 `<testcase>`** elements (surefire; no failsafe), **26 skipped**, 0 failures / errors |
| FE-GATE | `cd frontend && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` (node v20.11.1) | all exit 0 — vitest **478 files, 4 357 passed, 2 skipped (4 359)** |
| PORTAL-GATE | `cd portal && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | all exit 0 — vitest **51 files, 284 passed (284)** |
| E2E-LEGAL lifecycle | fresh stack (`e2e-down.sh && e2e-up.sh`), `PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle --update-snapshots=missing` | **105 passed / 105** (3.5 min) |
| E2E-LEGAL depth-ii | its own fresh stack, `PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii --update-snapshots=missing` | **40 passed, 4 skipped** (2.1 min) |
| Demo tenant `cashbook − stored card total` | — | **DEFERRED** (note 3) — expected R27 000,00 per `.arch-context-phase92.md` §0.2, **not measured** |

Notes
1. `scripts/verify.sh` was not used because its preflight (`verify-preflight.sh`) SIGKILLs any b2b-strawman Maven JVM older than 90 min, which on this machine were the developer's live Keycloak dev-stack `spring-boot:run` backend and gateway (2.5 days old). The same command `verify.sh` execs (`caffeinate -is ./mvnw verify`) was run directly after confirming no Surefire/verify JVM was live.
2. The legal Playwright configs default `baseURL` to `http://localhost:3000` (the Keycloak dev stack); `PLAYWRIGHT_BASE_URL=http://localhost:3001` is required against the mock-auth stack. A first attempt without it failed 13 + 4 tests at login (redirect to Keycloak :8180) — invocation error, discarded. Visual baselines are untracked in git, so this run wrote them (`--update-snapshots=missing`); they were archived and reused to diff the 645A run against `main`.
3. Demo provisioning (`POST /api/platform-admin/demo/provision`) requires Keycloak Admin (`DemoProvisionService.java:100` refuses without it), so it cannot run on the mock-auth E2E stack; the Keycloak dev stack holds no demo tenant, and provisioning one there needs platform-admin credentials and creates a Keycloak user and organisation in the developer's environment. Not done without authorisation. To be measured before 646A (which fixes the seeder) — or by 649A, which pins the seeded imbalance in a test.

Raw logs (machine-local): `/tmp/mvn-p92-baseline.log`, `/tmp/p92-base-fe-*.log`, `/tmp/p92-base-portal-*.log`, `/tmp/p92-baseline-e2e/{lifecycle2,depth-ii2}.log`.
