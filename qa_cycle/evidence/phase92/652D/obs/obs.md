# 652D OBS: PR #1818, head 5c2f17f640bb06338836ba620e0a52a60935e593

Run date: 2026-11-19, 16:24–16:33 UTC. Stack: e2e mock-auth on :3001/:8081. Browser: Playwright MCP (headless Chromium; the profile was free). Tenant schema: `tenant_7d218705360b`.

## Stack build proof
- `e2e-down.sh && e2e-up.sh` ran from the worktree root. The build log says "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-652D". EXIT=0 (`stack-build.log`, `stack.done`).
- `git rev-parse HEAD` = 5c2f17f640bb06338836ba620e0a52a60935e593.
- e2e-backend image d229a15f8f38 was built at 2026-11-19 18:25:55 SAST. The container was created at 2026-11-19T16:26:13Z (`stack-proof.txt`).
- The behaviour is the proof that the head is running: both documents pick the replacement account, which `main` does not do.

## Ids
- Client B: b2ca88fa-f1ba-4547-b009-8818a81ba734
- Matter B: 101d14ed-2c0a-4d51-866c-aeef618722ef
- Primary account (seed, "Mathebula & Partners Trust Account"): a76945ed-7901-4959-88bc-75dd493872d4
- Replacement account ("OBS 652D Replacement Trust Account"): 9d2ea361-5f80-4383-b1ce-d010311e7a66
- Payment transaction: 73882f0d-e854-42ba-85fc-5ec25e631b50
- SoA generated_document / statement row: da20dc15-e776-46f8-a957-a729a0ad1494
- CTS generated_document: 47bf63ef-19d2-4ddd-a9a9-50bd916d1e70

## Steps
1. **PASS (with a deviation).** Alice created the client and the matter in the UI.
   - Client "OBS 652D Client B" via New Client. Matter "OBS 652D Matter B" (LITIGATION) via Matters → New Matter.
   - Deviation: the client is in **ONBOARDING**, not ACTIVE. Start Onboarding was done in the UI. Activation needs an address and an 8-item checklist, so I did not do it.
   - Why it does not matter here: `CustomerLifecycleGuard` lets ONBOARDING create matters and documents. Nothing under `verticals/legal/trustaccounting` or `verticals/legal/statement` reads the lifecycle status (a grep for LifecycleStatus finds nothing).
2. **PASS.** All in the UI on Transactions; at this point the primary was the only account.
   - Deposit OBS652D-DEP1, R 10 000,00 → RECORDED.
   - Payment OBS652D-PAY1, R 10 000,00 → AWAITING APPROVAL.
   - Bob (admin) signed in and clicked Approve on the row → APPROVED (approved_by 1f4e245c…, recorded_by Alice 36689b0b…).
   - Evidence: `psql-step2-transactions.txt`.
3. **PASS. This is the one necessary API step.**
   - Call: `POST /api/trust-accounts/a76945ed…/close` with Alice's mock-IDP owner JWT (the same `/token` call as `get_jwt`) → HTTP 200, `"status":"CLOSED","isPrimary":true`.
   - Reason: there is no UI action to close a trust account. The frontend never calls `/close`.
   - Evidence: `api-step3-close-primary.txt`.
4. **PASS.** Alice used Trust Accounting → `add-trust-account-button` to add "OBS 652D Replacement Trust Account".
   - GENERAL, FNB 250655 / 62065200001. "Set as primary trust account" was unticked; it was ticked by default.
   - DB: replacement ACTIVE is_primary=f; primary CLOSED is_primary=t.
   - Deposit OBS652D-DEP2, R 10 000,00 for client B on Matter B → RECORDED on the replacement. The Transactions page now resolves to the replacement, because the account list only returns active accounts.
   - Client Ledgers page: OBS 652D Client B R 10 000,00.
   - Evidence: `psql-step4-accounts.txt`.
5. **PASS: SoA.** Matter → Finance → Statements → Generate Statement of Account, period 2026-11-01 to 2026-11-19, generated at 16:30:56Z. The preview shows:
   - Trust Activity: Opening balance R0,00.
   - Deposits: 19 November 2026, OBS652D-DEP2 "OBS 652D deposit on replacement", R10 000,00. No payments.
   - **Closing balance: R10 000,00.**
   - Summary: **Trust balance held: R10 000,00.** Closing balance owing: R0,00.
   - Neither DEP1 nor PAY1 from the closed primary is printed.
   - Statement list row statement-row-da20dc15…: "19 Nov 2026 | R 0,00 | **R 10 000,00** | Download".
   - Stored snapshot summary.trust_balance_held = "10000.00".
   - Screenshots: `652d-02-soa-preview-trust-section.png`, `652d-02b-soa-preview-summary.png`, `652d-03-soa-statement-list-row.png`.
6. **PASS: CTS.** Client page → Generate Document → Client Trust Statement shows:
   - **"Account: OBS 652D Replacement Trust Account"**, Opening balance R0,00.
   - One row: DEP2, R10 000,00, balance R10 000,00.
   - **Closing balance: R10 000,00.**
   - "The current balance held in trust on your behalf is R10 000,00."
   - Save to Documents → "Document saved successfully" (generated_documents 47bf63ef…, 16:31:35Z).
   - The client overview Trust balance card also reads R 10 000,00 / OBS 652D Replacement Trust Account.
   - Screenshots: `652d-04-cts-preview.png`, `652d-05-client-overview-after.png`.
7. **PASS: backend log** (WARN level; full JSON in `backend-log-excerpt.txt`):
   - 16:30:56.425Z StatementOfAccountContextBuilder: "Customer b2ca88fa… has activity on 2 trust accounts; using 9d2ea361-5f80-4383-b1ce-d010311e7a66 for SoA Trust Activity section".
   - 16:31:22–16:31:35Z ClientTrustStatementContext (×6): "Client b2ca88fa… has activity on 2 trust accounts; using 9d2ea361-5f80-4383-b1ce-d010311e7a66 for the client trust statement".
   - No ERROR lines in the backend log.
8. **PASS: read-only psql** (`psql-final.txt`).
   - Accounts: Mathebula & Partners CLOSED is_primary=t closed 2026-11-19; OBS 652D Replacement ACTIVE is_primary=f.
   - Client B ledger sum per account: **primary 0.00** (DEP1 +10000, PAY1 −10000 approved); **replacement 10000.00** (DEP2).
9. Optional case (a): skipped.

Teardown: `e2e-down.sh` EXIT=0, all e2e containers and volumes removed (`stack-teardown.log`).

## Screenshot of the trust accounts list
`652d-01-trust-accounts-settings.png` (Settings → Fees & Trust → Trust accounting) shows **only the replacement (ACTIVE)**. The UI never lists the closed primary, so the closed-primary state is evidenced by psql and the API response, not by a screenshot.

## Found
- (F-1, LOW, not 652D scope) One CTS save request (requestId b63a2b23…) logged the CTS account-selection WARN 3 times. The preview opening logged it 3 more times across 3 requests. So the client trust context is built more than once per generate, through `CustomerContextBuilder.java:158` (`clientTrustStatementContext.buildFor`), which is called by every customer-context build. It is redundant ledger work, not a correctness problem.
- (F-2, INFO) The trust-account lists in the UI hide CLOSED accounts. A firm cannot see the closed primary or that it still has is_primary=t; there is no UI close or reopen.
- (F-3, INFO) Closing the primary leaves is_primary=t on a CLOSED account. Every primary-based surface then falls back to the first non-investment active account (`trust-accounting/transactions/page.tsx:138`). That is consistent with the brief; recorded for awareness.
