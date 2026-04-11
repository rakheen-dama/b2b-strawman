# QA Cycle KC 2026-04-11 — Handoff for Next Session

Written at end-of-session 2026-04-11 after PR #1004 merged to `main`.
This file is a lightweight context dump so a fresh Claude session can pick up
the remaining work without re-reading the entire session history.

## Current state

- **Branch**: `bugfix_cycle_kc_2026-04-11` was squash-merged as commit `6d209c42` and the remote branch is deleted. You are starting from `main`.
- **Scenario file**: `qa/testplan/legal-onboarding-keycloak.md` — 7 sessions, Keycloak dev stack (not mock auth).
- **Status tracker**: `qa_cycle/status.md` — authoritative state. Read this before making decisions.
- **Auth mode**: Keycloak OIDC via gateway BFF. See `.claude/skills/qa-cycle-kc/SKILL.md` for the full workflow.

## What shipped in PR #1004 (don't re-do this work)

11 HIGH/MED items VERIFIED and merged:

| ID | Description |
|---|---|
| GAP-S4-01 | Trust account creation dialog |
| GAP-S4-02 | FICA TRUST variant pack |
| GAP-S4-05 | Trust-accounting dashboard header CTAs |
| GAP-S4-06 | `ClientLedgerService` empty ledger response + 404 for unknown customer |
| GAP-S5-01 | Contingency fee model (backend + frontend, end-to-end verified) |
| GAP-S5-02 | Court date NPE on null `customer_id` |
| GAP-S5-03 | `ProjectTemplateService` populates `customer_id` |
| GAP-S5-04 | Adverse-party customer dropdown response-shape fix |
| GAP-S5-05 | Legacy FICA pack deactivated (V92 + V93), typed-match filter in `ChecklistInstantiationService` |
| GAP-S5-06 | Proposal/retainer customer filter includes PROSPECT/ONBOARDING |
| Test infra | `TestcontainersConfiguration.java` — JVM-wide embedded-pg singleton + shutdown hook + `minimum-idle=0` on all Hikari pools. **Do not revert this — it fixes a 14-error build regression.** |

Sessions: 0, 1, 2, 4, 5 are **GREEN** (or PASS_WITH_NOTES). Sessions 3 and 6 are PARTIAL.

## What's left — 13 SPEC_READY items

### Medium severity (real blockers) — 2

| ID | Description | Effort | File |
|---|---|---|---|
| **GAP-S3-03** | FICA checklist blocks PROSPECT → ACTIVE via UI. Items require document upload, and lifecycle gates ACTIVE on Country+TaxNumber. Combined, no customer can reach ACTIVE through the UI. **This is the last real workflow blocker — fix this and Session 3 goes GREEN.** | M (~1.5 hr) | `qa_cycle/fix-specs/GAP-S3-03.md` |
| **GAP-S3-05** | `/projects/new?customerId=…` crashes — `new` routed as `[projectId]` detail param. Low traffic (dialog path works), but a dead URL. | M (~30 min) | `qa_cycle/fix-specs/GAP-S3-05.md` |

### Low-Medium — 1

| GAP-S3-04 | Matter from legal-za template has no custom field groups attached. Borderline 2hr (migration + seeder + service). | M (~1 hr) | `qa_cycle/fix-specs/GAP-S3-04.md` |

### Terminology rollup — 7 items, 1 batched PR

All covered by `qa_cycle/fix-specs/GAP-TERMINOLOGY-ROLLUP.md`:

- **GAP-S1-02** — landing "Kazi" vs emails/sidebar "DocTeams"
- **GAP-S2-02** — Settings sidebar + pages legal terminology
- **GAP-S2-04** — Dashboard "Getting started with DocTeams" helper
- **GAP-S3-02** — Clients empty state, "Back to Customers" link, "Customer Readiness" widget
- **GAP-S3-06** — Engagement Letters "New Proposal" button + dialog title
- **GAP-S6-01** — global sidebar logo, browser tab title, helper card, matter tab
- **GAP-S6-04** — Fee Notes body copy "invoice"/"project"

Effort: S (~30 min sweep — mostly wrapping strings in `t()` from the existing terminology map in `frontend/lib/terminology/terminology-map.ts`).

### Low-severity polish — 3

| ID | Description | Effort | File |
|---|---|---|---|
| GAP-S2-03 | Rate dialog defaults to Billing on Cost Rates tab | S (~15 min) | `qa_cycle/fix-specs/GAP-S2-03.md` |
| GAP-S6-05 | Fee Notes KPIs show $0.00 instead of ZAR (hardcoded USD) | S (~15 min) | `qa_cycle/fix-specs/GAP-S6-05.md` |
| GAP-S6-06 | Automation "Matter Onboarding Reminder" fails — `ORG_ADMINS` alias missing | S (~15 min) | `qa_cycle/fix-specs/GAP-S6-06.md` |

### Total: ~3–4 hr dev + 1 QA re-verification pass

## WONT_FIX / out of scope (don't re-triage)

GAP-S0-01 (infra cleanup), GAP-S1-01 + GAP-S3-01 (MCP automation friction), GAP-S4-03 (admin sidebar — needs product decision), GAP-S4-04 (conflict-check timing), GAP-S6-02 (activity-feed-from-audit projection), GAP-S6-03 (audit log UI — 1-2 day feature). GAP-S2-01 is NOT_A_BUG (no plan tiers by design).

## How to resume in a new session

Two viable paths depending on appetite:

### Path A — minimum closure (highest value, ~2 hr)
Fix **GAP-S3-03 only**. This promotes Session 3 from PARTIAL → GREEN and gives you the complete legal-za lifecycle working via UI top-to-bottom. Leave the LOW terminology/polish queue for a follow-up.

### Path B — full drain (close the cycle, ~4 hr)
Fix GAP-S3-03, GAP-S3-05, GAP-S3-04, the terminology rollup batch, and the 3 LOW polish items. After that, Sessions 0–6 all green except for WONT_FIX items, and the bugfix cycle is complete.

### Exact command to resume

```bash
# From /Users/rakheendama/Projects/2026/b2b-strawman on main
git checkout -b bugfix_cycle_kc_2026-04-12

# Then invoke the skill:
# /qa-cycle-kc qa/testplan/legal-onboarding-keycloak.md --resume
```

Note: the `--resume` flag is for an existing cycle on the same branch. Since the previous branch was deleted on merge, you're starting a new branch. You can drop `--resume` OR keep it — the skill will read `qa_cycle/status.md` either way and the Tracker already shows what's remaining.

Before starting dev work, re-read `qa_cycle/status.md` so the Tracker is your source of truth. The in-session Log entries tell the full cycle history.

## Dev stack startup (if needed)

```bash
# Infra (postgres, keycloak, mailpit, localstack)
bash compose/scripts/dev-up.sh

# Services (backend, gateway, frontend, portal — background, PID-tracked)
bash compose/scripts/svc.sh start all
bash compose/scripts/svc.sh status    # health check

# Test credentials (firm owner, created in Session 1):
# Thandi  thandi@mathebula-partners.co.za  SecureP@ss1  (owner)
# Bob     bob@mathebula-partners.co.za     (set in Session 2 invite)     (admin)
# Carol   carol@mathebula-partners.co.za   (set in Session 2 invite)     (member)
# Platform admin: padmin@docteams.local / password
# Org slug: mathebula-partners
```

Frontend runs on `http://localhost:3000` in keycloak mode. Login flow is OIDC redirect → KC login form → redirect back. See `frontend/e2e/fixtures/keycloak-auth.ts` for Playwright helpers if QA uses MCP.

## Key lessons from this session to carry forward

1. **Never dismiss test failures as "environmental"** — see feedback memory `feedback_test_failures_not_environmental.md`. Trace the Caused-by chain to the actual root cause. "Passes in isolation" is order-sensitivity, not clean.
2. **`TestcontainersConfiguration.java` singleton is load-bearing** — do not revert to `@Bean` scoping. If you see `Failed to load ApplicationContext` with `initdb` or `too many clients already` in the cause chain, run `ps aux | grep postgres` first and clean up orphans.
3. **CodeRabbit reviews are mandatory gate** — address every in-scope finding before merge. Existing memory: `feedback_coderabbit_reviews.md`. Out-of-scope findings (things that came from an upstream squash-merged PR) can be deferred to a separate PR and acknowledged in the review reply.
4. **Batch Dev turns when safe** — the 5-fix Cycle 5 Dev batch worked well because each fix was < 30 min and touched different files. Use sequential-but-batched Dev agents with `isolation: "worktree"` to avoid merge conflicts.
5. **Stop the dev stack before running `mvn verify`** — even with the singleton fix, having backend/gateway/frontend/portal all running locally competes for file descriptors and CPU. `bash compose/scripts/svc.sh stop all` first, test, then restart if needed.

## Gotchas for Dev agents

- **Worktree branch collision**: if the primary worktree holds `bugfix_cycle_kc_*`, a new agent with `isolation: "worktree"` can't check out the same branch. Workaround: create a local `sync-bugfix` branch from `origin/bugfix_cycle_kc_*`, push via `git push origin HEAD:bugfix_cycle_kc_*` for the status commit. This pattern showed up repeatedly in Cycle 5.
- **`NODE_OPTIONS=--openssl-legacy-provider`** is set in the user's shell. Always prefix frontend commands with `NODE_OPTIONS=""` and use absolute path `/opt/homebrew/bin/pnpm`.
- **`SHELL=/bin/bash`** prefix needed for `docker build` (zoxide alias breaks `cd`).
- **`./mvnw spotless:apply`** before every commit — the project's commit hooks may enforce it.
- **Don't skip `--verify`** on commit — the project uses commit hooks for code formatting and they're load-bearing.
