# QA Cycle Handoff — Slice 2 production-readiness sweep

Last updated: 2026-04-29 (after slice 1 close).

## TL;DR

`main` is at `8263d1c0`. The full legal ZA 90-day lifecycle scenario walks PASS end-to-end (isolation 36/36, demo-ready). Slice 1 of the production-readiness sweep is closed: GAP-L-93, GAP-L-96, GAP-L-99, GAP-L-100, GAP-L-101, TERM-CYCLE57, OBS-PortalContactBucketedAsSystem, OBS-PortalInvoicePaidNullActorId all VERIFIED on main. Slice 2 picks up the next layer: dependabot security backlog + KC password-drift root-cause + cycle-1 carry-forward LOW gaps + a few production-hygiene tasks. Detailed scope below.

`qa_cycle/status.md` is the canonical state file; this HANDOFF.md is the strategic overview. Read both.

## How to resume

For Slice 2 work, the existing `/qa-cycle-kc` skill is the wrong shape — that runs a per-day lifecycle walk loop. Slice 2 is project work, not QA cycling. Use the standard pattern:

1. Read this file fully.
2. Read `qa_cycle/status.md` (the active tracker).
3. Read `CLAUDE.md` (root) + `backend/CLAUDE.md` + `frontend/CLAUDE.md` for conventions.
4. Run `bash compose/scripts/svc.sh status` to confirm the dev stack is up.
5. Pick a slice-2 sub-slice (priority order below) and brief a Product/Dev pipeline like slice 1 did.

If you want to resume the QA lifecycle walk instead (e.g., regression run after slice 2 lands), invoke `/qa-cycle-kc qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` — it handles the per-day loop; Day 90 is the final scripted day.

## What just landed (slice 1 — closed)

PR #1213 (squash `c8e623ba`) → main, plus retest evidence PR #1214 (squash `8263d1c0`):

| Item | Severity | Type | Verified on main |
|---|---|---|---|
| GAP-L-93 | LOW | Closure dialog Step-2 "Generate Statement of Account" auto-attach checkbox | ✓ browser |
| GAP-L-96 | LOW | `retention_policies` MATTER row seeded on first close, atomic INSERT...ON CONFLICT DO NOTHING | ✓ code-level (forward-only) |
| GAP-L-99 | MEDIUM | `POST /internal/portal/digest/run-weekly` (X-API-KEY gated) — manual digest trigger | ✓ already merged in cycle 54 |
| GAP-L-100 | MEDIUM | Portal `/activity` Firm-actions allow-list + humaniser | ✓ already merged in cycle 56 |
| GAP-L-101 | MEDIUM | RetentionCard 3-state surface (pre-clock / unconfigured / active) | ✓ browser |
| TERM-CYCLE57 | LOW | Firm-side terminology labels (Project Health → Matter Health, Customer column → Client, dialog placeholders, breadcrumbs) — URL slugs UNCHANGED per scoping decision | ✓ browser |
| OBS-Cycle55-PortalContactBucketedAsSystem | LOW | ActivityService batch-resolves PORTAL_CONTACT actor names alongside USERs | ✓ browser |
| OBS-Cycle55-PortalInvoicePaidNullActorId | LOW | PaymentReconciliationService resolves actor_id via PRIMARY > BILLING > GENERAL on portal.invoice.paid | ✓ code-level (forward-only) |
| OBS-Cycle55-KCFormDoubleSubmit | LOW | Reclassified WONT_FIX-tooling (Playwright/Radix interaction quirk, not a real-user defect) | n/a |

Plus the Day 60–90 lifecycle walk results (cycles 51 → 57) merged earlier this rolling session.

Defensive validation added during CR review:
- `OrgSettings.legalMatterRetentionYears` now `@Max(100)` + setter range check
- `MatterClosureService.ensureMatterRetentionPolicy` uses `Math.multiplyExact(retentionYears, 365)` to guard arithmetic overflow
- Idempotent retention-policy seed via single SQL statement (no race window)

## Slice 2 scope

In rough priority order. Mix and match into sub-slices that match your time budget.

### Sub-slice 2A — dependabot security backlog (HIGH priority — production blocker)

GitHub reports **92 vulnerabilities on `main`'s default branch — 2 critical, 37 high, 47 moderate, 6 low**. Surfaces in every push warning. This is the single biggest blocker between current state and "would survive a security review."

Tasks:
1. Enumerate the 2 critical + 37 high — categorize by ecosystem (npm vs Maven vs Docker base images vs GitHub Actions)
2. Triage each: false positive vs upgradable vs blocking transitive vs requires app code change
3. Group fixes into PRs by ecosystem (one PR per ecosystem usually maps to one Dependabot batch)
4. Run full backend + frontend test suites after each major upgrade
5. Triage the 47 moderates as a follow-up

Effort: 1–3 days depending on how many require code adaptation (often the npm + Spring Boot upgrades cascade).

Entry point: `gh api repos/rakheen-dama/b2b-strawman/dependabot/alerts --paginate | jq '.[] | select(.state == "open" and (.security_advisory.severity == "critical" or .security_advisory.severity == "high"))'` to enumerate them. Or use the GitHub UI at the repo's security tab.

### Sub-slice 2B — KC password-drift root cause (3 hrs)

The recurring symptom: `bob@mathebula-test.local` documented password is rejected at the start of every long-gap session. Cycles 55, 57, 58 all hit this. Workaround was to rotate via KC admin API and hold the new value out-of-band. Production cannot live with this — it's an auth-management gap.

What I found investigating in slice 1:

- `compose/keycloak/realm-export.json` (120 lines) defines realm shape but no users / no password policy. Bob is created via the onboarding flow, not pre-seeded.
- KC is backed by Postgres in the same container (`KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak`); passwords persist across restarts unless `dev-down.sh --clean` wipes the volume.
- `start-dev --import-realm` is idempotent — won't reset existing user passwords.
- Two password-setting code paths exist:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/KeycloakProvisioningClient.java:264` — `temporary: false` (permanent, from invite flow)
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/keycloak/KeycloakAdminClient.java:407` — `temporary: true` (admin tool, forces reset on next login)

Hypothesis (~80% confident): a code path or test/seed harness is calling `KeycloakAdminClient.resetPasswordTemp(...)` against Bob between sessions. Could be a test cleanup running against the dev KC instead of test KC, an admin script that targets the wrong user, or a scheduled retest cycle.

Hardening — three layers:

1. **Bootstrap-time idempotent reset** (S, ~30 min). Extend `compose/scripts/keycloak-bootstrap.sh` with: "if user `bob@mathebula-test.local` exists with `temporary=true`, reset to documented password and clear the temporary flag". Same for Thandi/Carol. Documented password lives in `compose/scripts/.kc-test-passwords.env` (gitignored). Solves symptom, not cause.
2. **Identify and gate the drift source** (M, ~1–2 hr). Add `bash compose/scripts/kc-audit.sh bob@mathebula-test.local --last 24h` to surface password-reset events with timestamp + actor. Run it the moment Bob's password is rejected next time, trace to the offending code path, gate it behind a non-dev profile or feature flag. This is the actual fix.
3. **Production-readiness password lifecycle** (M, ~1–2 hr separate). Configure realm-export.json with explicit `passwordPolicy` (length, complexity, expiry) matching production intent. Document dev-vs-prod distinction in `compose/keycloak/README.md`. Add prod-checklist item: "rotate platform admin password on first deploy".

Recommendation: do layers 1 + 2 in slice 2 (~3 hrs). Layer 3 is more like slice 3 hardening.

### Sub-slice 2C — cycle-1 carry-forward LOW gaps (~2 weeks for full sweep)

22 LOW items deferred from cycle 1, list in `qa_cycle/_archive_2026-04-24_pre-verify/status.md`. Two of the original 22 were already resolved during the verify run (GAP-L-25 SECTION_86 trust account — PR #1123; GAP-L-44 PackReconciliationRunner — verified by build state). Net: **~19 active items**.

Recommended grouping:

| Group | Items | Sub-effort |
|---|---|---|
| **2C-1 onboarding+auth polish** | L-04 (KC end_session confirmation), L-20 (dashboard auto-redirect) | S–M |
| **2C-2 vertical terminology + branding** | L-26 (brand-colour CSS var), L-27 (VAT tax label), L-31 (clients empty-state copy), DOC-NEW-OBS-OverviewTabCustomerLabel-cycle58 | S |
| **2C-3 matter detail rework** (largest) | L-37 (field-group gating per template), L-38 (20-tab consolidation + dedup Disbursements), L-39 (matter-from-client pre-fill), L-40 (Add Portal Contact dialog firm-side), L-46 (FICA/KYC/Compliance status tile on Overview) | M–L |
| **2C-4 info-requests + portal polish** | L-29 (conflict-check dropdowns empty), L-30 (KYC integration unconfigured), L-41 (Due Date field on Create IR + DB column), L-45 (per-item Download/View on info-request detail), L-47 (portal read-model parent-status lag), L-51 ("Send for Acceptance" email subject), L-53 (Liquidation/Distribution template), L-54 (Beneficial Owners on TRUST clients), L-55 (portal trust ProblemDetail title consistency), P-03 (portal Matters tab "No projects yet"), P-05 (portal requests UI polish), P-07 (portal /documents 404), OBS-L-27 (portal /accept iframe LocalStack URL CORS) | M–L |

Pure UX polish for most, but a few are POPIA/compliance-relevant: L-30 KYC config, L-46 status tile, L-54 TRUST beneficial owners.

### Sub-slice 2D — production hygiene (separate scope)

Not in slice 2's "must" list but production-blocking:

- **Security review** — SAST/DAST/secret scan beyond Qodana (Qodana is a quality scanner, not security)
- **Load/perf baseline** — single-tenant happy path verified; multi-tenant concurrent load isn't
- **Observability** — structured logs ship, but metrics/traces/alerts haven't been validated end-to-end
- **Disaster recovery** — backup/restore drill never run
- **Ops runbooks** — for incidents like the KC password drift
- **Tenant offboarding / data deletion** — POPIA/GDPR right-to-erasure flow not in the scenario

Recommendation: scope these as slice 3 after slice 2 closes.

### Sub-slice 2E — stub integrations (out-of-scope per CLAUDE.md, but production needs them)

- Payment integration is a stub (no real PSP — Stripe/Yoco/Peach)
- KYC provider integration is a stub (no real Onfido/Veriff/Smile ID)

Production needs either real integrations or explicit production-gating on the stub endpoints. Decide whether this is a slice 4+ commitment or out of scope entirely.

## Tighter slice-2 alternative

If you want a focused 1-week slice that clears the most production-blocking issues:

- All of 2A (dependabot critical+high)
- 2B layers 1 + 2 (KC drift root-cause)
- Top 4 LOW gaps from 2C: **L-30 KYC config**, **L-37 field-group gating**, **L-46 status tile**, **L-54 TRUST beneficial owners** (the four POPIA/compliance-relevant ones)

Defer the rest of 2C to slice 3.

## Critical gotchas (read before doing any work)

### Workflow conventions

- **No SQL shortcuts in QA**. All operations through REST API or browser UI. Mailpit (`GET /api/v1/messages`) and `POST /internal/portal/digest/run-weekly` (X-API-KEY) are the only legitimate REST surfaces for QA. Read-only `SELECT` allowed for diagnostics, never `INSERT/UPDATE/DELETE`.
- **No workarounds in QA cycles**. Real product bugs become SPEC_READY → Dev fix → PR → retest. WONT_FIX is for tooling/out-of-scope only.
- **Branch convention**: `bugfix_cycle_2026-04-26-{day-or-slice}` for parent cycle branches; `fix/{GAP-ID}` for intra-cycle Dev branches that PR into the parent. Each Dev fix → its own intra-cycle PR (squash-merged into the parent).
- **Regression cadence**: full regression at slice→main rollup, NOT per intra-cycle merge. Per-spec verification gate is enough at the intra-cycle layer (`pnpm run lint`, `pnpm run build`, `pnpm test`, `./mvnw verify` per the affected stack).
- **One fix per PR** at the cycle-to-main layer. Bundling is acceptable when fixes touch the same file (e.g. GAP-L-93 + GAP-L-96 both touched MatterClosureService).
- **CodeRabbit review is mandatory** — all actionable findings addressed (or declined with rationale) before merge.
- **Identifier convention**: short `<prefix>-…` form in narrative status.md / day-XX.md (e.g. `cc390c4f-…`, `f3f74a9d-…`). Full UUIDs/filenames only in side-evidence files. Don't expand ellipses across narrative — every prior cycle's tracker uses the short form.

### Auth quirks (will bite you on first KC login attempt)

- **Bob's password documented across day-00/02/03/45.md is unreliable**. Try it; if rejected, rotate via KC admin API. Do NOT commit the new value (slice 2B fixes the root cause).
- **KC form double-submit Playwright quirk**. The KC email-step + password-step Sign In button needs a `form.submit()` JS call instead of a plain click when driven from Playwright MCP. Reclassified WONT_FIX-tooling — affects automation only, not real users.
- **Playwright MCP browser singleton lock**. If the dev stack has been running multiple Claude sessions, the Playwright user-data-dir lock at `/Users/rakheendama/Library/Caches/ms-playwright/mcp-chrome-5d273ba/SingletonLock` may be stale. Clean with `rm -f $LOCK_FILE` if `browser_navigate` returns "Browser is already in use".

### Stack quirks

- **Postgres host**: `b2mash.local:5432` for direct host-side connections. From within the dev stack containers it's `postgres:5432`.
- **`NODE_OPTIONS=--openssl-legacy-provider`** is set in the user's shell. Clear with `NODE_OPTIONS=""` before `pnpm` commands or Next.js dies.
- **pnpm path**: `/opt/homebrew/bin/pnpm`. Use `SHELL=/bin/bash` prefix when invoking via Bash tool (zoxide alias breaks `cd`).
- **Maven wrapper**: `./backend/mvnw`. Backend uses embedded Postgres (zonky) for tests — no Docker required for `./mvnw test`.
- **Backend full test suite is ~17 min**. Plan PR-merge cadence around it.
- **Qodana is ~6 min**. Backend+Qodana on a Java change is ~17 min; QA-only PRs are ~6 min.

## Where state lives

| File | Purpose |
|---|---|
| `qa_cycle/status.md` | Active tracker — every gap row, severity, status, owner, evidence pointers. **Single source of truth.** |
| `qa_cycle/HANDOFF.md` | This file — strategic overview, slice scope, gotchas. |
| `qa_cycle/fix-specs/{GAP-ID}.md` | Per-gap fix specifications (Product → Dev contract). |
| `qa_cycle/checkpoint-results/day-{NN}.md` + `cycle{N}-day{NN}-*` | QA evidence per day per cycle. |
| `qa_cycle/_archive_*/` | Frozen state from prior cycle endings. cycle-1 lives in `_archive_2026-04-21_legal-full-lifecycle/`. |
| `CLAUDE.md` (root) | Project conventions, tech stack, dev commands. |
| `backend/CLAUDE.md` | Spring Boot 4 / Hibernate 7 / Java 25 conventions; controller discipline; test taxonomy; multitenancy gotchas. |
| `frontend/CLAUDE.md` | Next.js 16 / React 19 / Shadcn conventions. |
| `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` | The 90-day lifecycle scenario that's been walked end-to-end. |
| `architecture/ARCHITECTURE.md` + ADRs | Technical architecture, ADRs (ADR-247 disbursements, ADR-248 closure, ADR-249 retention, ADR-250 SoA, ADR-251 acceptance-eligible). |

## Stack state (as of this writeup)

`bash compose/scripts/svc.sh status`:
- `backend:8080` PID 68327 — fresh JVM serving `main 8263d1c0`. Healthy.
- `gateway:8443` PID 71426 (ext) — healthy.
- `frontend:3000` PID 5771 — healthy.
- `portal:3002` PID 5677 — healthy.

Restart any of them with `bash compose/scripts/svc.sh restart {service}`. Backend / gateway need restart after Java changes; frontend / portal use HMR.

If services are down: `bash compose/scripts/dev-up.sh` (Docker infra) → `bash compose/scripts/keycloak-bootstrap.sh` (first-time only) → `bash compose/scripts/svc.sh start all`.

## Tenant + user context (test data on the dev stack)

- **Tenant schema**: `tenant_5039f2d497cf` (Mathebula & Partners — `mathebula-partners`)
- **Vertical**: legal-za
- **Users** (Keycloak):
  - Thandi (owner) — `thandi@mathebula-test.local`
  - Bob (admin) — `bob@mathebula-test.local`
  - Carol (member) — `carol@mathebula-test.local`
  - Sipho portal contact — `sipho.portal@example.com` (magic-link only, not a KC user)
  - Platform admin — `padmin@docteams.local` / `password`
- **Customers**:
  - Sipho Dlamini (INDIVIDUAL, RAF matter `cc390c4f-…` CLOSED, portal_contact `f3f74a9d-…`)
  - Moroka Family Trust (TRUST, EST-2026-002 matter `4e87b24f-…`, R 25 000 trust deposit)
- **Active product data**: 13 PORTAL_CONTACT audit_events on Sipho's matter; 36/36 isolation invariant intact between Sipho and Moroka.

## Re-entry checklist for the new agent

Before touching code:

- [ ] `git log -1 --oneline main` → confirm it's at `8263d1c0` (or later if more has landed)
- [ ] `bash compose/scripts/svc.sh status` → all 4 services healthy
- [ ] `gh pr list --base main --state open` → no surprise in-flight PRs
- [ ] Read `qa_cycle/status.md` (full file), check Cycle Count + ALL_DAYS_COMPLETE flag
- [ ] Read `CLAUDE.md` + `backend/CLAUDE.md` + `frontend/CLAUDE.md`
- [ ] Decide which slice 2 sub-slice to take first (recommend 2A dependabot if pure security focus, 2B if KC pain is blocking other work, 2C if cycle-1 polish is the goal)
- [ ] Cut a fresh branch: `git checkout -b bugfix_slice2-{sub-slice-id}` from `main`
- [ ] Run `bash compose/scripts/svc.sh status` again before any browser-driven retest
- [ ] If Bob's password is rejected on first KC login, rotate via admin API (do not commit the value); start with sub-slice 2B same session if you have time

## Anti-patterns to avoid (lessons from slice 1)

- **Don't `git add -A` on the cycle branch**. The repo accumulates scratch artefacts in `backend/.playwright-mcp/`, `frontend/test-results/`, and `.claude/scheduled_tasks.lock`. Add files explicitly. (`.claude/scheduled_tasks.lock` is now gitignored as of slice 1.)
- **Don't commit a live KC password to any file**. Out-of-band only. Cycle-1's `SecureP@ss2` literal in archives is grandfathered; new commits should not introduce literals.
- **Don't expand the `<prefix>-…` UUID convention to full UUIDs in narrative**. CodeRabbit will flag it as a "audit traceability" concern, but it conflicts with established style across 50+ cycles. Decline with rationale.
- **Don't bundle multiple unrelated fixes into one PR**. The exception is fixes that genuinely touch the same file (GAP-L-93 + GAP-L-96 → MatterClosureService.java is the precedent).
- **Don't dispatch parallel Dev agents on overlapping files**. Check spec scope before spawning. Frontend dialog renames + RetentionCard rewrite + ActivityService change can run in parallel; two backends both touching MatterClosureService cannot.
- **Don't accept CodeRabbit's literal fix without verifying**. Several CR findings on slice 1 were false positives (hallucinated method names, suggested overwriting raw tool output, claimed wording that wasn't in the file). Verify each finding against the actual code before applying.
- **Don't run regression mid-slice**. The PostToolUse hook fires after every PR merge; per `feedback_qa_cycle_regression_cadence` the cadence is full regression at slice→main rollup, not per intra-cycle merge.

## Post-slice-1 follow-up notes (informational, not active GAPs)

Logged during slice 1 retest, not blocking:

- **DOC-NEW-OBS-OverviewTabCustomerLabel-cycle58** — `frontend/components/projects/overview-tab.tsx:241` still hardcodes `Customer: {customerName}` on the matter detail card-band. Out of TERM-CYCLE57 §1–§6 scope; bundle into 2C-2 vertical terminology sub-slice.
- **OBS-Cycle55-PortalContactBucketedAsSystem** has a follow-up: portal `/activity` Mine tab still uses second-person "You did X" labels (correct per spec §5), but the Firm-actions tab now resolves portal-contact names. The two tabs use different humanization strategies, by design.
- **GAP-L-93/96/OBS-PaymentRec are forward-looking by design**. The verified evidence is integration-test only, since the only closeable matter (RAF) was closed pre-merge. Next live close on the dev stack will exercise the new code paths visually.
