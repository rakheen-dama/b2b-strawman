# Fix-Verification Pass — Lows Wave — QA Cycle 2026-07-06 — executed 2026-07-08

Stack: frontend :3000, backend :8080 (restarted 19:17Z post-merge, PID 18645), gateway :8443, portal :3002, Mailpit :8025.
Users: Thandi (owner, SecureP@ss1), Bob (admin, SecureP@ss2). Org: mathebula-partners, tenant `tenant_5039f2d497cf`.

**Environment note (affects baselines, not verdicts):** Docker infra restarted ~18:27Z today — Mailpit wiped to **0 messages** (fresh baseline for this pass) and **LocalStack S3 lost all objects** (branding logo + all previously generated document PDFs). The Day-1 branding logo was re-uploaded via Settings → General UI before LZKC-007 verification (org_settings still held `logo_s3_key`; only the S3 object was gone). Environment artifact, not a product bug.

## Verdicts

| Gap | Verdict | Key evidence |
|-----|---------|--------------|
| LZKC-001 | VERIFIED | 2× fresh /pipeline loads: 0 console errors, 0 hydration/aria-describedby messages; drag round-trip works (Won→Engagement drop registered, Engagement→Won with "Mark as Won" confirm; board restored to Won=3, R 98 500) |
| LZKC-011 | VERIFIED | 2× fresh /dashboard loads: 0 console errors (only DevTools INFO + HMR); DOM probe: 81 SVG paths, 0 malformed (all start with M) |
| LZKC-010 | VERIFIED | Generate Document → Invoice Cover Letter on INV-0002: preview shows **"Invoice Number: INV-0002"** and **"Total Amount: R800,00"** populated (Day-28 was blank). Backend restart log 19:17:52Z: "Reconciled template pack **common to v3** … (0 new templates, **1 refreshed**)" + "Reconciled template pack **legal-za to v7** … (0 new, **3 refreshed**)" for tenant_5039f2d497cf — existing-tenant content delivery confirmed. Screenshot: fix-verify-lzkc010-cover-letter-populated.png |
| LZKC-007/017 part 1 | VERIFIED (part 1) | Fee Note doc regenerated on INV-0002: letterhead **logo image renders** (img loaded from S3 presigned URL, naturalWidth 120×40) at top; all amounts uniform ZAR locale **R800,00 / R0,00** (6/6, zero plain "800.00" formats). SoA generated on Engagement Letter matter (period 1–8 Jul): logo image loaded, all amounts uniform (R0,00 / R800,00 / R50 000,00 / R20 000,00 / R70 000,00) — Day-61 mixed-locale symptom gone; regex probe for unprefixed decimals: 0 hits. Banking details/Payment Instructions empty = EXPECTED (part 2 deferred epic); VAT Reg blank = NOT-A-DEFECT (unset tenant data). Screenshots: fix-verify-lzkc007-feenote-logo-locale.png, fix-verify-lzkc007-soa-logo-locale.png |
| LZKC-013 | VERIFIED | Enabled Automation Rule Builder feature (Settings → Features) to reach the rules list; `Task Completion Chain` was ON for this existing tenant (per new-tenants-only fix scope) → toggled OFF via UI (aria-checked=false). Created scratch task "LZKC-013 verify — no follow-up on Done" on Engagement Letter matter, walked Open → In Progress → Done. Result: **no "Follow-up:" task spawned** — tasks panel default (Open+In Progress) filter shows "No tasks yet" (a spawned follow-up would be IN_PROGRESS and visible); read-only DB: exactly 1 task on the matter (`LZKC-013 verify… | DONE`); backend log contains zero "Follow-up" lines since restart |
| LZKC-014 | VERIFIED | Both closed matters render the member name: QAV-2026-001 closure history "**Closed by Thandi Mathebula**" (+ override justification), RAF-2026-001 "**Closed by Thandi Mathebula**"; regex probe `Closed by [uuid]` negative on both pages |
| LZKC-019 | VERIFIED | RAF-2026-001 Activity tab (90d): "**Sipho Dlamini downloaded document \"matter-closure-letter-…pdf\"**", "**Sipho Dlamini downloaded document \"statement-of-account-…pdf\"**", "**Thandi Mathebula generated a statement of account \"…\"**", "**Thandi Mathebula generated document \"…\" from template \"Matter Closure Letter\"**", "Thandi Mathebula closed the matter" — friendly copy with document names; negative probes: no "performed", no raw keys (`portal.document`/`statement.generated`/`generated_document`). Screenshot: fix-verify-lzkc019-activity-friendly.png |
| LZKC-015 | (in progress — second half of pass) |
| LZKC-020 | (in progress — second half of pass) |

## Flow notes

### LZKC-001 — /pipeline hydration + drag
- Load 1 and load 2 (fresh gotos): console = React DevTools INFO + [HMR] connected only. Zero errors/warnings both loads.
- Drag: dnd-kit live-region status confirmed both drops ("Draggable item f9ba9272… was dropped over droppable area bde084b7…" and the return drop). Coordinate note for future agents: the board horizontal-scrolls — re-snapshot with boxes before each drag; one mis-aimed drag opened the "Mark deal as lost" dialog for the wrong card (cancelled, no state change).

### LZKC-011 — dashboard sparkline
- 0 errors on two fresh loads; the Day-28 malformed-path error (`Expected moveto path command`) absent. 48 SVGs / 81 paths on page, all valid.

### LZKC-010 — cover letter + reconcile
- Reconcile lines prove the PR #1523 stored-hash self-consistency path refreshed PRISTINE templates on the existing tenant at backend restart (common v3: 1 refreshed = invoice-cover-letter; legal-za v7: 3 refreshed).
- Cover letter preview also shows the letterhead logo (shared rendering pipeline with 007 part 1).

### LZKC-007/017 part 1 — letterhead + locale
- First regeneration attempt showed logo `<img>` with valid presigned URL but 404 from LocalStack → diagnosed as the infra-restart S3 wipe (bucket listing returned 0 keys; docs from Day 60/61 also gone). Re-uploaded logo via Settings → General (UI), regenerated: image loads. The template fix itself was never in doubt (img node present with correct URL both times).
- SoA "Preview & Save" saved a fresh SoA PDF to the Engagement Letter matter's documents (scratch residue, documented below).

### LZKC-013 — automation toggle
- The automations settings page is gated behind the "Automation Rule Builder" feature toggle; enabled it via Settings → Features (UI-driven). This surfaced a **new observation** (below): enabling it silently dropped the `deadlines` module from `enabled_modules`.

## Observations (no fixes applied; for orchestrator triage)

1. **Features-page module clobber (candidate new gap, Medium?)** — Toggling "Automation Rule Builder" ON at `/settings/features` removed the `deadlines` module as a side effect. Backend log 19:25:56Z (OrgSettingsService): `added=[automation_builder], removed=[deadlines]`. Read-only DB confirms `enabled_modules` no longer contains `deadlines`. The Features page exposes only 4 toggles (bulk_billing, information_requests, resource_planning, automation_builder) and appears to write back a reconstructed module list that drops modules it doesn't know about. Left as-is (no SQL writes); tenant currently missing `deadlines`.
2. **`/api/assistant/invocations?status=PENDING_APPROVAL&size=0` → 404** — recurring console error on settings pages (features/general/automations). AI assistant poll against an endpoint that 404s when AI features are off. Env/noise-level, but pollutes "console clean" checks on settings routes. Not part of any fix under verify.
3. Scratch residue this pass (so far): task "LZKC-013 verify — no follow-up on Done" (DONE) + one SoA PDF (SOA-1c366c98-20260708) on Engagement Letter matter; Task Completion Chain rule now OFF; Automation Rule Builder feature now ON; branding logo re-uploaded (120×40 navy placeholder PNG — replaces the identical-intent Day-1 logo lost to the S3 wipe).
