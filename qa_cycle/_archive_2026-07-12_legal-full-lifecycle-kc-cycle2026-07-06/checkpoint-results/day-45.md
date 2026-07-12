# Day 45 — Firm: second info request + second trust deposit `[FIRM]` — 2026-07-06

**Actor**: Bob Ndlovu (Admin). Context swap: cookies cleared, fresh Keycloak login `bob@mathebula-test.local` / `SecureP@ss2` (first attempt with Thandi's password failed with KC "Invalid password." — correct per-user passwords are in the scenario §credentials table). Sidebar confirms Bob Ndlovu.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 45.1 | PASS (product-shape note) | Matter RAF-2026-001 → Client group tab → **Requests** sub-tab (`tab-item-requests`; info requests live under Client, not Work) → New Request → "Create Information Request" dialog (ad-hoc template, portal contact pre-bound to Sipho). No request *title* field exists in the product — scenario's "Supporting medical evidence" carried via the two item names/descriptions. Items: **Hospital discharge summary** + **Orthopaedic report** (both file-upload, required), due date 2026-07-13 (Day 52), reminder 5d → **Send Now** → **REQ-0003 · Sent · 0/2 accepted** in the requests table (numbering matches scenario expectation — REQ-0002 is Moroka's) |
| 45.2 | PASS | Mailpit `ZbBQntCejBJgR3Z3JBKtQf` → `sipho.portal@example.com`, subject "Information request REQ-0003 from Mathebula & Partners", body "…2 item(s) that require your attention", **View Request** magic link `http://localhost:3002/auth/exchange?token=8d-ej5bH…&orgId=mathebula-partners` |
| 45.3 | PASS | Trust Accounting → Record Transaction → **Record Deposit**: Client Sipho Dlamini, Matter "Dlamini v Road Accident Fund", Amount 20000, Reference **DEP/2026/003**, Description "Top-up per engagement letter", date 2026-07-06 → transaction list shows DEP/2026/003 · Deposit · R 20 000,00 · **RECORDED** |
| 45.4 | PASS (amended figure) | Deposits post RECORDED without dual-approval (consistent with Day 10). Client Ledgers: **Sipho Dlamini · Trust Balance R 70 000,00** (Total Deposits R 70 000,00 / Payments R 0 / Fee Transfers R 0). **This cycle's correct figure is R 70 000** (R 50 000 Day 10 + R 20 000 Day 45): the script's R 71 000 amendment included a cycle-15 OBS-1101 R 1 000 verify-deposit that does not exist in this fresh 2026-07-06 cycle. Moroka Family Trust R 25 000,00 listed separately, untouched |
| 45.5 | PASS | Matter RAF-2026-001 Finance > Trust: "Trust Balance — Funds Held **R 70 000,00**", Deposits R 70 000,00, Payments R 0,00, Fee Transfers R 0,00, last transaction 2026/07/06. No fee-transfer-out applied (as scripted) |

## Day 45 day-level checkpoints

- Second info request dispatched: **PASS** (REQ-0003, 2 items, magic-link email in Mailpit)
- Trust balance reconciles on client ledger and matter trust tab: **PASS at R 70 000** (cycle-local figure; R 71 000 amendment N/A — no OBS-1101 carry-over deposit in this cycle's data)

## Gaps

None new.

## Notes for Day 46

- Magic-link token for REQ-0003: `8d-ej5bH…` (Mailpit `ZbBQntCejBJgR3Z3JBKtQf`). Portal expectations: trust balance **R 70 000**, transaction list = 2 Sipho deposits (R 50 000 + R 20 000) — the scripted "three deposits" includes the phantom R 1 000; expect two.
