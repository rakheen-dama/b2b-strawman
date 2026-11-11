# Batch L (PR #1757): Keycloak browser check

- **Walker:** Batch L KC browser check (Opus 5.5), 2026-11-11, 21:53–22:15 SAST. No code changed.
- **Build:** main `6612135be`, which contains the PR #1757 merge `af0a84925`.
- **Flyway:** tenant **V15** (`g89 trust investment cancelled`, applied 21:52:43); global **V6**.
- **Stack** (`svc.sh`, all healthy):

  | Service | PID | Note |
  |---|---|---|
  | backend | 88604 (java :8080 88751) | |
  | gateway | 88805 | |
  | frontend | 88937 | `next dev` |
  | portal | 88994 | `next dev` at start |

- **Portal swap:** the portal ran as a **production build** during the walk (pnpm 90758 / next-server 90797), using the W2-P9 recipe, so the magic-link exchange was a single `POST`. It was restored to `next dev` at 22:14 (svc 93130). `next-env.d.ts` was copied back, and the `$MAIN` `git status` equals the pre-walk state. See `logs/portal-swap.txt` and `logs/portal-build.log`.
- **Tenant:** `tenant_5039f2d497cf`, "Mathebula & Partners" (`mathebula-partners`), continued from W2 and not reset.
- **Driver:** Playwright MCP Chromium.
  - Firm, default context = **Thandi** (KC two-step sign-in again; the services had been restarted).
  - Isolated context `bob` = **Bob** (KC "session expired" → sign in).
  - Isolated context `leratoL` = portal contact **Lerato**. It started with storage 0 and cookies 0 and was closed at the end.
  - The viewport was 1440×900 unless a row says 1280×720.
- **Evidence rules:**
  - Read-only SELECTs, in `logs/db-*.txt`; Mailpit through its API.
  - No SQL writes and no REST shortcuts; every action was taken in the UI.
  - Navigate-hook marker `/tmp/.e2e-keycloak-mode`: created 21:54, removed at the end.
  - caffeinate: `caffeinate -dims` PID **89475**, killed at the end.

## Rows

| id | check | status | observation | evidence |
|---|---|---|---|---|
| L1 | **F28**: Record Deposit requires a matter | **PASS** | Thandi, Trust › Transactions › Record Transaction › Record Deposit. The field label reads "**Matter**" (not "Optional"); the picker is disabled until a client is chosen, then lists only that client's matters ("Lerato Mthembu — RAF Claim (MVA 2025-11-03)").<br>Client Lerato, R 1 000, reference "G89-L KC check", **no matter** → Record Deposit. The dialog stays open, the Matter label turns red, and the field error reads "**A matter is required for client trust transactions**". No row was written.<br>Matter = RAF → submit → the dialog closes. SELECT: `59c1cc8c-…` DEPOSIT 1000.00 RECORDED, `project_id` = RAF `565e5a47-…`. | `screenshots/L1-1440-F28-deposit-no-matter-field-error.png`; `logs/db-L1-L2-deposits.txt` |
| L1b | F28: portal balance = firm ledger | **PASS** | Firm Trust › Client ledgers: "Lerato Mthembu · **R 11 123,02** · Deposits R 11 050,00 · Interest R 73,02".<br>Lerato portal (Mailpit `i4BrdXse8bvV3XUzviQMz9` → one `POST :8080/portal/auth/exchange` → `/matters`) › Trust statement `/trust/565e5a47-…`: "Trust balance **R 11 123,02** · As of 11 Nov 2026", with the row "11 Nov 2026 · Deposit · DEPOSIT — G89-L KC check · R 1 000,00". **Equal.**<br>SELECT `portal.portal_trust_balance` 11123.02 = `client_ledger_cards` 11123.02. | `screenshots/L1-1440-firm-client-ledgers-after-deposits.png`, `L2-1440-portal-lerato-trust-F28-F36.png`; `logs/db-L1-L2-deposits.txt`, `logs/mailpit-lerato.txt` |
| L2 | **F36**: 150-character reference | **PASS** | Record Deposit for Lerato on RAF, R 50,00, with a reference of exactly 150 characters (`G89-L-F36-REF150-hij…abcdef-END`; input value length 150). It was accepted, and the dialog closed.<br>The portal trust statement shows the row "Deposit · DEPOSIT — G89-L-F36-REF150-hijabc…ghi… · R 50,00 · running R 11 123,02". The description is visually truncated at 140 characters with "…" (the ADR-254 sanitiser cap, as allowed). The page is 1440/1440 with no horizontal overflow, and the balance is still equal (R 11 123,02 / R 11 123,02).<br>SELECT `portal.portal_trust_transaction`: `length(reference)` = **150**, ending `-END`, so the full reference is stored. The column is now `varchar(200)`. The firm row also has length 150, and there were 0 backend ERROR lines (no "Portal trust sync failed"). | `screenshots/L2-1440-portal-lerato-trust-F28-F36.png`; `logs/db-L1-L2-deposits.txt` |
| L3 | **D39**: Record Transfer needs Source Matter and Target Matter | **PASS** | The dialog fields are Source Client ID, Target Client ID, **Source Matter** ("Source client's matter UUID"), **Target Matter** ("Target client's matter UUID"), Amount, Reference, Description (Optional) and Transaction Date. The UUIDs are the client and matter URL ids (`/client-ledgers/<id>`, `/matters/<id>`).<br>With Target Matter blank → "A matter is required for client trust transactions" under Target Matter. With both blank → the error under each field. | `screenshots/L3-1440-D39-transfer-target-matter-required.png` |
| L3b | D39: wrong target matter refused | **PASS** | Source Lerato/RAF → target Moroka with **Sipho's matter** `c46f07d7-…`, R 100 → the dialog stays open with the inline error "**Matter Sipho Dlamini v. Standard Bank (civil) is not linked to client Moroka Family Trust**". The refused attempt wrote no rows; only the later valid transfer's two legs carry that reference. | `screenshots/L3-1440-D39-transfer-wrong-target-matter-refused.png`; `logs/db-L3-transfer.txt` |
| L3c | D39: transfer R 100, RAF → Moroka estate | **PASS** | Target matter `9181f514-…` (Estate Late Peter Moroka) → the dialog closes.<br>SELECT: TRANSFER_OUT `c3949f16-…` Lerato on the **RAF matter**; TRANSFER_IN `612a28b3-…` Moroka on the **estate matter**.<br>**Ledger cards:** Lerato "R 11 023,02 · … −Transfers out R 100,00"; Moroka "R 50 100,00 · +Transfers in R 100,00", with the history row "G89-L TRF KC check · Transfer In · R 100,00".<br>**Matter Trust tabs:** RAF "Funds Held **R 11 023,02**" (was 11 123,02, so **−100**); estate "Funds Held **R 50 100,00**" (was 50 000, so **+100**).<br>Portal balance rows: Moroka/estate 50100.00 and Lerato/RAF 11023.02. | `screenshots/L3-1440-D39-client-ledgers-after-transfer.png`, `L3-1440-D39-ledger-card-lerato.png`, `L3-1440-D39-ledger-card-moroka.png`, `L3-1440-D39-matter-trust-tab-raf.png`, `L3-1440-D39-matter-trust-tab-estate.png`; `logs/db-L3-transfer.txt` |
| L4a | Investments: Place Investment requires a Matter ID | **PASS** | Trust › Investments › Place Investment (1280×720). With Client ID Lerato and Matter ID blank → "A matter is required for client trust transactions" under Matter ID. | `screenshots/L4-1280x720-place-investment-matter-required.png` |
| L4b | Withdrawal gate: placement AWAITING_APPROVAL | **PASS** | INV-1 "G89-L Money Market A", R 500, RAF matter → the register row reads "ACTIVE · Record Interest · Withdraw". SELECT: placement PAYMENT `acd68cc2-…` is AWAITING_APPROVAL on the RAF matter.<br>Withdraw → Confirm Withdrawal → **refused**: the dialog stays open with "The investment's placement payment must be approved before the investment can be withdrawn (placement payment status: AWAITING_APPROVAL)". Nothing was credited. | `screenshots/L4-1280x720-inv1-active-awaiting-placement.png`, `L4-1280x720-inv1-withdraw-refused.png`; `logs/db-L4-inv1-placed.txt` |
| L4c | **F87**: Bob rejects the placement → Cancelled | **PASS** | Bob, Transactions › Awaiting Approval › "INV-G89-L Money Market A-G89L-INV-001 · Payment · R 500,00" › Reject, with a reason → the row is listed under Rejected.<br>The register, for **both** Bob and Thandi, reads "G89-L Money Market A · R 500,00 · … · **Cancelled**" with an **empty Actions cell** (no Record Interest, no Withdraw).<br>SELECT: investment `a58f5008-…` **CANCELLED**, placement REJECTED; audit `trust_investment.cancelled`; Lerato's balance unchanged at 11 023,02. | `screenshots/L4-1440-bob-placement-rejected.png`, `L4-1440-F87-register-after-reject.png`, `L4-1440-F87-register-thandi-cancelled.png`; `logs/db-L4-inv1-rejected.txt` |
| L4d | **D40**: reversing an approved placement is refused | **PASS** | INV-2 "G89-L Money Market B", R 600, RAF (Thandi) → Bob Approve → "APPROVED · Reverse".<br>Thandi, Transactions › Approved › that row › Reverse, with a reason → Reverse Transaction → **refused**: the dialog stays open with "This payment placed a trust investment (ACTIVE). **Withdraw the investment instead** of reversing its placement payment." SELECT: 0 REVERSAL rows. | `screenshots/L4-1440-bob-placement2-approved.png`, `L4-1440-D40-reverse-placement-refused.png`; `logs/db-L4-inv2-withdrawn.txt` |
| L4e | Withdraw an approved investment: credited once | **PASS** | Register › Money Market B › Withdraw ("Total Withdrawal R 600,00") › Confirm → the row reads "**WITHDRAWN**" with no actions.<br>SELECT: withdrawal DEPOSIT `cab8efc3-…` R 600 on the **RAF matter** (placement's matter); exactly one; 0 reversals.<br>Lerato's ledger card reads "Deposits R 11 650,00 + Interest R 73,02 − Payments R 600,00 − Transfers out R 100,00 = **R 11 023,02**" (arithmetic ✓). The rejected R 500 placement shows "REJECTED · —" and is excluded.<br>Audit: placed, cancelled, placed, withdrawn. | `screenshots/L4-1440-inv2-withdrawn-register.png`, `L4-1440-lerato-ledger-card-final.png`; `logs/db-L4-inv2-withdrawn.txt` |
| L5a | Dialog scroll: Record Transfer at 1280×720 | **PASS** | On open the panel (`max-h-[90vh] overflow-y-auto sm:max-w-lg`) is 648 px tall with a scroll height of 766, and the submit button is at y 743–779, **below the fold**. A mouse wheel over the dialog scrolls the panel by 120 px, bringing the button to y 623–659 (in view; a trial click hit-tests). | `screenshots/L5-1280x720-transfer-dialog-top.png`, `L5-1280x720-transfer-dialog-wheel-scrolled.png` |
| L5b | Dialog scroll: Place Investment at 1280×720 | **PASS** | The panel has the same classes, a scroll height of 1048 against 646, and the button at y 1025 on open. A wheel scroll of 402 px brings the button to y 623–659. INV-1 was **submitted by clicking** that button at this viewport. | `screenshots/L5-1280x720-place-investment-dialog-top.png`, `L5-1280x720-place-investment-dialog-wheel.png` |
| L6 | Final portal ↔ firm equality (all Batch L movements) | **PASS** | Portal Lerato `/trust/565e5a47-…`: "Trust balance **R 11 023,02**". The rows are: investment withdrawal +600, placement −600, "Transfer out" −100, the F36 +50, the F28 +1 000, then W2's interest and deposit. The rejected placement is absent. This equals the firm ledger (R 11 023,02) and `portal_trust_balance`. | `screenshots/L6-1440-portal-lerato-trust-final.png`; `logs/db-L4-inv2-withdrawn.txt` |
| L7 | **F71** sanity: portal `/deadlines` | **PASS** | On 11 Nov 2026 at 22:14 SAST:<br>- "WEEK OF 16 NOV 2026 · Hearing — Gauteng High Court · Court date · **Due soon** · 17 Nov 2026" (+6 days);<br>- "Prescription deadline · **Upcoming** · 1 Dec 2026" (+20);<br>- "Case management — Gauteng High Court · **Upcoming** · 26 Dec 2026" (+45).<br>Each status agrees with its date. `portal_deadline_view` holds DUE_SOON / UPCOMING / UPCOMING with days_out 6 / 20 / 45. | `screenshots/L6-1440-portal-lerato-deadlines-F71.png`; `logs/db-L6-F71-deadlines.txt` |

**Totals:** PASS 15 · FAIL 0 · PARTIAL 0.

The backend logged `ERROR` lines since boot = **0** (`logs/backend-warn-error.txt`: startup WARNs only).

## Candidate defects (severities are proposals)

| id | sev | summary | file:line | evidence |
|---|---|---|---|---|
| L-c1 | LOW (copy) | Portal trust statement rows that have no firm description show the **raw enum** as the description: "**TRANSFER_OUT** — G89-L TRF KC check" and "DEPOSIT — G89-L KC check". The ADR-254 fallback passes `txn.getTransactionType()` unlabelled. This is pre-existing and documented, but PR #1757 widens client exposure: transfer legs now always reach the portal on a matter, so "TRANSFER_OUT" with its underscore is newly client-visible. The Type column beside it already reads "Transfer out". | `backend/.../customerbackend/service/TrustLedgerPortalSyncService.java:287` (passes the raw type), `PortalTrustDescriptionSanitiser.java:78-90` | `screenshots/L6-1440-portal-lerato-trust-final.png` |
| L-c2 | LOW (copy) | Two firm-facing refusal messages embed raw statuses: "(placement payment status: **AWAITING_APPROVAL**)" and "This payment placed a trust investment (**ACTIVE**)". They are readable and correct, and are firm-only. | `backend/.../trustaccounting/transaction/TrustTransactionService.java:295`, `:1287` | `screenshots/L4-1280x720-inv1-withdraw-refused.png`, `L4-1440-D40-reverse-placement-refused.png` |

**Observations, not filed:**
- **Register status casing:** "Cancelled" is sentence case, while ACTIVE, MATURED and WITHDRAWN stay upper case. This is deliberate per `frontend/app/(app)/org/[slug]/trust-accounting/investments/page.tsx:25-32` ("the other statuses keep their existing text").
- **Rejected investment still listed:** the investments page lists the CANCELLED investment (as Cancelled, with no actions), as the check expects. The commit says the register **report** query (`InvestmentRegisterQuery`) leaves CANCELLED out. That report path was not exercised here.

## Data created (tenant `tenant_5039f2d497cf`), all through the UI

| What | id | Final state |
|---|---|---|
| Deposit "G89-L KC check" R 1 000 (Lerato, RAF) | `59c1cc8c-cda9-4e85-89c7-c7948410b3fc` | RECORDED |
| Deposit 150-character reference R 50 (Lerato, RAF) | `4add52b7-e533-4beb-adc5-d4ce600ac2fa` | RECORDED |
| Transfer "G89-L TRF KC check" R 100, OUT leg (Lerato, RAF) | `c3949f16-7ab3-4914-9a10-1b1959bb5623` | RECORDED |
| Transfer "G89-L TRF KC check" R 100, IN leg (Moroka, estate) | `612a28b3-45c5-4386-85f2-515b62c3a3f8` | RECORDED |
| INV-1 "G89-L Money Market A" R 500 (Lerato, RAF) | `a58f5008-0105-49ed-af3f-436f15a7c92c` | **CANCELLED**; placement `acd68cc2-b6e0-4a7e-9522-566d3e79379e` REJECTED (Bob) |
| INV-2 "G89-L Money Market B" R 600 (Lerato, RAF) | `a7e7cea6-a9fb-442f-81a7-e3bb8d9cbe87` | **WITHDRAWN**; placement PAYMENT APPROVED (Bob); withdrawal DEPOSIT `cab8efc3-dfd9-4858-855a-85382b9d3a56` R 600 RECORDED |
| Emails | Mailpit | trust activity ×2 (deposits) + Lerato magic link `i4BrdXse8bvV3XUzviQMz9` |

**Ending trust state:**

| Ledger | Balance |
|---|---|
| Lerato | **R 11 023,02** (RAF; portal equal) |
| Moroka | **R 50 100,00** (estate; portal equal) |
| Sipho | R 0,00 |
| Cashbook | R 61 123,02 |

## Restored / changed settings

- **Roles, capabilities and firm settings:** none changed.
- **Portal:** prod swap restored to `next dev` (svc 93130); `next-env.d.ts` restored; `$MAIN` `git status` equal to pre-walk. `portal/.next` now holds a dev build again, as after W2.
- **Navigate-hook marker** `/tmp/.e2e-keycloak-mode`: removed.
- **caffeinate 89475:** killed.
- **Browser:** the `leratoL` portal context was closed. Firm tabs remain signed in: Thandi in the default context and Bob in `bob`. Carol was not used.
