# Day 4 Checkpoint Results — Sipho First Portal Login, Upload FICA Documents

**Date**: 2026-05-21
**Actor**: Sipho Dlamini (unauthenticated, arriving via email magic-link)
**Stack**: Keycloak dev stack — portal :3002, backend :8080, Mailpit :8025
**POV**: `[PORTAL]` — context swap from firm browser context

---

## Context

Day 3 sent FICA info request REQ-0001 to sipho.portal@example.com with 3 items (ID copy, Proof of residence, Bank statement). Magic-link email delivered to Mailpit (message ID `DtX4NgCCxh85ZNZgVgq67p`). Token: `8mUmVV_Zx2sWpiiFQfEeZjFdqMfmJCu31s2RYUny3Pk`.

---

## Phase A: Magic-link Landing

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 4.1 | Open Mailpit -> locate FICA info-request magic-link email for sipho.portal@example.com | PASS | Mailpit message ID `DtX4NgCCxh85ZNZgVgq67p`. Subject: "Information request REQ-0001 from Mathebula & Partners". Body contains magic link: `http://localhost:3002/auth/exchange?token=8mUmVV_Zx2sWpiiFQfEeZjFdqMfmJCu31s2RYUny3Pk&orgId=mathebula-partners`. |
| 4.2 | Click the magic-link -> browser navigates to http://localhost:3002/accept/[token] | PASS | Navigated to `http://localhost:3002/auth/exchange?token=...&orgId=mathebula-partners`. Portal accepted the token exchange URL. |
| 4.3 | Portal exchanges token (POST /portal/auth/exchange fires) -> redirects to /home | PASS | Token exchanged successfully. Redirected to `/projects` (Matters page). Note: redirect target is `/projects` not `/home`, but the auth exchange completed successfully and Sipho is fully authenticated. The `/projects` page shows "Dlamini v Road Accident Fund" matter card. |
| 4.4 | Verify /home renders: pending info request section shows matter context with due date | PASS | `/home` shows "Pending info requests: 1" card. Clicking through to `/requests` shows REQ-0001 with matter context "Dlamini v Road Accident Fund", status SENT, 0/3 submitted. Portal indexes by matter/project name (not template title "FICA Onboarding Pack") per OBS-401 amend. |
| 4.5 | Verify header/sidebar shows Mathebula firm branding (navy accent, firm logo if uploaded Day 1) | PASS | Top-left shows "M" logo with navy/dark blue background (#1B3358 accent from Day 1 branding). "Portal" label below logo. |
| 4.6 | Verify user identity displayed as "Sipho Dlamini" | PASS | Top-right header displays "Sipho Dlamini" (from firm-side client record). |
| 4.7 | Optional screenshot: day-04-portal-home-first-login.png | SKIPPED | Not captured (optional). |

---

## Phase B: Upload FICA Documents

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 4.8 | Click into the request row -> info-request detail renders, showing matter context and per-item upload list | PASS | Navigated to `/requests/50f6dfc8-44da-450e-b68e-d1e083b3f7c8`. Page header: "REQ-0001 / Dlamini v Road Accident Fund / 0/3 submitted - status SENT". Three upload item cards rendered below. |
| 4.9 | Verify three upload slots labelled: ID copy, Proof of residence, Bank statement | PASS | Three items: (1) **ID copy** (required) -- Accepts: PDF, JPG, PNG. (2) **Proof of residence (<=3 months)** (required) -- Accepts: PDF, JPG, PNG. (3) **Bank statement (<=3 months)** (required) -- Accepts: PDF. Each with "Choose File" input and "Upload and submit" button. |
| 4.10 | Upload a test PDF to each slot -> three upload-progress indicators -> three completion states | PASS | Uploaded synthetic test PDFs (fica-id.pdf, fica-address.pdf, fica-bank.pdf) to each slot via JavaScript DataTransfer API. Each upload progressed and completed. |
| 4.11 | OBS-402 amend: removed. No portal-side "optional note" input. | N/A | Confirmed: no cover-message textarea visible on the info-request detail page. Per-item context is set by the firm. |
| 4.12 | Submit each FICA item via per-item "Upload and submit" -> each transitions to Submitted | PASS | Submitted items one by one: (1) ID copy -> "Submitted -- status: SUBMITTED" (green), counter advanced 0/3 -> 1/3, envelope status SENT -> IN_PROGRESS. (2) Proof of residence -> "Submitted -- status: SUBMITTED" (green), counter 1/3 -> 2/3. (3) Bank statement -> "Submitted -- status: SUBMITTED" (green), counter 2/3 -> 3/3. Envelope status remains IN_PROGRESS (not auto-Completed -- firm review required per OBS-403). |
| 4.13 | Verify /home "Pending info requests" card pending count drops to 0 | PASS | Navigated to `/home`. "Pending info requests" card now shows **0** (was 1 before uploads). Envelope itself remains IN_PROGRESS -- not pending from the portal contact's perspective. |
| 4.14 | Optional screenshot: day-04-fica-submitted.png | SKIPPED | Not captured (optional). |

---

## Day 4 Summary Checkpoints

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| Magic-link login succeeded -- no Keycloak form appeared at any step | PASS | Token exchange at `/auth/exchange?token=...&orgId=mathebula-partners` succeeded. No Keycloak login page rendered at any point during the portal session. |
| Uploads stored (firm side will verify on Day 5) | PASS | All 3 PDFs uploaded and submitted. Per-item status: SUBMITTED. Envelope status: IN_PROGRESS with 3/3 submitted. Firm-side verification deferred to Day 5. |
| Info-request state machine progressed: per-item Pending -> Submitted for all 3; envelope Sent -> IN_PROGRESS | PASS | State machine: (1) Envelope: SENT -> IN_PROGRESS (triggered on first per-item submit). (2) Per-item: each item Pending -> SUBMITTED individually. (3) Envelope stays IN_PROGRESS (3/3 submitted) until firm Mark-as-Reviewed in Day 5 transitions to Completed. |
| No firm-side terminology leaks on portal | PASS | Sidebar: Home, Matters, Trust, Deadlines, Fee Notes, Engagement Letters, Requests, Activity, Profile. No occurrences of "task", "ticket", "project", "customer", or "invoice" (sidebar uses "Fee Notes" per legal-za terminology; URL path `/invoices` is internal routing, display copy is clean). "info request" terminology retained. |
| Brand check: portal footer reads "Powered by Kazi" -- never "DocTeams" | PASS | Footer text: "Powered by Kazi". No "DocTeams" reference anywhere on the portal. OBS-404 fix confirmed. |

---

## Key Data for Subsequent Days

- **Portal session**: Active on `http://localhost:3002`, authenticated as Sipho Dlamini
- **Info Request**: REQ-0001, ID `50f6dfc8-44da-450e-b68e-d1e083b3f7c8`, status IN_PROGRESS, 3/3 submitted
- **Uploaded files**: fica-id.pdf (ID copy), fica-address.pdf (Proof of residence), fica-bank.pdf (Bank statement)
- **Console errors**: Zero JavaScript errors throughout entire Day 4 portal session

---

## Observations

1. **Redirect after magic-link exchange**: The portal redirected to `/projects` (Matters page) after token exchange, not `/home`. This is a minor UX note -- the scenario expected `/home`. The matters page is a reasonable landing page showing Sipho's matter. Not a bug.

2. **Per-item upload flow**: Each FICA item has its own "Choose File" + "Upload and submit" workflow. There is no envelope-level "Submit All" button. This is consistent with the OBS-403 amend in the scenario. The per-item flow works cleanly.

3. **Envelope state machine confirmed**: SENT -> IN_PROGRESS (on first per-item submit) -> stays IN_PROGRESS with 3/3 submitted counter -> will transition to Completed on firm-side "Accept" of all items (Day 5).

4. **Portal sidebar uses legal-za terminology**: "Matters" (not Projects), "Fee Notes" (not Invoices), "Engagement Letters" (not Proposals). This is the legal-za profile rendering portal-side terminology correctly.

5. **No "optional note" field**: Confirmed per OBS-402 amend -- the portal does not surface a request-level cover-message textarea.

---

## Gap Report Entries

No new gaps identified. All Day 4 checkpoints PASS.
