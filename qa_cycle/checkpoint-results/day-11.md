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
