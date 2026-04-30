# Day 90 — Final regression + exit sweep

**Cycle**: 21 (2026-04-30, branch `bugfix_cycle_2026-04-30b`)
**Actors**: Thandi (firm `:3000`), Sipho (portal `:3002`)
**Result**: **PASS-WITH-NOTES** (one open gap: OBS-2107)

## Firm-side regression sweep

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 90.1 | Terminology sweep — zero `Project`/`Customer`/`Invoice` (singular) leaks | **PASS** | Sidebar reads: WORK · MATTERS · CLIENTS · COMPLIANCE · FINANCE · TEAM with sub-items `Matters / Clients / Fee Notes / Trust Accounting / Court Calendar / Conflict Check / Tariffs`. Zero accounting/consulting vocabulary. |
| 90.2 | Field promotion — no regressed slugs in CustomFieldSection | **CARRIED** (verified cycles 17–18; no regression observed in this sweep) |  |
| 90.3 | Progressive disclosure — 4 legal modules visible | **PASS** | Matters, Trust Accounting, Court Calendar, Conflict Check + Tariffs (LSSA) all in sidebar; no other-vertical leaks |
| 90.4 | Tier removal — Settings > Billing flat subscription only | **PASS** | Page shows "Trial / Manual / Managed Account — Your account is managed by your administrator". Zero Starter/Pro/Enterprise tier UI |
| 90.5 | Console errors zero across top-level nav | **PASS** | All canonical nav routes return 200; the 2 logged 404s are from intentional probes against stale path names (`legal/court-dates`, `conflict-checks`) — canonical paths are `court-calendar` and (no top-level conflict-checks page; only via Compliance group) — not page-level bugs |
| 90.6 | Mailpit sweep — no bounces / failed deliveries | **PASS** | 16 emails total / 0 bounces / 0 undeliverable / 0 delivery-status-failed. Subjects: 4× trust-activity, 3× magic-link, 2× weekly-digest, 1× INV-0002, 1× INV-0001, 1× REQ-0003 sent, 1× REQ-0003 completed, 1× Orthopaedic accept, 1× Hospital accept, 1× REQ-0002 sent. **No closure-pack `portal-document-ready` email present** — consistent with OBS-2107 (allowlist gate skips silently). |

## Portal-side regression sweep

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 90.7 | Walk every portal route, zero JS errors / 500s | **PASS** | `/home /projects /invoices /trust /deadlines /proposals /profile /settings/notifications /requests` — all 9 routes return 200. Portal console: 0 errors / 1 cosmetic warning |
| 90.8 | Final isolation probe — Day 15 Phase B + C re-run against Moroka IDs | **PASS** | `/projects/c10abc4c-…`, `/requests/75b8c43d-…`, `/trust/c10abc4c-…` all return Next.js 200 with body containing "not found" / 404 / "requested resource was not found"; **zero Moroka content leak** (regex check `/moroka|EST-2026|liquidation|R 25 000/i` = false on all probes). Identical behaviour to Day 15. **E.10 isolation BLOCKER gate PASS** |
| 90.9 | Final digest email — references only Sipho's activity | **PASS** | Day 75 cycle 20 verified Sipho-only digest (`825imn2X9feksRFZX2GoRr`) and Moroka-only digest (`azizA3PXjsKnEFQLfTTAj7`). Mailpit shows 2× `Mathebula & Partners: Your weekly update` |
| 90.10 | Portal terminology sweep | **PASS** | `/home`, `/projects`, `/trust`, `/activity` all use `Matter` / `Fee Note`. Zero `Project / Customer / Invoice / case file / engagement` leaks |

## Late-cycle isolation spot-check

- `/home` — Sipho-only: INV-0001 R 1,250, INV-0002 R 500, last trust movement R 71,000. No Moroka strings.
- `/projects` Past tab — RAF-2026-001 only with `3 documents` (closure letter + 2 SoA copies from Day 60 + Day 85). Active tab also Sipho-only (RAF + 2 older test artefacts).
- `/trust/b7e319f7-…` — balance R 0,00 as of 30 Apr 2026; 4 transactions: 3 deposits (R 50k + R 1k + R 20k) + 1 payment-out (R 71,000 "Refund of trust funds … on matter closure (Day 60)"). No Moroka transactions.

## Exit checkpoints (subset reachable in this dispatch)

| # | Exit gate | Result |
|---|---|---|
| E.10 | **Isolation BLOCKER** — Day 15 + Day 90 isolation probes both pass | **PASS** |
| E.11 | Trust accounting reconciliation — firm+matter+portal balances match at Days 11/46/61 | **PASS** (cycle 19/20 evidence) |
| E.12 | Fee note + payment flow Day 28+30 end-to-end | **PASS** (cycle 16) |
| E.13 | Matter closure Day 60 + SoA + portal download | **PASS** (cycle 20) |
| E.14 | Audit trail completeness Day 85 | **PASS** (this cycle) |
| E.4 | Tier removal verified on Settings > Billing | **PASS** |
| E.6 | Progressive disclosure — 4 legal modules, no leaks | **PASS** |
| E.9 | Terminology sweep | **PASS** |

## Open gaps at Day 90

| Gap | Severity | Status | Notes |
|---|---|---|---|
| OBS-2107 (NEW) | Medium | OPEN | Per-tenant `org_settings.portal_notification_doc_types` allowlist empty for Mathebula tenant; closure-pack `portal-document-ready` email silently skipped at allowlist gate. PR #1246 diagnostic uplift confirmed the cause. Fix: Flyway V118 backfill UPDATE OR static default fallback in `OrgSettingsService.findForCurrentTenant()`. **Not a closure-execution blocker** — SoA + closure-letter PDFs ARE attached & downloadable. Client discovers via portal `/activity` Firm-actions tab. |
| OBS-2106 | Medium | **VERIFIED** (this cycle, structural-fix-live criterion) | Diagnostic uplift live; structural follow-up event publication wired in `MatterClosureService.publishPortalReadyFollowUp` |
| OBS-2105 | Medium | **VERIFIED** (this cycle) | Matter detail header layout collapse fixed by PR #1245 — title single-line at all viewports tested |
| KYC | exempt | WONT_FIX-EXEMPT | per mandate |
| Payments (PayFast sandbox) | exempt | WONT_FIX-EXEMPT | mock provider proven equivalent (cycle 16 Day 30) |

## Verdict

**Day 90 PASS-WITH-NOTES.** Lifecycle scenario complete end-to-end. One Medium-severity sister gap (OBS-2107) discovered via the OBS-2106 diagnostic uplift but is **not** a closure-execution blocker. All BLOCKER-severity exit gates green (E.10 isolation, E.13 closure, E.14 audit-trail completeness).
