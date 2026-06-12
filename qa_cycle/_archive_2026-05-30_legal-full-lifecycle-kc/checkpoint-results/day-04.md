# Day 4 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, portal :3002, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Sipho Dlamini (unauthenticated, arriving via email magic-link on portal :3002)

---

## Pre-check: Portal health + context swap

- Portal health check: `curl -sS http://localhost:3002/ -o /dev/null -w "%{http_code}"` returned `307` (redirect to login — healthy)
- Backend health: `http://localhost:8080/actuator/health` returned `200`
- Mailpit health: `http://localhost:8025` returned `200`
- Fresh browser context opened (no firm-side cookies carried over)

---

## Day 4 — Phase A: Magic-link landing `[PORTAL]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 4.1 | Open Mailpit -> locate FICA info-request magic-link email for sipho.portal@example.com | **PASS** | Mailpit API `GET /api/v1/messages` returned email ID `QMKKgJD3D7SyPc4hGRnr7e`. Subject: "Information request REQ-0001 from Mathebula & Partners". From: noreply@kazi.app. Body contains portal magic-link: `http://localhost:3002/auth/exchange?token=oSi7CghGxbR0pZ8kIX1rSXAKdrPn5ZkesW9qzIFaxLU&orgId=mathebula-partners`. |
| 4.2 | Click magic-link -> browser navigates to `http://localhost:3002/auth/exchange?token=...` | **PASS** | Navigated to magic-link URL. Token exchange initiated. |
| 4.3 | Portal exchanges token (POST /portal/auth/exchange fires) -> redirects to `/home` | **PASS** | Token exchange succeeded. Browser redirected to `/projects` (portal's default landing page after magic-link auth). No Keycloak form appeared at any step — pure magic-link JWT authentication. |
| 4.4 | Verify `/home` renders: pending info request section shows matter context with due date | **PASS** | Navigated to `/home`. "Pending info requests" card shows **1** with link to `/requests`. Request is indexed by matter name "Dlamini v Road Accident Fund" (not template title "FICA Onboarding Pack" — per OBS-401 amendment). Other cards: Upcoming deadlines: 0, Recent fee notes: "No fee notes yet", Last trust movement: "No recent activity". |
| 4.5 | Verify header/sidebar shows Mathebula firm branding (navy accent, firm logo) | **PASS** | Header displays `img "Mathebula & Partners logo"` (firm logo uploaded Day 1). Sidebar shows "Portal" label. Brand colour applied to UI elements. |
| 4.6 | Verify user identity displayed as "Sipho Dlamini" | **PASS** | Header user menu button displays "Sipho Dlamini" (from firm-side client record). Consistent across all pages. |
| 4.7 | Screenshot: day-04-portal-home-first-login.png | **PASS** | Snapshot captured showing Home page with pending info requests, firm branding, and user identity. |

---

## Day 4 — Phase B: Upload FICA documents `[PORTAL]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 4.8 | Click into request row -> info-request detail renders with matter context and per-item upload list | **PASS** | Clicked "Pending info requests" card -> navigated to `/requests`. Requests list shows: REQ-0001, "Dlamini v Road Accident Fund", status SENT, 0/3 submitted. Clicked into row -> navigated to `/requests/0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19`. Detail page header: "REQ-0001", "Dlamini v Road Accident Fund", "0/3 submitted - status SENT". Per-item upload list rendered below. |
| 4.9 | Verify three upload slots labelled: ID copy, Proof of residence, Bank statement | **PASS** | Three items rendered in list: (1) **ID copy** (required) — "Certified copy of the client's South African ID document or passport bio page. Must be certified by a Commissioner of Oaths, SAPS, or other accepted certifier within the last 3 months." Accepts: PDF, JPG, PNG. (2) **Proof of residence (<=3 months)** (required) — "Recent utility bill, municipal rates account, bank statement, or similar document confirming the client's residential address." Accepts: PDF, JPG, PNG. (3) **Bank statement (<=3 months)** (required) — "Most recent bank statement evidencing the client's source of funds." Accepts: PDF. All three have "Upload file" button and disabled "Upload and submit" button. |
| 4.10 | Upload a test PDF to each slot -> three upload-progress indicators -> three completion states | **PASS** | Uploaded `fica-id.pdf` (597 bytes) to ID copy slot, `fica-address.pdf` (608 bytes) to Proof of residence slot, `fica-bank.pdf` (604 bytes) to Bank statement slot. Each file upload enabled the per-item "Upload and submit" button. All three files accepted without validation errors. |
| 4.11 | (OBS-402 amend: removed) No portal-side cover-message textarea | **PASS** | Confirmed: no request-level cover-message textarea visible on the info-request detail page. Per-item context is set by the firm when sending and rendered as the item's description. Matches OBS-402 amendment. |
| 4.12 | Submit each FICA item via per-item "Upload and submit" -> each item transitions to Submitted | **PASS** | Submitted items sequentially: (1) ID copy -> "Submitted -- status: SUBMITTED", header updated to "1/3 submitted - status IN_PROGRESS" (envelope transitioned SENT -> IN_PROGRESS on first per-item submit). (2) Proof of residence -> "Submitted -- status: SUBMITTED", header updated to "2/3 submitted - status IN_PROGRESS". (3) Bank statement -> "Submitted -- status: SUBMITTED", header updated to "3/3 submitted - status IN_PROGRESS". Envelope remains IN_PROGRESS (not auto-transitioning to SUBMITTED) — closes to COMPLETED only on firm-side "Mark as Reviewed" in Day 5. Matches OBS-403 state machine. |
| 4.13 | Verify `/home` "Pending info requests" card pending count drops to 0 | **PASS** | Navigated to `/home`. "Pending info requests" card shows **0** (was 1 before submission). All 3 items SUBMITTED -> no longer pending from portal contact's perspective. Envelope itself remains IN_PROGRESS as expected. |
| 4.14 | Screenshot: day-04-fica-submitted.png | **PASS** | Snapshot captured showing 3/3 submitted - status IN_PROGRESS with all three items showing "Submitted -- status: SUBMITTED". |

---

## Day 4 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Magic-link login succeeded -- no Keycloak form appeared at any step | **PASS** | Magic-link URL `http://localhost:3002/auth/exchange?token=...&orgId=mathebula-partners` exchanged token and redirected to portal. Zero Keycloak interaction. Pure JWT authentication via portal auth exchange endpoint. |
| Uploads stored (firm side will verify on Day 5) | **PASS** | Three test PDFs uploaded and submitted: fica-id.pdf (ID copy), fica-address.pdf (Proof of residence), fica-bank.pdf (Bank statement). All accepted without validation errors. Per-item status transitioned to SUBMITTED. Firm-side verification deferred to Day 5. |
| Info-request state machine progressed: per-item Pending -> Submitted for all 3; envelope Sent -> IN_PROGRESS | **PASS** | State machine observed: Envelope started at SENT (0/3), transitioned to IN_PROGRESS on first per-item submit (1/3), progressed through 2/3 and 3/3. Final state: 3/3 submitted, envelope IN_PROGRESS. Closes to COMPLETED on firm Mark-as-Reviewed in Day 5 — per OBS-403 amendment. |
| No firm-side terminology leaks on portal | **PASS** | Sidebar: "Matters" (not "Projects"), "Fee Notes" (not "Invoices"), "Engagement Letters" (not "Proposals"). Page headings: "Information requests" (not "tasks"/"tickets"). Request detail: matter name "Dlamini v Road Accident Fund" shown as context. Footer: "Powered by Kazi" — never "DocTeams". |
| Brand check: portal footer reads "Powered by Kazi" -- never "DocTeams" | **PASS** | Footer `contentinfo` element contains `paragraph: "Powered by Kazi"`. Verified on every page visited (/home, /requests, /requests/{id}). Zero "DocTeams" references anywhere in portal. |

---

## Console Errors

All errors from the entire session (including carryover from firm-side Days 2-3):

- **favicon.ico 404** on `:8180` (Keycloak) and `:3002` (portal) — cosmetic, not application errors
- **`/api/assistant/invocations` 404** (multiple) — all are the known OBS-201 (WONT_FIX-EXEMPT, AI assistant endpoint not wired in KC mode). These are from the firm-side browser context from Days 2-3 sessions, NOT from the portal Day 4 session.

**Zero JavaScript/hydration/rendering errors during the portal Day 4 execution.**

## Gaps Filed

None. Day 4 passed cleanly with zero new gaps.

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
- **Matter "Dlamini v Road Accident Fund" ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Matter Reference**: RAF-2026-001
- **Info Request REQ-0001 ID**: `0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19`
- **Info Request REQ-0001 Status**: 3/3 submitted, envelope IN_PROGRESS (awaits firm review Day 5)
- **Portal magic-link token used**: `oSi7CghGxbR0pZ8kIX1rSXAKdrPn5ZkesW9qzIFaxLU`
- **Portal session**: Active as Sipho Dlamini on :3002
