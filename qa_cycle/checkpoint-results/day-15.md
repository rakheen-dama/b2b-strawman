# Day 15 — Isolation Check: Sipho Cannot See Moroka's Data [PORTAL]

**Date**: 2026-05-21
**Actor**: Sipho Dlamini (portal contact, sipho.portal@example.com)
**Stack**: Portal :3002, Backend :8080
**Auth**: Fresh magic-link token exchange (POST /portal/auth/request-link -> /auth/exchange)

## Entity IDs Used

| Entity | ID |
|--------|-----|
| Sipho Client | d8327ceb-c66a-4305-b8be-fbda2c52f576 |
| Sipho RAF Matter | 85b09bb3-5cdd-42b9-8364-1bea1e83153d |
| Moroka Client | ae29fada-e2bb-47a6-9b33-212d5b15a5c0 |
| Moroka Matter | ca96c33f-c365-455d-a6ce-4810657d36e4 |
| Moroka Info Request | 3ac5f213-9ba4-4d54-ab9d-b7026d44d12c |
| Moroka Trust Tx | bd3ec3f8-b989-452f-98b5-e51a41709245 |

## Phase A: List-View Leak Probe

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 15.1 | Login as Sipho via magic-link | PASS | Token exchange succeeded, redirected to /projects, user identity "Sipho Dlamini" displayed |
| 15.2 | /home — no Moroka data | PASS | Pending info requests: 0, Upcoming deadlines: 0, Recent fee notes: "No fee notes yet.", Last trust movement: R 50 000,00 (Sipho only, not R 75,000 aggregate). No "Liquidation and Distribution" entry. |
| 15.3 | /projects — only Sipho's matters | PASS | 2 cards: "Engagement Letter - Litigation (Dlamini v RAF)" + "Dlamini v Road Accident Fund". No Moroka Estate matter. |
| 15.4 | /trust — only Sipho's balance | PASS | Trust balance: R 50 000,00 (NOT R 75,000 aggregate). 1 transaction: "Initial trust deposit - RAF-2026-001" R 50,000. No Moroka R 25,000 deposit. Matter ref 85b09bb3 (Sipho's). |
| 15.5 | /invoices — empty, no Moroka leak | PASS | "No fee notes yet." — empty as expected |
| 15.6 | /deadlines — no Moroka deadlines | PASS | "No deadlines in this view" — no Moroka Master's Office filing deadlines |
| 15.7 | /proposals — only Sipho's accepted | PASS | 1 row: PROP-0001 "Engagement Letter - Litigation (Dlamini v RAF)", ACCEPTED, 21 May 2026. No other proposals. |
| 15.8 | Screenshot captured | PASS | day-15-portal-home-isolated.png saved |

## Phase B: Direct-URL Probe (Hard Negative)

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 15.9 | /projects/{morokaMatterId} | PASS | "The requested resource was not found." with "Try again" / "Back to matters" links. No Moroka matter detail rendered. |
| 15.10 | /requests/{morokaInfoRequestId} | PASS | Red error banner: "The requested resource was not found." No Moroka info request data. |
| 15.11 | /documents/{morokaMatterId} | PASS | 404 "Page not found" with "Go Home" button. No Moroka document content. |
| 15.12 | /trust/transactions/{morokaTrustTxId} | PASS | 404 "Page not found" with "Go Home" button. No Moroka R 25,000 deposit rendered. |
| 15.13 | Screenshot captured | PASS | day-15-portal-denial.png saved (404 denial page) |

## Phase C: API-Level Probe (Hard Negative)

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 15.14 | GET /portal/projects/{morokaMatterId} | PASS | HTTP 404: "No project found with id ca96c33f-c365-455d-a6ce-4810657d36e4" |
| 15.15 | GET /portal/requests/{morokaInfoRequestId} | PASS | HTTP 404: "No informationrequest found with id 3ac5f213-9ba4-4d54-ab9d-b7026d44d12c" |
| 15.16 | GET /portal/trust/matters/{morokaMatterId}/transactions | PASS | HTTP 404: "No project found with id ca96c33f-c365-455d-a6ce-4810657d36e4" |
| 15.17 | GET /portal/documents (full list) | PASS | Empty array []. No Moroka documents in response. |
| 15.18 | GET /portal/projects (full list) | PASS | 2 projects only: b04a44a5 (Engagement Letter) + 85b09bb3 (Dlamini v RAF). Zero Moroka IDs (ca96c33f/ae29fada/3ac5f213/bd3ec3f8) in response. |

**Additional trust API verification:**
- GET /portal/trust/summary: 1 matter (85b09bb3, balance 50000.0). No Moroka matter.
- GET /portal/trust/movements: 1 deposit (R 50,000 "Initial trust deposit - RAF-2026-001"). No Moroka R 25,000.
- GET /portal/trust/matters/{siphoMatterId}/transactions: 1 transaction (DEP/2026/001, R 50,000). No Moroka data.

## Phase D: Activity Trail + Digest Leak Probe

| # | Checkpoint | Result | Evidence |
|---|-----------|--------|----------|
| 15.19 | /activity — only Sipho's events | PASS | "Your actions" tab: 6 events (3x submitted info request item, 3x started uploading document). "Firm actions" tab: 6 events (info request created/sent, 3x item accepted, completed) — all by Bob Ndlovu on Sipho's matter. API /portal/activity: all events reference projectId 85b09bb3 (Sipho). Zero Moroka IDs. |
| 15.20 | Digest email check | PASS | No digest email delivered yet (Day 15 too early). All 13 Sipho emails in Mailpit reference only his matters (FICA, proposals, trust, portal access). Zero Moroka references in any subject. |

## Summary Checkpoints

| Checkpoint | Result |
|-----------|--------|
| List views show ONLY Sipho's data (/home, /projects, /trust, /invoices, /deadlines, /proposals) | PASS |
| Direct-URL probes to 4 Moroka entity IDs denied at frontend (404 / not found) | PASS |
| API-level probes to 4+ Moroka endpoints denied at backend (404, never 200) | PASS |
| Trust balance card shows R 50,000 (Sipho only) — not R 75,000 (aggregate leak) | PASS |
| Activity trail / digest have zero Moroka references | PASS |
| Console errors | NONE |

## New Gaps

None. Zero new gaps filed.

## Result: ALL CHECKPOINTS PASS (20/20)

Day 15 isolation check is fully passing. The portal's authorization correctly scopes all data to the logged-in portal contact. Moroka Family Trust data (matter, info request, trust deposit, documents) is completely invisible to Sipho across all surfaces: list views, direct URL probes, API endpoints, activity trail, and email.
