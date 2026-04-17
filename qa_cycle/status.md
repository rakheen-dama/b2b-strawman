# QA Cycle Status — Legal ZA 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-17

## Current State

- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 0 — 0.22 (BLOCKED on GAP-L-01 — Keycloak invite single-session collision + token consumption)
- **Cycle**: 1
- **Dev Stack**: READY
- **NEEDS_REBUILD**: false
- **Branch**: `bugfix_cycle_legal_2026-04-17`
- **Scenario**: `qa/testplan/demos/legal-za-90day-keycloak.md`
- **Focus**: Fresh tenant run — full onboarding through 90-day law firm lifecycle. Re-run with v3 profile set (post-consulting cycle) to validate legal-za vertical end-to-end.
- **Auth Mode**: Keycloak (real OIDC)

## Environment

| Service | URL | Status |
|---------|-----|--------|
| Frontend (kc mode) | http://localhost:3000 | UP |
| Backend (local+keycloak profile) | http://localhost:8080 | UP |
| Gateway (BFF) | http://localhost:8443 | UP |
| Portal | http://localhost:3002 | UP |
| Keycloak | http://localhost:8180 | UP |
| Mailpit UI | http://localhost:8025 | UP |
| Postgres (docteams) | localhost:5432 | UP |
| LocalStack (S3) | http://localhost:4566 | UP |

## Carry-Forward Watch List (from prior legal-za archives)

These are gaps logged during `_archive_2026-04-13_legal-90day-kc-v2` and earlier runs. If they reproduce, log fresh GAP IDs referencing the archive:

- **Trust accounting module not gated correctly** (prior runs flagged direct URL exposure pre-fix). Now expected to show "Module Not Available" when disabled.
- **Matter template custom-field defaults** (analogous to consulting GAP-C-09) — template fields may not auto-fill on project creation.
- **Retention clock / matter closure gating** (ADRs 247-249 recently added) — new code paths; treat as first-run.
- **Statement of account template + acceptance-eligible manifest flag** (ADRs 250-251 recently added) — new code paths; validate end-to-end.

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|
| GAP-L-01 | Day 0 / 0.22 | HIGH | OPEN | Keycloak org-invite: same-profile session collision blocks registration AND single-use token is consumed on failed attempt, leaving user unable to register. Carry-forward of GAP-C-02; workaround of "fresh tab" does not work (tabs share cookies). | Dev | 0 |
| GAP-L-02 | Day 0 / 0.22 | LOW | OPEN | Untranslated i18n key `expiredActionMessage` rendered as heading on Keycloak expired-link page; body text is localized but heading key is raw. Missing from KC theme message bundle. | Dev | 0 |
| GAP-L-03 | Day 0 / 0.20 | LOW | OPEN | Keycloak registration page does not display target org name despite it being encoded in the invite token. User lacks visual confirmation of which org they're registering with. | Product | 0 |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-17 — Cycle initialized. Prior consulting-za cycle (ALL_DAYS_COMPLETE 14:32 SAST same day) archived to `_archive_2026-04-17_consulting-complete/`. Fresh branch `bugfix_cycle_legal_2026-04-17` created from `main`. Scenario: `qa/testplan/demos/legal-za-90day-keycloak.md`. First action: Infra Agent to verify stack health and run `keycloak-bootstrap.sh` if needed.
- 2026-04-17 18:28 SAST — Infra Agent: Dev stack READY. All 4 Docker containers (postgres, keycloak, mailpit, localstack) healthy for 21h; Keycloak `/realms/docteams` returns 200 (platform admin preserved from prior cycles, no bootstrap needed). All 4 local services already running from earlier: backend PID 24024 (maven wrapper, child java PID 24179 on :8080, etime 5h43m), gateway PID 71302 (etime 8h08m, serves HTTP on :8443 — note env ref table said HTTPS but actual is plain HTTP), frontend PID 15582 (ext, next-server v16.1.6, etime 21h05m, curl 200), portal PID 71100 (pnpm dev, etime 8h08m). Nothing started/restarted — all endpoints already responding 200. Backend log post-startup clean of stack-trace ERRORs; only non-fatal AutomationEventListener WARNs about a tenant's malformed `BudgetThresholdTriggerConfig.thresholdPercent` (operational data issue, not infra — pre-existing from prior cycle, handed to QA/Dev to track if it recurs during legal-za flow).
- 2026-04-17 18:56 SAST — QA Agent: Day 0 **BLOCKED** at checkpoint 0.22 (owner Keycloak registration). Phases A-B PASS end-to-end: access-request form → OTP (`447884` from Mailpit) → "Request Submitted" card, then padmin login → Mathebula & Partners approval → tenant provisioning all green. Tenant schema `tenant_5039f2d497cf` created; `vertical_profile=legal-za`, ZAR, en-ZA-legal terminology, all 4 legal modules enabled, all legal-za packs applied (common-project/task, legal-za-customer/project, compliance-za, fica-individual/trust/company, legal-za-clauses, automation-legal-za, legal-za-project-templates). Invitation email delivered. Phase C BLOCKER: clicking invite link in new Chrome tab inherits padmin KC cookie → "You are already authenticated as different user" (carry-forward GAP-C-02 reproduces); worse, invite token is consumed by the failed attempt, so logging padmin out then retrying the link yields `expiredActionMessage`. 3 gaps logged: GAP-L-01 HIGH (Keycloak session collision + token consumption on failure), GAP-L-02 LOW (untranslated i18n key), GAP-L-03 LOW (registration page doesn't show org name). 3 screenshots captured (access-request-submitted, keycloak-session-collision, invite-token-expired). Also noted 3 LOW observations: OBS-L-01 (access-requests row click has no detail page — all fields already inline, probably fine), OBS-L-02 (Playwright MCP click unreliable on Radix Button onClick — worked around with `page.evaluate('btn.click()')`), OBS-L-03 (Radix Tabs triggers don't switch under Playwright MCP — verified via SQL and empty-Pending evidence). Next QA Position: Day 0 — 0.22 remains blocked; Day 1+ cannot proceed until Thandi can register. Recommended fix direction for GAP-L-01: (a) clear Keycloak session on invite-click, or (b) make org-invite tokens non-consumable on session-collision errors, or (c) add "logout and continue" CTA on collision page.
- 2026-04-17 18:32 SAST — Orchestrator: Fixed malformed test-fixture PDFs reported by user. Before: `.playwright-mcp/test-docs/*.pdf` were 28–55 B text stubs (`%PDF-1.4 test FICA document\n`) with no objects/xref/trailer — every PDF viewer rejected them. Archive `test-fixtures/*.pdf` were 317 B structurally-valid but contentless (blank page, no `/Contents` stream). New script `qa_cycle/make_test_pdf.py` generates well-formed PDF 1.4 documents (proper catalog/pages/page/content-stream/font objects, correct xref byte offsets, `%%EOF`, WinAnsi/CP1252 text encoding so em-dashes render). Regenerated 92 PDFs across 4 locations: `qa_cycle/test-fixtures/` (23 legal-za fixtures), `.playwright-mcp/test-docs/` (23, gitignored — MCP cache), archive `test-fixtures/` and `test-files/` (46). All 46 active files pass `qlmanage -t` thumbnail rendering with visible legible text. QA Agent can rely on fixtures from `qa_cycle/test-fixtures/` for upload checkpoints. Script is idempotent and parameterized — to add new doc types, edit the `DOCS` dict and re-run.
