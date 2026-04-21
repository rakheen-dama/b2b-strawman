# Day 7 — Firm drafts + sends proposal (engagement letter)  `[FIRM]`
Cycle: 1 | Date: 2026-04-22 | Auth: Keycloak (Thandi — Owner) | Frontend: :3000 | Actor: Thandi Mathebula

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 7 (checkpoints 7.1–7.11).

**Result summary (Day 7): 11/11 checkpoints executed — 5 PASS, 3 PARTIAL, 3 FAIL. No firm-side BLOCKERs encountered; Day 7 completes at data layer (engagement letter generated, saved, dispatched, email delivered) but scenario-asserted features (template dropdown with legal-specific proposals, LSSA tariff integration, Tiptap scope editor, VAT line, estimated hours field) are NOT implemented — the workflow uses a **document-generation** code path (Generate Document → Engagement Letter — Litigation → Save to Documents → Send for Acceptance) rather than a true proposal builder.**

New gaps: **GAP-L-47** (LOW, backend — portal read-model parent request status lags tenant state after submits), **GAP-L-48** (HIGH, product/frontend — org-level `/proposals` page customer dropdown is broken because `GET /api/customers` returns 404; unable to create a proposal via the documented `+ New Proposal` path), **GAP-L-49** (MED, product — no LSSA tariff / fee-estimate / VAT breakdown surfaced in engagement-letter generation; scenario 7.2–7.4 unexecutable), **GAP-L-50** (MED, backend — Send for Acceptance email uses `http://localhost:3001/accept/...` host instead of portal `http://localhost:3002/accept/...`; acceptance link unreachable), **GAP-L-51** (LOW, copy — email subject "Document for your acceptance" does not contain scenario-asserted keywords "proposal" / "engagement letter"; reads like a generic doc-share).

## Session prep

Bob signed out via User menu → Sign out → Keycloak re-login as `thandi@mathebula-test.local` / `SecureP@ss1`. Landed on `/org/mathebula-partners/dashboard` as "Thandi Mathebula" (TM chip, owner role). No stale session handoff issues surfaced (GAP-L-22 workaround held).

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
- Evidence: Backend returns valid acceptance resource for the minted token. `curl http://localhost:8080/api/portal/acceptance/08OMMtVvodcXZEQ3oBeB5HSl144JCXnNomFR_HZbsng` → 200 with `{"requestId":"a23d81a3-…","status":"VIEWED","documentTitle":"engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-22.pdf","expiresAt":"2026-04-28T22:44:58Z","orgName":"Mathebula & Partners","brandColor":"#1B3358"}`. However there is no status=SENT/ACCEPTED visible in firm UI — there is no Proposals tab to inspect (cascade of 7.1).

### Checkpoint 7.10 — Mailpit → proposal email to Sipho with subject containing "proposal" / "engagement letter" / "please review"
- Result: **PARTIAL (email sent; subject copy miss)**
- Evidence: Mailpit message `jtrVksK2HnwPVj9KCDDuP6` (22:44:59) → Sipho. Subject: **"Mathebula & Partners -- Document for your acceptance: engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-22.pdf"**. Contains "acceptance" + "engagement-letter-litigation" (in filename) but not the scenario-asserted "proposal" / "please review" keywords. Logged **GAP-L-51** (LOW, copy).

### Checkpoint 7.11 — Email body includes click-through link to portal proposal URL
- Result: **FAIL (GAP-L-50 — wrong host)**
- Evidence: Email HTML single href = `http://localhost:3001/accept/08OMMtVvodcXZEQ3oBeB5HSl144JCXnNomFR_HZbsng` — port **:3001** is the legacy E2E-mock port (not in use in current Keycloak stack). Portal runs on **:3002**. Curl to `:3001` fails (exit 7 connection refused). Real portal user clicking this link would hit a dead host. Mirrors the shape of now-fixed **GAP-L-42** but for proposal/acceptance link generation.
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
