# ADR-340: `LegalDisbursement` Is the Only Cost Concept Visible on a Matter — Expenses Tab Removed, Generic `Expense` Endpoints Retained, Profitability Cost Leg Left on `Expense` (Follow-Up)

**Status**: Accepted

**Context**:

A matter carries two cost entities for one legal concept (`.arch-context.md` §7). The generic `$B/expense/Expense` → `expenses` (Phase 13: `project_id, task_id, member_id, date, amount, category, receipt_document_id, billable, invoice_id, markup_percent`; [ADR-114](ADR-114-expense-billing-status-derivation.md) billing status, [ADR-115](ADR-115-expense-markup-model.md) markup) is served by `ExpenseController` (`/api/projects/{projectId}/expenses[/{id}[/write-off|/restore]]`, `/api/expenses/mine`), feeds `InvoiceCreationService`/`UnbilledTimeService` (`unbilledExpenses`), billing runs (`…/unbilled-expenses`) and **is the only cost source in profitability** (`report/ReportRepository` native SQL `FROM expenses e` at lines 187 and 221, `ExpenseProfitabilityProjection`, `reporting/ProjectProfitabilityReportQuery`). The legal `verticals/legal/disbursement/LegalDisbursement` → `legal_disbursements` (Phase 67, [ADR-247](ADR-247-legal-disbursement-sibling-entity.md) — a sibling, not a subclass) adds `customer_id`, `vat_treatment`/`vat_amount`, `payment_source` (trust/office) with `trust_transaction_id`, supplier fields, a submit → approve/reject approval model, billing status, write-off and events; it is served by `DisbursementController` (`/api/legal/disbursements` — `POST`, `GET`, `PATCH`, `/unbilled`, `/{id}/{submit,approve,reject,write-off,receipt}`; capabilities `MANAGE_/APPROVE_/WRITE_OFF_DISBURSEMENTS`; module `disbursements`), feeds `InvoiceCreationService.appendDisbursementLinesToInvoice`, billing runs (`…/unbilled-disbursements`), the statement of account, the matter-closure gates (`AllDisbursementsApprovedGate`, `AllDisbursementsSettledGate`, [ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md)) and `TrustBoundaryGuard` (blocks trust-funded lines from Xero). `InvoiceLineType` is `TIME, EXPENSE, MANUAL, FIXED_FEE, TARIFF, DISBURSEMENT`; `invoice_lines` has both `expense_id` and `disbursement_id`. Neither is referenced by MCP.

On the matter detail page (Phase 73, [ADR-287](ADR-287-grouped-tabs-dense-navigation.md)) the Finance group shows **two tabs, `expenses` ("Expenses") and `disbursements` ("Disbursements")** (`lib/constants/project-tab-groups.ts`), and the terminology map labelled `Expense → Disbursement`, so a user saw two "Disbursements"-like tabs. `projects/[id]/page.tsx:502` still hard-codes `disbursementsEnabled={false} // TODO: derive from org module flags`. A standalone `projects/[id]/expenses/page.tsx` and `projects/[id]/expense-actions.ts` (create/update/delete/write-off/restore) exist; `my-work` reads `/api/expenses/mine`. The requirements ask this ADR to decide "which entity is the face, what happens to the other". Phase 87 adds no migration.

**Options Considered**:

1. **Hide `Expense` on matters; `LegalDisbursement` is the face; `Expense` endpoints retained (CHOSEN)** — remove the `expenses` tab, `expensesPanel`, the standalone `projects/[id]/expenses` page and the log/edit expense dialogs from the matter UI; keep `ExpenseController` (renamed to `/api/matters/{matterId}/expenses` per [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md)) and `/api/expenses/mine`; keep the invoice/billing-run expense legs; no UI entry point creates an `Expense` from a matter. Profitability keeps reading `expenses`.
   - Pros:
     - One cost concept on the matter, with the legal fields a firm needs (VAT treatment, trust vs office funding, approval, statement and closure gates).
     - No migration, no deletion of a package that `invoice/`, `billingrun/` and `report/` import.
     - Existing `expenses` rows (dev/demo data only) stay readable.
     - Fee-note generation already picks up legal disbursements (`components/invoices/__tests__/unbilled-summary-disbursements.test.tsx`), so billing is unaffected.
     - The `disbursementsEnabled` TODO is resolved by deriving from `enabledModules` (`disbursements` is VERTICAL and default-on for `legal-za`).
   - Cons:
     - **Profitability's cost leg still reads `Expense` and will be empty for every matter created through the new shell** — the Profitability page shows revenue with zero cost until the leg is switched.
     - This is an honest, visible gap recorded as a follow-up, not hidden.
     - `Expense` becomes a retained-but-unreachable write path (API only).
     - `InvoiceLineType.EXPENSE` and `invoice_lines.expense_id` persist with no new producer.
     - `/api/expenses/mine` and `my-work`'s personal-expenses read either show legacy rows only or are removed from `my-work` — confirm at build time; neither creates data.
2. **Delete `Expense` entirely** — drop the package, table, `InvoiceLineType.EXPENSE`, `invoice_lines.expense_id`, the invoice/billing-run legs, and rewrite profitability to read `legal_disbursements`.
   - Pros:
     - One entity, no retained dead path, no follow-up.
   - Cons:
     - A schema change (`V4`) in a migration-free phase.
     - Profitability SQL (`ReportRepository`, `ProjectProfitabilityReportQuery`, `ExpenseProfitabilityProjection`) is a behaviour rewrite with its own test suite.
     - `InvoiceCreationService`, `UnbilledTimeService`, `BillingRunSelectionService`, `BillingRunDtos` all change — far outside "DTO/path renames" and a Phase 86-style subtraction the requirements rule out ("backend behaviour changes beyond DTO/path renames" are out of scope).
     - Markup (`markup_percent`, [ADR-115](ADR-115-expense-markup-model.md)) has no equivalent on `LegalDisbursement`.
     - A design decision on whether firms mark up disbursements is a product call, not a shell call.
3. **One tab with a type switch** — a single "Disbursements" tab listing both entities with a toggle or merged list.
   - Pros:
     - Legacy expenses stay visible on the matter; nothing is hidden.
   - Cons:
     - Two status models (`ExpenseBillingStatus` vs approval + `DisbursementBillingStatus`), two create dialogs and two action sets behind one tab is the Phase 73 density problem in a new coat.
     - New users would still be offered the generic create path, which is the thing being removed.
     - ADR-247 explicitly rejected a shared `Billable` abstraction.
     - A merged list re-litigates it in the UI.
4. **Keep both tabs**, relabel "Expenses" to something distinct ("Office costs").
   - Pros:
     - Zero backend or action change; no data hidden.
   - Cons:
     - Two ways to record a sheriff's fee.
     - The requirements name this as the defect ("Expenses and Disbursements are two tabs for one legal concept; one must become the face of the other").
     - Profitability would still count only the generic one — the confusing outcome today.

**Decision**: Option 1 — `LegalDisbursement` is the only cost concept visible on a matter (one "Disbursements" tab, module-derived), the generic `Expense` entity, repository, invoice/billing legs and endpoints are retained under `/api/matters/{matterId}/expenses` with no UI entry point, and switching profitability's cost leg from `expenses` to `legal_disbursements` is recorded as an explicit follow-up behaviour change, not done here.

**Rationale**:

1. **The legal entity already has everything the UI needs; the generic one has what profitability needs.** `LegalDisbursement` carries VAT, trust/office sourcing, approval, statements and closure gates — the fields an attorney and a bookkeeper actually use. `Expense` carries markup, task and member links that profitability's SQL joins on. Hiding one and retaining the other is the only split that needs neither a migration nor a report rewrite in this phase.
2. **Scope discipline.** Phase 87 is a shell phase; CLAUDE.md §7 forbids bundling a behaviour change ("while I was here, profitability…") into an IA PR, and the requirements exclude backend behaviour changes beyond renames and one diary read. The profitability leg switch is a one-PR follow-up with its own full verify and its own scenario step (Day 90 review reads profitability).
3. **No migration, no deletion.** Deleting `Expense` is a `V4` and a Phase 86-style subtraction across `invoice/`, `billingrun/` and `report/`; retaining it costs nothing at runtime and keeps `InvoiceLineType.EXPENSE` valid for any legacy fee note.
4. **Honesty over tidiness.** The consequence — zero cost on profitability for new matters — is stated here, in the architecture doc's open-follow-ups list and in the closure report, so nobody reads a clean Profitability page as a clean margin.

**Consequences**:

- Positive:
  - `TAB_GROUPS.finance` loses `expenses`.
  - `ProjectTabs` loses `expensesPanel` (and the orphaned `membersPanel`/`"members"` content in the same relabel PR).
  - `projects/[id]/expenses/page.tsx` is deleted and permanently redirected to the matter's `?tab=disbursements`.
  - `components/expenses/{log-expense-dialog,delete-expense-dialog,expense-list}.tsx` and `projects/[id]/expense-actions.ts` go if no other caller remains (confirm at build time).
  - `disbursementsEnabled` is derived from `useOrgProfile().enabledModules` (`disbursements`), closing the `page.tsx:502` TODO.
  - Backend: `ExpenseController` keeps all endpoints under the renamed path.
  - `ExpenseService`, `ExpenseRepository`, invoice and billing-run expense legs, `InvoiceLineType.EXPENSE`, `invoice_lines.expense_id` unchanged.
  - `./mvnw verify` keeps the expense test classes.
  - Fee notes, billing runs, statements and closure gates are unaffected — disbursements already flow through all four.
- Negative:
  - **Profitability (`/api/projects/{id}/profitability` → `/api/matters/{matterId}/profitability`, `/api/reports/profitability`, `reports/profitability` page) shows no cost for matters whose only costs are `LegalDisbursement` rows** — i.e. every new matter. The Profitability page gets a one-line note until the follow-up lands.
  - `GET /api/invoices/unbilled-summary` keeps `unbilledExpenseCount`/`unbilledExpenseAmount` columns that will read zero.
  - The home-page unbilled-WIP widget uses time + disbursements, not these.
  - `lib/types/expense.ts` survives only for `my-work`'s `getMyExpenses` if that read is kept.
  - Otherwise it is deleted — a decision the S8 slice records.
- Follow-ups:
  - **Profitability cost leg → `legal_disbursements`** (behaviour change; one PR; full verify; amend Day 90 scenario step): decide markup semantics for disbursements, rewrite `ReportRepository` SQL and `ExpenseProfitabilityProjection`, keep `expenses` as a legacy additive leg or drop it.
  - If no legacy `expenses` rows exist after the first take-on (Phase 84), a later subtraction phase may delete `Expense` with a migration.
- Related:
  - [ADR-247](ADR-247-legal-disbursement-sibling-entity.md) (why the two entities exist side by side), [ADR-114](ADR-114-expense-billing-status-derivation.md) / [ADR-115](ADR-115-expense-markup-model.md) (the generic model retained), [ADR-248](ADR-248-matter-closure-distinct-state-with-gates.md) (closure gates read disbursements only), [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) (the tab groups relabelled here), [ADR-337](ADR-337-wire-rename-two-nouns-entities-stay.md) (`expense` stays on the wire), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (`InvoiceLineType` set after 86).
