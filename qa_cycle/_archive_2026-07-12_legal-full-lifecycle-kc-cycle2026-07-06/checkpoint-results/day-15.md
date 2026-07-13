# Day 15 — Isolation check: Sipho cannot see Moroka's data `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini on :3002 (magic-link session). Probe IDs from `isolation-probe-ids.txt`.

## Phase A — list-view leak probes

| # | Result | Evidence |
|---|--------|----------|
| 15.1 | PASS | Session from Day 8 magic link still valid (user menu "Sipho Dlamini") |
| 15.2 | PASS | `/home`: Pending info requests **0** (no "Liquidation and Distribution"), no fee notes, deadlines 0, **Last trust movement R 50 000,00** — NOT the more recent Moroka R 25 000 deposit (recorded 9 min later firm-side); scoping proven by recency |
| 15.3 | PASS (count note) | `/projects`: exactly 2 matters, both Sipho's — RAF matter + the acceptance-auto-created "Engagement Letter — Litigation (Dlamini v RAF)" (Day 10 observation). **No Moroka Estate matter** |
| 15.4 | PASS | `/trust`: balance **R 50 000,00** (not R 75 000 aggregate); single transaction (Sipho's DEP/2026/001); zero mention of Moroka / EST-2026-002 / R 25 000 |
| 15.5 | PASS | `/invoices`: "No fee notes yet." |
| 15.6 | PASS | `/deadlines`: "No deadlines in this view" — no Moroka estate deadlines |
| 15.7 | PASS | `/proposals`: only PROP-0001 ACCEPTED |
| 15.8 | PASS | 📸 `day-15-portal-home-isolated.png` |

## Phase B — direct-URL probes (Moroka IDs)

| # | Result | Evidence |
|---|--------|----------|
| 15.9 | PASS | `/projects/54baf135-…` → "The requested resource was not found." denial page; all 5 underlying fetches 404 from backend (`/portal/projects/{id}[/summary \| tasks \| documents \| comments]`) |
| 15.10 | PASS | `/requests/6b6b6b7d-…` → "The requested resource was not found." |
| 15.11 | PASS | `/documents/733d2950-…` → 404 page (portal exposes no such route; document access is via project scope — API probe in Phase C) |
| 15.12 | PASS | `/trust/54baf135-…` (portal's real trust-ledger URL shape) → "No trust balance is recorded for this matter" + transactions/statements not found; R 25 000 never rendered |
| 15.13 | PASS | 📸 `day-15-portal-denial.png` |

## Phase C — API probes (Sipho JWT from localStorage `portal_jwt`, backend :8080)

| # | Result | Evidence |
|---|--------|----------|
| 15.14 | PASS | `GET /portal/projects/{morokaMatterId}` → **404** `{"title":"Project not found"}`; also `/documents` sub-resource 404 |
| 15.15 | PASS | `GET /portal/requests/{morokaInfoRequestId}` (real route shape) → **404** `{"title":"InformationRequest not found"}` |
| 15.16 | PASS | No per-transaction endpoint exists; `GET /portal/trust/summary` → 200 with ONLY `{"matterId":"272be4f8-…","currentBalance":50000.00}`; `GET /portal/trust/movements` → 200 with only Sipho's d75a4d1c deposit. Moroka tx eed7ad82 absent |
| 15.17 | PASS | `GET /portal/documents/{morokaDocumentId}/presign-download` → **404** `{"title":"Document not found"}` — no bytes returned |
| 15.18 | PASS | No aggregate `/portal/home` endpoint (home composes list endpoints); all list endpoints verified: `/portal/requests` → only REQ-0001 (projectId 272be4f8), `/portal/projects` → only Sipho's 2 matters, `/portal/trust/summary`+`/movements` → only Sipho's. Zero Moroka IDs in any response |

## Phase D — activity + digest

| # | Result | Evidence |
|---|--------|----------|
| 15.19 | PASS | `/activity`: "Your actions" = Sipho's FICA submits/uploads only; "Firm actions" = engagement-letter accept + REQ-0001 lifecycle only. Moroka events from 9:16–9:18 (REQ-0002 create/send, doc upload, R 25 000 deposit) absent |
| 15.20 | N/A | No weekly digest delivered yet (Mailpit has none) — will be covered Day 75 |

Cross-check: Moroka emails (REQ-0002 magic-link, trust-activity) addressed to moroka.portal@example.com only — nothing Moroka-related sent to Sipho.

## Day 15 checkpoints (BLOCKER gate)

- List views show ONLY Sipho's data: **PASS**
- Direct-URL probes denied (4/4): **PASS**
- API probes denied at backend (404, never 200-with-data): **PASS**
- Trust balance R 50 000 not R 75 000: **PASS**
- Activity trail zero Moroka references: **PASS**

**ISOLATION GATE: PASS — zero leaks at list, URL, and API levels.**

## Gaps

None.
