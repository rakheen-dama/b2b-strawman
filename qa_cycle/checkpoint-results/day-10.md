# Day 10 — Firm activates matter, deposits trust funds `[FIRM]` — 2026-07-06

**Actor**: Thandi Mathebula (Owner) on :3000 (existing Keycloak session from Day 7).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 10.1 | PASS | Firm `/proposals/a7e87eac-…`: badge **Accepted**, "Accepted: 6 Jul 2026" in Proposal Details; audit history shows acceptance event "2 minutes ago" (matches Day-8 accept time) after 2 Thandi events (create/send). NOTE: acceptance audit entry actor renders as "**System**", not the portal contact — flagged for Day 85 actor-attribution check |
| 10.2 | PASS | Matter header badge **Active** (was Active pre-acceptance; matter lifecycle already active per Day 3 template — no manual transition needed). NOTE: proposal acceptance **auto-created a second matter** "Engagement Letter — Litigation (Dlamini v RAF)" for Sipho (portal banner "Your matter has been set up"; matter picker + client card "2 engagements"). Product behaviour; will surface on portal `/projects` for Days 15/75/90 — tracked as observation |
| 10.2a | PASS | Board drag Engagement → Won (dnd-kit needed stepped mouse moves; Playwright `dragTo` alone drops without activating) → "Mark deal as won" dialog → **Mark as Won** → card in Won column, 100% · R 87 500,00; board Win rate 100%, open weighted value R 0 |
| 10.2b | PASS | Client lifecycle badge observed **Prospect** at 08:59 (pre-win) → **Onboarding** at 09:06 (post-win). Nudge fired PROSPECT → ONBOARDING exactly as specified |
| 10.2c | PARTIAL | Deal detail: status badge **Won**, Overview "Won: 6 Jul 2026" — PASS. **"You won a deal" email to Bob NOT in Mailpit** (checked immediately + after 10s; newest email remains 09:00:26 magic-link). Backend log 09:05:57: `Post-commit DEAL_WON notification sent for deal 64c2e57c…` — in-app notification only (Thandi's bell 3→4); no mail dispatch logged → **gap LZKC-005** |
| 10.2d | PASS | 📸 `day-10-deal-won.png` |
| 10.3 | PASS | `/trust-accounting`: "Mathebula Trust — Main cashbook balance" card (R 0,00 pre-deposit, 0 transactions — confirms dead prior session did NOT touch trust) |
| 10.4 | PASS | Manual deposit path (option b): Record Transaction → Record Deposit dialog → Client=Sipho Dlamini, Matter=Dlamini v Road Accident Fund, Amount 50000, Ref DEP/2026/001, Description "Initial trust deposit — RAF-2026-001", Date 2026-07-06 → submitted |
| 10.5 | PASS | Transaction posts directly with status **RECORDED** (deposits do not require dual approval; Pending Approvals card = 0). Dual approval applies to payments (Day 60 path) |
| 10.6 | N/A | No approval queue for deposits — no Bob switch required |
| 10.7 | PASS | Client Ledgers: Sipho Dlamini row Trust Balance **R 50 000,00**, Total Deposits R 50 000,00, Last Transaction 06 Jul 2026 |
| 10.8 | PASS | Matter Finance > Trust tab (`?tab=trust`): Trust Balance **R 50 000,00** (Funds Held), Deposits R 50 000,00, last transaction 2026/07/06 |
| 10.9 | PASS | 📸 `day-10-firm-trust-deposit-recorded.png` (trust dashboard: cashbook R 50 000,00 + DEP/2026/001 row) |

## Day 10 day-level checkpoints

- Proposal acceptance flowed portal → firm (timestamp matches): **PASS**
- DEAL-0001 won; win-nudge recorded; owner notification email: **PARTIAL — LZKC-005** (in-app DEAL_WON notification confirmed via backend log; no email to deal owner in Mailpit)
- Trust deposit posts against correct client ledger (Section 86): **PASS**
- Client ledger + matter trust tab + account cashbook all reconcile to **R 50 000,00**: **PASS**

## Gaps

- **LZKC-005** (Medium): deal-won notification is in-app only — scenario expects a "You won a deal" **email** to the deal owner (Bob) in Mailpit. Backend fires `DealWonEventHandler` notification with no mail dispatch. Needs Product triage: feature gap vs scenario amendment.
