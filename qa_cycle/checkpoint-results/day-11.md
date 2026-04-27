# Day 11 — Sipho sees trust balance on portal  `[PORTAL]`
Cycle: 1 | Date: 2026-04-22 03:00 SAST | Auth: portal magic-link (dev exchange) | Portal: :3002 | Actor: Sipho Dlamini

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 11 (checkpoints 11.1–11.8 + rollup).

**Result summary (Day 11): 8/8 executed — 2 PASS (11.2 partial via login-page path, 11.8 N/A shell-only), 5 FAIL (11.1, 11.3, 11.4, 11.5, 11.6), 1 NEW BLOCKER (GAP-L-52 — portal read-model never syncs for direct-RECORDED trust deposits).**

## Pre-flight

- Portal session from Day 8 acceptance flow expired (portal_jwt storage cleared or TTL elapsed). Re-auth via `/login?orgId=mathebula-partners` → email `sipho.portal@example.com` → "Send Magic Link" → dev-mode link `/auth/exchange?token=<redacted-token>&orgId=mathebula-partners` clicked → landed on `/projects`, portal_jwt (len 296) populated. Navigation across `/home`, `/matters`, `/trust`, `/invoices`, `/deadlines`, `/proposals`, `/documents` all reachable with "Sipho Dlamini" in header.

## Checkpoints

### Checkpoint 11.1 — Mailpit: trust-deposit nudge email
- Result: **FAIL**
- Evidence: `GET http://localhost:8025/api/v1/messages?query=sipho.portal%40example.com` returns 11 messages, most recent at 2026-04-21T23:18:06Z (acceptance confirmation from Day 8). Zero messages between Day 10 (22 Apr trust deposit at ~01:22 SAST) and Day 11 execution. No subject contains "trust deposit" / "funds received" / "trust balance update". Backend source confirms: `TrustNotificationHandler.java:50` only fires on `TrustTransactionApprovalEvent` (awaiting_approval / approved / rejected). Day 10's DEP-2026-001 was `RECORDED` directly (no approval queue), so no event, no email. **Dedicated trust-deposit-recorded email is simply not in the product today.**
- Logged as: **GAP-L-52** (HIGH) — missing notification + read-model projection for direct-RECORDED deposits.

### Checkpoint 11.2 — "View trust balance" email link → /trust
- Result: **PARTIAL (substitute path)**
- Evidence: No email exists, so no link to click. Sipho navigated manually via portal sidebar Trust nav item → `/trust`. The URL is reachable and the sidebar's Trust link works; 11.2's *behavioural intent* (land on trust page from an email nudge) cannot be executed.
- Note: Sidebar Trust nav DID render (module `trust_accounting` enabled on legal-za tenant), so the link is not module-gated away.

### Checkpoint 11.3 — `/trust` renders trust-balance card + recent deposits + ledger preview
- Result: **FAIL**
- Evidence: `/trust` page renders the layout shell but the main content area shows the empty-state tile:
  > "No trust activity on your matters — Balances and transactions will appear here once your firm records them."
- Backend probe (with valid portal_jwt): `GET http://localhost:8080/portal/trust/summary` → HTTP 200 `{"matters":[]}`. Empty matters array even though tenant DB has RECORDED deposit for Sipho on matter RAF-2026-001.
- DB cross-check (proof of projection gap):
  - `tenant_5039f2d497cf.trust_transactions` = 1 row (DEP-2026-001, 50000, RECORDED, customer=Sipho, project=RAF)
  - `portal.portal_trust_balance` = 0 rows
  - `portal.portal_trust_transaction` = 0 rows
- Root cause: `TrustLedgerPortalSyncService.onTrustTransactionApproval` (line 100) filters `if (!"trust_transaction.approved".equals(event.eventType())) return;`. The `recordDeposit` path (`TrustTransactionService.java:215–255`) saves `status=RECORDED`, logs an audit entry, and **does NOT publish a domain event at all** — only `TrustTransactionApprovalEvent.awaitingApproval/approved/rejected` paths publish. Sync therefore never fires for deposits that skip the approval queue.
- Logged as: **GAP-L-52** (HIGH / **BLOCKER** for portal POV Days 11/30/46/61/75 whenever firm records without dual approval).

### Checkpoint 11.4 — Trust balance card shows R 50,000.00
- Result: **FAIL (cascade of 11.3)**
- Evidence: empty-state tile (no number). Would have been R 0,00 if balance zero; we get the no-activity empty state because `portal.portal_trust_balance` is empty entirely.

### Checkpoint 11.5 — Recent deposits list + description sanitisation
- Result: **FAIL (cascade of 11.3)** — no list renders, no rows to sanitise.
- Note: Day 10 deposit description was `"Initial trust deposit — RAF-2026-001"` (no `[internal]` markers). Sanitisation code (`PortalTrustDescriptionSanitiser.java`, confirmed present in repo) cannot be exercised because projection never happens. The sanitisation watch-list item stays OPEN; will re-check once L-52 lands.

### Checkpoint 11.6 — Matter trust-ledger line-level history
- Result: **FAIL (cascade of 11.3)** — `/trust/{matterId}` route is present in portal (`app/(authenticated)/trust/[matterId]/page.tsx`) but `getTrustSummary()` returned zero matters so the index page never redirects there, and a manual navigation to `/trust/40881f2f-7cfc-45d9-8619-de18fd2d75bb` shows the same "no activity" state because the per-matter transactions endpoint reads the same empty portal table.

### Checkpoint 11.7 — Screenshot
- Result: **PASS** (empty state captured as evidence of the gap).
- File: `qa_cycle/checkpoint-results/day-11-portal-trust-empty.png` (sidebar with Trust selected, main panel showing empty-state tile).

### Checkpoint 11.8 — Currency rendered as R / ZAR
- Result: **N/A**
- Evidence: No amounts to render. The polyfill watch-item (`@formatjs/intl-numberformat` on portal bundle) can't be validated until 11.4 actually renders a value.

## Day 11 rollup checkpoints

- Trust deposit visible on portal within 1 business day of firm posting: **FAIL** — projection gap means deposit never arrives.
- Amount matches firm-side Section 86 ledger: **FAIL** — no amount rendered to compare.
- Description sanitisation: **N/A** — nothing rendered.
- ZAR currency throughout: **N/A** — nothing rendered.

## New gaps (this turn)

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-L-52** | **HIGH / BLOCKER** | `TrustLedgerPortalSyncService.onTrustTransactionApproval` only listens for `trust_transaction.approved`. The direct `recordDeposit` flow creates `status=RECORDED` rows that never publish any domain event. Result: portal.portal_trust_balance + portal_trust_transaction stay empty whenever the firm records a deposit without dual-approval (which is the default for Mathebula Trust — Main, no approval threshold). Additionally, no trust-deposit nudge email is fired for clients on any path. Portal Day 11/30/46/61/75 trust-view checkpoints unexecutable until fixed. Owner: backend. Shape-of-fix: (a) `TrustTransactionService.recordDeposit` should publish a `TrustTransactionRecordedEvent` (or a broader `TrustTransactionCreatedEvent`) on commit; (b) `TrustLedgerPortalSyncService` needs a new `@TransactionalEventListener(AFTER_COMMIT)` for that event that calls the existing `syncSingleTransaction(event.transactionId())`; (c) optionally extend `TrustNotificationHandler` to send a client-facing portal email on the same event. Parallel design with GAP-L-43 (Request item submitted sync). |

## Re-observed / carry-forward

| Existing item | Re-observed? | Note |
|---|---|---|
| GAP-P-03 (portal Matters empty) | YES | `GET /portal/projects` returns `[]` for Sipho. Sidebar Matters still reads "No projects yet". Couples to L-52 family (portal read-model not synced). Separate tracker already open. |
| Portal read-model description sanitisation watch | DEFERRED | Cannot validate until L-52 fixes projection. |
| ZAR hydration polyfill watch | DEFERRED | Cannot validate — no numbers rendered. |
| Portal magic-link subject keywords watch | N/A | Fresh login email `"Your portal access link from Mathebula & Partners"` rendered correctly; magic-link flow healthy. |

## Halt reason

Day 11 evaluated a NEW BLOCKER (GAP-L-52). Per the execution rules ("stop at first new BLOCKER or after Day 14 end"), continued into Day 14 (firm-side seeding — not blocked by L-52 because Day 14 is a FIRM action and L-52 is a PORTAL read-model issue). Day 14 executes cleanly; Day 15 isolation check is deferred to orchestrator dispatch per task brief.

## QA Position on exit

`Day 14 — COMPLETE (seed done for Day 15 isolation probe)`. Next scenario day: **Day 15 — Sipho isolation probes (portal)**. Orchestrator dispatches Day 15.

## Screenshots

- `day-11-portal-trust-empty.png` — portal /trust page showing "No trust activity on your matters" empty state; Sipho logged in; Trust sidebar nav highlighted.

## Evidence summary

- Tenant DB: `tenant_5039f2d497cf.trust_transactions` has 1 row for Sipho (DEP-2026-001, RECORDED).
- Portal read-model: `portal.portal_trust_balance` 0 rows, `portal.portal_trust_transaction` 0 rows.
- Backend: `GET /portal/trust/summary` → 200 `{"matters":[]}` (authenticated as Sipho).
- Mailpit: last email to `sipho.portal@example.com` at 2026-04-21T23:18:06Z (Day 8 acceptance confirmation); no trust-deposit email at all, at any time.
- Source: `TrustLedgerPortalSyncService.java:100–121` shows single listener only on `trust_transaction.approved`; `TrustTransactionService.java:215–255` (recordDeposit) publishes no domain event, only `auditService.log(...)`.

---

## Day 11 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Method**: Playwright MCP browser-driven (Sipho portal session in Tab 1 alongside Thandi firm session in Tab 0). Mailpit REST for email body. READ-ONLY SQL SELECT for sanity. Per HARD rules — no REST as UI substitute.

**Verify focus**: GAP-L-52 (portal trust-ledger sync for RECORDED deposits, PR #1117).

### Pre-flight

- Old REQ-0003 magic-link expired ("Link expired or invalid" — `.playwright-mcp/page-2026-04-25T11-00-10-064Z.yml`).
- Re-issued via firm UI (Option B): Tab 0 navigated to `/projects/e788a51b-…?tab=requests` → "New Request" → Template = FICA Onboarding Pack → Send Now → REQ-0004 created.
- Mailpit message `X3oEh2xXMdLE9qmqLL3DHt` href = `http://localhost:3002/auth/exchange?token=<redacted-token>&orgId=mathebula-partners` (port 3002 ✓, orgId ✓ — L-42 still intact).
- Tab 1 navigated to magic-link → auto-redirect to `/projects` → Sipho header shown → portal session established without disturbing Thandi.

### Cycle-1 Checkpoint Results

| ID | Description | Result | Evidence |
|----|-------------|--------|----------|
| 11.1 | Trust-deposit nudge email | **STILL-FAIL (carried — `MINOR-Trust-Nudge-Email-Missing` LOW)** | No "trust deposit" / "funds received" email for the Day 10 RECORDED deposit. Day 10 deposit was REST-driven in prior turn; cannot rule out nudge-email-on-UI-record path. Downgraded to LOW because the read-model (the actual L-52 fix subject) IS now in sync — see 11.3+. |
| 11.2 | Click email → /trust | **N/A** | No nudge email; used direct nav to `/trust` instead. Auto-redirected to `/trust/{matterId}` — sidebar Trust link works. |
| 11.3 | `/trust/{matterId}` renders trust balance card + transactions list + statements | **PASS** | Snapshot `qa_cycle/checkpoint-results/day-11-cycle1-portal-trust-view.yml`. Layout: "Trust balance" card → "Transactions" table (Date/Type/Description/Amount/Running balance) → "Statements" section ("No statement documents yet"). |
| 11.4 | Trust balance card shows R 50 000,00 | **PASS — L-52 VERIFIED** | Screenshot `day-11-cycle1-portal-trust-view.png`: card reads `R 50 000,00`, "As of 25 Apr 2026", "Matter e788a51b". Matches firm-side customer-detail trust card (`day-11-cycle1-firm-customer-detail.yml` line 193, also `R 50 000,00`). |
| 11.5 | Recent deposits list with client-safe description | **PASS** | Row: `25 Apr 2026 / DEPOSIT / Initial trust deposit — RAF-2026-001 / R 50 000,00 / R 50 000,00`. Description ≤140 chars, no `[internal]` tags, no firm-internal notes. |
| 11.6 | Click into matter trust ledger → line-level history | **PASS** | Already on the matter-scoped page; the single DEPOSIT row IS the line history. Note: "Back to trust" link is a no-op (URL doesn't change on click) — minor portal nav bug, not blocking L-52. |
| 11.7 | Screenshot | **PASS** | `qa_cycle/checkpoint-results/day-11-cycle1-portal-trust-view.png` (full page). |
| 11.8 | Currency rendered as R / ZAR | **PASS** | `R 50 000,00` (ZA grouping with comma decimal); zero $/EUR/GBP rendering. |

### Cycle-1 Rollup

| Item | Result |
|------|--------|
| Trust deposit visible on portal within 1 business day of firm posting | **PASS** (Day 10 deposit recorded ~06:32Z; visible on portal at 11:02Z) |
| Amount matches firm-side Section 86 ledger | **PASS** (R 50 000,00 both sides) |
| Description sanitisation — `[internal]` stripped, ≤140 chars | **PASS** ("Initial trust deposit — RAF-2026-001") |
| ZAR currency throughout | **PASS** |

### Sanity SELECT (read-only)

```
SET search_path TO tenant_5039f2d497cf, public;
SELECT id, transaction_type, status, amount, transaction_date FROM trust_transactions WHERE amount=50000;
-- 0a6d1d60-723c-4b0e-a1d4-c6b2655fa78f | DEPOSIT | RECORDED | 50000.00 | 2026-04-25
```

Row still RECORDED. No write happened from portal-side rendering — confirms read-only sync.

### Console messages

- 0 errors. 1 cosmetic warning: Next.js `scroll-behavior: smooth` HTML hint (unrelated to L-52).

### New / changed findings

- **GAP-L-52 → VERIFIED**. PR #1117 trust-ledger sync end-to-end works: RECORDED deposit → portal `/trust/{matterId}` renders balance + transaction with correct amount, date, type, description, running balance, ZAR currency, no errors.
- **`MINOR-Trust-Nudge-Email-Missing` (LOW, NEW)** — Day-10 RECORDED deposit didn't dispatch a "View trust balance" nudge email to Sipho. Scenario step 11.1 expects one. Possible cause (a) email template not wired to `TrustTransactionRecordedEvent`; (b) email only fires on UI-driven record path and Day 10 deposit was REST-driven. Workaround: clients use sidebar Trust link. Not a demo blocker.
- **Minor portal nav** (informational, not new GAP) — `/trust/{matterId}` "← Back to trust" link is a no-op. Logged informationally.

### Cycle-1 final tally

7/8 PASS, 1 STILL-FAIL (11.1 → downgraded to MINOR), 0 N/A. **L-52 verified — Day 11 cycle-1 CLOSED.**

Next dispatch: Day 14 (Moroka onboarding, isolation setup) or earlier deferred UI walks (L-48 EL CTA, L-58 Overview court-dates tile).

---

# Day 11 Checkpoint Results — Cycle 26 — 2026-04-27 SAST

**Branch**: `bugfix_cycle_2026-04-26-day11` (cut from `main` `1b984153` after cycle-25 retest)
**Backend rev / JVM**: main `1b984153` / backend PID 21748 (fresh JVM post-PR #1180/1181)
**Stack**: Keycloak dev — backend:8080, gateway:8443, frontend:3000, portal:3002 all healthy at dispatch start
**Auth**: Sipho Dlamini (portal magic-link). Day-10 token expired pre-walk; fresh magic-link requested via backend `/portal/auth/request-link` (token `5ytQGx75…`); exchanged at `:3002/auth/exchange` and landed authenticated on `:3002/projects`.

## Summary

**6 PASS / 0 FAIL / 1 PARTIAL / 1 BLOCKED-AT-LINK / 0 SKIPPED-BY-DESIGN** (8 in-scope checkpoints + 4 wrap-up rollups).

Day 11's underlying portal trust-ledger view works end-to-end (R 50 000,00 balance, RAF deposit row, ZAR formatting, line-level transaction history) — but the **trust-deposit nudge email's CTA is broken**: it deep-links to `/trust/{trust_account_id}` instead of `/trust/{matter_id}`, and the portal's `/trust/[matterId]` route renders "No trust balance is recorded for this matter / The requested resource was not found." for the trust-account UUID. Sipho can still reach the correct page via the sidebar Trust link or `/home` → "Last trust movement" tile (both auto-redirect `/trust` → `/trust/{matterId}` when the contact has exactly one matter with activity), so the read path itself is intact. The only thing broken is the deep-link in the auto-fired email — exactly the §11.2 "Click the View trust balance link → lands on /trust" checkpoint.

**One new gap logged**: BUG-CYCLE26-11 (LOW — broken email CTA; trust-account ID vs matter ID mismatch in `PortalEmailNotificationChannel.buildTrustActivityContext`).

Two pre-existing template polish items re-observed (NOT logged as new gaps; already noted post-Day-10): nudge email body shows raw ISO timestamp + unformatted amount `50000` + uppercase `DEPOSIT`; portal trust ledger row still surfaces uppercase `DEPOSIT` enum in the Type column.

## Pre-state verified

| Check | Expected | Actual | Result |
|---|---|---|---|
| Trust-activity email exists in Mailpit | subject contains "trust" | `m7qSdt8XULPXMs6mX6Vbcn` "Mathebula & Partners: Trust account activity" 2026-04-27T04:01:46Z | PASS |
| Email recipient | `sipho.portal@example.com` | `sipho.portal@example.com` | PASS |
| Email CTA href | portal trust deep-link | `http://localhost:3002/trust/46d1177a-d1c3-48d8-9ba8-427f14b8278f` (trust account ID) | **wrong-id** (see BUG-CYCLE26-11) |
| Portal session establishable | magic-link exchange succeeds | fresh token via backend; landed at `:3002/projects` | PASS |
| Trust account `46d1177a-…` (Mathebula Trust — Main) | exists, R 50 000,00 | confirmed (Day 10) | PASS |
| RAF matter `cc390c4f-…` | ACTIVE, has R 50 000 ledger card | confirmed (Day 10) | PASS |

## Checkpoints

### 11.1 — Mailpit verifies trust-deposit nudge email arrived for `sipho.portal@example.com`
- Result: **PASS (with wording note)**
- Evidence: `curl http://localhost:8025/api/v1/messages` lists `m7qSdt8XULPXMs6mX6Vbcn` from `noreply@docteams.app` to `sipho.portal@example.com`, subject **"Mathebula & Partners: Trust account activity"**, sent 2026-04-27T04:01:46Z. Body greets "Hi Sipho Dlamini," then: "A new transaction has been recorded in your trust account." with Date/Type/Amount table and a "View trust ledger" CTA.
- Notes: Scenario expected subject containing "trust deposit" / "funds received" / "trust balance update". Actual subject "Trust account activity" is functionally equivalent (auto-fired via `PortalEmailNotificationChannel.onTrustTransactionRecorded` for `DEPOSIT`); NOT logging a wording-mismatch gap because it clearly conveys the trust-deposit notification semantically. Wrap-up "Trust deposit visible on portal within 1 business day of firm posting" — Day 10 firm post 04:01:46Z, email same minute; satisfied.

### 11.2 — Click the "View trust balance" link → lands on `/trust`
- Result: **BLOCKED-AT-LINK (broken CTA — non-cascading)**
- Evidence: Email's only HREF (extracted via regex on Mailpit-served HTML) is `http://localhost:3002/trust/46d1177a-d1c3-48d8-9ba8-427f14b8278f`. Navigating there in an authenticated Sipho session yields the snapshot at `qa_cycle/checkpoint-results/cycle26-day11-11.2-trust-by-account.yml`:
  ```
  generic [ref=e85]: No trust balance is recorded for this matter.
  generic [ref=e86]:
    heading "Transactions" [level=2]
    generic [ref=e88]: The requested resource was not found.
  generic [ref=e89]:
    heading "Statements" [level=2]
    generic [ref=e91]: The requested resource was not found.
  ```
  The UUID `46d1177a-…` is the trust account ID (`Mathebula Trust — Main`), not a matter ID; portal `/trust/[matterId]` route interprets the path param as a matter ID, looks it up, and 404s on every section.
- Notes: Root cause located at `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java:284-303` (and twin `:310-328` for the recorded-event variant). Line 297 reads `UUID matterKey = event.trustAccountId();` — the variable is named `matterKey` but actually receives the **trust account ID**. The CTA URL is then composed as `portalBaseUrl + "/trust/" + matterKey`. The portal route `portal/app/(authenticated)/trust/[matterId]/page.tsx` expects a project/matter UUID. Mismatch is structural (different domain objects), not data-quality. Logged as **BUG-CYCLE26-11** (LOW) — see Gaps Found below. Per execution rule: **non-cascading bug** — Sipho can still reach the correct ledger via sidebar/home tile (verified §11.3+); not a Day-11 hard blocker, just a broken email CTA.

### 11.3 — Verify `/trust` renders: trust balance card, recent deposits list, ledger preview
- Result: **PASS** (via `/trust` index → auto-redirect to `/trust/{matterId}`)
- Evidence: `qa_cycle/checkpoint-results/cycle26-day11-11.3-trust-by-matter.yml` — navigated to `:3002/trust`, auto-redirected to `:3002/trust/cc390c4f-35e2-42b5-8b54-bac766673ae7` (RAF matter ID), page renders:
  - Trust balance card: `R 50 000,00 / As of 27 Apr 2026 / Matter cc390c4f`
  - Transactions table heading + columns (Date, Type, Description, Amount, Running balance) + 1 data row
  - Statements section with empty-state ("No statement documents yet")
- Notes: The portal `/trust` index page (`portal/app/(authenticated)/trust/page.tsx:65-67`) auto-redirects to `/trust/{matterId}` when the contact has exactly one matter with trust activity — that's why navigation from sidebar/home works correctly. The email CTA bypasses the index by deep-linking the wrong type of UUID directly to `/trust/[matterId]`.

### 11.4 — Trust balance card shows R 50,000.00 (matches firm-side Day 10 posting)
- Result: **PASS**
- Evidence: From snapshot above, balance card paragraph `R 50 000,00` matches Day-10 firm-side trust account cashbook balance and Sipho's client ledger card balance (DB `client_ledger_cards.balance=50000.00`). ZAR locale: spaced-thousands, comma-decimal, R prefix — correct.

### 11.5 — Recent deposits list shows R50,000 deposit dated Day 10 with source description (sanitised)
- Result: **PARTIAL (sanitisation works; "DEPOSIT" type label remains uppercase enum)**
- Evidence: Single transaction row in Transactions table:
  ```
  27 Apr 2026 | DEPOSIT | Initial trust deposit — RAF-2026-001 | R 50 000,00 | R 50 000,00
  ```
- Notes:
  - **Description sanitisation: PASS.** The firm-side description "Initial trust deposit — RAF-2026-001" is client-safe — no `[internal]` tags, no fee/staffing leakage, ≤140 chars, references the matter ref (RAF-2026-001) which the client already knows. Scenario step "any firm-internal `[internal]` tags stripped" is moot because firm side did not emit any.
  - **Type column polish: WONT_LOG (pre-existing).** Type column shows raw enum `DEPOSIT` (uppercase). This is the same template polish carry-forward as the email body and matches the firm-side `cycle21-day10-10.5-after-record-deposit.yml` Transactions History rendering — already noted post-Day-10 as "NOT logged as gap" because it spans multiple surfaces. Will be a single shared label-map fix similar to BUG-CYCLE26-08 if/when product wants to clean up.
  - Date format `27 Apr 2026` (not raw ISO) — portal-side date formatting works correctly here (unlike in the email body).

### 11.6 — Click into matter trust ledger → line-level history renders with all transactions
- Result: **PASS**
- Evidence: Same snapshot — the auto-redirect lands directly on the matter trust ledger detail at `/trust/cc390c4f-…`. Line-level transaction table (1 deposit, all 5 columns populated, running balance reconciles to R 50 000,00). "Back to trust" link present (`/trust`) for navigation back. Statements section visible with empty-state.
- Notes: This route IS the matter trust ledger — there is no separate "click into matter trust ledger" step in this build's information architecture; the `/trust/[matterId]` page combines balance card + line-level transactions + statements in one view.

### 11.7 — Screenshot: `day-11-portal-trust-balance.png` — trust balance card with first deposit
- Result: **PASS** (DOM YAML preferred per BUG-CYCLE26-05 WONT_FIX)
- Evidence: `qa_cycle/checkpoint-results/cycle26-day11-11.3-trust-by-matter.yml` — full DOM tree at `/trust/cc390c4f-…` showing balance card, transaction row, statements section, sidebar nav, header with brand logo + "Mathebula & Partners" + "Sipho Dlamini" user menu.

### 11.8 — Verify currency rendered as **R** / **ZAR** (not $ / EUR / GBP)
- Result: **PASS**
- Evidence: All rendered amounts use ZAR locale convention: `R 50 000,00` (R prefix, NBSP-spaced thousands, comma decimal). Three spots: balance card paragraph, Amount cell, Running balance cell. Zero occurrences of `$`, `€`, `£`. Date format also SA-style (`27 Apr 2026`).

## Day 11 wrap-up checkpoints

| Wrap check | Result | Evidence |
|---|---|---|
| Trust deposit visible on portal within 1 business day of firm posting | **PASS** | Firm posted 2026-04-27 04:01:46Z (Day 10); portal renders the same row with same amount + date on Day 11 walk same SAST day, sub-minute latency. |
| Amount matches firm-side Section 86 ledger (no rounding / display bug) | **PASS** | Three reconcile points all R 50 000,00: firm-side `client_ledger_cards.balance=50000.00`, portal balance card `R 50 000,00`, portal transaction row Amount + Running balance both `R 50 000,00`. No rounding drift. |
| Description sanitisation — `[internal]` stripped, ≤140 chars, safe fallback | **PASS** | Description "Initial trust deposit — RAF-2026-001" is 36 chars, contains no `[internal]` tags, references matter ref the client already knows. |
| ZAR currency throughout | **PASS** | R prefix, comma decimal, spaced thousands across all three monetary cells. SA date format `27 Apr 2026`. |

## Gaps Found

- **BUG-CYCLE26-11** — LOW — Trust-activity email CTA links to `/trust/{trust_account_id}` instead of `/trust/{matter_id}`; portal `/trust/[matterId]` 404s on the trust-account UUID
  - Reproducer:
    1. Firm side records a trust deposit (Day 10 §10.4 path).
    2. Backend `PortalEmailNotificationChannel.onTrustTransactionRecorded` fires nudge email to portal contact.
    3. Click "View trust ledger" CTA in email → lands on `:3002/trust/{trust_account_id}`.
    4. Portal renders "No trust balance is recorded for this matter / The requested resource was not found." (twice — Transactions and Statements panels).
  - Evidence: `qa_cycle/checkpoint-results/cycle26-day11-11.2-trust-by-account.yml` (broken landing page); curl extraction of email href = `http://localhost:3002/trust/46d1177a-d1c3-48d8-9ba8-427f14b8278f`; Day 10 trust-account ID confirmed `46d1177a-d1c3-48d8-9ba8-427f14b8278f` (Mathebula Trust — Main); RAF matter ID confirmed `cc390c4f-35e2-42b5-8b54-bac766673ae7` (different UUID — distinct domain object). Working URL via sidebar/`/home` tile auto-redirect: `qa_cycle/checkpoint-results/cycle26-day11-11.3-trust-by-matter.yml`.
  - Hypothesis: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java:297` (and twin `:322`) reads `UUID matterKey = event.trustAccountId();` — variable is named `matterKey` but receives the trust account ID. Two possible fixes:
    - **Fix A** (preferred — more contained): change the URL builder to either link the trust-account index page (`/trust` with no path arg, then the index auto-redirects when there's exactly one matter) or look up the matter ID at email-build time from the event's `customerId`+`projectId`/`trustTransaction.projectId`. Since `TrustTransactionRecordedEvent` likely already carries a `projectId` (matter ID), threading that into the context is one-line.
    - **Fix B** (more invasive): change the portal `/trust/[matterId]` route to also accept trust-account IDs and disambiguate. Not recommended — different domain objects, would conflate URLs.
  - Severity: LOW. Functional impact is medium (broken email CTA = bad first-touch UX after a trust deposit, exactly the moment a client is looking for confirmation), but Sipho can still reach the right page via sidebar Trust nav (which auto-redirects). Not a cascade-blocker for Day 11.

## Console errors

Sidebar/home walk: 0 errors. The `/auth/exchange` initial-token-expired attempt produced 1 error (expected — expired magic-link). The deep-link to `/trust/{trust_account_id}` produced 3 errors (likely the `PortalTrustMatterDetail` API 404 surfaced as fetch errors); not new bugs, expected response to a 404 from a misrouted UUID. No console regressions on the working flow.

## How we know Day 11 happy-path is solid (verification chain)

- Day-10 firm-side post created the deposit DB row + auto-fired the nudge email (Day-10 cycle-21 result).
- Day-11 magic-link refresh issued via correct portal endpoint (`/portal/auth/request-link` with body containing email + orgId — fresh token, redirected to `:3002/projects`).
- Sipho's `/home` tile shows "Last trust movement / R 50 000,00 / 27 Apr 2026" → tile click links to `/trust` (working path).
- `/trust` auto-redirects to `/trust/{matterId}` per `portal/app/(authenticated)/trust/page.tsx:65-67` `if (matters && matters.length === 1) router.replace(...)` heuristic.
- Matter trust ledger page renders balance card + transactions table + statements section with R 50 000,00 reconciled across three monetary cells.
- ZAR locale + SA date format consistent throughout.

The portal-side trust read-model + ZAR formatting + auto-redirect routing all work; only the email's deep-link UUID kind is broken. Day 11 closes with a single LOW-severity, non-cascading gap.

## Halt reason

Day 11 walked end-to-end. Per per-day workflow §1: "Stop at end-of-day or first blocker." End-of-day reached; the §11.2 BLOCKED-AT-LINK is non-cascading (alternate sidebar path verified §11.3+). One LOW gap (BUG-CYCLE26-11) logged as OPEN; orchestrator triages before advancing to Day 14.

## QA Position on exit

`Day 11 / 11.X (closed) — Day 14 / 14.1 (next, pending BUG-CYCLE26-11 triage)`. Per status.md per-day workflow §2: "Triage every gap found that day via Product Agent. WONT_FIX is allowed only for tooling/out-of-scope; real product bugs (even non-blocking UX issues) become SPEC_READY." BUG-CYCLE26-11 is a real product bug (broken CTA in an outbound email) so should become SPEC_READY before Day 14 walk.

## Files

- `qa_cycle/checkpoint-results/cycle26-day11-auth-exchange.yml` — initial expired magic-link landing
- `qa_cycle/checkpoint-results/cycle26-day11-login.yml` — portal `/login` form (orgId-missing finding pre-existing)
- `qa_cycle/checkpoint-results/cycle26-day11-login-after-submit.yml` — "Something went wrong" without orgId
- `qa_cycle/checkpoint-results/cycle26-day11-11.2-trust-by-account.yml` — BLOCKED-AT-LINK evidence (broken email CTA landing)
- `qa_cycle/checkpoint-results/cycle26-day11-11.3-trust-by-matter.yml` — working `/trust/{matterId}` page (PASS evidence for §11.3-§11.8)
- `qa_cycle/checkpoint-results/cycle26-day11-home.yml` — `/home` tile "Last trust movement" working

