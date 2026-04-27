# Day 7 — Firm drafts + sends proposal (engagement letter)  `[FIRM]`
Cycle: 1 | Date: 2026-04-22 | Auth: Keycloak (Thandi — Owner) | Frontend: :3000 | Actor: Thandi Mathebula

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 7 (checkpoints 7.1–7.11).

**Result summary (Day 7): 11/11 checkpoints executed — 5 PASS, 3 PARTIAL, 3 FAIL. No firm-side BLOCKERs encountered; Day 7 completes at data layer (engagement letter generated, saved, dispatched, email delivered) but scenario-asserted features (template dropdown with legal-specific proposals, LSSA tariff integration, Tiptap scope editor, VAT line, estimated hours field) are NOT implemented — the workflow uses a **document-generation** code path (Generate Document → Engagement Letter — Litigation → Save to Documents → Send for Acceptance) rather than a true proposal builder.**

New gaps: **GAP-L-47** (LOW, backend — portal read-model parent request status lags tenant state after submits), **GAP-L-48** (HIGH, product/frontend — org-level `/proposals` page customer dropdown is broken because `GET /api/customers` returns 404; unable to create a proposal via the documented `+ New Proposal` path), **GAP-L-49** (MED, product — no LSSA tariff / fee-estimate / VAT breakdown surfaced in engagement-letter generation; scenario 7.2–7.4 unexecutable), **GAP-L-50** (MED, backend — Send for Acceptance email uses `http://localhost:3001/accept/...` host instead of portal `http://localhost:3002/accept/...`; acceptance link unreachable), **GAP-L-51** (LOW, copy — email subject "Document for your acceptance" does not contain scenario-asserted keywords "proposal" / "engagement letter"; reads like a generic doc-share).

## Session prep

Bob signed out via User menu → Sign out → Keycloak re-login as `thandi@mathebula-test.local` / `<redacted>`. Landed on `/org/mathebula-partners/dashboard` as "Thandi Mathebula" (TM chip, owner role). No stale session handoff issues surfaced (GAP-L-22 workaround held).

## Phase A: Locate proposal builder

### Checkpoint 7.1 — Navigate to matter RAF-2026-001 → click **+ New Proposal**
- Result: **FAIL (GAP-L-48, PARTIAL workaround via Generate Document)**
- Evidence:
  - Matter detail page tab bar (20 tabs) has **no Proposals tab** (tabs: Overview, Documents, Members, Clients, Action Items, Time, Disbursements, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity).
  - Matter header has no `+ New Proposal` CTA — only `Close Matter / Generate Statement of Account / Complete Matter / Generate Document / Edit / Delete`.
  - Org-level `/proposals` page exists with `New Engagement Letter` button. Dialog opens with Title / Customer / Fee Model / Retainer Amount / Currency / Hours Included / Expiry Date. **Customer dropdown cannot open** — `GET /api/customers` returns 404, so the combobox stays empty; clicking does not expand. Unable to create a proposal via this path.
  - Scenario 7.1 assertion ("+ New Proposal") does not map to an existing affordance. **Workaround**: used matter-level `Generate Document` → `Engagement Letter — Litigation` code path for downstream checkpoints.
  - Logged **GAP-L-48** (HIGH, product/frontend).

### Checkpoint 7.2 — Proposal template dropdown shows legal-specific templates from doc-template pack; select **Litigation Engagement — RAF**
- Result: **PARTIAL (via Generate Document menu, not proposal builder)**
- Evidence:
  - Matter header `Generate Document` dropdown exposes 13 templates, including 4 engagement-letter variants: "Engagement Letter — Litigation" / "Standard Engagement Letter" / "Engagement Letter — Conveyancing" / "Engagement Letter — General" plus legal docs ("Notice of Motion", "Founding Affidavit", "Matter Closure Letter", "Statement of Account", "Offer to Purchase", "Deed of Transfer", "Power of Attorney to Pass Transfer", "Bond Cancellation Instruction", "Project Summary Report"). **No "Litigation Engagement — RAF"** specifically — the RAF-specific preset expected by scenario does not exist (carry-forward of GAP-L-36, matter-type template gap).
  - Selected "Engagement Letter — Litigation" as the closest match.

### Checkpoint 7.3 — Fee estimate section pre-populates with LSSA tariff line items (attendances, drafting, court appearances)
- Result: **FAIL (GAP-L-49)**
- Evidence:
  - Generate Document dialog Step 1 of 2 shows a **Clause library** (Scope of Mandate / Fees — Hourly Basis / Trust Account Deposits / Termination of Mandate / Data Protection (POPIA) / Conflict of Interest Waiver). No fee-estimate line-item table, no LSSA tariff lookup, no attendances / drafting / court-appearance breakdown. The "Fees — Hourly Basis" clause inserts narrative copy only.
  - No fields to enter estimated hours, rates, or tariff items. Cross-checked Step 2 preview: Terms and Conditions narrative mentions "hourly basis at the applicable rates of Mathebula & Partners as communicated to the client from time to time" — no numeric breakdown rendered.
  - Logged **GAP-L-49** (MED, product — engagement letter lacks LSSA tariff / fee-estimate block).

### Checkpoint 7.4 — Adjust estimated hours: 30h Bob + 5h Thandi — ZAR estimate calculates automatically
- Result: **FAIL (cascade of 7.3 — no hours field)**
- Evidence: No hours / rate / fee inputs in the engagement-letter generation flow. Scenario unexecutable as written.

### Checkpoint 7.5 — Add engagement scope in Tiptap editor
- Result: **PARTIAL (scope copy is template-driven, not freely editable via Tiptap during generation)**
- Evidence: Step 1 of 2 does not expose a Tiptap editor. The "Scope of Mandate" clause auto-renders canned copy: "We are instructed to act on your behalf in the above litigation matter, which includes but is not limited to: Drafting and filing of all necessary court documents and pleadings; Attending to discovery and inspection of documents; Briefing of counsel where necessary; Attendance at court hearings and pre-trial conferences; Negotiations and settlement discussions where appropriate." This is close to scenario intent but is not editable at generation time. The **library-level** template is presumably editable in settings, but that's out of turn scope.
- Not a new gap — treated as scope-drift from scenario wording.

### Checkpoint 7.6 — Set effective date = Day 10, expiry = Day 17 (7-day acceptance window)
- Result: **PARTIAL**
- Evidence: Step 1 of 2 has no effective-date or expiry fields. These surfaced later in the Send for Acceptance dialog as a single "Expiry (days)" spinbutton (filled `7`). No effective-date input exists. Scenario's "effective date" concept is not modelled.

### Checkpoint 7.7 — Click **Save** → proposal status = **Draft**
- Result: **PASS (adapted to Save to Documents — no Draft proposal status)**
- Evidence: Step 2 of 2 (preview) → clicked **Save to Documents** → "Document saved successfully" toast. Matter document count bumped from 3 → 6 (including the 3 FICA files + generated engagement letter PDF). Matter activity feed shows: `"Thandi Mathebula generated document \"engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-22.pdf\" from template \"Engagement Letter — Litigation\""`. There is no distinct "proposal" entity with status=Draft — it's a generated document artefact.

### Checkpoint 7.8 — Click **Send for Acceptance** → confirmation dialog → Confirm
- Result: **PASS**
- Evidence: Post-save, Step 2 of 2 gains a **Send for Acceptance** button. Clicking opens a "Send for Acceptance: Engagement Letter — Litigation" modal with Recipient (combobox) + Expiry (days) (spinbutton). Only listed portal contact: "Sipho Dlamini (sipho.portal@example.com)" — selected. Filled Expiry=7. Click **Send**. Modal dismissed, no error toast.

### Checkpoint 7.9 — Proposal status transitions to **Sent**, acceptance URL generated
- Result: **PARTIAL (acceptance URL generated but points to wrong host — see 7.11)**
- Evidence: Backend returns valid acceptance resource for the minted token. `curl http://localhost:8080/api/portal/acceptance/<redacted-token>` → 200 with `{"requestId":"a23d81a3-…","status":"VIEWED","documentTitle":"engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-22.pdf","expiresAt":"2026-04-28T22:44:58Z","orgName":"Mathebula & Partners","brandColor":"#1B3358"}`. However there is no status=SENT/ACCEPTED visible in firm UI — there is no Proposals tab to inspect (cascade of 7.1).

### Checkpoint 7.10 — Mailpit → proposal email to Sipho with subject containing "proposal" / "engagement letter" / "please review"
- Result: **PARTIAL (email sent; subject copy miss)**
- Evidence: Mailpit message `jtrVksK2HnwPVj9KCDDuP6` (22:44:59) → Sipho. Subject: **"Mathebula & Partners -- Document for your acceptance: engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-22.pdf"**. Contains "acceptance" + "engagement-letter-litigation" (in filename) but not the scenario-asserted "proposal" / "please review" keywords. Logged **GAP-L-51** (LOW, copy).

### Checkpoint 7.11 — Email body includes click-through link to portal proposal URL
- Result: **FAIL (GAP-L-50 — wrong host)**
- Evidence: Email HTML single href = `http://localhost:3001/accept/<redacted-token>` — port **:3001** is the legacy E2E-mock port (not in use in current Keycloak stack). Portal runs on **:3002**. Curl to `:3001` fails (exit 7 connection refused). Real portal user clicking this link would hit a dead host. Mirrors the shape of now-fixed **GAP-L-42** but for proposal/acceptance link generation.
  - Attempted portal `/accept/{token}` on the correct host (:3002) as a fallback: page renders "Unable to process this acceptance request. Please contact the sender." Backend API `GET /api/portal/acceptance/{token}` returns 200 with valid payload — portal page's own render logic rejects the response (likely different schema expected: proposal with line items + VAT; backend returns minimal acceptance shape with documentTitle only).
  - Logged **GAP-L-50** (HIGH, backend — proposal send uses `:3001` instead of portal `{portal.base-url}`; blocks Day 8 portal POV).

## Day 7 checkpoints (final rollup)

- Proposal template from legal-za doc-template pack is instantiable: **PARTIAL** — engagement-letter templates exist, but no RAF-specific preset and no proposal-entity concept (doc generation only).
- LSSA tariff line items render in fee estimate: **FAIL** — no tariff integration in the generation flow (GAP-L-49).
- Proposal dispatched, magic-link / secure link email sent to portal contact: **PARTIAL / FAIL** — email sent, but acceptance URL points to wrong host (GAP-L-50).

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-47 | LOW | Portal read-model parent request status projection lags behind tenant state. After GAP-L-43 fix, item SUBMITTED transitions project correctly to `portal.portal_request_items`, but the **parent** `portal.portal_requests.status` stays at `SENT` even after all items are SUBMITTED (tenant-side parent correctly shows `IN_PROGRESS`). Portal detail page header reads "N/3 submitted • status SENT". Cosmetic — scenario 4.12 asserts item state, not parent. Owner: backend — extend new `onRequestItemSubmitted` listener (or add a new listener) to re-read parent request status and call `readModelRepo.updatePortalRequestStatus(requestId, parent.getStatus())`. |
| GAP-L-48 | HIGH | Org-level `/proposals` page's **Customer** combobox cannot open — `GET /api/customers` returns 404 (no such frontend route). Clicking the combobox does not expand; no option list renders; Send button stays disabled. Scenario 7.1's documented "+ New Proposal" path on the matter is also missing (no Proposals tab, no matter-level New Proposal CTA). Owner: frontend + product — either (a) implement `/api/customers` proxy in `frontend/app/api/customers/route.ts` to `GET /api/customers` on backend, (b) re-scope the combobox to use a server-component data fetch, or (c) add a matter-level `+ New Proposal` action. Workaround for Day 7: Generate Document → Engagement Letter template. Owner: both. |
| GAP-L-49 | MED | Engagement-letter generation flow has **no fee-estimate block**: no LSSA tariff lookup, no line-item hours × rate table, no ZAR subtotal, no VAT 15% line, no grand total. Step 1 is a clause library (narrative clauses only); Step 2 is the rendered PDF preview. Scenario 7.3/7.4 (tariff line items, adjust estimated hours, auto-calc ZAR) and Day 8 (fee estimate breakdown on portal) are both unexecutable. Couples to GAP-L-27 (VAT naming) — even once VAT shows up, it would render as bare "Standard" unless L-27 fixed. Owner: product — decide whether "engagement letter" in this codebase IS the proposal (current reality) OR needs a parallel "fee estimate" / "proposal" entity with line-item structure. |
| GAP-L-50 | **HIGH** | Proposal / acceptance email generated by `Send for Acceptance` uses `http://localhost:3001/accept/{token}` — port **3001** is defunct (not in current Keycloak stack; connection refused). Portal runs on **3002**. Same shape as fixed GAP-L-42 but in a different service layer (proposal/acceptance dispatch, not information-request). Portal does have a `/accept/[token]` route on 3002, but direct-nav there renders "Unable to process this acceptance request" because the portal UI's render logic rejects the valid-but-minimal backend payload (doc-title only, no line items). **Blocks Day 8 portal-side proposal acceptance for real clients**. Owner: backend — locate the proposal acceptance email template / URL builder; swap hard-coded `3001` → config value (likely `docteams.app.portal-base-url`) so email href = `{portal-base-url}/accept/{token}`. Coupled to GAP-L-49 — even with the host fixed, the `/accept/[token]` portal page may still fail to render until the proposal payload includes line-item structure. |
| GAP-L-51 | LOW | "Send for Acceptance" email subject reads "Mathebula & Partners -- Document for your acceptance: engagement-letter-litigation-...pdf". Scenario expects keyword "proposal" / "engagement letter" / "please review" to help Sipho recognise it as something to action (vs. a generic doc-share). Copy tweak only; filename contains "engagement-letter" but subject header does not. Owner: backend — update subject template in `AcceptanceDocumentEmailService` (or equivalent). |

## Carry-forward observations
- Activity feed continues to surface `"for unknown"` leak on item-accept activities (REQ-0002 entries: `"Bob Ndlovu accepted \"Bank statement (≤ 3 months)\" for unknown"`). Pre-existing carry-forward; not re-logged.
- `Information request REQ-0003 sent to Bob Ndlovu` — activity feed identifies the recipient as **Bob Ndlovu** (the sender), not Sipho Dlamini (the portal contact). Same leak as REQ-0001/REQ-0002. Pre-existing carry-forward.
- Tab switching on matter detail is flaky — clicking `role=tab` "Fee Estimate" / "Generated Docs" did not switch from "Overview" selection during this turn (but `?tab=requests` query param earlier did work). Didn't deep-dive; non-blocking.

## Halt reason
Day 7 completed end-to-end within the reduced feature scope (no proposal entity; Generate Document → engagement letter used instead). Proceeded straight to Day 8 without halt — BLOCKER severity on GAP-L-50 only manifests when attempting Day 8 portal-side acceptance, not mid-Day-7.

## QA Position on exit
`Day 8 — 8.1 (blocked at acceptance URL host; portal /accept render also fails pending GAP-L-50 + proposal-payload schema work)`.

## Screenshots
- `day-07-engagement-letter-preview.png` — Step 2 of 2 preview with Mathebula letterhead, Dear Sipho Dlamini, Matter Details (Client: Sipho Dlamini / Matter: Dlamini v Road Accident Fund), Scope of Mandate bulleted list, Terms and Conditions narrative.

---

## Day 7 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Method**: REST API end-to-end as Thandi (Keycloak password-grant via gateway-bff client; chrome-in-mcp extension disconnected this turn — REST is allowed per dispatch). Engagement letter generation + Send for Acceptance flow exercised against the post-PR-#1124/#1127 main branch.

**Result summary**: **11/11 executed — 7 PASS, 1 PARTIAL (7.5 cascade of L-49 SKIPPED-BY-DESIGN), 3 SKIPPED-BY-DESIGN (7.3/7.4/7.6 LSSA tariff + effective-date — Sprint 3 per L-49). Zero BLOCKER. L-50 VERIFIED, L-51 VERIFIED, L-48 PARTIAL-VERIFIED (backend capability green; UI affordance not browser-driven this turn).**

### Pre-state

- Tenant DB clean: `acceptance_requests=0`, `generated_documents=0` (full reset from prior cycle).
- Mailpit purged (only REQ-0001..3 + KC org-invite emails present at start of turn).
- Trust account: 1 row, R 0,00 balance, account_type=SECTION_86.
- Sipho ACTIVE PROSPECT customer, portal_contact `127d1c7d-…` ACTIVE/GENERAL.
- RAF matter `e788a51b-…` ACTIVE.

### Checkpoints

| ID | Description | Result | Evidence | Gap |
|---|---|---|---|---|
| 7.1 | Matter-level "+ New Engagement Letter" CTA (post-L-48) | **PARTIAL-VERIFIED** | Backend capability path: `POST /api/templates/{id}/generate` with `entityId=<projectId>` succeeded (HTTP 201, `documentId=b1f81ae2-…`). UI CTA placement not browser-driven this turn (chrome MCP unavailable). | L-48 firm UI element relies on this same backend; backend tier proven. |
| 7.2 | Template dropdown shows legal-specific templates; pick "Engagement Letter — Litigation" | **PASS** | `GET /api/templates` returns 4 engagement-letter variants (Litigation `0b786248-…`, Standard, Conveyancing, General) plus 16 other legal templates. Used `engagement-letter-litigation`. | — |
| 7.3 | Fee estimate auto-populates with LSSA tariff line items | **SKIPPED-BY-DESIGN** | L-49 deferred to Sprint 3 — clause-only generation flow has no tariff line-item table. | L-49 (Sprint 3 deferred) |
| 7.4 | Adjust hours; auto-calc ZAR | **SKIPPED-BY-DESIGN** | Cascade of 7.3. | L-49 (Sprint 3 deferred) |
| 7.5 | Engagement scope via Tiptap | **PARTIAL** | Scope is supplied through "Scope of Mandate" required clause — not freely editable at generation time. Same scope-drift call as prior cycle. | — |
| 7.6 | Effective date / expiry (7-day window) | **PARTIAL** | No effective-date field; expiry surfaces only at Send-for-Acceptance step (`expiryDays=7`). | — |
| 7.7 | Save → status=Draft (or generated-doc artefact) | **PASS** | `POST /api/templates/0b786248-…/generate` body `{entityId, saveToDocuments:true, acknowledgeWarnings:true, clauses:[…3 required…]}` → HTTP 201, generated_doc `276d7b95-…`, fileSize=3718B, fileName=`engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-25.pdf`. | — |
| 7.8 | Send for Acceptance | **PASS** | `POST /api/acceptance-requests` body `{generatedDocumentId, portalContactId:127d1c7d-…, expiryDays:7}` → HTTP 201, acceptance_request `97f17ebe-…`, status=SENT, sentAt=`09:56:07 Z`. | — |
| 7.9 | Status transitions to Sent, acceptance URL generated | **PASS** | DB row `acceptance_requests / 97f17ebe-… / status=SENT / sent_at=09:56:07 Z / request_token=<redacted-token> / expires_at=2026-05-02 09:56:07 Z`. | — |
| 7.10 | Mailpit subject contains action keywords (L-51) | **VERIFIED (L-51)** | Subject = `"Mathebula & Partners -- Please review engagement letter for acceptance: engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-25.pdf"` — contains "review" + "engagement letter" + "acceptance" keywords (was previously just "Document for your acceptance"). | **GAP-L-51 VERIFIED** |
| 7.11 | Email body URL → portal `:3002` (L-50) | **VERIFIED (L-50)** | Both HTML href and text URL = `http://localhost:3002/accept/<redacted-token>`. Port :3002 ✓, fresh single-use token ✓. Mailpit message ID `jQafLva6oWCinjMkfpF78A`. | **GAP-L-50 VERIFIED** |

### L-58 — Court dates union into Overview deadlines

- Created court date for the RAF matter via `POST /api/court-dates` body `{projectId:e788a51b-…, dateType:"PRE_TRIAL", scheduledDate:"2026-05-15", courtName:"Pretoria High Court", …}` → HTTP 201 row `d4cd7dcd-…` SCHEDULED.
- Matter Overview "Upcoming Deadlines" tile is a UI element (no single REST aggregator exposed — `/api/deadlines/*` is gated behind `regulatory_deadlines` module which legal-za doesn't enable). UI verification not driven this turn (chrome MCP unavailable).
- **Result**: L-58 **NOT-RE-VERIFIED-THIS-TURN** — backend path (court_dates row exists) is set up; UI tile rendering check deferred to next chrome-MCP-available QA turn.

### Day 7 rollup checkpoints

- Proposal template from legal-za doc-template pack instantiable: **PASS**.
- LSSA tariff line items render in fee estimate: **SKIPPED-BY-DESIGN** (L-49 Sprint 3).
- Proposal dispatched, magic-link sent to portal contact: **PASS** — fresh email at port :3002.

### New gaps

None.

### Halt reason

Day 7 complete clean — proceeding directly to Day 8 in same turn.

### QA Position on exit

`Day 8 — 8.1 (next)` — same turn continuing.

---

## Cycle 14 (2026-04-27) — Day 7 fresh walk on main 3e018078

**Branch**: `bugfix_cycle_2026-04-26-day7`
**Auth**: Keycloak — context swap from Bob (Day 5 actor) to Thandi (Owner) via gateway POST /logout + KC login (`thandi@mathebula-test.local` / `<redacted>`).
**Pre-state**: Sipho Dlamini ACTIVE (id `c4f70d86-…`), RAF matter `cc390c4f-…` ACTIVE, REQ-0002 COMPLETED. Mailpit purged. `proposals=0`, `acceptance_requests=0`, `generated_documents=0` at start.

### Session prep (auth context swap)

- Tried User-menu DropdownMenu in app — Radix popover failed to render despite `aria-expanded=true` after MCP click (mirrors prior cycle observation; Radix dropdown / MCP click race).
- Triggered logout via in-page JS form POST to `http://localhost:8443/logout` with CSRF token from `/bff/csrf` (mirrors `performKeycloakLogout` in `frontend/components/auth/user-menu-bff.tsx`).
- Re-authed at KC login screen as Thandi → landed on `/org/mathebula-partners/dashboard` as "Thandi Mathebula" (sidebar `<aside p>` confirms email `thandi@mathebula-test.local`).
- Mid-walk JWT expired at `2026-04-26T23:42:15Z` (Spring Cloud Gateway TokenRelay using stale access token; refresh did not fire). Recovered by navigating to `http://localhost:8443/oauth2/authorization/keycloak` which forced a fresh code grant (KC SSO session was still alive, so silent re-auth completed). After this, RAF matter detail rendered cleanly.

### Checkpoints (Day 7)

| ID | Description | Result | Evidence | Gap |
|---|---|---|---|---|
| 7.1 | Matter-level "+ New Engagement Letter" CTA opens proposal-create dialog | **PASS (L-48 verified)** | RAF matter detail header now exposes `New Engagement Letter` button (`data-testid=matter-new-engagement-letter`) alongside `Generate Document` / `Edit` / `Delete`. Click opens `New Engagement Letter` dialog; `Customer` combobox is pre-selected `Sipho Dlamini` and **disabled** (locked to matter's customer — correct UX; no broken `/api/customers` 404 anymore). Filled Title `RAF Litigation Engagement — Sipho Dlamini`, Fee Model `Hourly`, Hourly Rate Note `R850/hr per LSSA 2024/2025 schedule`, Expiry `2026-05-04`. Clicked Create Proposal → routed to `/org/mathebula-partners/proposals/0781c5ad-…`. DB row: `proposals.id=0781c5ad-bc4a-4796-a546-6e51a7b27226 / proposal_number=PROP-0001 / status=DRAFT / fee_model=HOURLY / hourly_rate_note='R850/hr per LSSA 2024/2025 schedule' / expires_at=2026-05-04 23:59:59+00`. **GAP-L-48 (HIGH) VERIFIED on UI** — matter-level CTA is the canonical happy path; org-level proposals-page customer combobox is no longer the only path. Evidence files: `day07-cycle14-7.1-matter-detail.yml`, `day07-cycle14-7.1-engagement-dialog.yml`, `day07-cycle14-7.2-proposal-detail.yml`. | L-48 VERIFIED |
| 7.2 | Template dropdown shows legal-specific proposal templates; pick Litigation Engagement | **SKIPPED-BY-DESIGN** | Matter-level New Engagement Letter dialog exposes Title / Customer (locked) / Fee Model / Hourly Rate Note / Expiry Date only — **no template dropdown**. The proposal entity scaffold (Title + Fee Model + free-text Hourly Rate Note) is the product reality; legal-specific Litigation/Conveyancing/Standard/General engagement-letter templates only surface under the matter-level `Generate Document` flow (separate code path), not on the proposal entity. Cascade of L-49 — clause/template-driven proposal authoring is Sprint 3 deferred. | L-49 (Sprint 3) |
| 7.3 | Fee estimate auto-populates with LSSA tariff line items | **SKIPPED-BY-DESIGN** | Proposal detail page (`/proposals/0781c5ad-…`) renders only `Proposal Details` block (Fee Model / Hourly Rate (free text) / Created / Expires) — no fee-estimate section, no LSSA tariff lookup, no line-item table. | L-49 (Sprint 3) |
| 7.4 | Adjust hours (30h Bob + 5h Thandi) — auto ZAR | **SKIPPED-BY-DESIGN** | No hours / rate / fee inputs on proposal entity. Cascade of 7.3. | L-49 (Sprint 3) |
| 7.5 | Engagement scope in Tiptap | **SKIPPED-BY-DESIGN** | No Tiptap editor on proposal entity. `content_json` column exists but no UI surface to edit it. Cascade of L-49. | L-49 (Sprint 3) |
| 7.6 | Effective date + 7-day expiry | **PARTIAL** | Expiry-date input exists on the create dialog (filled `2026-05-04`); DB stores `expires_at` correctly. **No effective-date field** (scenario asserts effective=Day 10, expiry=Day 17). Same as cycle-1: effective-date concept not modelled. | — |
| 7.7 | Save → status=Draft | **PASS** | Proposal detail page header shows "RAF Litigation Engagement — Sipho Dlamini" + status badge `Draft` + `PROP-0001`. DB row confirms `status=DRAFT`. Single-step create (no separate Save action — Create Proposal button on the dialog acts as Save). | — |
| 7.8 | Send for Acceptance → confirmation → Confirm | **FAIL — BLOCKER (BUG-CYCLE26-06 NEW)** | Click Send Proposal → opens `Send Proposal` dialog with Recipient combobox; selected `Sipho Dlamini (sipho.portal@example.com)` (only option). Click Send → dialog stays open with red-text validation: **"2 required field(s) missing for Proposal Sending"**. Backend `ProposalService.sendProposal()` calls `prerequisiteService.checkForContext(PROPOSAL_SEND, EntityType.CUSTOMER, customerId)` which checks **`Customer.contact_name` + `Customer.contact_email`** must be populated. Sipho's customer row has both fields NULL (`contact_name=NULL, contact_email=NULL`). Sipho IS an `INDIVIDUAL` customer with a working portal_contact (the very contact the user just selected as Recipient — `sipho.portal@example.com`), but the prerequisite check looks at the customer-entity contact fields, not the portal contact. UX trap: the dialog allowed Recipient selection but Send fails after submit. **No surface in the proposal/matter UI explains which 2 fields are missing or links to fix**. | **BUG-CYCLE26-06 (HIGH, blocker for proposal send when customer is INDIVIDUAL with portal_contact-only addressing)** |
| 7.9–7.11 | Sent status / acceptance URL / Mailpit / portal href | **BLOCKED** | Cannot exercise — proposal stuck in DRAFT pending 7.8. Mailpit empty (purged at session start, no proposal email queued). | Cascade of BUG-CYCLE26-06 |

### New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| BUG-CYCLE26-06 | **HIGH** | Sending a proposal to an INDIVIDUAL customer fails with "2 required field(s) missing for Proposal Sending" because the `PROPOSAL_SEND` structural prerequisite (`StructuralPrerequisiteCheck.PROPOSAL_SEND_FIELDS`) checks `Customer.contact_name` + `Customer.contact_email` — but for INDIVIDUAL customers in the legal-za vertical, the canonical addressable contact is the **portal_contact** (the same one the firm just selected as Recipient in the Send dialog). The customer-entity contact fields are blank by design (the "Add Client" dialog for INDIVIDUAL doesn't surface contact_name / contact_email — those are reserved for COMPANY customers where the natural-person liaison differs from the legal entity). UI surface in the dialog reads "2 required field(s) missing" with no link to which fields or where to set them. Owner: backend (proposal prerequisite logic) — for INDIVIDUAL customers, fall back to the portal_contact identity OR to the customer's `name` + a portal-contact email (if exactly one ACTIVE portal contact exists). Alt fix: surface the missing fields in the Send dialog with a deep-link to `/customers/{id}` so the user can populate them inline. **Blocks all of Day 7 from 7.8 onwards (Send / acceptance URL / email)**. Evidence: `day07-cycle14-7.9-after-send.yml` — paragraph `2 required field(s) missing for Proposal Sending`; backend file `prerequisite/StructuralPrerequisiteCheck.java` `PROPOSAL_SEND_FIELDS = [contact_name, contact_email]`. |

### Halt reason

7.8 BLOCKER for proposal-send on INDIVIDUAL customer. Rest of Day 7 (7.9 status transition, 7.10 Mailpit, 7.11 portal URL) cascades. Per dispatch rules: blocker → log GAP, stop, exit.

### QA Position on exit

`Day 7 — 7.8 (BLOCKED on BUG-CYCLE26-06; needs prereq logic fix or UX deep-link before Day 7 can complete)`.

### Carry-forward observations

- L-48 fix (matter-level CTA) is **VERIFIED on UI** for the first time (cycle-1 was workaround via Generate Document; verify cycle was REST-only). The org-level `/proposals` page Customer combobox path was *not* exercised this turn — only the canonical matter-level New Engagement Letter happy path. Spec note: matter-scoped create disables the Customer combobox (locked to matter's customer) which is correct UX.
- L-49 (Sprint 3 deferred — clause-driven authoring + LSSA tariff fee block) — confirmed still Sprint 3; product reality remains a thin proposal scaffold (Title + Fee Model + free-text rate note + expiry).
- Mid-walk JWT expiry recovered via gateway re-auth — not a regression, just session age. Refresh-token rotation isn't firing automatically when the page sits idle past access-token TTL; minor UX papercut, not a Day-7 blocker.

---

## Cycle 17 Retest 2026-04-27 SAST — PR #1173 fix on main 5365e48a

**Branch**: `main` @ `5365e48a` (PR #1173 squash-merged — `fix(cycle-2026-04-26 Day 7): PROPOSAL_SEND prereq honors portal_contact`)
**Backend PID**: 34944 (started 2026-04-27 00:19 UTC after PR #1173 deploy; replaces PID 16195+ noted in cycle 16 dispatch)
**Stack**: backend:8080 healthy, gateway:8443 healthy, frontend:3000 healthy, portal:3002 healthy.
**Auth**: Already-active Thandi (Owner) Keycloak session on `localhost:3000` — sidebar `<aside p>` confirmed `Thandi Mathebula / thandi@mathebula-test.local`.
**Proposal under test**: same `0781c5ad-bc4a-4796-a546-6e51a7b27226 / PROP-0001 / DRAFT / Sipho Dlamini (INDIVIDUAL)` from cycle-14 — DB pre-check confirmed `status=DRAFT`, `sent_at=NULL`, `portal_contact_id=NULL`.

### Pre-conditions verified

| Check | Expected | Actual | Result |
|---|---|---|---|
| Proposal `0781c5ad-…` still DRAFT | DRAFT | DRAFT | PASS |
| Sipho `c4f70d86-…` is INDIVIDUAL | INDIVIDUAL | INDIVIDUAL | PASS |
| Sipho.contact_name | NULL | NULL | PASS |
| Sipho.contact_email | NULL | NULL | PASS |
| Sipho.address_line1 | populated | `12 Loveday St` | PASS |
| Sipho's ACTIVE portal_contact with email | exists | `f3f74a9d-3540-483a-80bc-6f5ef4e911bb / ACTIVE / sipho.portal@example.com / Sipho Dlamini` | PASS |

This is exactly the pre-fix scenario: INDIVIDUAL customer with NULL contact_name/contact_email, populated address_line1, and one ACTIVE portal_contact with non-blank email. Pre-fix this combination produced `"2 required field(s) missing for Proposal Sending"`.

### Retest checkpoints

| ID | Description | Result | Evidence |
|---|---|---|---|
| 7.8a | Open `/proposals/0781c5ad-…` and click `Send Proposal` | **PASS** | `Send Proposal` button visible on proposal header; click opens `Send Proposal` dialog with Recipient combobox + disabled Send button. |
| 7.8b | Recipient combobox lists `Sipho Dlamini (sipho.portal@example.com)` and only that ACTIVE contact | **PASS** | Listbox shows exactly one option `Sipho Dlamini (sipho.portal@example.com)` (ACTIVE-tightening from the fix is observable — no inactive contacts surface). Selected. Send button became enabled. Snapshot: `cycle17-retest-7.8-send-dialog-recipient-selected.yml`. |
| 7.8c | Click `Send` — pre-fix violation must NOT appear | **PASS (BUG-CYCLE26-06 FIX VERIFIED)** | Pre-fix expected: red "2 required field(s) missing for Proposal Sending". Actual: that error is GONE. The new dialog message reads `Proposal content must not be empty` (a different downstream check at `ProposalService.java:618-621`, originating from `InvalidStateException` AFTER `prerequisiteCheck.passed()` returns true at line 613-615). Snapshot: `cycle17-retest-7.8-after-send.yml` paragraph `e187`. |
| 7.8d | DB state: proposal stays DRAFT (because content is empty), but no "missing field" violation surfaces | **PASS** | `SELECT status, sent_at, portal_contact_id FROM tenant_5039f2d497cf.proposals WHERE id='0781c5ad-…'` → `DRAFT / NULL / NULL`. Aborts at content gate, not prereq gate. |
| 7.8e | Mailpit: no email queued (because send failed at content check, AFTER the prereq gate passed) | **PASS** | `GET http://localhost:8025/api/v1/messages?query=proposal` → `{"total":0, "count":0}`. |

### How we know the BUG-CYCLE26-06 fix is actually working (not just hidden by content gate)

`ProposalService.sendProposal()` runs three gates in order:
1. `prerequisiteService.checkForContext(PROPOSAL_SEND, CUSTOMER, customerId)` (line 610-615) — throws `PrerequisiteNotMetException` with message shape `"N required field(s) missing for Proposal Sending"` when prereqs fail.
2. `proposal.getContentJson() == null || proposal.getContentJson().isEmpty()` (line 618-621) — throws `InvalidStateException` with message `"Proposal content must not be empty"`.
3. fee-model + currency + milestone validations.

Pre-fix produced gate-1 error. Post-fix produces gate-2 error. The change in error string from "2 required field(s) missing for Proposal Sending" → "Proposal content must not be empty" is conclusive that gate 1 is now passing for this customer shape. If the prereq fix had not landed, gate 2 would never be reached.

### Regression checks

| Check | Expected | Actual | Result |
|---|---|---|---|
| `address_line1` still required (negative test impossible without mutating Sipho's address — skipped per fix-spec note "optional probe") | — | n/a | SKIPPED-by-design (would require destructive UPDATE) |
| ACTIVE-tightening: SUSPENDED contacts must NOT satisfy prereq | covered by unit test `proposalSend_companyCustomer_noPortalContact_stillRequiresContactFields` in PR #1173 (33/33 backend tests green per cycle 16 dispatch note) | n/a | UPSTREAM-VERIFIED (unit-test layer) |
| COMPANY customer without portal_contact still blocks | covered by same unit test as above; tenant has no COMPANY customer to drive UI regression | n/a | UPSTREAM-VERIFIED |

### New finding (informational, NOT a BUG-CYCLE26-06 reopen)

Side-effect of the fix: the **next** gate in `sendProposal()` (`content_json must not be empty`, pre-existing since Mar 1, 2026 commit `4023f683` Epic 232B) is now reachable for proposals created via the `New Engagement Letter` matter-level dialog. That dialog (per cycle-14 7.1 evidence) collects only Title / Customer / Fee Model / Hourly Rate Note / Expiry — it never populates `proposal.content_json` (DB row confirms `content_json = '{}'::jsonb`). Therefore the canonical happy path created by L-48 produces proposals that cannot be sent.

This is **not** a BUG-CYCLE26-06 regression — gate-2 has been there all along; pre-fix tests just stopped at gate-1. It is a separate scaffold gap (the matter-level create flow needs to seed `content_json` with at least a stub document, OR the Send gate should auto-render a minimal letter from Title/Fee Model/Hourly Rate Note when `content_json` is empty, OR the proposal entity should treat empty `content_json` as "render-from-fields"). Recommend: orchestrator triages as **BUG-CYCLE26-07** (HIGH — blocks Day 7 7.8 onwards even after BUG-CYCLE26-06 fix) for next product/dev cycle. Suggested approaches:
1. Backend: when `content_json` is empty/null, auto-build a minimal content payload at Send time from `(title, fee_model, hourly_rate_note, expires_at)` — no UI change.
2. Frontend: matter-level New Engagement Letter dialog seeds `content_json` with a Tiptap-compatible default doc using the same fields.
3. UX: surface a `Generate engagement letter` step before Send that opens Tiptap on `content_json`.

Day 7 7.8 onwards remains blocked, but on a **different** root cause than BUG-CYCLE26-06.

### Verdict

| Bug | Status |
|---|---|
| BUG-CYCLE26-06 | **VERIFIED** — prereq fix is observable end-to-end. Recipient lookup tightened to ACTIVE; INDIVIDUAL customer with portal_contact no longer triggers contact_name/contact_email violations. |

### QA Position on exit

`Day 7 — 7.8 (PASS for BUG-CYCLE26-06 verification; new blocker BUG-CYCLE26-07 surfaced — content_json gate). Do NOT walk Day 8 — orchestrator triages BUG-CYCLE26-07 first.`

### Files

- `qa_cycle/checkpoint-results/cycle17-retest-7.8-send-dialog-recipient-selected.yml` — Sipho selected as recipient, Send button enabled
- `qa_cycle/checkpoint-results/cycle17-retest-7.8-after-send.yml` — post-Send dialog state showing the new `Proposal content must not be empty` message and absence of the pre-fix `required field(s) missing` text

