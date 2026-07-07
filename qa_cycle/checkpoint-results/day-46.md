# Day 46 — Sipho responds to second info request + trust re-check + isolation spot-check `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini (portal :3002, magic-link auth — no Keycloak).

**Recovery note (Day-7 pattern)**: a prior QA session died mid-Day-46 without recording. DB evidence (read-only SELECT) shows it had already consumed the REQ-0003 magic link and submitted both items at 13:42:49Z / 13:43:07Z (this session started 14:43Z). Its uploads: `hospital-discharge-summary.pdf` (461 B) + `orthopaedic-report.pdf` (466 B), both `application/pdf`, status UPLOADED, linked as `document_id` on the two REQ-0003 request items. Remaining checkpoints verified live against observed state.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 46.1 | PASS (recovery) | Original REQ-0003 magic link (`8d-ej5bH…`, Mailpit `ZbBQntCejBJgR3Z3JBKtQf`) rejected with "Link expired or invalid" — consumed by the dead session. Fresh link requested via portal `/login` (sipho.portal@example.com) → Mailpit `n9UFTnrfqYEAAV84GcCbp4` "Your portal access link from Mathebula & Partners" → `/auth/exchange?token=5QwlcJgDxStucGD3oTBeLMmJy9kMG7y_1D5R-wuWNPw` → authenticated (landed `/projects`, sidebar "Sipho Dlamini") |
| 46.2 | PASS (recovered) | Pre-submission pending state was exercised by the dead session (DB `submitted_at` timestamps prove the click-through + submit happened). Observed now: `/requests` lists **REQ-0003 · Dlamini v Road Accident Fund · IN_PROGRESS · 2/2 submitted** (plus REQ-0001 COMPLETED 3/3) |
| 46.3 | PASS (recovered + observed) | REQ-0003 detail (`/requests/0ef0dfe2-56c8-48a6-b619-973d00e33826`): **Hospital discharge summary — "Submitted — status: SUBMITTED"** and **Orthopaedic report — "Submitted — status: SUBMITTED"**, header "2/2 submitted • status IN_PROGRESS". Envelope IN_PROGRESS (not COMPLETED) is correct product shape per OBS-403 — completion needs firm-side accept (Day 60.3–60.5). DB: both items SUBMITTED with document_ids `ee92cacc…` / `5c3ecf2e…` |
| 46.4 | PASS (cycle-local figure) | `/trust` auto-forwards to matter ledger `272be4f8` (single-matter behaviour, consistent Day 11): **Trust balance R 70 000,00 — As of 6 Jul 2026**. Script's R 71 000 includes the cycle-15 OBS-1101 R 1 000 carry-over that does not exist in this fresh cycle (documented Day 45) |
| 46.5 | PASS (2 deposits, cycle-local) | Transactions table, newest first: **6 Jul 2026 · DEPOSIT · "Top-up per engagement letter" · R 20 000,00 · running R 70 000,00** then **6 Jul 2026 · DEPOSIT · "Initial trust deposit — RAF-2026-001" · R 50 000,00 · running R 50 000,00**. Dates/amounts/running balances correct; the scripted third R 1 000 deposit is the phantom carry-over — N/A here |
| 46.6 | PASS | Passive isolation: trust view shows only Sipho's matter ledger; no Moroka R 25 000 anywhere; `/trust` index itself forwards straight to Sipho's single matter |
| 46.7 | PASS | `/home` → "Pending info requests **0**" (medical-evidence request no longer pending); Recent fee notes INV-0001 R 1 250,00; Last trust movement R 20 000,00 · 6 Jul 2026 |
| 46.8 | PASS | 📸 `qa_cycle/checkpoint-results/day-46-portal-trust-two-deposits.png` |

## Day 46 day-level checkpoints

- Second info request lifecycle complete (portal side — both items SUBMITTED): **PASS**
- Trust balance update visible on portal (both deposits): **PASS at R 70 000** (cycle-local)
- Isolation holds — no Moroka data leak 31+ days after explicit check: **PASS**

## Console

Only console error on portal pages: `favicon.ico` 404 (present all cycle, environment noise, not an app JS error). No hydration/JS errors.

## Gaps

None new.

## Notes for Day 60

- REQ-0003 items await firm-side Accept ×2 (Bob) → envelope COMPLETED (60.1–60.5).
- Trust payout PAY/2026/001 must be **R 70 000** (not the script's R 71 000) for the balance to zero.
- Court date in this cycle is **Pre-Trial 2026-07-20 Gauteng Division Pretoria** (script's "Jun 4 2026" text is stale — cycle-local date from Day 21).
