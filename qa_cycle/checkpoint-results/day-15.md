# Day 15 — Isolation check: Sipho cannot see Moroka's data `[PORTAL]` — Cycle 2026-07-12

**Actor**: Sipho Dlamini on :3002 (Day-4 magic-link session still valid). Probe targets from `isolation-probe-ids.txt`.

## Phase A — List-view leak probe

| # | Result | Evidence |
|---|--------|----------|
| 15.1 | PASS | Existing session valid, no re-login needed |
| 15.2 | PASS | `/home`: Pending info requests 0 (no "Liquidation and Distribution"), no fee notes, deadlines 0, Last trust movement R 50 000,00 (Sipho's own) — no Moroka data |
| 15.3 | PASS | `/projects`: only Sipho's 2 matters (RAF-2026-001 + known auto-created engagement matter). No Estate Late Peter Moroka |
| 15.4 | PASS | `/trust` (auto-forward to Sipho's ledger): balance **R 50 000,00** (NOT R 75 000 aggregate); only the DEP/2026/001 transaction; no Moroka/EST-2026-002/R 25 000 anywhere |
| 15.5 | PASS | `/invoices`: "No fee notes yet." (empty is correct) |
| 15.6 | PASS | `/deadlines`: "No deadlines in this view" — no Moroka Master's Office deadlines |
| 15.7 | PASS | `/proposals`: only PROP-0001 ACCEPTED |
| 15.8 | PASS | 📸 `day-15-portal-home-isolated.png` |

Automated leak scan (regex `moroka|EST-2026|Liquidation|25 000`) over all six views: **0 hits**.

## Phase B — Direct-URL probes (Moroka IDs)

| # | Result | Evidence |
|---|--------|----------|
| 15.9 | PASS | `/projects/690b8246…` → "The requested resource was not found." denial panel — no matter data |
| 15.10 | PASS | `/requests/a2452183…` → "The requested resource was not found." — no L&D content |
| 15.11 | PASS | `/documents/40d050cc…` → 404 "Page not found" (route doesn't exist on portal) — no PDF |
| 15.12 | PASS | `/trust/690b8246…` → "No trust balance is recorded for this matter" + resource-not-found for tx/statements — no R 25 000 |
| 15.13 | PASS | 📸 `day-15-portal-denial.png` (Moroka matter probe denial) |

## Phase C — API probes (Sipho's portal JWT from :3002 localStorage; real API shapes are `/portal/*` on :8080, not the scenario's `/portal/api/*`)

Positive controls first: `GET /portal/projects/{siphoMatter}` → 200 Sipho data; `GET /portal/trust/matters/{siphoMatter}/transactions` → 200 with only DEP/2026/001.

| # | Result | Evidence |
|---|--------|----------|
| 15.14 | PASS | `GET /portal/projects/690b8246…` → **404** `{"title":"Project not found"}` (existence-denying) |
| 15.15 | PASS | `GET /portal/requests/a2452183…` → **404** `{"title":"InformationRequest not found"}` |
| 15.16 | PASS | `GET /portal/trust/matters/690b8246…/transactions` → **404** Project not found; `/statement-documents` → **404**. (Scenario's `/portal/trust/transactions/{txId}` shape doesn't exist as an endpoint at all — "No static resource") |
| 15.17 | PASS | No portal document-by-id endpoint exists (`/portal/documents/{id}` + `/download` → "No static resource" 404); Moroka documents unreachable — `GET /portal/projects/690b8246…/documents` → **404** Project not found; zero bytes of Moroka content returned on any probe |
| 15.18 | PASS | List/aggregate endpoints: `/portal/projects` (2 Sipho matters only), `/portal/requests` (REQ-0001 only), `/portal/trust/summary` (only matter 66451e87…, balance 50000.00). grep for `moroka|690b8246|a2452183|EST-2026` → 0 hits |

## Phase D — Activity trail + digest

| # | Result | Evidence |
|---|--------|----------|
| 15.19 | PASS | `/activity` "Your actions": only Sipho's REQ-0001/engagement-letter events. **"Firm actions" tab also probed**: only Bob's REQ-0001 lifecycle events — Moroka REQ-0002 firm activity (same firm, minutes earlier) correctly absent |
| 15.20 | N/A | No digest email delivered yet (0 digest messages for sipho.portal@ in Mailpit) — digest leak check lands Day 75 |

## Day 15 day-level checkpoints (BLOCKER severity)

- List views show ONLY Sipho's data: PASS
- Direct-URL probes (4) denied at frontend: PASS
- API probes (4+ endpoints) denied at backend (404, never 200 with Moroka data): PASS
- Trust balance R 50 000 (not R 75 000 aggregate): PASS
- Activity trail zero Moroka references (both tabs): PASS

**No blocker. Tenant-internal portal-contact isolation fully enforced.**

## Gaps

- None new.
