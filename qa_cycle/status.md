# QA Cycle Status — Legal ZA 90-Day Demo (Fresh Tenant, Keycloak) — 2026-04-17

## Current State

- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 0 — 0.1 (not started)
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
| _no gaps logged yet_ | — | — | — | — | — | — |

## Legend

- **Status**: OPEN -> SPEC_READY -> FIXED -> VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-17 — Cycle initialized. Prior consulting-za cycle (ALL_DAYS_COMPLETE 14:32 SAST same day) archived to `_archive_2026-04-17_consulting-complete/`. Fresh branch `bugfix_cycle_legal_2026-04-17` created from `main`. Scenario: `qa/testplan/demos/legal-za-90day-keycloak.md`. First action: Infra Agent to verify stack health and run `keycloak-bootstrap.sh` if needed.
- 2026-04-17 18:28 SAST — Infra Agent: Dev stack READY. All 4 Docker containers (postgres, keycloak, mailpit, localstack) healthy for 21h; Keycloak `/realms/docteams` returns 200 (platform admin preserved from prior cycles, no bootstrap needed). All 4 local services already running from earlier: backend PID 24024 (maven wrapper, child java PID 24179 on :8080, etime 5h43m), gateway PID 71302 (etime 8h08m, serves HTTP on :8443 — note env ref table said HTTPS but actual is plain HTTP), frontend PID 15582 (ext, next-server v16.1.6, etime 21h05m, curl 200), portal PID 71100 (pnpm dev, etime 8h08m). Nothing started/restarted — all endpoints already responding 200. Backend log post-startup clean of stack-trace ERRORs; only non-fatal AutomationEventListener WARNs about a tenant's malformed `BudgetThresholdTriggerConfig.thresholdPercent` (operational data issue, not infra — pre-existing from prior cycle, handed to QA/Dev to track if it recurs during legal-za flow).
- 2026-04-17 18:32 SAST — Orchestrator: Fixed malformed test-fixture PDFs reported by user. Before: `.playwright-mcp/test-docs/*.pdf` were 28–55 B text stubs (`%PDF-1.4 test FICA document\n`) with no objects/xref/trailer — every PDF viewer rejected them. Archive `test-fixtures/*.pdf` were 317 B structurally-valid but contentless (blank page, no `/Contents` stream). New script `qa_cycle/make_test_pdf.py` generates well-formed PDF 1.4 documents (proper catalog/pages/page/content-stream/font objects, correct xref byte offsets, `%%EOF`, WinAnsi/CP1252 text encoding so em-dashes render). Regenerated 92 PDFs across 4 locations: `qa_cycle/test-fixtures/` (23 legal-za fixtures), `.playwright-mcp/test-docs/` (23, gitignored — MCP cache), archive `test-fixtures/` and `test-files/` (46). All 46 active files pass `qlmanage -t` thumbnail rendering with visible legible text. QA Agent can rely on fixtures from `qa_cycle/test-fixtures/` for upload checkpoints. Script is idempotent and parameterized — to add new doc types, edit the `DOCS` dict and re-run.
