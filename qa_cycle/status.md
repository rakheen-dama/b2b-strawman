# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak) — 2026-04-26

## Current State

**Purpose**: Run the legal ZA full-lifecycle scenario (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`) end-to-end on the Keycloak dev stack. Fix every gap encountered at the root cause — no workarounds, no SQL shortcuts. Prior verify-cycle status archived to `qa_cycle/_archive_2026-04-26_post-verify/status.md`.

- **Branch**: `bugfix_cycle_2026-04-26`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 2, Checkpoint 2.1 (Day 1 complete — branding saved + LSSA tariffs verified + Section 86 trust account created. Two non-cascading partials logged as GAP-L-90/-91. Ready for Day 2 client onboarding as Bob.)
- **Dev Stack**: Running — backend:8080, gateway:8443, frontend:3000, portal:3002, keycloak:8180 all healthy as of 2026-04-26
- **NEEDS_REBUILD**: false
- **Cycle Count**: 6

## Tracker

| GAP_ID | Severity | Status | Owner | Summary | Evidence |
|--------|----------|--------|-------|---------|----------|
| BUG-CYCLE26-01 | LOW | FIXED | dev | Team-invite form: Radix Select role choice ("Admin") does not propagate to backend POST under Playwright MCP; backend records role=member regardless. Root cause shared with -02 — fragile RHF binding pattern (custom onChange shadows register, role state held in useState outside RHF). Spec: qa_cycle/fix-specs/BUG-CYCLE26-01-02.md. Fixed in PR #1162 (squash merge a6b7cbab). | qa_cycle/checkpoint-results/day-00.md §0.28; backend.log `Created invitation for email=bob@mathebula-test.local with role=member` |
| BUG-CYCLE26-02 | LOW | FIXED | dev | Team-invite form: MCP-driven `fill()` on email input does not consistently flow through react-hook-form `register()`; Server Action POSTs return 200 but never reach backend. Same root cause as -01 — combined fix. Spec: qa_cycle/fix-specs/BUG-CYCLE26-01-02.md. Fixed in PR #1162 (squash merge a6b7cbab). | qa_cycle/checkpoint-results/day-00.md §0.29 |
| GAP-L-90 | BUG | FIXED | dev | Branding: brand-colour Save Settings persists `brand_color` to org_settings and injects `--brand-color: <hex>` on `<html>`, but no CSS rule consumes `var(--brand-color)`; setting brand colour has zero visible effect on UI. Logo persistence works fine. Scoped fix: sidebar active indicator + sidebar org-name label (high-signal, minimal impact). Spec: qa_cycle/fix-specs/GAP-L-90.md. Fixed in PR #1163 (squash merge b3b5a8d4). | qa_cycle/checkpoint-results/day-01.md §1.2; `day-01-1.2-after-refresh.png` |
| GAP-L-91 | LOW | RESOLVED | product | Legal-za rate pack ships LSSA 2024/2025 schedule (the latest real-world LSSA schedule — tariffs are revised every 2–3 years). Scenario expectation of "2026 schedule" was aspirational. Resolved by editing scenario 1.4 to reference the latest published LSSA schedule. No code change. | qa/testplan/demos/legal-za-full-lifecycle-keycloak.md §1.4 (edited 2026-04-26); evidence `day-01-1.3-1.4-tariff-schedule.png` |

## Standing Rules (apply to every agent)

- **Root-cause every bug**. No workarounds. No SQL INSERT/UPDATE/DELETE to skip steps. All operations through REST API or browser UI.
- **No mocks for QA**. Drive the browser via Playwright MCP. Mailpit API is the only legitimate REST surface for QA.
- **One fix per PR**. Dev agents commit to `fix/{GAP_ID}` branches off this cycle branch, PR-and-merge with squash.
- **Restart after Java changes**. Backend/gateway: `bash compose/scripts/svc.sh restart {service}`. Frontend/portal: HMR auto-reloads.
- **Read CLAUDE.md** in the relevant subdirectory before changing service code.
- **Commit between turns**. Each agent pushes its state changes (status.md, fix-spec, checkpoint result) before returning.

## Known Out-of-Scope Constraints (do NOT open gaps for these)

- **Payment integration is a stub**. There is no real PSP integration (Stripe, Yoco, Peach, etc.). Invoice "Mark Paid" / portal payment flows are stub endpoints that just flip status. Do NOT open gaps for missing payment-gateway redirects, webhook signing, real card processing, or PSP-side reconciliation. If the scenario asks the user to "pay an invoice", treat the stub Mark-Paid action as the equivalent and continue.

## Log

- 2026-04-26 SAST — Orchestrator: cycle initialized on branch `bugfix_cycle_2026-04-26`. Prior verify-cycle status (ALL_DAYS_COMPLETE 2026-04-25) archived. Dev stack confirmed healthy. About to dispatch QA agent for Day 0.
- 2026-04-26 SAST — QA: Day 0 cycle-1 — 30 PASS / 2 PARTIAL (tooling) / 0 FAIL / 0 BLOCKER. All Day-0 wrap-up checks satisfied: 3 KC users registered, tenant_5039f2d497cf provisioned with vertical=legal-za, full legal sidebar nav present, zero tier/upgrade UI. Two LOW-severity tooling-only bugs logged (BUG-CYCLE26-01, BUG-CYCLE26-02) — both Playwright-MCP-Radix interaction quirks, not real-user product defects. L-22 (KC owner registration → /org/{slug}/dashboard) reconfirmed working. Bumping QA Position to Day 1, Checkpoint 1.1.
- 2026-04-26 SAST — QA: Day 1 cycle-2 — 5 PASS / 2 PARTIAL / 0 FAIL / 0 BLOCKER. Logo upload + persisted to S3, brand_color persisted to DB, LSSA tariff schedule confirmed pre-seeded (19 items in ZAR, schedule year 2024/2025), Section 86 trust account "Mathebula Trust — Main" created with R 0,00 balance. GAP-L-90 (BUG): brand-colour CSS variable injected but no stylesheet consumes it — visual no-op. GAP-L-91 (LOW): tariff schedule is 2024/2025 not 2026 — scenario or seed pack mismatch. Both non-cascading; advancing to Day 2.
- 2026-04-26 SAST — Product: triaged 4 OPEN items → 3 SPEC_READY + 1 RESOLVED. Combined BUG-CYCLE26-01 + -02 into single spec `BUG-CYCLE26-01-02.md` (shared root cause: RHF binding pattern in invite-member-form mixes register-shorthand with override-onChange and useState-outside-form). GAP-L-90 specced as scoped sidebar fix (active indicator + org-name label only — not a full theme overhaul). GAP-L-91 resolved-via-scenario-edit: scenario §1.4 now references "latest published LSSA schedule (2024/2025)" since LSSA tariffs are revised every 2–3 years. Cycle Count → 4.
- 2026-04-26 SAST — Dev: BUG-CYCLE26-01-02 merged (PR #1162). Refactored invite form to FormField+Controller pattern. Frontend HMR picks up changes — no service restart needed.
- 2026-04-26 SAST — Dev: GAP-L-90 merged (PR #1163). Sidebar consumers now read --brand-color; fallback teal in :root. HMR — no restart.
