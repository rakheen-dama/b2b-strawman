# G89 Batch A: KC browser verification (PR #1746, `9cb368e50`)

- **Walked:** 2026-11-07, 01:10–01:33 SAST.
- **Stack:** Keycloak dev stack. Firm app :3000 (org `mathebula-partners`), portal :3002, Mailpit :8025.
- **Tenant schema:** `tenant_5039f2d497cf`.
- **Backend build:** PID 91747 started 01:09:15 SAST, after the merge commit (01:08:37). `target/classes/.../InterestService.class` was rebuilt at 01:09, and `javap` shows the new `interestCreditReference(UUID, UUID, UUID)`.
- **Driver:** Playwright MCP at 1440×900. Each person had their own isolated context: Thandi (Owner) in the default context, Bob (Admin) in `bob`, and Lerato (portal) in `lerato`.
- **Method:** every write went through the UI. SQL was read-only and used for evidence only.

## Result

| Row / gap | Status | Observation (exact UI text / amounts) | Evidence |
|---|---|---|---|
| **F10**: wizard shows client names and the arrangement-rate label | **VERIFIED** | Calculate step header reads `Client · Avg Daily Balance · Gross Interest · LPFF Rate · LPFF Share · Client Share`.<br>Row reads `Lerato Mthembu · R 10 000,00 · R 55,21 · LPFF arrangement rate 0.00% · R 0,00 · R 55,21`.<br>There is no "Client ID" header, no UUID and no bare "Arrangement".<br>Note: *n* is the LPFF share % (0.00 %), per `InterestRunWizard.tsx:413` (`formatSharePercent(a.lpffSharePercent)`). It is not the 6.50 % interest rate. | `B3-F10-allocations-table.png` |
| **F22**: portal balance = firm client ledger after interest | **VERIFIED** | Firm, Trust › Client ledgers › Lerato: `Trust Balance R 10 055,21`. Its rows are `INT-CREDIT-5724401a-…-e0f25d3f-…-ec988df3 Interest Credit R 55,21` and `DEP/LERATO-g89a Deposit R 10 000,00`.<br>Portal (Lerato, magic link from Mailpit): `/trust` redirects to `/trust/e0f25d3f-…`, which shows `Trust balance R 10 055,21`.<br>The portal row `Interest credited · Interest credit for period 2026-10-01 to 2026-10-31 · R 55,21 · R 10 055,21` is visible.<br>`portal.portal_trust_balance.current_balance = 10055.21` (matter e0f25d3f), with 2 `portal_trust_transaction` rows that include the INTEREST_CREDIT.<br>`TRUST_INTEREST_POSTED` notifications went to Thandi and Bob, so InterestPosted was published.<br>After the step E refund, the portal shows R 0,00, in step with the matter. | `C1-firm-client-ledger-lerato.png`, `C4-portal-trust.png`, `C3-portal-home.png`, `E8-portal-trust-after-refund.png`, `db-02-portal-after-interest.txt`, `db-04-backend-log-excerpt.txt` |
| **F22 / ADR-366**: interest allocated to the matter (step B SQL) | **VERIFIED** | Run `5724401a-30b4-47ca-b314-965c5c67abf1` (01 Oct → 31 Oct, POSTED) has one allocation line: `project_id = e0f25d3f-…`, `balance_days 310000.00`, `client_share 55.21`.<br>The INTEREST_CREDIT `bae1cbe0-…` has `project_id = e0f25d3f-f195-42fe-ba95-842e79de983d`, `transaction_date 2026-10-31`, and `reference = INT-CREDIT-5724401a-30b4-47ca-b314-965c5c67abf1-e0f25d3f-f195-42fe-ba95-842e79de983d-ec988df3`, which matches `INT-CREDIT-{run}-{matter}-{8hex}`.<br>Interest check: R 10 000 × 6.5 % × 31/365 = R 55,21. | `db-01-interest-run.txt`, `B7-run-posted.png` |
| **F14**: matter Trust tab shows the matter balance | **VERIFIED** | After the unmattered R 2 000 deposit, Matter › Fees · Trust shows `Trust Balance  Funds Held  R 10 055,21` and `Lerato Mthembu — trust balance across all matters: R 12 055,21`.<br>After the exact refund it shows `No Funds  R 0,00` and `Lerato Mthembu — trust balance across all matters: R 2 000,00`. | `D2-F14-matter-trust-tab.png`, `E5-matter-trust-tab-after-refund.png` |
| **F13**: ledger totals reconcile | **VERIFIED** | Before the refunds: `Deposits R 12 000,00 · +Interest R 55,21 · −Payments R 0,00 · −Fee transfers R 0,00 · −Refunds R 0,00 · =Balance R 12 055,21`. Arithmetic: 12 000 + 55,21 = 12 055,21 ✓.<br>After the refunds: `Deposits R 12 000,00 · +Interest R 55,21 · −Payments R 0,00 · −Fee transfers R 0,00 · −Refunds R 10 055,21 · =Balance R 2 000,00`. Arithmetic: 12 000 + 55,21 − 10 055,21 = 2 000,00 ✓.<br>The AWAITING_APPROVAL over-refund (R 10 056,21) is correctly excluded.<br>The Transfers in/out lines are not rendered; both are zero for this client.<br>On the client-ledgers list, the Lerato row (`R 2 000,00 · R 12 000,00 · R 55,21 · R 0,00 · R 0,00 · R 10 055,21`) reconciles, and so do Moroka and Sipho. | `D3-F13-ledger-breakdown.png`, `E6-F13-ledger-breakdown-after-refund.png`, `E7-client-ledgers-list.png` |
| **F12**: matter-level debit guard | **VERIFIED** | Thandi recorded a refund of R 10 056,21 on the RAF matter. The client card held R 12 055,21 and the matter R 10 055,21.<br>When Bob clicked Approve, the row showed: **"Insufficient trust balance on matter Lerato Mthembu — RAF Claim (MVA 2025-11-03) for Lerato Mthembu. Available on matter: R10055.21, Requested: R10056.21"**.<br>The status stayed `AWAITING_APPROVAL`, with no `trust_refund.approved` audit event.<br>The exact refund `REF/LERATO-g89a-exact` (R 10 055,21) was approved by Bob, and the status is `APPROVED`. The matter cell is now 0.00 and the unmattered cell 2000.00. | `E2-F12-over-refund-refused.png`, `E4-exact-refund-approved.png`, `db-03-after-refunds.txt` |

Step A, the backdated LPFF rate: the UI accepted an effective date of 2026-10-01 (6.5 %, LPFF share 0 %, same as the existing rate). No step was deferred. See `A1-add-rate-form.png` and `A2-rate-added.png`.

Separation of duties held. In Thandi's own wizard, Approve showed "Awaiting approval by another member". Bob approved and Thandi posted. See `B4-awaiting-approval-creator.png` and `B5-bob-approved.png`.

## Unexpected / notes (not Batch A failures)

1. **The portal shows the recording date, not the transaction date.** The portal Trust page dates every row "7 Nov 2026", while the firm ledger has the deposit on 01 Oct 2026 and the interest credit on 31 Oct 2026. `portal_trust_transaction.occurred_at` is the row's `created_at`. The portal Home "Last trust movement … 7 Nov 2026" has the same cause, and so does the Mailpit "Trust account activity" email for the deposit ("Date 7 Nov 2026"). This is probably pre-existing behaviour, not something #1746 introduced. Worth a defect row.
2. **Unmattered money is not on the portal.** After step D the firm client card is R 12 055,21, but the portal shows only the matter (R 10 055,21). After step E the firm shows R 2 000,00 and the portal R 0,00. This is expected under ADR-366 D7, where trust rows with no matter are out of scope (G89-F28). Under F28, portal and firm still diverge for any client holding unmattered money.
3. **The refusal amounts are not locale-formatted.** The F12 message reads `R10055.21` / `R10056.21` rather than `R 10 055,21`. This is cosmetic.
4. **The interest run's Posted Date shows "06 Nov 2026".** The run was posted at 01:18 SAST on 07 Nov (`posted_at 2026-11-06 23:18 UTC`). That is a UTC-date display, the same class as G88-F48.
5. **The magic-link exchange initially showed "Login Failed — Link expired or invalid".** The backend log shows the exchange succeeded at 23:20:15Z ("Exchanged magic link for portal JWT"). A Next dev Fast Refresh then re-ran the exchange, and the second call got a 401. The JWT was already stored, and `/home` loaded signed-in. This is a dev-mode artefact of first-compile HMR.
6. **The over-refund is still pending.** `REF/LERATO-g89a-over` stays in AWAITING_APPROVAL. I deliberately did not reject it, because the brief did not ask for that.
7. **Console (F):**
   - No application errors on the firm pages.
   - The only errors were `favicon.ico` 404s (Keycloak :8180 and portal :3002) and the one `401 /portal/auth/exchange` from note 5.
   - The portal logged a Next warning: "Detected `scroll-behavior: smooth` on the `<html>` element".
   - Logs: `console-thandi.log`, `console-bob.log`, `console-lerato-portal.log`.
8. **No backend log line for the refusal or for InterestPosted.** Neither the 400 refusal nor InterestPosted is logged at INFO. The refusal is evidenced by the UI text and the unchanged status; the publish is evidenced by the `TRUST_INTEREST_POSTED` notifications.

## Data created (tenant `tenant_5039f2d497cf`)

| What | Id | Detail |
|---|---|---|
| LPFF rate | `5d75711a-1b0f-48ed-bfa3-3013f9f6ae1b` | effective 2026-10-01, 6.50 %, share 0 %, notes "G89 Batch A verify - backdated arrangement rate -g89a" |
| Deposit | `079830ba-defd-4121-9251-1cc04ed67faf` | Lerato, RAF matter, R 10 000,00, 2026-10-01, `DEP/LERATO-g89a`, RECORDED |
| Interest run | `5724401a-30b4-47ca-b314-965c5c67abf1` | 2026-10-01 → 2026-10-31, POSTED, total R 55,21 (created Thandi, approved Bob, posted Thandi) |
| Interest allocation | `1d568a81-e8f9-46f3-b146-9792b6ac4d2b` | Lerato |
| Allocation line | `77a9452f-fcf0-4535-a761-b1f976569a2f` | RAF matter, R 55,21 |
| Interest credit | `bae1cbe0-d6cc-4c61-8fb3-2db730b6ec69` | Lerato, RAF matter, R 55,21, 2026-10-31 |
| Deposit (no matter) | `432c0c97-080a-4c52-a39a-0004e3c961ab` | Lerato, R 2 000,00, 2026-11-07, `DEP2/LERATO-g89a`, RECORDED |
| Refund (over) | `cf65e9ef-8cef-4242-863c-5ee9cd676704` | RAF matter, R 10 056,21, `REF/LERATO-g89a-over`, **AWAITING_APPROVAL** (refused on approve) |
| Refund (exact) | `857b5426-a6d4-4f3d-a903-4fc1ed561d1f` | RAF matter, R 10 055,21, `REF/LERATO-g89a-exact`, APPROVED by Bob |
| Notifications | — | 2 × `TRUST_INTEREST_POSTED` (Thandi, Bob) |
| Portal magic link | — | 1 email sent to lerato.mthembu@email.co.za (Mailpit `BxohTGh68k3zRGtkcrxYWQ`), consumed |

**Net effect on the tenant:**
- Lerato's client card went from R 0,00 to **R 2 000,00**, all of it unmattered.
- The RAF matter cell is **R 0,00**.
- The trust account cashbook went up by R 2 000,00 net. Before the walk the dashboard showed R 48 019,15.
- The October 2026 period now has a POSTED interest run, so a later October run for this account would overlap.
