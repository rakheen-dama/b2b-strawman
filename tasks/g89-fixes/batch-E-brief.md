# Batch E brief: layout, copy and small backend LOWs (G89-F5, F7, F8, F9, F15, F17, F20, F40, F41, carried P88 LOWs, 634A, review follow-ups)

**Branch:** `fix/g89-e-layout-copy`, cut from fresh `origin/main` after Batch G has merged.
**PR title:** `fix: layout overflow, raw labels, stale overview hours, reconciliation list, small copy fixes (G89 Batch E)`
**Authorisation:** founder D1/D2. This is one same-family cluster PR.
**Gates:**
- backend full verify (the orchestrator runs it; this batch has backend LOWs)
- frontend gate
- two bound reviews
- CodeRabbit
- browser checks on the Keycloak stack at **1440 and 1024**, as a before/after table, for every layout item

Line numbers come from the scout read at `30317a851`. Re-locate them; Batches A–G have changed some of these files.

## MEDIUM

### G89-F17: Trust › Reconciliation list always shows "Failed to load reconciliations"
- `TrustReconciliationController.listReconciliations` (around `:100-104`) returns a `List`.
- `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/actions.ts:51-60` reads `result.page.*`.
- The same latent bug exists in `fetchBankStatements` (`actions.ts:130-134`).
- **Fix (frontend):** read the `List` in both actions and keep the page component's shape. Add a vitest wire pin that asserts both actions accept a JSON array, in the manner of `__tests__/customers/data-protection-wire-shapes.test.ts` / ADR-365.
- Reproduce first with a vitest red test against the real action and a mocked fetch returning an array.

## LOW: layout (screenshots at 1440 and 1024, before and after)

| Id | Where | Fix |
|---|---|---|
| F7 | `frontend/components/projects/kpi-dashboard.tsx:331` uses `lg:grid-cols-3`, so the 4th tile wraps alone | `lg:grid-cols-4` (check that 1024 still fits; otherwise `xl:grid-cols-4` with `lg:grid-cols-2`) |
| F8 | `settings/documents/_tabs/templates-content.tsx:131-165`: the Name cell inherits `whitespace-nowrap` from `components/ui/table.tsx:74`, giving a 1621 px table | Name cell `whitespace-normal` plus `min-w-[16rem]` / `max-w`; the description and docx filename wrap |
| F9 | `components/invoices/PaymentEventHistory.tsx:173-230`: 7 columns with `px-3`, 9 px scroll at 1024 | Tighten the padding, or hide Provider/Method below `xl` |
| F15 | `components/documents/documents-panel.tsx:438-488`: File cell `truncate` has no effect, giving a 938 px table in 654 px | File cell `w-full max-w-0` so the truncate works; Size/Uploaded move to `xl:table-cell` |

## LOW: copy and raw labels

| Id | Where | Fix |
|---|---|---|
| F20a | `compliance/requests/[id]/page.tsx:7,49-52`: a server component imports `STATUS_CONFIG` from the `"use client"` `DataRequestTable.tsx`, gets a client reference, and falls back to the raw status | Move `STATUS_CONFIG` to a non-client module (e.g. `lib/compliance/data-request-status.ts`) and import it in both places |
| F20b | `DataRequestTimeline.tsx`: the "Processing Started" stage has no date | If the entity, or the audit trail through an existing response field, has the timestamp, show it. Otherwise render the stage without a date line (no "—"). **No new backend field** unless it already exists |
| G-D10a | `settings/firm/_tabs/audit-log-client.tsx:1022` prints `{event.eventType}` under the label | Delete the line. The key stays searchable, per the G-D10b ruling |
| G88-F27 (a) | `AuditEventTypeRegistry.java:68` "Access Denied" (BE) | "Access denied" |
| G88-F27 (b) | Phase 88 `gaps.md:114`: the disbursement detail "Invoiced · Yes" label (`legal/disbursements/[id]/detail-client.tsx:291`) | Read the Phase 88 row and apply its stated fix |
| G88-F24 | `ActivityMessageFormatter.java:439`: "status to DONE" (BE) | Map the status to a label ("Done", "In progress", "Open", "Cancelled"), reusing the task status labels if a backend map exists |
| G88-F2 | `components/team/member-list.tsx:75,124,213` hard-code "Joined —" | Map it from the member's created/joined date if the API returns one; otherwise drop the column |
| F40 | Matter debit guard refusal prints "R10055.21" (`TrustTransactionService` `requireMatterCellCovers` message, BE) | Format with the house money formatter used in other backend ProblemDetails (find the precedent, e.g. a ZAR formatter in `config/` or `VariableFormatter`): "R 10 055,21" |
| F41 | Interest run "Posted Date" shows the UTC date (06 Nov for a 01:18 SAST post) | Find the render (`InterestRunsTable` or similar) and format the instant in `Africa/Johannesburg` using the frontend SA date helper (the ADR-358 / `KaziTime` pattern) |

## LOW: staleness

| Id | Where | Fix |
|---|---|---|
| F5 | Matter Overview "HOURS LOGGED" stays stale. `DashboardService.java:120-121` Caffeine `projectCache` (1-min TTL, never evicted) caches `getProjectMemberHours` (`:343-356`) | Evict `tenant:project:{id}:*` keys on time-entry create/update/delete. Prefer an event listener on the existing time-entry events, if they exist, over calls from the service. Integration test, red first: log time, then the member-hours read reflects it immediately |
| G88-M9 | `invoices/[id]/page.tsx:194,218`: `GeneratedDocumentsList` and `InvoiceAuditSection` fetch once on mount, so they go stale after send/pay | Pass `key={invoice.status + invoice.updatedAt}` (or a `refreshKey`) from the server page so they remount |

## LOW: 634A carried items

Re-locate them from `tasks/phase89-go-live-readiness.md` (634A follow-ups): inactive member fields, `dependsOn`, unnamed card buttons, and the Slug console error. Fix each if it is under about 20 lines. If one turns out to be larger, list it in the PR as deferred, with the reason.

## Review follow-ups from Batch A (PR #1746)

- `TrustBalanceCard` matter mode: a failed client-ledger fetch blanks the card. Show the matter figure and drop only the secondary client line.
- `ledger-totals-breakdown.tsx`: add an `sr-only` "less" before subtracted rows.
- `InterestPortalProjectionIntegrationTest.postInterestRun_notifiesAdminsNotClients`: add a plain member and assert they are not notified.
- ADR-366: one sentence noting that unmattered interest credits are not projected to the portal (D7 / G89-F28).

## Tests

- **Red first** wherever the bug is observable in a test: F17, F20a, F5, G88-F24, F40, F41 and the TrustBalanceCard error case.
- **Pure CSS layout** (F7, F8, F9, F15) needs no red test. The browser before/after at 1440 and 1024 is the evidence. Add a simple class assertion only where a test already renders the component.

## Process

As in `batch-B-brief.md` "Process":
- the targeted set includes `architecture/**` and `project/WireVocabularyContractTest`
- run the frontend gate
- open the PR; no full verify, no merge
- report back with the PR number, head sha, counts, test changes, judgment calls and any deferred 634A item

---

## Additions since the brief was written (2026-11-08)

The branch is cut from fresh `origin/main` **after Batch G (PR #1751) merges**. The next free tenant migration is **V15**, if one is needed.

### MEDIUM (counts against the exit bar)

**G89-F50: the Section 35 Data Pack interest section lists only the latest interest run in the financial year (D14).**
- `report/Section35DataPackQuery` uses its interest-allocation sub-report with "latest run within year" semantics. See the `InterestAllocationReportQuery` usage and the code comment.
- Fix: include every POSTED interest run whose period falls in the financial year, one row per client per run, with a per-section total.
- Red test: two posted runs in one FY must both appear (`Section35DataPackContentIntegrationTest`).
- Check `InterestAllocationReportQuery` callers outside Section 35, and do not change those.

### LOW

**G89-F44 (D14): portal "Firm actions" shows trust refund approvals but no deposit or interest events.**
- Add trust deposit (recorded) and interest posted (per client) to the portal activity allow-list (`portal/PortalActivityEventTypes.java`), with client-facing labels (`PortalActivityEventResponse`).
- Check the existing audit event types and their `entity_id` / customer linkage, so each client sees only their own events (cf. `findActivityFirmForCustomer`).
- Red test: a deposit for client A appears in A's Firm actions and not in B's.

**G89-F47: Section 35 on-screen table.**
- The ITEM cell doesn't wrap the long `INT-CREDIT-<uuid>…` references, so AMOUNT ends up off-screen at 1440 px.
- Make the ITEM cell `whitespace-normal break-words`, with a max width, in `report-results.tsx`.

**G89-F48: Section 35 PDF prints raw money ("10000.00").**
- Format amounts as in the other trust PDFs, or better, with the ZAR house format if a Thymeleaf helper exists.
- This changes `TrustReportPackSeeder` `SECTION_35_TEMPLATE`, so it needs **PACK_VERSION 2→3 and a RECONCILE entry**, following the 1→2 precedent from C1.

**G89-F49: CSV negative amounts are apostrophe-prefixed by the formula-injection guard (`ReportRenderingService.escapeCsv`), so they read as text.**
- Exempt plain numeric values (regex `^-?\d+(\.\d+)?$`) from the guard.
- Keep the guard for `=`, `+`, `@` and `-` followed by non-digits.

**G89-F57: the SoA summary reads "Total fees: R0,00" while Professional Fees lists unbilled time.**
- Under D9 this is correct but reads as a contradiction.
- Relabel the summary lines "Fees billed in period" and "Disbursements billed in period".
- This is a legal-za pack edit, so **bump v13→v14 with reconcile entries** (ADR-356 cumulative chain).
- The founder signs off the wording before merge (put it in the PR body under "Wording for sign-off").

**G89-F62 (D21): `POST /api/ai/skills/drafting` without a `templateId` returns 500 (`Map.of` with a null).**
- Make it null-safe and return a 400 ProblemDetail ("Template required") if the skill needs one.
- Red test first.

**G89-F64: a member without VIEW_TRUST gets 500s on the client trust tab, and the page then wrongly says "No trust account configured".**
- Find the failing call. It is probably a trust endpoint that throws AccessDenied, which the frontend treats as "no account".
- Fix: either don't call trust endpoints when `!canViewTrust`, or handle 403 as "Trust details are not available".
- No 500s are allowed: the backend must return 403, not 500. Find why it is a 500.
- Red test on the backend status code, plus a vitest.

**G89-F65: after a role change the Team list doesn't refresh, and on reload it shows the raw slug `org:associate-no-trust`.**
- Revalidate after the role change.
- Render the role's display name, not the slug.

**Batch A review follow-ups:** unchanged from the list above.

### Not in E

- **G89-F63** (the role editor can't grant VIEW_LEGAL etc.) is awaiting a founder ruling.
- **G89-F61** is by design (D21).
