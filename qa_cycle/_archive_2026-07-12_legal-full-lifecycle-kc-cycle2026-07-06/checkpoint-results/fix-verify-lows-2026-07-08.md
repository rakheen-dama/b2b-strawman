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
| LZKC-015 | VERIFIED | Scratch matter QAV-2026-002 closed with both doc flags → **TWO** "Document ready" emails in Mailpit at 19:41:37, one per doc: `R5zEYL9eUhsHoxriEgERbG` (matter-closure-letter-…pdf) + `HtzwwtC7rthx95vLvTyphN` (statement-of-account-…pdf), both → sipho.portal@example.com (Day-60 symptom: closure-letter email only). Bonus signal: standalone SoA "Preview & Save" on the Engagement Letter matter also produced its own Document-ready email (`KjywZfspLLE8mT3cuwexhn`) |
| LZKC-020 | VERIFIED | PROP-0004 sent to Sipho → portal magic-link login → Accept. (1) Portal /activity "Your actions" now includes "**Engagement letter accepted — You — just now**" (Day-88: absent); (2) firm audit history on the proposal: acceptance entry actor "**Portal Contact**", Source PORTAL (Day-88: "System"); (3) auto-created matter's Activity feed shows the acceptance with **SD avatar**, and the actor filter lists "**Sipho Dlamini**" — selecting it isolates exactly the 'Proposal "PROP-0004" was accepted' event. Screenshots: fix-verify-lzkc020-portal-your-actions.png, fix-verify-lzkc020-firm-feed-sipho-actor.png |

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

### LZKC-015 — closure doc emails
- Scratch matter "QA Lows-Verify Closure — LZKC-015" (`11c9bb83-e8c1-4f2b-bd25-a95eab870fcf`, ref QAV-2026-002, client Sipho, no template → no task-gate noise) created and closed by Thandi: gates all green except "No final bill issued" (amber) → override with justification; Concluded + notes; both "Generate closure letter" and "Generate Statement of Account" checked (defaults).
- Mailpit went 2 → 4 messages within a second of closure: the two Document-ready emails above. Matter page confirms CLOSED with "Closed by Thandi Mathebula" (further LZKC-014 confirmation on a brand-new closure record).

### LZKC-020 — portal acceptance attribution
- Send path: the pre-existing Day-7 draft PROP-0004 (client Sipho already bound) was sent via proposal detail → Send Proposal → recipient "Sipho Dlamini (sipho.portal@example.com)". Send email subject "Mathebula & Partners: New engagement letter PROP-0004 for your review" (legal-za noun — LZKC-004 corroboration on the proposal-send template).
- Fresh magic link (`nRGec7iCLAhGxKUjs8G4Gj`) → `/auth/exchange` → redirectTo honoured onto the proposal → Accept Engagement Letter → ACCEPTED + "Your matter has been set up" (acceptance auto-created matter `186c836d-d2a1-47b5-99b0-304a063ffe36`, Day-10 precedent).
- Attribution verified at all three surfaces (portal Your-actions / firm audit actor / firm activity feed actor filter). Residual noted below (payload `actor_name`).

## Observations (no fixes applied; for orchestrator triage)

1. **Features-page module clobber (candidate new gap, Medium?)** — Toggling "Automation Rule Builder" ON at `/settings/features` removed the `deadlines` module as a side effect. Backend log 19:25:56Z (OrgSettingsService): `added=[automation_builder], removed=[deadlines]`. Read-only DB confirms `enabled_modules` no longer contains `deadlines`. The Features page exposes only 4 toggles (bulk_billing, information_requests, resource_planning, automation_builder) and appears to write back a reconstructed module list that drops modules it doesn't know about. Left as-is (no SQL writes); tenant currently missing `deadlines`.
2. **`/api/assistant/invocations?status=PENDING_APPROVAL&size=0` → 404** — recurring console error on settings pages (features/general/automations). AI assistant poll against an endpoint that 404s when AI features are off. Env/noise-level, but pollutes "console clean" checks on settings routes. Not part of any fix under verify.
3. **CreateProposalDialog client combobox is inert (candidate new gap)** — In the org-level "New Engagement Letter" dialog, clicking the Client trigger (`proposal-customer-trigger`) never opens the popover (tried trusted CDP clicks, stepped mouse, Enter/keyboard; aria-expanded stays false, no popper mounts). Root cause read from source (not fixed): `frontend/components/ui/form.tsx:86-98` `FormControl` is a `cloneElement` wrapper that clones only `{aria props, ...child.props}` and **discards every prop passed to FormControl itself** — so `PopoverTrigger asChild`'s injected onClick/aria-haspopup/data-state never reach the Button (`create-proposal-dialog.tsx:281-294`, confirmed empirically: rendered button's React props contain no onClick). Not caused by this fix wave (#1517 diff only adds the mount-gate; form.tsx untouched since #1421, June 11) but it means org-level proposal creation with customer selection is currently impossible in the running build; matter/deal-level CTAs (defaultCustomerId, combobox locked) are unaffected — which is how this pass proceeded (sent existing PROP-0004). Same FormControl+PopoverTrigger composition likely affects sibling dialogs (LZKC-023 family adjacency). Needs its own gap ID + reproduction test.
4. **Audit payload `actor_name` stale on portal acceptance** — the `proposal.accepted` audit entry header/actor is correctly "Portal Contact" (Source PORTAL) and feeds resolve "Sipho Dlamini", but the event payload still carries `actor_name: System`. Display attribution (the LZKC-020 fix criterion) is correct; the payload field is a minor residual for the LZKC-025 family sweep.
5. **`project.created` raw copy** — new matter auto-created by acceptance logs feed copy "System performed project.created on project" (raw event key). Not one of LZKC-019's 13 fixed arms; same-class residual, note for the formatter backlog.
6. Scratch residue this pass: task "LZKC-013 verify — no follow-up on Done" (DONE) on Engagement Letter matter; SoA PDF (SOA-1c366c98-20260708) + Document-ready email on Engagement Letter matter; closed matter QAV-2026-002 with closure letter + SoA (PORTAL visibility) + 2 emails; PROP-0004 now ACCEPTED + auto-created Active matter `186c836d…` ("Engagement Letter — Litigation (Dlamini v RAF)", Sipho — 3rd matter of that name family); deal "QA fix-verify deal — LZKC-005" round-tripped Won→Engagement→Won (re-win emailed Bob, `MftEgUfScHS99LSycvdLuE`); Task Completion Chain rule OFF; Automation Rule Builder feature ON (deadlines module dropped — observation 1); branding logo re-uploaded (120×40 navy placeholder PNG replacing the Day-1 logo lost to the S3 wipe); Sipho portal session re-minted.

## Console/log hygiene
- Backend log: **0 ERROR lines** across the whole pass (restart 19:17Z → end).
- Firm console: clean on /dashboard, /pipeline, /proposals flows; the only recurring error is observation 2 (assistant-invocations 404 on settings pages).
- Portal console: clean during login/accept/activity (favicon-class noise only).
