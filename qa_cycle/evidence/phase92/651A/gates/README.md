# 651A — gate evidence

Slice head observed: `818fc08a7` (PR #1811). Stack: `compose/scripts/e2e-up.sh` from the 651A worktree on 2026-11-19 (fresh volumes; the e2e tenant was provisioned at 06:48:49 UTC and ran 27 tenant migrations, V27 included). Mock-auth frontend :3001, backend :8081, mock IDP :8090, Postgres :5433.

Gate cadence (founder ruling 2026-11-18): 651A is not a stage checkpoint (651E is), so E2E-LEGAL is not run here; 651A adds or edits no e2e spec. OBS runs on a stack built from the slice head; BE-VERIFY runs last, on the final head.

## OBS — PASS (a–f)

Script: [`obs.cjs`](obs.cjs) (Playwright library, headless Chromium, Alice / owner). Result: [`obs/obs-result.json`](obs/obs-result.json), screenshots `obs/d*.png`.

| # | Observation | Result |
|---|---|---|
| a | The seven retired interest-run routes, owner JWT: `GET`/`POST /api/trust-accounts/{id}/interest-runs`, `GET`/`DELETE /api/interest-runs/{id}`, `POST …/calculate`, `…/approve`, `…/post` | all **404**, ProblemDetail title "Not Found" — PASS |
| b | Kept routes still served: `GET /api/trust-accounts`, `GET /api/trust-accounts/{id}`, `GET /api/trust-accounts/{id}/lpff-rates` (removed in 651E, not here), `GET /api/trust-integrity` | all **200** — PASS |
| c | `GET /api/notifications/preferences` | 200; 44 types; `TRUST_INTEREST_POSTED` absent, `TRUST_RECONCILIATION_COMPLETED` present — PASS |
| d | Browser: trust accounting, transactions, client ledgers, reconciliation, investments, other trust creditors, Settings › Connections › Notifications | all 200, no error page, no "interest run" copy, 0 page errors; `/trust-accounting/interest` 404 (651C) — PASS. The single console error is the expected 404 resource load of the retired route. |
| e | Database, read-only ([`db-checks.txt`](db-checks.txt)) | `interest_runs`, `interest_allocations`, `interest_allocation_lines` → `to_regclass` NULL; `lpff_rates` present; `idx_trust_txn_project` present; `flyway_schema_history` V27 "phase92 drop interest run" `success = t` — PASS |
| f | Backend log ([`backend-log-excerpt.txt`](backend-log-excerpt.txt)) | `Started BackendApplication`; Flyway applied V1–V27 to the tenant; **0** ERROR lines; WARNs are the usual startup bean / font-cache notices and the seeded draft-invoice tax-number notice — PASS |

Note on e: a fresh tenant creates the three tables in V1/V12 and V27 drops them. The data-bearing path (a V26 schema holding DRAFT and APPROVED runs, allocations and matter lines → V27; a second V27 run is a no-op; `lpff_rates` rows survive) is covered in-test by `InterestRunRemovalTest`.
