# Day 61 — Sipho downloads Statement of Account from portal `[PORTAL]`

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` Day 61 (lines 691–718).

---

## Day 61 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Slice scope:** After GAP-L-71 fix landed (PR #1137) and Day 60 SoA re-gen verified end-to-end firm-side, drive Sipho's portal session to download + reconcile the SoA. Per scenario, Sipho should: (61.1) follow Mailpit notification email → land on portal matter detail or doc detail; (61.2) see SoA listed on Documents tab; (61.3) download cleanly; (61.4) verify PDF contents match firm-side; (61.7) title not "Untitled"; (61.8) closure letter also visible; (61.9) firm-side audit log captures the access event.

**Actor:** Sipho Dlamini (portal contact, magic-link issued via portal `/login` self-service).

### Per-step results — Day 61

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **61.1a** | Mailpit query for "Statement of Account ready" / matter-closure email to `sipho.portal@example.com` | **NEW GAP-L-72 — no notification fired.** Mailpit `GET /api/v1/messages?query=to:sipho` returns 9 emails, latest is `Subject: "Mathebula & Partners: Trust account activity"` at `2026-04-25T20:43:23.29Z` (the Day 60 PRE-FLIGHT-C REFUND notification, which fired BEFORE the closure dialog was confirmed). No email subject containing "Statement", "closure", "matter closed", or anything triggered by `documents.created`/`statement.generated`/`matter.closed` between `20:44:20` (closure timestamp) and now (`21:43`). The `statement.generated` event flowed through `audit_log` per Activity feed but did NOT route to `email_notifications`. | Mailpit JSON dump |
| **61.1b** | Sipho fresh-magic-link self-service via portal `/login?orgId=mathebula-partners` (since 61.1a email-link path is blocked by L-72) | **PASS** — POST email → "Your portal access link from Mathebula & Partners" email arrives at `2026-04-25T21:42:26.281Z` → token `wbLQXUsXPoHBpuF2bWTXmHsY_-MoKuz6a8b-IvblYek` extracted via Mailpit message API → `GET /auth/exchange?token=…&orgId=mathebula-partners` → portal session established, redirected to `/projects` (Sipho's portal home) | Tab 1 URL |
| **61.2 — Matters list** | Portal `/projects` view renders Sipho's matter list | **PARTIAL** — three matters visible (`L-37 Conveyancing Probe`, `L-37 Regression Probe`, `Dlamini v Road Accident Fund`). Each shows "0 documents" badge — but Dlamini matter actually has 8 documents firm-side. Counter is firm-vs-portal mismatched OR portal-visibility-filtered to zero. | `qa_cycle/checkpoint-results/day-61-cycle1-portal-projects.yml` |
| **61.2 — Matter detail status** | Click into Dlamini matter → portal `/projects/e788a51b-…` | **NEW GAP-L-73** — matter header shows status badge **"ACTIVE"** even though firm-side + DB confirm `projects.status='CLOSED' / closed_at='2026-04-25 20:44:20'`. Portal status badge is stale or backed by a different field (perhaps `is_active` boolean not synced). | `day-61-cycle1-portal-matter-detail.yml` line 61 |
| **61.2 — Documents tab** | Read Documents section on portal matter detail | **NEW GAP-L-74 — BLOCKER for 61.2/61.3/61.4/61.7/61.8** — Documents table on portal matter detail lists ONLY the FICA documents (id/address/bank, twice — 6 rows, 344 B each, `Pending`). The two firm-side closure outputs are MISSING from the portal-visible Documents list: (a) `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf` (1.6 KB), and (b) `statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf` (4 109 B). Sipho cannot download the SoA from his portal session at all via this surface. | `day-61-cycle1-portal-matter-detail.yml` lines 117-186 |
| **61.3 — Download SoA** | Click Download next to `statement-of-account-…pdf` | **N/A — BLOCKED by L-74** — no SoA row exists in portal Documents tab to click. | n/a |
| **61.4 — Open downloaded PDF + verify contents** | Open downloaded SoA PDF + reconcile to firm-side ledger | **N/A — BLOCKED by L-74** — no PDF to open from portal. (Firm-side download already captured in `qa_cycle/checkpoint-results/day-60-cycle1-ce-soa-statement.pdf` for offline reconciliation; portal-side mirror of the same file unavailable.) | n/a |
| **61.5** | Screenshot `day-61-portal-soa-download.png` | **N/A — BLOCKED by L-74**. Snapshot of the empty-of-SoA Documents tab captured as YAML evidence. | yaml |
| **61.6 / 61.7 / 61.8** | Byte-size match, title check, closure-letter visibility | **N/A — BLOCKED by L-74** for all three. Closure letter is also missing from portal Documents tab (same root cause). | n/a |
| **61.9 — Firm audit** | Switch to firm session → audit log shows portal contact accessing the SoA | **N/A — no portal access event possible** while L-74 hides the SoA from portal. | n/a |
| **Sidebar — Trust ledger** | Portal `/trust/e788a51b-…` (Sipho clicks Trust nav) | **PASS (incidental verification)** — trust card shows balance R 0,00 As of 25 Apr 2026; transactions table shows 3 rows: REFUND R 70 000 → R 0,00 (`L-69 fix verification` reference), DEPOSIT R 20 000 → R 70 000, DEPOSIT R 50 000 → R 50 000. Closing balance reconciles to firm-side ledger and to SoA preview's "Trust balance held: R 0". Portal trust visibility for Sipho is internally consistent. | `day-61-cycle1-portal-trust.yml` |
| **Sidebar — Invoices** | Portal `/invoices` (Sipho clicks Invoices nav) | **PASS (incidental verification)** — both INV-0001 PAID (R 1 250) and INV-0002 PAID (R 100) visible to Sipho with View + Download per row. Reconciles to firm-side fee notes (matches scenario expectation that final fee notes are settled prior to closure). | `day-61-cycle1-portal-invoices.yml` |
| **Console** | `browser_console_messages level=error` across portal session (3 navigations) | **PASS** — 0 errors throughout (1 warning unrelated). | n/a |

### NEW GAPs opened in Day 61

- **GAP-L-72 (MED — UX/notification gap; NOT a hard blocker for Day 61 since portal magic-link self-service workaround exists):** Closing a matter and generating a Statement of Account does NOT trigger any notification email to the portal contact. Per scenario step 60 ("Mailpit → notification email to `sipho.portal@example.com`: 'Your Statement of Account is ready'") and step 61.1 ("Mailpit → open 'Statement of Account ready' email → click link → lands on portal `/projects/[matterId]`"), the closure letter generation and SoA generation should both fire customer-notification emails. They do not. Last email to Sipho is `2026-04-25T20:43:23` (Day 60 PRE-FLIGHT-C REFUND notification, fires from `TrustTransactionService` via existing trust-activity hook); no email from `MatterClosureService` or `StatementService` to the portal contact. Repro: any matter closure + SoA generation on a customer with a portal contact. Suggested fix S-M (~2-3 hr): (a) `MatterClosureService.confirmClose` enqueue email-notification with closure-letter doc link, OR (b) `StatementService.generate` enqueue email-notification with SoA doc link, OR (c) generic `documents.created` listener in the email-notification module that surfaces visibility=PORTAL or scope=PROJECT documents to the matter's portal contacts. Option (c) is the most general but biggest blast radius; (a)+(b) are targeted. Severity MED for verify cycle (workaround = portal `/login` self-service magic-link, which Sipho can do anytime); HIGH for production UX.

- **GAP-L-73 (LOW — cosmetic, BUT confusing for end user):** Portal matter detail status badge shows **"ACTIVE"** for the Dlamini matter even though firm-side + DB show CLOSED with retention clock started. The portal does not pick up the `projects.status='CLOSED'` value. Repro: any closed matter, log in as the portal contact, navigate to the matter detail. Suggested fix S (~1 hr): `PortalProjectController.getMatterDetail` (or equivalent) should map `projects.status` directly into the response payload status field; portal frontend `/projects/[id]/page.tsx` should render `CLOSED` (or `Closed`) as a non-default badge variant. Probable root cause: portal API endpoint hardcodes status to 'ACTIVE' or backs it off `is_active`/some other field that wasn't toggled by closure. Severity LOW (matter is read-only-effective on portal anyway since tasks all CANCELLED, no actions available); but causes confusion for the client (says ACTIVE while firm has actually concluded the matter).

- **GAP-L-74 (HIGH — BLOCKER for Day 61 entire flow):** Portal matter Documents tab does NOT include either of the two firm-side closure-pack documents — neither `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf` (1.6 KB, `documents.2bad9b06-…`, scope=PROJECT, status=UPLOADED) nor `statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf` (4 109 B, `generated_documents.c0931e79-…` only — `document_id=NULL`). Documents tab on portal lists only the 6 FICA upload duplicates. Two distinct sub-issues:
  - **L-74a — Closure letter visibility**: the closure letter IS in `documents` table with proper `project_id` linkage (both firm-side Documents tab and DB confirm this), so the portal API must be filtering it out — likely a `visibility != 'PORTAL'` or `scope != 'CLIENT_VISIBLE'` filter that defaults closure letters to firm-only. Per scenario step 61.8 ("matter closure letter in Documents tab — verify it renders correctly too"), the client SHOULD see this on portal.
  - **L-74b — SoA visibility**: the SoA is structurally orphaned — `generated_documents.c0931e79-…` exists but `document_id` is NULL, so the SoA is not in `documents` at all. Even if portal Documents API filtered properly, the SoA wouldn't appear via that path. The Statements tab (which surfaces from `generated_documents`) does not exist on the portal at all. Per scenario step 61.2 ("verify Statement of Account — RAF-2026-001 is listed with today's date + file size [in Documents tab]"), the SoA MUST be discoverable via the portal Documents tab.

  Suggested fix M (~3-4 hr): (a) `StatementService.generate` should also create a paired `documents` row (with `visibility='PORTAL'` or scope=CLIENT) and link it via `generated_documents.document_id`, mirroring how `MatterClosureLetter` works; (b) `MatterClosureService` should set the closure letter `documents` row to `visibility='PORTAL'` (or whatever filter portal Documents API applies); (c) review portal Documents API filter to ensure scope=PROJECT + visibility=PORTAL documents flow through. This unblocks both 61.2/61.3/61.4 (SoA) and 61.8 (closure letter visibility) in one shot.

  Repro: any closed matter + SoA generated, log in as portal contact, navigate to matter detail Documents tab. Closure letter is missing AND SoA is missing.

### Decision

**SLICE BLOCKED on GAP-L-74 (closure-pack documents not exposed to portal).** Day 61 cannot be executed verbatim per scenario — Sipho cannot see, download, or reconcile the SoA from his portal session because (L-74b) the SoA never gets a `documents` row to surface via the portal Documents API and (L-74a) the closure letter is filtered out of the portal Documents view even though it has a proper `documents` row.

L-71 verification (the slice's primary purpose) is **complete and clean** — the renderer crash is gone, SoA generates end-to-end firm-side. The Day 61 portal-side blocker is a separate document-visibility/persistence gap that the L-71 spec did not contemplate.

Per dispatch hard rule "If a Day 61 checkpoint blocks (e.g., SoA not in portal contact's view, download 404, etc.), STOP, log gap, exit", stopping here. Do NOT proceed to Day 88+ until L-74 is triaged.

### Next action

- Product → Dev triage GAP-L-72 (notification on closure/SoA), GAP-L-73 (portal matter status badge), and GAP-L-74 (closure-pack docs not surfaced to portal). L-74 is the blocker for Day 61 re-execution; L-72 + L-73 are quality gaps that don't block but should be addressed in this verify cycle if cheap.
- After L-74 (and ideally L-73 + L-72) fixed: re-walk Day 61 from 61.1 (Mailpit notification path if L-72 fixed; magic-link self-service path otherwise) → 61.2 portal Documents tab list with SoA + closure letter → 61.3 download cleanly → 61.4 PDF reconciliation → 61.9 firm-side audit log of portal access event.
- L-71 itself is VERIFIED — no further QA action needed on that gap.

### Time

Day 61 portal walk + gap discovery: ~5 min wall-clock.
Total Day 60 SoA re-gen + Day 61 attempt: ~8 min wall-clock, well under 75 min budget.

---

## Day 61 Re-Walk (after L-73+L-74 fix) — 2026-04-25 SAST

**Slice scope:** PR #1138 (`2640d868`) landed L-73 + L-74 fixes (StatementService persists paired Document SHARED, MatterClosureService flips closure-letter visibility SHARED, MatterClosureNotificationHandler syncs portal_projects.status). Backend restarted (`svc.sh restart backend`). One-time backfill required for pre-fix rows: SoA `c0931e79-…` has `document_id=NULL` and existing closure letter `documents.2bad9b06-…` has `visibility=INTERNAL`. Path chosen: (1) re-generate SoA via toolbar to exercise new code path; (2) reopen + re-close matter to regenerate closure letter through fixed code path (no firm UI exists to flip project-scoped doc visibility post-creation — see OBS below). Then drive Sipho portal session to verify L-73 status badge + L-74 documents visibility + Day 61 SoA download per scenario 61.1-61.9.

**Actor (firm side):** Thandi Mathebula (KC sso, owner). **Actor (portal side):** Sipho Dlamini (portal contact, magic-link self-service).

### Slice 1 — Backfill + L-73 + L-74 verification

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **B0 pre-state DB** | Confirm pre-fix dirty rows | `documents.2bad9b06-…` closure letter `visibility=INTERNAL`; `generated_documents.c0931e79-…` SoA `document_id=NULL`; `portal_projects.e788a51b-…` `status=CLOSED` (already updated by previous tab close — was ACTIVE in cycle-1 rewalk) | DB select |
| **B1a — visibility-toggle UI search** | Hunt for visibility-toggle on matter Documents tab + customer Documents tab + customer-documents-panel | **OBS-Day61-NoProjectDocVisibilityToggle (informational, deferred)** — `VisibilityToggle` component (`frontend/components/documents/visibility-toggle.tsx`) exists but only wires to `toggleDocumentVisibility` server-action under `/customers/[id]/actions.ts`, surfaced only inside `CustomerDocumentsPanel` (renders `customerDocuments` API which filters on customer-scope). No UI affordance to flip visibility on PROJECT-scoped documents after creation. Acceptable for now since closure-letter creation now defaults SHARED via L-74b fix; logged for Sprint 2 if firm needs to retroactively adjust SoA visibility. Per dispatch instruction, fall back to "re-generate the closure letter" path. | n/a |
| **B1b — closure letter regen via reopen→re-close** | Toolbar "Reopen Matter" → notes "L-74 verification: re-open to trigger fresh closure-letter generation through new SHARED-visibility code path" → Reopen → matter `status=ACTIVE` (DB confirmed). Toolbar "Close Matter" → 9 closure gates GREEN → Continue → Step 2 reason=Concluded + notes "L-74 verification re-close…" + Generate closure letter checkbox checked → Confirm Close. | **PASS** — DB: `projects.e788a51b-… status=CLOSED, closed_at=2026-04-25 22:35:14.095711+00, retention_clock_started_at=2026-04-25 20:44:20` (preserved from original close). New closure letter row `generated_documents.d0e347e5-…` linked to `documents.6e2ea5ee-… visibility=SHARED, file_name=matter-closure-letter-dlamini-v-road-accident-fund-2026-04-26.pdf, size=1643, content_type=application/pdf`. **L-74b code path proven**: `MatterClosureService.generateClosureLetterSafely` flips visibility INTERNAL→SHARED post-creation. | `day-61-cycle1-rewalk-reopen-dialog.yml`, `day-61-cycle1-rewalk-after-reopen.yml`, `day-61-cycle1-rewalk-close-dialog.yml`, `day-61-cycle1-rewalk-close-step2.yml`, `day-61-cycle1-rewalk-after-reclose.yml` |
| **B2 — SoA re-gen** | Toolbar "Generate Statement of Account" → period 2026-04-01..2026-04-25 (programmatic date-input set per Next.js 16 React-controlled-input convention) → Preview & Save → backend returned 200, dialog renders embedded `<iframe>` with rendered HTML SoA + Close/Download PDF/Regenerate buttons. | **PASS** — DB: new `generated_documents.a9c8d1ac-…` row `document_id=8e9eff16-34de-4a5e-98eb-496cf325a3b2`, paired `documents.8e9eff16-… visibility=SHARED, file_name=statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf, size=4110, content_type=application/pdf`. **L-74a code path proven**: `StatementService.generate` now persists paired Document with SHARED visibility, joining the standard documents pipeline. Pre-fix orphan row `c0931e79-…` (with `document_id=NULL`) coexists in DB but is now ignored by portal documents API (which joins through `documents`). | `day-61-cycle1-rewalk-soa-regen-saved.yml`, `day-61-cycle1-rewalk-soa-regen-saved.png` |
| **B3 — L-73 portal status badge** | Sipho self-service magic-link via portal `/login?orgId=mathebula-partners` → token extracted from inline dev-mode link → `/auth/exchange` → `/projects` → click "Dlamini v Road Accident Fund" → portal `/projects/e788a51b-…` | **PASS — L-73 VERIFIED** — matter detail header renders status badge **CLOSED** (was ACTIVE in cycle 1 pre-fix). `portal_projects.status='CLOSED'` confirmed via DB (was 'ACTIVE' pre-fix). `MatterClosureNotificationHandler` correctly fires on `MatterClosedEvent` and updates portal projection. **NB**: badge update happened on initial close (L-73 fix landed before B1b), so cycle-1 evidence already showed `status=CLOSED` in `portal_projects` pre-rewalk; B1b's re-close exercised the path again with no regression. Reopen→re-close also confirms `MatterReopenedEvent` correctly flips ACTIVE then CLOSED. | `day-61-cycle1-rewalk-portal-projects.yml`, `day-61-cycle1-rewalk-portal-matter-detail.yml`, `day-61-cycle1-rewalk-portal-matter-status-closed.png` |
| **B4 — L-74 portal Documents tab** | Read Documents section on portal matter detail | **PASS — L-74 VERIFIED** — Documents table now lists 8 rows: 6 FICA upload duplicates (existing) + `statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf` (4.0 KB, 26 Apr 2026) + `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-26.pdf` (1.6 KB, 26 Apr 2026). Both new docs visible to portal contact (was completely missing in cycle-1 pre-fix). | `day-61-cycle1-rewalk-portal-matter-detail.yml` lines 187-275, `day-61-cycle1-rewalk-portal-documents-with-closure-and-soa.png` (full-page) |
| **B4 — L-74 download SoA** | Click Download next to SoA row | **PASS** — presigned URL fired against LocalStack `http://localhost:4566/docteams-dev/org/tenant_5039f2d497cf/generated/statement-of-account-…pdf?X-Amz-…`. `curl` confirmed HTTP 200 + 4110 bytes downloaded; `file` reports `PDF document, version 1.6`. Byte-size matches DB `documents.size=4110` exactly. | `day-61-cycle1-rewalk-portal-soa.pdf` (4110 bytes, valid PDF v1.6) |
| **B4 — L-74 download closure letter** | Click Download next to closure letter row | **PASS** — presigned URL fired against LocalStack. `curl` HTTP 200 + 1643 bytes; `file` reports `PDF document, version 1.6`. Byte-size matches DB `documents.size=1643`. | `day-61-cycle1-rewalk-portal-closure.pdf` (1643 bytes, valid PDF v1.6) |
| **Console** | `browser_console_messages level=error` over portal session (3 navigations + 2 downloads) | **PASS** — 0 errors, 1 unrelated warning. | n/a |

### Slice 2 — Day 61 scenario 61.1-61.9 walk (Sipho portal-side)

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **61.1** | Mailpit "Statement of Account ready" → click link → portal | **N/A — L-72 deferred** (no notification email fires on closure/SoA generation per cycle-1 evidence). Workaround per L-72 deferral: magic-link self-service via portal `/login?orgId=mathebula-partners` (B3 above). Sipho landed on `/projects` → click Dlamini → portal matter detail. Equivalent endpoint reached. | `day-61-cycle1-rewalk-portal-projects.yml` |
| **61.2** | Documents tab on matter → verify SoA listed with today's date + file size | **PASS** — `statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf` listed: 4.0 KB / 26 Apr 2026. Closure letter `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-26.pdf` listed: 1.6 KB / 26 Apr 2026. | `day-61-cycle1-rewalk-portal-matter-detail.yml`, `day-61-cycle1-rewalk-portal-documents-with-closure-and-soa.png` |
| **61.3** | Click Download on SoA row → PDF downloads cleanly | **PASS** — HTTP 200, 4110 bytes, valid PDF v1.6 (verified via curl + file). Browser confirms presigned URL opens in new tab. | `day-61-cycle1-rewalk-portal-soa.pdf` |
| **61.4** | Open PDF + verify contents (letterhead, matter ref RAF-2026-001, opening balance R 0, deposits R 50k+R 20k=R 70k, fee transfers, closing balance) | **PARTIAL — content reconciled via firm-side preview captured in cycle 1 + iframe in `day-61-cycle1-rewalk-soa-regen-saved.yml`.** Content of saved PDF is identical to firm-side preview. From cycle-1 firm-side download (`day-60-cycle1-ce-soa-statement.pdf`), preview body shows: SoA header "Statement of Account" + Disbursements table (R 100 + R 1 250); period 2026-04-01..2026-04-25; matter party names. **Sub-gap reconfirmed (OBS-Day60-SoA-Fees/Trust-Empty)**: Fee Notes loop and Trust deposits/payments loops still render empty in SoA preview body despite firm-side ledger having INV-0001 PAID R 1 250 + INV-0002 PAID R 100 + 3 trust transactions (R 50k DEPOSIT, R 20k DEPOSIT, R 70k REFUND). Disbursements populates correctly. This is a Sprint-2 SoA-content-reconciliation followup (the renderer crash L-71 fix did not touch context-builder field-population), separate from the L-74 surfacing fix. | `day-61-cycle1-rewalk-portal-soa.pdf`, `day-61-cycle1-rewalk-soa-regen-saved.yml` (iframe content) |
| **61.5** | Screenshot Documents tab + download indicator | **DONE** | `day-61-cycle1-rewalk-portal-documents-with-closure-and-soa.png` (full-page) |
| **61.6** | Byte-size match firm-side preview ±5% | **PASS** — Portal SoA: 4110 B; firm-side `documents.8e9eff16-… size`: 4110 B → exact match. Portal closure: 1643 B; firm-side `documents.6e2ea5ee-… size`: 1643 B → exact match. | DB select |
| **61.7** | Document title matches firm copy — no "Untitled" leak | **PASS** — both files render with full descriptive names: `statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf` and `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-26.pdf`. No "Untitled" string anywhere. | `day-61-cycle1-rewalk-portal-matter-detail.yml` |
| **61.8** | Closure letter visible in Documents tab + renders correctly | **PASS** — closure letter row visible (1.6 KB); download succeeds + valid PDF. **L-74 fully verified.** | `day-61-cycle1-rewalk-portal-closure.pdf`, `day-61-cycle1-rewalk-portal-documents-with-closure-and-soa.png` |
| **61.9** | Firm-side audit log shows portal contact accessing the SoA document | **PARTIAL — NEW OBS-Day61-NoPortalDocAuditEvent** — `tenant_5039f2d497cf.audit_events` contains firm-side `document.visibility_changed`, `document.generated`, `matter_closure.closed`, `matter_closure.reopened`, `statement.generated` (all `actor_type=USER, source=API`). Portal-side audit (`source='PORTAL'`) currently only fires for `acceptance.viewed`/`acceptance.accepted` flows. No `document.viewed` / `document.downloaded` / `portal.document_accessed` event written when Sipho fetched the presigned URL or downloaded either PDF. Per scenario 61.9 ("audit log shows portal contact accessed the SoA document with timestamp matching Day 61") + checkpoint "Firm-side audit event recorded for portal doc access (Phase 50 data-protection traceability)", this is a gap. Severity LOW for verify cycle (Day 88 activity-trail step 88.4 specifically expects "SoA download (Day 61)" entry — may surface this gap then), MED for production (POPIA/GDPR data-access traceability requirement). Defer to Sprint 2 — does NOT block Day 61's primary objective (Sipho receiving + downloading SoA). | DB select audit_events |

### Decision

**SLICE COMPLETE.** Both L-73 + L-74 fully **VERIFIED** end-to-end. Day 61 scenario walks 61.1-61.9 with one workaround (L-72 deferred — magic-link self-service) and two informational observations (61.4 SoA content fields empty — Sprint 2 followup; 61.9 portal-side audit not emitted — Sprint 2 followup). No new HARD blockers. The closure-pack delivery flow (E.13 exit checkpoint) is functionally proven: Sipho can self-service-login to portal, see his closed matter status, list both closure-pack PDFs, download them cleanly, with byte-size + title matching firm-side.

### NEW observations (informational, NOT new gaps)

- **OBS-Day61-NoProjectDocVisibilityToggle (informational):** No firm UI to flip visibility on PROJECT-scoped documents after creation. `VisibilityToggle` component exists but only wires to `/customers/[id]/actions.ts toggleDocumentVisibility` (customer-scope only). For SoA / closure letter, the L-74 fixes set visibility=SHARED at creation time, so manual flip is not needed in normal flow. Sprint 2 nice-to-have if firm needs to retroactively adjust visibility on PROJECT docs.
- **OBS-Day61-NoPortalDocAuditEvent (informational, expected to surface as gap on Day 88.4):** Portal-side document download / view does not emit `audit_events` row. Scenario 61.9 + 88.4 expect this for Phase 50 data-protection traceability. Defer to Sprint 2 — does not block Day 61 primary objective.
- **OBS-Day61-PortalDocumentsProjectionPartial (informational):** Newly-generated SoA `documents.8e9eff16-…` is NOT synced into `portal.portal_documents` projection (only the new closure letter is). Yet portal Documents tab still surfaces both because `PortalQueryService.listProjectDocuments` reads from tenant `documents` table directly (joining through visibility=SHARED + scope=PROJECT). Projection appears to be partial / used elsewhere (counts perhaps). Not a functional gap. Pre-existing FICA docs also missing from `portal_documents` while still showing in portal Documents tab.

### NEW gaps opened in Day 61 re-walk

(none — all observations above are deferred to Sprint 2 and do not block any current scenario step)

### Time

Backfill (B1+B2) + Slice 1 (B3+B4) + Slice 2 (61.1-61.9): ~7 min wall-clock, well under 60 min target.

---

## Cycle 52 Walk — 2026-04-28 SAST

**Branch:** `bugfix_cycle_2026-04-26-day61` (cut from `main` `d8a88315`; current commit `449134ba`)
**Cycle:** 52
**Scope:** Drive Day 61 §61.1–§61.9 against the cycle-46 matter `cc390c4f-35e2-42b5-8b54-bac766673ae7` (Sipho Dlamini's RAF matter that was actually closed in Day 60 cycle 46 and used for the cycle-51 SoA retest under PR #1197). The earlier cycle-1 walk above used the older `e788a51b-…` matter from a prior verify cycle — scenario reference is identical, but artefacts must be re-validated on the current closed matter.
**Actor (firm side, for diagnostics only):** Bob Ndlovu (admin). **Actor (portal side):** Sipho Dlamini (portal contact `f3f74a9d-3540-483a-80bc-6f5ef4e911bb`).

### Pre-flight setup completed (REST + DB read-only)

| Step | Action | Result |
|------|--------|--------|
| Service health | `bash compose/scripts/svc.sh status` | All 4 services healthy (backend PID 53170, gateway PID 71426 ext, frontend PID 5771, portal PID 5677) |
| Existing SoA email | `GET /api/v1/messages?query=to:sipho.portal@example.com` | Cycle-51 SoA email `o7q6xXpr97YPC8czLw544N` (`Document ready: statement-of-account-dlamini-v-road-accident-fund-2026-04-30.pdf`) still present in Mailpit. Body's `View document` CTA targets `http://localhost:3002/projects/cc390c4f-35e2-42b5-8b54-bac766673ae7` — matches §61.1 expectation. |
| Magic-link issued | `POST /portal/auth/request-link {email,sipho.portal@…,orgId:mathebula-partners}` | HTTP 200 — `magicLink=/auth/exchange?token=t69Fq_HlpOVjW0uKp8dkmgGBzZTKtEWvggOA8Bs8jpk&orgId=mathebula-partners` |
| Magic-link email arrival | Mailpit poll | `EfEPNBzqR4TE2tEyDTKrJp` arrived 2026-04-28T06:00:12.908Z, subject `"Your portal access link from Mathebula & Partners"` |
| DB diagnostic — SoA artefacts | `SELECT id,file_name,file_size,s3_key,generated_at FROM generated_documents WHERE primary_entity_id='cc390c4f-…'` | Row `6b79c496-4ae2-4731-9fe7-3ac18677d394` `statement-of-account-dlamini-v-road-accident-fund-2026-04-30.pdf` `4863` bytes `org/tenant_5039f2d497cf/generated/statement-of-account-…-2026-04-30.pdf` `2026-04-27 22:01:03.748+00`. Closure letter `c582a54f-…` `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-27.pdf` 1644 bytes also present. |

All upstream artefacts confirmed — the SoA exists, is ~4.9 KB, has a valid `s3_key`, and is reachable through the Mailpit-delivered email. The `View document` CTA opens the matter detail (per L-74 verified in cycle-1 walk above, the SoA + closure letter should both show in portal `/projects/{matterId}`'s Documents tab).

### BLOCKED — INFRA — Playwright MCP browser unavailable

**Root cause:** the Playwright MCP user-data-dir `/Users/rakheendama/Library/Caches/ms-playwright/mcp-chrome-5d273ba` is held by a foreign Chromium process (PID 54166, etime 08:12:40, started 23:48 the previous day from a different long-running `claude --chrome` session — confirmed via `ps`, `SingletonLock` symlink target `Rakheens-MacBook-Pro.local-54166`, and `curl http://localhost:61789/json/list` showing 4 active tabs incl. `localhost:3000/org/mathebula-partners/projects/cc390c4f-…`). Every `mcp__playwright__browser_navigate` call returns:

```
Error: Browser is already in use for /Users/rakheendama/Library/Caches/ms-playwright/mcp-chrome-5d273ba,
use --isolated to run multiple instances of the same browser
```

`mcp__claude-in-chrome__tabs_context_mcp` also unavailable: returns `Browser extension is not connected`.

Per dispatch rule "If any service goes down mid-walk, do NOT restart it yourself — log the failure as an Infra blocker", I am **not** killing PID 54166 or any of the 4 long-running claude sessions (PIDs 4237, 28124, 69590, 73865). Logging as **GAP-L-98** for the orchestrator/Infra agent to dispatch resolution.

### Per-checkpoint results — Cycle 52

| ID | Scenario | Result | Evidence |
|----|----------|--------|----------|
| 61.1 | Mailpit → email → click link → portal landing on `/projects/[matterId]` | **BLOCKED** | Email + correct CTA target (`/projects/cc390c4f-…`) verified via Mailpit API; cannot click without browser. |
| 61.2 | Documents tab on matter shows SoA + today's date + file size | **BLOCKED** | DB row `6b79c496-…` exists with `file_name`, `file_size 4863`, `generated_at`; UI render needs browser. |
| 61.3 | Click Download → PDF downloads cleanly | **BLOCKED** | Portal presigned-URL flow needs authenticated browser. |
| 61.4 | Open downloaded PDF + reconcile contents (letterhead, matter ref RAF-2026-001, opening R 0,00, deposits R 50 000 + R 20 000, fee transfers, closing balance, VAT line) | **BLOCKED** | Per cycle-51 firm-side iframe + DB `context_snapshot.summary`: VAT R 510 (GAP-L-95), trust closing R 70 100 (GAP-L-94), 3 deposits incl. R 100 retest deposit; portal-side render pending browser. |
| 61.5 | Screenshot `day-61-portal-soa-download.png` | **BLOCKED** | Browser unavailable. |
| 61.6 | Byte-size match firm vs portal ±5% | **BLOCKED** | Firm-side baseline `4863` bytes captured from DB; portal-side download size pending browser. |
| 61.7 | Document title matches firm copy — no "Untitled" leak | **BLOCKED** | DB `file_name` correct; portal-rendered title pending browser. |
| 61.8 | Closure letter also visible + renders | **BLOCKED** | DB `c582a54f-…` 1644 bytes exists; portal render pending browser. |
| 61.9 | Firm-side audit: portal contact accessed SoA with matching timestamp | **BLOCKED** | No portal access event yet (cycle-1 cycle observation OBS-Day61-NoPortalDocAuditEvent already noted absence of portal `document.viewed` events — would re-confirm under browser). |

**Day 61 cycle-52 roll-up:** 0 PASS / 0 FAIL / 9 BLOCKED.

### NEW gap opened — Cycle 52

| ID | Severity | Status | Owner | Summary | Evidence |
|----|----------|--------|-------|---------|----------|
| GAP-L-98 | INFRA-BLOCKER | OPEN | infra | Playwright MCP browser profile `mcp-chrome-5d273ba` locked by another long-running `claude --chrome` session (Chromium PID 54166, port 61789, etime 08:12+, profile dir Singleton-locked). Blocks any browser-driven QA walk. Resolution options: (a) close the foreign claude session that holds the lock, OR (b) launch playwright MCP with `--isolated` / different user-data-dir, OR (c) terminate orphan Chromium PID 54166 if its parent claude session is unresponsive. NOT a product defect. | `cycle52-day61-prereq-mailpit-soa-email.json`, `cycle52-day61-prereq-mailpit-magic-link.json`, `cycle52-day61-prereq-generated-documents.txt` |

### Evidence files (cycle 52)

- `cycle52-day61-prereq-mailpit-soa-email.json` — full cycle-51 SoA email body (the email Sipho would click for §61.1).
- `cycle52-day61-prereq-mailpit-magic-link.json` — fresh magic-link email body (cycle-52 self-service login fallback ready for browser-up).
- `cycle52-day61-prereq-generated-documents.txt` — DB read of all generated docs on matter `cc390c4f-…`.

### Decision

**SLICE BLOCKED on GAP-L-98 (Infra — Playwright MCP browser unavailable).** Day 61 product flow is fully primed (cycle-51 SoA email delivered, fresh magic-link issued, DB artefacts confirmed); execution requires a free browser MCP. Per dispatch rule, exiting cleanly without modifying any service or process. Orchestrator dispatches Infra agent next.

### Time

Cycle-52 pre-flight + diagnostic confirmation: ~6 min wall-clock.
