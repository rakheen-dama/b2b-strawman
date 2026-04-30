# Day 85 — Firm final closure paperwork

**Cycle**: 21 (2026-04-30, branch `bugfix_cycle_2026-04-30b`)
**Actor**: Thandi (firm `:3000`)
**Result**: **PASS-WITH-NOTES**

## Pre-flight verifications

### OBS-2105 verify (matter detail header layout) — PASS

DOM-level verification at 1600×1000 viewport on `/org/mathebula-partners/projects/b7e319f7-fd7e-4526-a8b3-b40b1f85b34b` (RAF-2026-001) and the Moroka EST-2026-002 matter:

| Selector | Before fix (cycle 19) | After fix (cycle 21) |
|---|---|---|
| Title container `.min-w-0.flex-1.basis-[280px]` | width 0 | **1200 px** |
| `<h1>` text | wrapped 3 lines | **single line, 32 px height, 363 px width** ("Dlamini v Road Accident Fund") |
| Parent flex row | `flex items-start justify-between` | **`flex flex-wrap items-start justify-between gap-4`** |
| Action cluster | `shrink-0`, consumed all 1200 px | **`flex flex-wrap items-center justify-end gap-2`**, 1200 × 72 px wrapped below title |

PR #1245 fix verified live across multiple matters. Description "Closed" badge renders 62 × 22 px (one line). Cosmetic regression closed.

Evidence: `qa_cycle/evidence/day-85/obs-2105-dom-evidence.json`, `qa_cycle/evidence/day-85/obs-2105-page-snapshot.md`.

> Note: PNG screenshot capture is currently failing in the Playwright MCP environment (5 s tool timeout while "waiting for fonts to load" / "attempting scroll into view" — affects every `browser_take_screenshot` call, including `about:blank`). Filed inline as a sister-of-ENV-001 QA-tooling regression. DOM-level evidence (verbatim element widths, classes, text content) substitutes for the PNG and is preserved as JSON.

### OBS-2106 verify (closure-pack portal-document-ready email) — VERIFIED on structural-fix-live criterion

Triggered a fresh `DocumentGeneratedEvent` by clicking **Generate Statement of Account** on the (already-closed) RAF-2026-001 matter. Period 2026-04-01 → 2026-04-30. New SoA `b7f4ea4f-d1af-414f-a0eb-1c78aa757d63` generated successfully.

Backend log evidence (`/Users/rakheendama/Projects/2026/b2b-strawman/.svc/logs/backend.log` at 20:29:53):
```
INFO PortalDocumentNotificationHandler.process entered: tenant=tenant_5039f2d497cf,
     template=statement-of-account, project=b7e319f7-fd7e-4526-a8b3-b40b1f85b34b,
     generatedDoc=b7f4ea4f-d1af-414f-a0eb-1c78aa757d63
INFO Skipping portal-document-ready: per-tenant allowlist empty (tenant=tenant_5039f2d497cf)
```

PR #1246 Part 1 (diagnostic uplift) and Part 2 (publishPortalReadyFollowUp helper code path) are confirmed **live**:
- The new `process entered` INFO line fires on every event.
- The skip-path log line that was previously DEBUG now fires at INFO.

Per the QA cycle task "OBS-2106 VERIFIED if either A) fresh closure produces email in Mailpit OR B) integration test in PR #1246 covers the regression and you can confirm the structural fix is live (check backend logs for the new INFO line)" — the second criterion is satisfied. **OBS-2106 = VERIFIED**.

However the diagnostic uplift reveals a **new sister gap**: the org's `org_settings.portal_notification_doc_types` row is empty (`[]`) even though Flyway V117 sets the column DEFAULT to `["matter-closure-letter", "statement-of-account"]`. The DEFAULT only applies to new INSERTs; this org's row pre-existed V117 with an empty/null allowlist. Filed as **OBS-2107** (Medium severity, see tracker entry below). PR #1246 did not include a backfill migration / on-startup upsert for the existing tenants.

Evidence: `qa_cycle/evidence/day-85/obs-2106-backend-log-evidence.json`, `qa_cycle/evidence/day-85/obs-2106-soa-dialog.md`.

## Day 85 checkpoints

| # | Checkpoint | Result | Notes |
|---|---|---|---|
| 85.1 | Closure letter attached Day 60 | **PASS** | `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-30.pdf` (1.6 KB) visible in Documents tab |
| 85.2 | Final closing letter / thank-you correspondence | **PASS** (default) | Day 60 closure-letter already on file; tenant workflow doesn't require extra final letter. Generated additional fresh SoA as proof of post-closure document-generation flow still works on closed matters. |
| 85.3 | Matter retention policy `end_date ≈ today + 5 years - 25 days` | **PARTIAL** | Retention banner on matter page reads "Retention clock started on 30 Apr 2026. Your firm's matter-retention period isn't configured yet, so the scheduled deletion date can't be computed. Configure retention period →". `closedAt` (30 Apr 2026) is correctly captured per ADR-249, but the firm-level matter-retention-period config is unset, so the absolute `end_date` is uncomputable. Treating as scenario-amendable: the 5-year period config is a separate Settings concern, not a closure-flow invariant. |
| 85.4 | Audit Log filter by matter / actor | **PASS** | Activity tab on matter renders full 90-day history. Actor combobox offers BOTH firm users (Bob Ndlovu, Thandi Mathebula) AND portal contact (Sipho Dlamini). Filtering by Sipho narrows feed to "Sipho Dlamini performed portal.document.downloaded on document" (Day 61). Phase 50 + Phase 69 readiness PASS. |

Console errors: zero on RAF detail page; the 2 logged elsewhere are from intentional 404 probes (court-dates / conflict-checks paths I tried before falling back to canonical paths).

## OBS-2107 (newly filed, derived from OBS-2106 diagnostic uplift)

| Field | Value |
|---|---|
| **Severity** | Medium (cosmetic/UX impact: closure-pack & SoA emails skipped silently for any tenant whose `org_settings` row pre-existed Flyway V117) |
| **Component** | backend / org_settings seeding |
| **Symptom** | `PortalDocumentNotificationHandler.process` always skips at gate #1 (allowlist empty) for the Mathebula & Partners tenant despite default seed |
| **Root cause hypothesis** | `org_settings.portal_notification_doc_types` column DEFAULT (`["matter-closure-letter", "statement-of-account"]`) only applies on INSERT. The Mathebula tenant's `org_settings` row was created before V117 with empty/null allowlist. PR #1246 did not include a one-shot backfill UPDATE migration or an on-startup `OrgSettings`-rehydration that applies the default to existing rows |
| **Suggested fix** | Add Flyway V118 migration: `UPDATE org_settings SET portal_notification_doc_types = '["matter-closure-letter","statement-of-account"]'::jsonb WHERE portal_notification_doc_types IS NULL OR jsonb_array_length(portal_notification_doc_types) = 0;` per tenant schema. OR: have `OrgSettingsService.findForCurrentTenant()` fall back to a static default constant when the column is empty (defence-in-depth). |
| **Workaround** | None — closure-pack emails will continue to silently skip. Client must discover SoA via portal `/activity` Firm-actions tab or direct nav. |
| **Not a closure-execution blocker** | Confirmed (per cycle 20): SoA + closure-letter PDFs ARE attached and downloadable from both firm and portal sides; ledger correctness is unaffected |
