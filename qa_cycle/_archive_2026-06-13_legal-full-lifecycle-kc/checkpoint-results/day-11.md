# Day 11 — Sipho sees trust balance on portal `[PORTAL]` — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Sipho Dlamini — portal :3002 (reused the still-valid Day 4/7/8 magic-link session, zero re-auth; header shows "Sipho Dlamini").
**Driver**: QA agent via Playwright MCP — portal app browser UI only on :3002. Mailpit message API used ONLY to read the trust-activity nudge email body + CTA link (legitimate QA email use). Code read (portal trust page + transaction-list component + backend `PortalTrustLedgerService`) used to confirm the `/trust` redirect and the description-sanitisation/fallback contract — diagnostic, not a UI bypass.
**Pre-checks**: svc.sh status — backend (PID 45933) / gateway / frontend / portal all RUNNING+HEALTHY.
**Result**: **8/8 in-scope checkpoints PASS + 4/4 summary checkpoints PASS. Zero new gaps.** Trust balance R 50 000,00 on the portal matches the firm-side Day 10 Section 86 posting exactly; DEP/2026/001 deposit row renders with client-safe description, ZAR currency throughout, zero portal-origin JS errors.

## Entry point — the trust-activity nudge email (Mailpit)

Mailpit message **`oKbxNYcDLsjM3LTYostKqv`** — `From: noreply@kazi.app`, `To: sipho.portal@example.com`, `Subject: "Mathebula & Partners: Trust account activity"`, sent 2026-06-13T11:31:47Z (the Day 10 deposit-time nudge). Body: "A new transaction has been recorded in your trust account." with a table — **Date 13 Jun 2026 / Type DEPOSIT / Amount R 50 000,00** — and a **"View trust ledger"** CTA linking to `http://localhost:3002/trust/08ad56c4-ff5e-49c2-a034-cb5fa04b462c` (the RAF-2026-001 matter ledger). The email body does NOT contain the firm-entered description "Initial trust deposit — RAF-2026-001" — sanitisation already applied at the email layer (amount/type/date only).

## Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 11.1 | Mailpit → verify trust-deposit nudge email arrived for sipho.portal@example.com (subject contains "trust deposit" / "funds received" / "trust balance update") | PASS | Email `oKbxNYcDLsjM3LTYostKqv`, subject **"Mathebula & Partners: Trust account activity"** (the legal-za trust-activity template — semantically equivalent to the scenario's "trust balance update" phrasing), to sipho.portal@example.com, amount R 50 000,00 / DEPOSIT / 13 Jun 2026. Sent at deposit time (11:31:47Z). |
| 11.2 | Click the "View trust balance" link → lands on /trust | PASS | CTA "View trust ledger" → `http://localhost:3002/trust/08ad56c4-…` → renders the RAF-2026-001 matter trust ledger directly. NOTE: the email deep-links to the **matter ledger** (`/trust/{matterId}`), not the bare `/trust` index. Separately verified that navigating to bare `/trust` **auto-redirects to the same matter ledger** — the `/trust` index (`portal/app/(authenticated)/trust/page.tsx` L64-68) `router.replace`s to the single matter when the customer has exactly one matter with trust activity (Sipho has only RAF-2026-001). Both entry points land on the same correct surface. |
| 11.3 | Verify /trust renders: trust balance card at top, recent deposits list, ledger preview | PASS | Matter ledger renders all three regions: (1) **Trust balance card** at top — "Trust balance" heading, **R 50 000,00**, "As of 13 Jun 2026", "Matter 08ad56c4"; (2) **Transactions** table (Date / Type / Description / Amount / Running balance) = the recent-deposits / line-level ledger; (3) **Statements** section ("No statement documents yet" — empty by design; `PortalTrustLedgerService.listStatementDocuments` returns `[]` until a STATEMENT category lands, phase 67 — avoids leaking firm-internal docs). For the single-matter case the matter-ledger view IS the trust surface (index collapses into it). |
| 11.4 | Trust balance card shows R 50,000.00 (matches firm-side Day 10 posting) | PASS | Balance card paragraph = **R 50 000,00** — exact match to the Day 10 firm-side Section 86 posting (Trust Accounting overview R 50 000,00, Sipho client ledger R 50 000,00, RAF-2026-001 matter Trust tab R 50 000,00). No rounding/display drift. |
| 11.5 | Recent deposits list shows the R50,000 deposit dated Day 10 with source description (sanitisation may strip internal notes — verify only client-safe copy visible) | PASS | Transactions table has exactly one row: **13 Jun 2026 / DEPOSIT / "Initial trust deposit — RAF-2026-001" / R 50 000,00 / running balance R 50 000,00**. Description is the firm-entered client-safe copy — no `[internal]` tags, 36 chars (≤140), no firm-internal memo/template leakage. Date matches the Day 10 deposit. |
| 11.6 | Click into the matter trust ledger → line-level history renders with all transactions (just the one deposit) | PASS | Already on the matter ledger (`/trust/08ad56c4-…`). Line-level history = the single DEPOSIT row above with per-transaction Amount AND Running balance columns (R 50 000,00 / R 50 000,00). Exactly one transaction at this point, as expected. |
| 11.7 | 📸 Screenshot day-11-portal-trust-balance.png — trust balance card with first deposit | PASS | Saved `checkpoint-results/day-11-portal-trust-balance.png` (full page: balance card R 50 000,00 + transactions table + statements empty-state). |
| 11.8 | Verify currency rendered as R / ZAR (not $ / EUR / GBP) | PASS | Both balance card and table render **R 50 000,00** — "R" symbol, space thousands separator, comma decimal (en-ZA / ZAR locale via `formatCurrency(amount, "ZAR")`, default currency = "ZAR" in `TransactionList`). No $/€/£ anywhere. |

## Day 11 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Trust deposit visible on portal within 1 business day of firm posting | PASS | Deposit posted firm-side 11:31:47Z (Day 10); visible on the portal same-day (nudge email + ledger both reflect it immediately). Well within 1 business day. |
| Amount matches firm-side Section 86 ledger (no rounding / display bug) | PASS | Portal R 50 000,00 = firm-side R 50 000,00 across all three firm surfaces (account overview, client ledger, matter Trust tab). Zero discrepancy. |
| Description sanitisation — firm-internal `[internal]` tags stripped, copy ≤ 140 chars, safe fallback if no client-safe copy | PASS | Portal shows the firm-entered client-safe description verbatim ("Initial trust deposit — RAF-2026-001", 36 chars, no `[internal]` tags). No internal tag present to strip in this instance. Safe-fallback path confirmed in code: `transaction-list.tsx` `describeTransaction` (L26-36) synthesises "{HumanType} — {reference}" when `description` is blank, so a row is never empty. Email body separately omits the description (amount/type/date only). |
| ZAR currency throughout (legal-za default) | PASS | R / ZAR on the balance card and both money columns; default currency "ZAR" in `TransactionList`, en-ZA formatting. |

## Console / network notes
- **Portal page (:3002) console: 0 errors, 1 warning.** The single warning is the benign Next.js dev `scroll-behavior: smooth` advisory (`localhost:3002/_next/...`), not a runtime error. Scoped (non-`all`) console read returned **0 errors** for the portal navigation.
- The `localhost:3000` errors that appeared in an `all:true` console dump (OBS-201 `/api/assistant/invocations` 404, OBS-506 `INTAKE/sessions` 404, favicon) are **stale carry-over from the earlier firm-side Day 10 session in the same browser context** — NOT emitted by the portal (:3002) page. They are firm-side exempt carry-overs, not Day 11 portal defects.

## Carry-over exemptions observed (not re-filed)
- **OBS-201 / OBS-506**: firm-side (:3000) assistant 404s present only as stale console history from the prior firm session — not portal-origin, exempt, not re-filed.
- **OBS-701** (no structured fee/VAT line on portal proposal): not relevant to the trust surface; not re-observed.

## Gaps filed
None. Day 11 passed cleanly with zero new gaps.

## Observations (non-defects, for Product awareness)
- The trust-activity email subject is "Trust account activity" rather than the scenario's literal "trust balance update" / "funds received". Semantically equivalent and clearly client-facing; treated as PASS (subject-phrasing is a copy choice, not a defect).
- The email CTA deep-links straight to the matter ledger (`/trust/{matterId}`) rather than the `/trust` index. This is good UX for the single-matter case and the index would redirect there anyway. No gap.

## Entity IDs (for downstream days)
- **Sipho Dlamini Client ID**: `2211a80a-5523-4a6d-8f96-0d638dff88f6` (unchanged)
- **RAF Matter ID**: `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` (RAF-2026-001) — the matter whose trust ledger Sipho views; portal balance R 50 000,00
- **Trust Transaction**: DEP/2026/001, DEPOSIT, R 50 000,00, running balance R 50 000,00, 2026-06-13 — visible on the portal ledger
- **Trust-activity email**: Mailpit `oKbxNYcDLsjM3LTYostKqv` (CTA → `:3002/trust/08ad56c4-…`)

## Note for Day 14
Day 12/13 are not in this scenario's day list (next scripted firm day is **Day 14 — Firm onboards Moroka Family Trust**, isolation setup, context swap back to :3000 as Thandi). Day 15 then re-uses Sipho's portal session to prove tenant/customer isolation (he must NOT see Moroka's data).
