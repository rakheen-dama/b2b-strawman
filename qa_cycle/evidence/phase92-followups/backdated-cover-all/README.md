# Phase 92 residuals — one cluster PR (founder-authorised 2026-11-23, CLAUDE.md §7 cluster exception)

Source: `tasks/trust-accounting-open-issues-2026-11-23.md` §1.1–§1.5 and §2 D-1 / D-2. Base: `main` `ef0563fe3`.

| # | Item | Severity | Verdict | Evidence |
|---|---|---|---|---|
| 1 | §1.1 / §1.2 back-dated outflows covered only as at today (D-1) | MEDIUM | **Fixed** | `notes.md`, `red-run.txt`, `tier2-run.txt` (this folder) |
| 2 | §1.3 `reverseInterest` vs auto-match deadlock (CF-3b class) | MEDIUM hypothesis | **Not reproduced — impossible by construction**; guard tests kept | `../reverse-interest-deadlock/` |
| 3 | D-2 interest reversal requires a reason | ruling | **Done** (backend + dialog) | below; red in `red-run.txt` (backend) and `../f11-reload-retry/red-run.txt` (dialog) |
| 4 | §1.5 an Idempotency-Key survives a reload (648B F-11 residual) | MEDIUM | **Fixed** (frontend only, option (a)) | `../f11-reload-retry/` |
| 5 | §1.4 document trust flag vs printed PDF (FU-3 residual) | MEDIUM (rated 2026-11-23) | **Fixed** | `../document-trust-flag/` |

## 1. Outflows changed (D-1)

Every client-ledger outflow dated before today is now covered by the client balance and the matter cell at their lowest point from its date on (FU-5's helpers); dated today → today's check and message, unchanged. Refusal: 400 "Insufficient trust balance", " as at <date>", matter properties unchanged.

| Outflow | Check |
|---|---|
| `PAYMENT` | approval |
| `REFUND` | approval |
| `INV_PLACE` (s86(4)) | request (matter cell) **and** approval (client + cell), keyed on `depositDate` |
| `TRANSFER_OUT` | `recordTransfer` (source client + its cell) |
| `reverseTransferOut` | receiving client + its cell, keyed on `reversalDate` |
| `reverseUnidentifiedAllocation` | client + its cell, keyed on `reversalDate` |
| `FEE_TRANSFER`, deposit reversal | unchanged behaviour (already from the date on: F-646D-1, FU-5); now through the same one rule |

**Left unchanged, with reason** (full table in `notes.md`): `INV_WITHDRAW` (investment ledger; dated on/after the deposit and the last interest entry, so it cannot dip as at a past date); interest reversals (dated the entry's own date — nets to zero); contras of payments / refunds / fee transfers (client inflows); `TRANSFER_IN` / `UNIDENT_ALLOC_IN` direct reversal (refused, go through their pair); non-client rows; the FU-9 cashbook check (unchanged by instruction); the demo seeder.

Lock order unchanged (the client anchor still precedes every read; account rows still last).

## 2. Deadlock verdict

**Not reproduced; cannot occur.** `reverseInterest` refuses a line-recorded entry before locking any row; an entry without a line is a bank advice, a bank advice is always NET (GROSS without a line is refused), and a NET entry has no Fund-share row (`chk_invest_interest_fund_txn`). So `reverseInterest` locks exactly one trust row, and it shares only that row with auto-match — no cycle. File:line chain in `../reverse-interest-deadlock/notes.md`. `ReverseInterestAutoMatchLockOrderIntegrationTest` (3 cases, house gate pattern, both orders) passes on main and stays as the guard; the first case fails if the one-row invariant is ever broken. No lock-order change made.

## 3. Interest reversal requires a reason (D-2)

- `POST /api/trust-investment-interest/{id}/reverse` takes a body `ReverseInterestRequest(@NotBlank String reason)` — `ReverseRequest`'s validation (`@NotBlank`, no length limit; the dialog caps it at 500 like the transaction reversal). A new record rather than `ReverseRequest` itself because `ReverseRequest.reversalDate` would be silently ignored (an interest contra is dated the entry's date).
- Missing body → 400 "Malformed request body"; `{}` or a blank reason → 400 "Validation failed" (`fieldErrors[0].field = reason`). A service caller without a reason → 400 "Invalid reversal" / "Reversal reason is required" (as `reverseTransaction`).
- The reason is each contra's description and the `trust_transaction.reversed` audit `reason`, as for every other reversal; it is also added to `trust_investment.interest_reversed` (an unmatch passes none). The fixed text "Section 86(4) interest entry reversed" is gone from the write path.
- Client surfaces never show it: `TrustTransactionTypeLabels.clientDescription` renders a REVERSAL as "Reversal — <reference>"; `TrustLedgerPortalSyncServiceIntegrationTest.aReversedInterestShowsBothMovements` now reverses with a firm reason and asserts the portal row does not contain it.
- `@IdempotentTrustWrite` kept; the request hash covers the body: same key + same reason replays (`Idempotency-Replayed: true`), same key + another reason → 409 (`TrustInvestmentControllerTest`).
- No API contract / wire snapshot covers this endpoint (grepped `TrustWireContractTest`, test and main resources).
- Frontend: `InvestmentInterestEntriesDialog`'s confirm step has a required "Reason" field — the transaction reversal dialog's label, placeholder "Reason for reversal", `maxLength` 500 and "Reason is required" (`interestReversalReasonSchema = reversalReasonSchema.pick({ reason })`); `reverseInvestmentInterest(interestId, { reason }, key)` sends it in the body with the F-11 key.

## 4. Idempotency key survives a reload

`useIdempotencyKey` persists an unsettled key in `sessionStorage` (keyed by the request's identity: endpoint name + ids + normalised form values), from send until a definite answer; reload + same submit reuses it. Success / definite refusal clears it; 24 h TTL; storage failures fall back to memory; calculate keeps its fresh key; a hint ("A submit of this entry may already have been recorded — check the ledger before submitting again.") shows on every trust-write form with a stored unsettled key. All 17 hook call sites wired. Not covered: a second device or tab. **Escape action (ruling 2026-11-23):** with the notice comes "Submit as a new entry" ("Only if you have checked the ledger and it was not recorded:") — a deliberate second step that forgets the form's stored key and submits with a fresh one, so a key stuck IN_PROGRESS server-side no longer blocks the form for 24 h; the backend in-progress 409 detail now names that action ("…if it was not, choose 'Submit as a new entry'."; title and code unchanged). Details in `../f11-reload-retry/notes.md`.

## 5. Document trust flag from the rendered context

`generateDocument` now builds the context once; validation, `containsTrustData` and the PDF (`PdfRenderingService.renderPdf`, new) all use that map. DOCX and Statement of Account were already single-build. Red/green via the FU-3 race harness. `../document-trust-flag/notes.md`.

## Runs

- Backend red: `red-run.txt` (items 1–3), `../document-trust-flag/red-run.txt` (item 5), `../reverse-interest-deadlock/red-run.txt` (item 2, not reproduced).
- Backend Tier 2: `tier2-run.txt` — 221 classes, 2 212 testcases, 0 failures / errors (surefire XML). Full `./mvnw verify` not run (the orchestrator's).
- Frontend red: `../f11-reload-retry/red-run.txt`; green + lint + typecheck: `../f11-reload-retry/green-run.txt` (targeted 25 files / 367 tests). Full frontend gate not run (the orchestrator's).
