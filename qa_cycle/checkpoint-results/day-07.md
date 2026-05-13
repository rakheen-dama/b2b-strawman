# Day 7 — Firm drafts + sends proposal (engagement letter) — Checkpoint Results

**Date**: 2026-05-14
**QA Agent**: cycle 1 (branch `bugfix_cycle_2026-05-13`)
**Branch**: `bugfix_cycle_2026-05-13`
**Stack**: Keycloak dev (firm `:3000`, portal `:3002`, backend `:8080`, gateway `:8443`, KC `:8180`, Mailpit `:8025`)

---

## Pre-flight

- Stack health verified: all 4 services UP (backend :8080, gateway :8443, frontend :3000, portal :3002).
- Mailpit: 10 messages from Days 0-5 present at start.
- Logged in as Thandi Mathebula (`thandi@mathebula-test.local` / `SecureP@ss1`) via Keycloak OIDC redirect. Landed on `/org/mathebula-partners/dashboard`. Zero console errors on dashboard.

---

## Checkpoint Execution

### 7.1 — Navigate to matter RAF-2026-001, click + New Engagement Letter

- Navigated to `/org/mathebula-partners/projects/c90832a4-c993-4eaa-9ea7-404a259b0e29`.
- Matter detail renders: "Dlamini v Road Accident Fund", status **Active**, ref `RAF-2026-001`.
- Action bar shows **"New Engagement Letter"** button (legal-za terminology mapping: Proposal -> Engagement Letter).
- Clicked "New Engagement Letter" -> dialog opened.
- Console errors on matter page: 2x OBS-203 (`/api/assistant/invocations` 404) -- known pre-existing nit.

**Result: PASS**

### 7.2 — Dialog opens with Client pre-filled = Sipho Dlamini (disabled)

- Dialog title: "New Engagement Letter"
- Client combobox: **Sipho Dlamini** (disabled, pre-filled from matter context).
- Fields visible: Title, Client (disabled), Fee Model (combobox), Hourly Rate Note (optional), Expiry Date (optional).
- No template-picker (OBS-701 WONT_FIX from previous cycle -- proposal authoring is a thin lifecycle wrapper, not a document-builder).

**Result: PASS**

### 7.3 — Set Title

- Filled Title = "Engagement Letter -- Litigation (Dlamini v RAF)".

**Result: PASS**

### 7.4 — Fee Model = Hourly, set Hourly Rate Note

- Fee Model defaulted to **Hourly** (legal-za default for engagement letters). Correct.
- Filled Hourly Rate Note = "R 2,500/hr (LSSA tariff High Court Party-and-Party 2024/2025) -- 30h Bob Ndlovu (attorney) + 5h Thandi Mathebula (senior partner) ~ R 87,500.00 estimate."

**Result: PASS**

### 7.5 — Set Expiry Date = Day 17 (7-day acceptance window)

- Set Expiry Date = `2026-05-21` (7 days from today).
- HTML date input accepted ISO format.

**Result: PASS**

### 7.6 — Click Create Proposal -> redirected to detail page

- Clicked "Create Proposal" -> redirected to `/org/mathebula-partners/proposals/d7481b7a-8878-43ee-928c-2845bf8bffd0`.
- Status badge = **Draft**.
- Reference = **PROP-0001**.
- Proposal Details rendered:
  - Fee Model: Hourly
  - Hourly Rate: full breakdown text
  - Created: May 14, 2026
  - Expires: **May 21, 2026** (matches input 2026-05-21 -- no +1 day tz drift; OBS-702 fix confirmed)

**Result: PASS**

### 7.7 — Click Send Proposal -> select recipient -> Send

- Clicked "Send Proposal" button on detail page -> "Send Proposal" sub-dialog opened.
- Recipient combobox: "Select a contact" placeholder -> expanded -> single option: `Sipho Dlamini (sipho.portal@example.com)`.
- Selected Sipho -> clicked **Send**.

**Result: PASS**

### 7.8 — Proposal status transitions to Sent

- Detail page header shows status badge **Sent**.
- "Sent: May 14, 2026" field now appears in Proposal Details.
- Action button changed from "Send Proposal" to **Withdraw**.

**Result: PASS**

### 7.9 — Backend log confirms send + portal sync + email

Backend logs confirmed:
1. `Created proposal d7481b7a-8878-43ee-928c-2845bf8bffd0 (PROP-0001) for customer 334bf98f-9f02-4d2f-9ee8-80bbed65ea5b`
2. `Sent proposal d7481b7a-8878-43ee-928c-2845bf8bffd0 to contact 7f429963-c841-4c75-96cb-73b26dbe7d43`
3. `Portal sync completed for proposal PROP-0001 after commit`
4. `Portal notification sent template=portal-new-proposal contact=7f429963-c841-4c75-96cb-73b26dbe7d43 to=sipho.portal@example.com`

**Result: PASS**

### 7.10 — Mailpit: proposal email arrives at sipho.portal@example.com

- Mailpit total messages: 11 (was 10 before send).
- Newest message:
  - Subject: `Mathebula & Partners: New proposal PROP-0001 for your review`
  - From: `noreply@docteams.app`
  - To: `sipho.portal@example.com`
  - Created: `2026-05-13T22:08:08.316Z`
  - HTML body contains portal proposal link: `http://localhost:3002/proposals/d7481b7a-8878-43ee-928c-2845bf8bffd0` (correct portal URL with proposal UUID).
- OBS-703 fix confirmed: email IS dispatched on Send.

**Result: PASS**

### 7.11 — Portal /proposals index shows PROP-0001 for Sipho

- Context swap: authenticated as Sipho on portal `:3002` via magic-link exchange.
- Navigated to `/proposals`.
- "Awaiting Your Response" section renders table with one row:
  - `PROP-0001 | Engagement Letter -- Litigation (Dlamini v RAF) | SENT | 14 May 2026 | - | View`
  - Link to `/proposals/d7481b7a-8878-43ee-928c-2845bf8bffd0`.
- Footer: "Powered by Kazi" (correct branding, no "DocTeams" leak).
- Zero console errors on portal `/proposals` page.

**Result: PASS**

---

## Day 7 Checkpoint Summary

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 7.1 | Navigate to matter, click + New Engagement Letter | **PASS** | Dialog opened; legal-za terminology correct |
| 7.2 | Client pre-filled = Sipho Dlamini (disabled) | **PASS** | Combobox disabled, value = Sipho Dlamini |
| 7.3 | Title set | **PASS** | "Engagement Letter -- Litigation (Dlamini v RAF)" |
| 7.4 | Fee Model = Hourly, Rate Note filled | **PASS** | Hourly default; rate breakdown in note field |
| 7.5 | Expiry Date set | **PASS** | 2026-05-21 (7 days) |
| 7.6 | Create Proposal -> Draft, PROP-0001 | **PASS** | Status = Draft, ref = PROP-0001, expiry renders correctly (no tz drift) |
| 7.7 | Send Proposal -> select Sipho -> Send | **PASS** | Recipient combobox lists Sipho, Send clicked |
| 7.8 | Status = Sent, Withdraw button | **PASS** | Badge = Sent, Sent date field, Withdraw button |
| 7.9 | Backend logs confirm send + sync + email | **PASS** | 4 log lines confirmed |
| 7.10 | Mailpit email to Sipho with portal link | **PASS** | Subject + body + link verified |
| 7.11 | Portal /proposals shows PROP-0001 (SENT) | **PASS** | "Awaiting Your Response" table, correct link |

**Overall: 11/11 PASS, 0 blockers, 0 new gaps**

---

## Day 7 Scenario Checkpoint Summary

| Checkpoint | Result | Notes |
|------------|--------|-------|
| Proposal lifecycle: Draft -> Sent succeeds end-to-end | **PASS** | Created, sent, portal sync, email all working |
| Portal email dispatched (OBS-703) -- subject + body verified, link reaches /proposals/{id} | **PASS** | OBS-703 fix confirmed working |
| Portal /proposals projection shows PROP-0001 (firm->portal sync) | **PASS** | Sipho sees PROP-0001 in "Awaiting Your Response" |
| Frontend console clean (no hydration mismatch on /proposals index -- OBS-704) | **FAIL** | OBS-704 still present -- see below |
| Expiry date renders consistently with date input (no +1-day tz drift -- OBS-702) | **PASS** | Input 2026-05-21, rendered "May 21, 2026" -- no drift |

---

## OBS-704 — Hydration mismatch on /proposals index (still present)

Navigated to `/org/mathebula-partners/proposals` (firm-side Engagement Letters page). Console fires:

```
Error: Hydration failed because the server rendered HTML didn't match the client.
...
<DialogTrigger asChild={true}>
  <DialogTrigger data-slot="dialog-tri..." asChild={true}>
    <Primitive.button ...>
      <Primitive.button.Slot ...>
        <Primitive.button.SlotClone ...>
+         <button data-slot="button" ... aria-controls="radix-_R_4clritrqiqbn5rknelb_" ...>
```

Same root cause as previous cycle: `CreateProposalDialog` `DialogTrigger asChild` button generates a client-side `aria-controls` Radix ID that diverges from SSR output. Page still functions correctly but violates "frontend must run clean" mandate.

**Status**: OPEN (non-blocking nit, does not cascade to Day 8)

---

## Console Error Summary

- Matter detail page: 2x OBS-203 (`/api/assistant/invocations` 404) -- known pre-existing nit
- Proposal detail page (Draft + Sent): 0 new errors (same OBS-203 from matter navigation carried over)
- Firm proposals index: 1x OBS-704 hydration mismatch -- pre-existing
- Portal `/proposals`: 0 errors
- Portal exchange/auth: 0 errors

---

## Entities Created

- Proposal PROP-0001 (id `d7481b7a-8878-43ee-928c-2845bf8bffd0`, status SENT, matter RAF-2026-001, client Sipho Dlamini)
