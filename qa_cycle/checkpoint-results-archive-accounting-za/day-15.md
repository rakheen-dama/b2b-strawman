# Day 15 — Isolation check (Sipho cannot see Moroka's data)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-13`
**Cycle**: 1 (clean slate)
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Sipho Dlamini on portal `:3002` — fresh magic-link login via `/login` dev mode.

**Severity**: BLOCKER. Any failure means data crosses customer boundary within the same tenant.

## Pre-flight

- All services healthy: backend `:8080` (200), portal `:3002` (307 redirect — normal for unauthenticated), mailpit `:8025` (200).
- Sipho authenticated via dev magic-link at `/login` (email `sipho.portal@example.com`).
- Sipho's `portal_jwt` captured from `localStorage`: sub `334bf98f-9f02-4d2f-9ee8-80bbed65ea5b` (Sipho's customer_id), type `customer`, org `mathebula-partners`.
- Moroka entity IDs from `qa_cycle/isolation-probe-ids.txt`:
  - customer `b7e205be-4e7e-40f1-9d8d-940e6a2e4fee`
  - matter `43c3dd6b-4bc8-4504-b775-bd61fd19ed7a` (EST-2026-002)
  - info-request `d114eae8-7b44-460e-984c-1f3044e30690` (REQ-0002)
  - document storage_key `c1e78e13-a3b2-49b3-91f7-bebef1d589c3`
  - trust-tx `d52ff25d-a0af-44e4-9651-b10bb781e038`

## Phase A — List-view leak probes

### 15.1, 15.2 `/home`
- Logged in as Sipho via dev magic-link. `/home` rendered:
  - Pending info requests **0**
  - Upcoming deadlines **0**
  - Recent fee notes: "No fee notes yet."
  - Last trust movement: **R 50 000,00** / 14 May 2026
- Programmatic body-text scan for `moroka`, `est-2026`, `liquidation`, `r 25 000`, `r 25,000`, `r25 000`, `r25,000`, `peter`, `lerato`, `75 000`, `75,000` — ALL false. **PASS.**
- Evidence: `day-15-portal-home-isolated.png`

### 15.3 `/projects`
- 2 matters listed — both Sipho's:
  - "Engagement Letter -- Litigation (Dlamini v RAF)" (id `4245a6cf-...`)
  - "Dlamini v Road Accident Fund" (id `c90832a4-...`)
- Scan for `moroka`, `est-2026`, `estate`, `liquidation`, `peter` — ALL false. **PASS.**

### 15.4 `/trust`
- Redirected to `/trust/c90832a4-...` (Sipho's matter — single-matter redirect).
- Trust balance card: **R 50 000,00** (Sipho's Day 10 deposit only).
- Transaction table: 1 row — "Initial trust deposit -- RAF-2026-001, R 50 000,00".
- Scan for `moroka`, `est-2026`, `r 25 000`, `r 25,000`, `25 000`, `75 000`, `75,000`, `peter`, `estate` — ALL false.
- Balance is R 50,000 (Sipho only), NOT R 75,000 (aggregate leak). **PASS.**

### 15.5 `/invoices`
- "No fee notes yet." — empty as expected. **PASS.**

### 15.6 `/deadlines`
- "No deadlines in this view." No Moroka data. **PASS.**

### 15.7 `/proposals`
- 1 proposal listed: PROP-0001 "Engagement Letter -- Litigation (Dlamini v RAF)" — ACCEPTED. Sipho only.
- Scan for `moroka` — false. **PASS.**

### Requests `/requests`
- 1 request listed: REQ-0001 "Dlamini v Road Accident Fund" — COMPLETED 3/3 accepted.
- Scan for `moroka`, `req-0002`, `liquidation`, `peter`, `lerato`, `est-2026` — ALL false. **PASS.**

### Activity `/activity`
- "Your actions" tab: 6 entries — all Sipho's portal events (info-request item submissions + document upload starts).
- "Firm actions" tab: 6 entries — all Bob Ndlovu's actions on Sipho's info request (item accepted x3, request completed, request sent, request created).
- Scan for `moroka`, `est-2026`, `estate`, `liquidation`, `peter`, `lerato`, `thandi` — ALL false. **PASS.**

### 15.8 Screenshot
- `day-15-portal-home-isolated.png` saved.

## Phase B — Direct-URL probes (using Moroka entity IDs)

### 15.9 `/projects/43c3dd6b-4bc8-4504-b775-bd61fd19ed7a`
- Rendered: "**The requested resource was not found.** This project may have been removed, you may not have access, or the request failed." with "Try again" and "Back to projects" buttons.
- Console: 10 errors — all 404s from backend portal endpoints (expected — SPA fires parallel queries on matter open; all correctly 404'd). **PASS.**

### 15.10 `/requests/d114eae8-7b44-460e-984c-1f3044e30690`
- Rendered: "**The requested resource was not found.**"
- Console: 2 errors (expected 404). **PASS.**

### 15.11 `/documents/c1e78e13-a3b2-49b3-91f7-bebef1d589c3`
- Rendered: **404 — Page not found** (Next.js-level 404 page). **PASS.**

### 15.12 `/trust/transactions/d52ff25d-a0af-44e4-9651-b10bb781e038`
- Rendered: **404 — Page not found** (Next.js-level 404 page). **PASS.**

### 15.13 Screenshot
- `day-15-portal-denial.png` saved (Moroka matter probe denial state).

## Phase C — Backend API probes (curl with Sipho's portal_jwt)

JWT sub = `334bf98f-9f02-4d2f-9ee8-80bbed65ea5b` (Sipho's customer_id), org = `mathebula-partners`.

| Probe | URL | HTTP Status | Response Body | Result |
|-------|-----|-------------|---------------|--------|
| 15.14 | `GET /portal/projects/43c3dd6b-...` | **404** | `{"detail":"No project found with id 43c3dd6b-...","status":404,"title":"Project not found"}` | PASS |
| 15.15 | `GET /portal/requests/d114eae8-...` | **404** | `{"detail":"No informationrequest found with id d114eae8-...","status":404,"title":"InformationRequest not found"}` | PASS |
| 15.16 | `GET /portal/trust/matters/43c3dd6b-.../transactions` | **404** | `{"detail":"No project found with id 43c3dd6b-...","status":404,"title":"Project not found"}` | PASS |
| 15.17 | `GET /portal/documents/c1e78e13-.../presign-download` | **404** | `{"detail":"No document found with id c1e78e13-...","status":404,"title":"Document not found"}` | PASS |

### 15.18 List endpoints — Moroka leak check

| Endpoint | HTTP Status | Payload | Result |
|----------|-------------|---------|--------|
| `GET /portal/projects` | **200** | 2 items: `4245a6cf-...` (Engagement Letter) + `c90832a4-...` (Dlamini v RAF). Zero Moroka IDs (`43c3dd6b`, `b7e205be`, `EST-2026`) in payload. | PASS |
| `GET /portal/trust/summary` | **200** | 1 matter: `c90832a4-...` with `currentBalance: 50000.0`. Zero Moroka matter ID. No R 25,000 or aggregate R 75,000. | PASS |

All 6 backend probes confirm customer isolation: Moroka entities return 404, and Sipho's list endpoints contain zero Moroka data.

## Phase D — Email isolation

### 15.19 Activity trail
- Verified in Phase A above (both "Your actions" and "Firm actions" tabs). Zero Moroka references. **PASS.**

### 15.20 Mailpit — email content inspection

- **Sipho's inbox**: 13 emails to `sipho.portal@example.com`. Each email body (Text + HTML) scanned for `moroka`, `est-2026`, `estate`, `liquidation`, `peter`, `25 000`, `25,000`. **All 13 CLEAN.** Subjects:
  - 6x "Your portal access link from Mathebula & Partners"
  - 1x "Mathebula & Partners: Trust account activity" (Sipho's R 50,000 deposit)
  - 1x "New proposal PROP-0001 for your review"
  - 1x "Request REQ-0001 completed"
  - 3x "Item accepted" (ID copy, Proof of residence, Bank statement)
  - 1x "Information request REQ-0001"
- **Moroka's inbox**: 2 emails to `moroka.portal@example.com` — correctly addressed to Moroka, NOT cross-sent to Sipho:
  - "Mathebula & Partners: Trust account activity" (R 25,000 deposit)
  - "Information request REQ-0002 from Mathebula & Partners"
- **Sipho received zero emails containing Moroka data.** Email isolation holds. **PASS.**

## Day 15 checkpoints (BLOCKER severity)

| ID | Description | Result |
|-----|-------------|--------|
| 15.1 | Login as Sipho | PASS (magic-link) |
| 15.2 | `/home` shows only Sipho's data | PASS |
| 15.3 | `/projects` shows only Sipho's matters (2) | PASS |
| 15.4 | `/trust` shows only R 50,000 (no aggregate, no Moroka) | PASS |
| 15.5 | `/invoices` empty / Sipho-only | PASS |
| 15.6 | `/deadlines` empty / Sipho-only | PASS |
| 15.7 | `/proposals` shows only Sipho's PROP-0001 | PASS |
| -- | `/requests` shows only Sipho's REQ-0001 | PASS |
| -- | `/activity` shows only Sipho's events | PASS |
| 15.8 | Screenshot saved | PASS |
| 15.9 | Direct URL `/projects/{morokaMatter}` denied | PASS (404 page) |
| 15.10 | Direct URL `/requests/{morokaReq}` denied | PASS (404 page) |
| 15.11 | Direct URL `/documents/{morokaDoc}` denied | PASS (404 page) |
| 15.12 | Direct URL `/trust/transactions/{morokaTx}` denied | PASS (404 page) |
| 15.13 | Screenshot saved | PASS |
| 15.14 | API `GET /portal/projects/{moroka}` -> 404 | PASS |
| 15.15 | API `GET /portal/requests/{moroka}` -> 404 | PASS |
| 15.16 | API `GET /portal/trust/matters/{moroka}/transactions` -> 404 | PASS |
| 15.17 | API `GET /portal/documents/{moroka}/presign-download` -> 404 | PASS |
| 15.18 | API list endpoints contain no Moroka IDs | PASS |
| 15.19 | Activity feed has zero Moroka events (both tabs) | PASS |
| 15.20 | Mailpit -- Sipho got zero Moroka emails; Moroka emails correctly addressed | PASS |

**22/22 checkpoints PASS.**

## Summary

- List views on `/home`, `/projects`, `/trust`, `/invoices`, `/deadlines`, `/proposals`, `/requests`, `/activity` show ONLY Sipho's data. **PASS.**
- 4 direct-URL probes against Moroka entity IDs all denied at frontend (404 / "not found"). **PASS.**
- 6 API-level probes against Moroka endpoints all denied at backend (404, never 200). **PASS.**
- Trust balance card shows R 50,000 (Sipho only), NOT R 75,000 (aggregate leak). **PASS.**
- Activity trail (both tabs) + Mailpit have zero Moroka references for Sipho. **PASS.**

## Console health

- Phase A list views: 0 errors / 1-2 dev-mode warnings (Image priority, not product bugs).
- Phase B Moroka direct-URL probes: 10-15 console errors per probe — all expected 404 fetch failures proving backend isolation. NOT product bugs.

## New gaps

**None.** Zero BLOCKER findings. Customer isolation holds at all layers (frontend list, frontend direct-URL, backend API, email).

## Status

- **Day 15 COMPLETE -- 22/22 PASS. Tenant/customer isolation verified.**
- **No drift, no leaks. ZERO BLOCKER findings.**
- Ready for Day 21.
