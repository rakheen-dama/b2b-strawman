# Day 10 — Firm activates matter, deposits trust funds `[FIRM]` — Cycle 2026-07-12

**Actor**: Thandi Mathebula on :3000 (Keycloak session persisted from Day 7).

| # | Result | Evidence |
|---|--------|----------|
| 10.1 | PASS | Firm `/proposals/ad3a65ba…`: badge **Accepted**, "Accepted: 12 Jul 2026"; audit history (collapsible) shows acceptance event actor "**Portal Contact**" 2 minutes after Day-8 accept, following 2 Thandi events (create/send). Actor attribution improved vs prior cycle's "System" (LZKC-020/025 family); generic label rather than "Sipho Dlamini" — consistent with known open LZKC-025-family observation, not re-filed |
| 10.2 | PASS | Matter header badge **Active** (active since template creation; no manual transition needed). Known observation re-confirmed: acceptance auto-created a 2nd matter "Engagement Letter — Litigation (Dlamini v RAF)" (Matters list "2 matters"; client card "2 engagements") — same product behaviour as prior cycle, tracked observation, not a gap |
| 10.2a | PASS | Board drag Engagement → Won (`.ring-2` oracle read "Won" before release) → "Mark deal as won" dialog → Mark as Won → card in Won column **100% · R 87 500,00**; Win rate 100%, open weighted value R 0,00 |
| 10.2b | PASS | Client lifecycle badge: was **Prospect** through Day 7; post-win shows **Onboarding** — nudge fired PROSPECT → ONBOARDING as specified |
| 10.2c | PASS (email leg N/A by design) | Deal detail: `deal-status-badge` = **Won**, Overview "WON 12 Jul 2026" — PASS. Backend log 21:18:25Z: `Transitioned deal 3aad1c89… into stage fe7bb7e8… (WON)` + `Post-commit DEAL_WON notification sent` (in-app). **No "You won a deal" email in Mailpit — expected**: per LZKC-005 VERIFIED disposition (prior cycle), the deal-won email channel requires per-member opt-in via /settings/notifications; Bob's opt-in was never enabled on this cycle's fresh tenant. Not a regression, not re-filed. NOTE for scenario upkeep: 10.2c's email expectation implicitly requires an opt-in step earlier in the script |
| 10.2d | PASS | 📸 `day-10-deal-won.png` |
| 10.3 | PASS | `/trust-accounting`: "Mathebula Trust — Main cashbook balance" R 0,00 pre-deposit, 0 transactions |
| 10.4 | PASS | Manual deposit path (option b): Record Transaction menu → Record Deposit dialog → Client=Sipho Dlamini (sole option), Matter=Dlamini v Road Accident Fund (both matters offered), Amount 50000, Ref DEP/2026/001, Description "Initial trust deposit — RAF-2026-001", Date 2026-07-12 → submitted |
| 10.5 | PASS | Posts directly, status **RECORDED** (deposits bypass dual approval; Pending Approvals 0) — same product shape as prior cycle |
| 10.6 | N/A | No approval queue for deposits — no Bob switch required |
| 10.7 | PASS | Client Ledgers: Sipho Dlamini — Trust Balance **R 50 000,00**, Total Deposits R 50 000,00, Payments R 0,00, Fee Transfers R 0,00, Last Transaction 12 Jul 2026 |
| 10.8 | PASS | Matter Finance > Trust (`?tab=trust`): Trust Balance (Funds Held) **R 50 000,00**, Deposits R 50 000,00, last transaction 2026/07/12 |
| 10.9 | PASS | 📸 `day-10-firm-trust-deposit-recorded.png` (dashboard: cashbook R 50 000,00, Active Clients 1, DEP/2026/001 RECORDED row) |

## Day 10 day-level checkpoints

- Proposal acceptance flowed portal → firm (timestamp matches Day 8): PASS
- DEAL-0001 won; win-nudge recorded (Prospect→Onboarding); owner notification: in-app confirmed via backend log; email leg N/A (opt-in by design, LZKC-005 disposition): PASS
- Trust deposit posts against correct client ledger (Section 86): PASS
- Client ledger + matter trust tab + account cashbook all reconcile to **R 50 000,00**: PASS

Console: 0 errors.

## Gaps

- None new.

## IDs for later days

- Trust deposit DEP/2026/001, 12 Jul 2026, R 50 000,00 RECORDED
- Won stage id: `fe7bb7e8-6084-4316-8e92-926691409faf`
