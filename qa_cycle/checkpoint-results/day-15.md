# Day 15 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, portal :3002)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Sipho Dlamini (portal contact, authenticated via magic link)

---

## Pre-check: Authenticate as Sipho on Portal

Navigated to `http://localhost:3002/login`. Entered `sipho.portal@example.com`, clicked "Send Magic Link". Dev-mode shortcut link appeared. Clicked it -> authenticated successfully, redirected to `/projects`. Header shows "Sipho Dlamini". Zero JavaScript errors on login flow.

---

## Phase A: List-view leak probe (no Moroka data should appear anywhere)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.1 | Login as Sipho via magic-link | **PASS** | Authenticated via dev-mode magic link. Portal header: "Sipho Dlamini". Redirected to `/projects`. |
| 15.2 | `/home` — verify only Sipho's data | **PASS** | Pending info requests: **0** (Sipho's REQ-0001 completed; Moroka's REQ-0002 NOT shown). Recent fee notes: "No fee notes yet." Upcoming deadlines: **0**. Last trust movement: **R 50 000,00** (Sipho only, NOT R 75,000 aggregate). Zero Moroka references anywhere on home. |
| 15.3 | `/projects` — verify only Sipho's matters listed | **PASS** | 2 matters listed: "Engagement Letter -- Litigation (Dlamini v RAF)" + "Dlamini v Road Accident Fund". Zero Moroka matters ("Estate Late Peter Moroka" NOT listed). No EST-2026-002 reference. |
| 15.4 | `/trust` — verify balance is R 50,000 (Sipho only) | **PASS** | Trust balance card: **R 50 000,00** (NOT R 75,000 aggregate). Transaction table: 1 row — "30 May 2026, DEPOSIT, Initial trust deposit -- RAF-2026-001, R 50 000,00, running balance R 50 000,00". Zero mention of Moroka, EST-2026-002, or R 25,000 deposit. URL resolved to `/trust/d80aeac5` (Sipho's matter). |
| 15.5 | `/invoices` — verify empty or Sipho-only | **PASS** | "No fee notes yet." — empty as expected. No Moroka invoices. |
| 15.6 | `/deadlines` — verify only Sipho's deadlines | **PASS** | "No deadlines in this view." Status/Type filters available but no items. No Moroka Master's Office filing deadlines. |
| 15.7 | `/proposals` — verify only Sipho's proposals | **PASS** | 1 row: PROP-0001 "Engagement Letter -- Litigation (Dlamini v RAF)", ACCEPTED, 30 May 2026. No other proposals. |
| 15.8 | Screenshot: `day-15-portal-home-isolated.png` | **PASS** | Screenshot captured showing Sipho's `/home` with only his data. |

**Additional list-view probe**: `/requests` page shows only REQ-0001 (Sipho's FICA, COMPLETED, 3/3 accepted). Moroka's REQ-0002 ("Liquidation and Distribution Account Pack") NOT listed.

---

## Phase B: Direct-URL probe (hard negative — Moroka IDs used)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.9 | Navigate to `/projects/3cf31082-b371-4ae5-abdf-34f6f38df708` (Moroka matter) | **PASS** | Page rendered: "The requested resource was not found. This matter may have been removed, you may not have access, or the request failed." with "Try again" and "Back to matters" buttons. Moroka matter data **did NOT render**. Backend returned 404 for all subresources (projects, summary, tasks, documents, comments). |
| 15.10 | Navigate to `/requests/e6cc55cd-3250-4a51-a5bd-9c9231c91e2c` (Moroka info request) | **PASS** | Page rendered: "The requested resource was not found." No "Liquidation and Distribution Account" data shown. Backend returned 404. |
| 15.11 | Navigate to `/documents/2fe8a1cb-2359-4533-868d-ae19f91d2594` (Moroka death certificate) | **PASS** | Next.js 404 page: "404 — Page not found". Moroka death-certificate PDF **NOT returned**. |
| 15.12 | Navigate to `/trust/transactions/2d9fec05-b071-4ff9-85c1-b2175a312f8b` (Moroka trust tx) | **PASS** | Next.js 404 page: "404 — Page not found". Moroka R 25,000 deposit **NOT rendered**. |
| 15.13 | Screenshot: `day-15-portal-denial.png` | **PASS** | Screenshot captured showing denial page from Moroka matter probe (15.9). |

---

## Phase C: API-level probe (hard negative — backend enforcement)

JWT used: `portal_jwt` from Sipho's localStorage (sub=`d74963c8`, org_id=`mathebula-partners`).

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.14 | `GET /portal/projects/3cf31082-...` (Moroka matter) | **PASS** | HTTP 404: `{"detail":"No project found with id 3cf31082-b371-4ae5-abdf-34f6f38df708","status":404,"title":"Project not found"}`. No 200, no Moroka data returned. |
| 15.15 | `GET /portal/requests/e6cc55cd-...` (Moroka info request) | **PASS** | HTTP 404: `{"detail":"No informationrequest found with id e6cc55cd-3250-4a51-a5bd-9c9231c91e2c","status":404,"title":"InformationRequest not found"}`. No 200. |
| 15.16 | `GET /portal/trust/matters/3cf31082-.../transactions` (Moroka trust) | **PASS** | HTTP 404: `{"detail":"No project found with id 3cf31082-b371-4ae5-abdf-34f6f38df708","status":404,"title":"Project not found"}`. No 200, no R 25,000 data. |
| 15.17 | `GET /portal/documents/2fe8a1cb-.../presign-download` (Moroka document) | **PASS** | HTTP 404: `{"detail":"No document found with id 2fe8a1cb-2359-4533-868d-ae19f91d2594","status":404,"title":"Document not found"}`. No presigned URL, no document bytes. |
| 15.18 | `GET /portal/projects` — verify no Moroka IDs in list | **PASS** | Returns array of 2 projects: `afe80827` (Engagement Letter) + `d80aeac5` (Dlamini v RAF). Zero Moroka IDs (`3cf31082`, `3d3557f7`). Zero EST-2026-002 references. |

**Additional API probes**:
- `GET /portal/trust/summary` — returns 1 matter (`d80aeac5`, balance=50000.0). No Moroka matter, no R 25,000, no R 75,000 aggregate.
- `GET /portal/trust/movements` — returns 1 movement (Sipho's deposit, R 50,000). No Moroka deposit.
- `GET /portal/requests` — returns 1 request (REQ-0001, COMPLETED). No Moroka REQ-0002.

---

## Phase D: Activity trail + digest leak probe

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 15.19 | `/activity` — verify only Sipho's events | **PASS** | "Your actions" tab: 6 entries (3x "submitted an information request item" + 3x "started uploading a document") — all Sipho's Day 4 FICA uploads. "Firm actions" tab: 6 entries (Bob: 1x info request created, 1x sent, 3x items accepted, 1x request completed) — all from Sipho's REQ-0001. Zero Moroka events on either tab. |
| 15.20 | Digest email check — verify no Moroka references | **PASS** | Searched all 13 emails to `sipho.portal@example.com` in Mailpit. Subjects: magic links, trust activity, proposal, info request acceptance/completion. Zero contain "Moroka", "EST-2026", "Deceased", or "Liquidation". Trust notification email body confirmed: R 50,000 deposit to matter `d80aeac5` only. |

---

## Day 15 Summary Checkpoints (BLOCKER severity)

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| List views on `/home`, `/projects`, `/trust`, `/invoices`, `/deadlines`, `/proposals` show ONLY Sipho's data | **PASS** | All 7 list-view pages verified (including `/requests`). Zero Moroka data on any page. |
| Direct-URL probes to 4+ Moroka entity IDs denied at the frontend (no matter data renders) | **PASS** | 4 probes (matter, info request, document, trust tx) — all denied (404 / "not found"). Zero Moroka data rendered. |
| API-level probes to 4+ Moroka endpoints denied at backend (403/404, never 200) | **PASS** | 4 primary probes + 3 additional (trust summary, movements, requests list) — all return 404 or scoped-to-Sipho results. Zero 200 with Moroka data. |
| Trust balance card shows R 50,000 (Sipho's only) — not R 75,000 (aggregate leak) | **PASS** | Portal: R 50 000,00. API: `currentBalance: 50000.0`. Home: "Last trust movement R 50 000,00". No aggregate leak. |
| Activity trail / digest have zero Moroka references | **PASS** | Activity page (both tabs): zero Moroka events. All 13 Sipho emails: zero Moroka references. |

---

## Console Errors

**During Moroka matter probe (15.9)**: 10 console errors — all HTTP 404s from backend denying access to Moroka's project subresources (`/portal/projects/3cf31082-.../summary`, `/tasks`, `/documents`, `/comments`). These are **expected behavior** — the frontend attempted to fetch matter detail data, and the backend correctly returned 404 for every endpoint. No data leak.

**All other pages**: Zero JavaScript/hydration/rendering errors. Only HMR info logs.

## Gaps Filed

**None.** Day 15 passed cleanly with zero new gaps. Portal isolation is complete and correct at all three levels (list views, direct URLs, API).

## Screenshots

- `day-15-portal-home-isolated.png` — Sipho's `/home` showing only his data
- `day-15-portal-denial.png` — denial page from Moroka matter direct-URL probe
