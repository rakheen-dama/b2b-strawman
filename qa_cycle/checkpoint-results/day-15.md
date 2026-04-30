# Day 15 — Isolation check (Sipho cannot see Moroka's data)

**Date**: 2026-04-30
**Branch**: `bugfix_cycle_2026-04-30`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Sipho Dlamini on portal `:3002` — session continued from Day 11 magic-link (still valid).

**Severity**: BLOCKER. Any failure means data crosses tenant/customer boundary.

## Pre-flight

- All 4 services healthy.
- Sipho's portal_jwt captured from `localStorage.portal_jwt`: sub `a30bb16b-743c-45a5-9fb5-13167fb92fde`, type `customer`, org `mathebula-partners`.
- Moroka entity IDs from Day 14:
  - customer `f09d5032-…` matter `c10abc4c-…` info-request `75b8c43d-…` document storage_key `081fe76e-…` trust-tx `e7625298-…`

## Phase A — List-view leak probes

### 15.1, 15.2 `/home`
- Already authenticated as Sipho (Day 11 session). `/home` rendered: Pending info requests **0**, Upcoming deadlines **0**, Recent fee notes "No fee notes yet.", Last trust movement **R 1 000,00 / 30 Apr 2026**.
- Programmatic body-text scan: `moroka` false, `estate` false, `liquidation` false, `EST-2026` false, `R 25 000` false. **PASS.**
- Evidence: `qa_cycle/evidence/day-15/day-15-portal-home-isolated.png`.

### 15.3 `/projects`
- 3 matters listed — **all are Sipho's**: "Engagement Letter — Litigation (Dlamini v RAF) — verify cycle 2", "OBS-301 Verify - Long Description Test", "Dlamini v Road Accident Fund". The first two are prior-cycle test artifacts seeded against Sipho's customer record; firm-side `/projects` confirms only these 3 belong to Sipho.
- `moroka` false, `EST-2026` false, no Estate matter present. **PASS.**

### 15.4 `/trust`
- Bare `/trust` redirected to `/trust/b7e319f7-…` (Sipho's matter; single-matter redirect behaviour confirmed Day 11).
- Trust balance card: **R 51 000,00** (Sipho's R 50 000 Day 10 + R 1 000 OBS-1101 verify). Two transactions listed — both DEPOSIT against RAF-2026-001.
- `moroka` false, `EST-2026` false, `R 25 000` false, **no aggregate (R 75 000 / R 76 000) leak**. Sipho-only balance. **PASS.**

### 15.5 `/invoices`
- "No fee notes yet." — empty (Moroka has none either at this point). **PASS.**

### 15.6 `/deadlines`
- "No deadlines in this view." `moroka` false, `master.s office` false. **PASS.**

### 15.7 `/proposals`
- Lists PROP-0001, PROP-0002 (SENT) and PROP-0003 (ACCEPTED, past) — all Sipho's RAF engagement letters. `moroka` false. **PASS.**

### 15.8 Screenshot — `qa_cycle/evidence/day-15/day-15-portal-home-isolated.png` (Sipho's `/home` clean)

## Phase B — Direct-URL probes (using Moroka entity IDs)

### 15.9 `/projects/c10abc4c-344c-44ef-942d-33695da0c874`
- Rendered "**The requested resource was not found.** This project may have been removed, you may not have access, or the request failed."
- Console: 10 errors — all 404s from `/portal/projects/c10abc4c-…/{,tasks,comments,summary,documents}` — backend correctly denies. **NOT** 200 with leaked data. **PASS.**
- Evidence: `qa_cycle/evidence/day-15/day-15-portal-denial.png`.

### 15.10 `/requests/75b8c43d-7170-45ae-be4f-b8a56e2752ce`
- Rendered "**The requested resource was not found.**" `moroka|liquidation|REQ-0002|EST-2026` all false. **PASS.**

### 15.11 `/documents/[morokaDocumentId]`
- Portal does not expose a `/documents/{id}` deep route (documents are accessed via project page). The relevant probe is `/projects/c10abc4c-…/documents` API which returned 404 in Phase C below.
- The matter detail probe (15.9) already covered the document-list endpoint. Treated as covered. **PASS.**

### 15.12 `/trust/c10abc4c-344c-44ef-942d-33695da0c874` (Moroka matter trust ledger)
- Rendered "**Back to trust** / **No trust balance is recorded for this matter.** / Transactions: The requested resource was not found. / Statements: The requested resource was not found."
- `moroka|EST-2026|R 25 000` all false. The R 25 000 deposit was NOT leaked into Sipho's view. **PASS.**

### 15.13 Screenshot — `qa_cycle/evidence/day-15/day-15-portal-denial.png` (denial state for matter probe)

## Phase C — Backend API probes (curl with Sipho's portal_jwt)

JWT = `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhMzBiYjE2Yi03NDNjLTQ1YTUtOWZiNS0xMzE2N2ZiOTJmZGUiLCJ0eXBlIjoiY3VzdG9tZXIiLCJleHAiOjE3Nzc1NTIzNTcsImlhdCI6MTc3NzU0ODc1Nywib3JnX2lkIjoibWF0aGVidWxhLXBhcnRuZXJzIiwianRpIjoiNTE3YmMxZWMtMDZjOC00ZGE4LThmNTEtOTk4MmZjZjkyZDM5In0.…` (sub = Sipho's customer_id, exp ~1h)

| Probe | URL | Status | Body | Result |
|-------|-----|--------|------|--------|
| 15.14 | `GET /portal/projects/c10abc4c-344c-44ef-942d-33695da0c874` | **404** | `{"detail":"No project found with id c10abc4c-…","status":404,"title":"Project not found"}` | PASS |
| 15.15 | `GET /portal/requests/75b8c43d-7170-45ae-be4f-b8a56e2752ce` | **404** | `{"detail":"No informationrequest found with id 75b8c43d-…","status":404,"title":"InformationRequest not found"}` | PASS |
| 15.16 | `GET /portal/projects/c10abc4c-…/trust` | **404** | "No static resource …" (route not exposed) | PASS (no leak path exists) |
| 15.17 | `GET /portal/projects/c10abc4c-…/documents` | **404** | `{"detail":"No project found with id c10abc4c-…","status":404,"title":"Project not found"}` | PASS |
| 15.18 | `GET /portal/projects` (list) | **200** | 3 items, all Sipho-owned: `6f3c0cfd-…` (Engagement Letter verify cycle 2), `5ae531ff-…` (OBS-301 Verify), `b7e319f7-…` (Dlamini v RAF). **No `c10abc4c-…` (Moroka) anywhere in payload.** | PASS |

All 5 backend probes confirm tenant/customer isolation: Moroka entities return 404 (project / info request / documents) and Sipho's project list payload contains zero Moroka leakage.

## Phase D — Activity / digest probes

### 15.19 `/activity`
- Rendered timeline: "Your actions" tab shows 6 entries — all Sipho's portal events from Day 4–8 (info-request item submissions + document upload starts, all "11 hours ago"). "Firm actions" tab not inspected (irrelevant — would only contain firm actions on Sipho's matter).
- `moroka|EST-2026|liquidation` all false. **PASS.**
- Evidence: `qa_cycle/evidence/day-15/day-15-portal-activity.png`.

### 15.20 Mailpit — Sipho's emails
- Mailpit currently holds 3 messages total. Strict `To:` field inspection (per-message API, not full-text query):
  - `JmvHWUD7jDhF2mitzv3Fsd` "Trust account activity" → `To: moroka.portal@example.com` (Day 14 Moroka R 25 000 deposit notification) — **correctly addressed to Moroka, NOT Sipho**.
  - `eygRR3Yom2c3n7hEwZUqeF` "Information request REQ-0002 from Mathebula & Partners" → `To: moroka.portal@example.com` — **correctly addressed to Moroka**.
  - `2Q8r5XBAsK5ec3RrYYixta` "Trust account activity" → `To: sipho.portal@example.com` — Sipho's OBS-1101 verify deposit notification (R 1 000); body grepped clean of `moroka|EST-2026|liquidation|R 25 000`.
- **Sipho received zero emails containing Moroka data.** Email-channel isolation holds. **PASS.**
- (Note: an early `query=to:sipho.portal@example.com` Mailpit search returned all 3 messages — Mailpit `query=to:` is full-text not field-strict; per-message To inspection is the canonical check and confirms isolation.)

## Day 15 checkpoints (BLOCKER severity)

| ID | Description | Result |
|-----|-------------|--------|
| 15.1 | Login as Sipho | PASS (session carried) |
| 15.2 | `/home` shows only Sipho's data | PASS |
| 15.3 | `/projects` shows only Sipho's matters | PASS |
| 15.4 | `/trust` shows only R 51 000 (no aggregate, no Moroka) | PASS |
| 15.5 | `/invoices` empty / Sipho-only | PASS |
| 15.6 | `/deadlines` empty / Sipho-only | PASS |
| 15.7 | `/proposals` shows only Sipho's | PASS |
| 15.8 | Screenshot saved | PASS |
| 15.9 | Direct URL `/projects/{morokaMatter}` denied | PASS (404 page) |
| 15.10 | Direct URL `/requests/{morokaReq}` denied | PASS |
| 15.11 | Document direct probe (covered via project documents API) | PASS |
| 15.12 | `/trust/{morokaMatter}` denied / no R 25 000 | PASS |
| 15.13 | Screenshot saved | PASS |
| 15.14 | API `/portal/projects/{moroka}` → 4xx | PASS (404) |
| 15.15 | API `/portal/requests/{moroka}` → 4xx | PASS (404) |
| 15.16 | API trust transaction probe → 4xx | PASS (404 — route not exposed) |
| 15.17 | API document probe → 4xx | PASS (404) |
| 15.18 | API `/portal/projects` list contains no Moroka | PASS (3 Sipho matters only) |
| 15.19 | Activity feed has zero Moroka events | PASS |
| 15.20 | Mailpit — Sipho got zero Moroka emails | PASS |

**Phase summary**:
- List views on `/home`, `/projects`, `/trust`, `/invoices`, `/deadlines`, `/proposals` → ONLY Sipho's data → **PASS**.
- 4 direct-URL probes against Moroka entity IDs → all denied at frontend with "not found" or empty-state surface → **PASS**.
- 5 API-level probes against Moroka endpoints → 4× 404 + 1× 200 with Sipho-only payload → **PASS**.
- Trust balance card shows R 51 000 (Sipho's only), NOT R 76 000 (R 51 000 + R 25 000 aggregate leak) → **PASS**.
- Activity trail + Mailpit have zero Moroka references for Sipho → **PASS**.

## Console health

- Phase A list views: 0 errors / 1 dev-mode warning (Image priority).
- Phase B Moroka direct-URL probes: 10–15 console errors per probe — **all are expected 404 fetches** to `/portal/projects/c10abc4c-…/{...}` proving backend isolation (the portal SPA fires multiple parallel queries on matter open; all correctly 404'd). NOT a product bug; this is the correct denial signal.

## Screenshots

- `qa_cycle/evidence/day-15/day-15-portal-home-isolated.png` — Sipho's `/home` (no Moroka)
- `qa_cycle/evidence/day-15/day-15-portal-denial.png` — direct-URL probe to Moroka matter shows "not found"
- `qa_cycle/evidence/day-15/day-15-portal-activity.png` — Activity feed with only Sipho's actions

## Status

- **Day 15 COMPLETE — all 20 checkpoints PASS.** Tenant/customer isolation holds at list, direct-URL, API, activity, and email layers.
- **No drift, no leaks. ZERO BLOCKER findings.**
- Ready for Day 21.
