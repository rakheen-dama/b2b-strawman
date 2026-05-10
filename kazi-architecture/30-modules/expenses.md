# Expenses

**Bounded context:** see [`10-bounded-contexts.md` § expenses](../10-bounded-contexts.md).
**Glossary:** [`Expense`](../glossary.md), [`Disbursement`](../glossary.md), [`ExpenseCategory`](../glossary.md), [`Billable`](../glossary.md).

## 1. Purpose

Tracks reimbursable project costs (a member spent money on behalf of a project) so they can be:
- categorised, written off, or charged back through to the customer on an invoice;
- pushed to invoice lines with a per-expense or org-default markup applied at invoice time;
- gated by `FINANCIAL_VISIBILITY` for write-off / restore operations.

In the **legal vertical** the on-screen label is "Disbursement" rather than "Expense", but the underlying entity for the generic case is still `Expense`. Legal verticals additionally have a separate `LegalDisbursement` aggregate (see §7) for the firm-vs-trust payment-source dimension that generic expenses don't carry. (`glossary.md:123` ; `glossary.md:104`).

## 2. Entities owned

| Entity | Table | Source | Notes |
|--------|-------|--------|-------|
| `Expense` (aggregate root) | `expenses` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/Expense.java:19` | Project-anchored. Mutates through behaviour methods (`update`, `writeOff`, `restore`, `markBilled`, `unbill`) — no field setters except for `taskId`, `receiptDocumentId`, `markupPercent`, `notes`. |
| `ExpenseCategory` (enum) | inline | `expense/ExpenseCategory.java:4` | Generic categories. Legal vertical replaces with `DisbursementCategory`. |
| `ExpenseBillingStatus` (enum, derived) | not stored | `expense/ExpenseBillingStatus.java:4` | `BILLED` / `NON_BILLABLE` / `UNBILLED`, computed in `Expense.getBillingStatus()` (`Expense.java:100-108`) from `(invoiceId, billable)`. One of three `BillingStatus` enums in the codebase — disambiguate. (`glossary.md:124`, `glossary.md:332`) |

Key fields on `Expense` (`Expense.java:25-71`): `projectId` (NN), `taskId` (nullable), `memberId` (NN, who incurred), `date`, `description`, `amount` + `currency`, `category`, `receiptDocumentId` (link to uploaded receipt), `billable`, `invoiceId` (set on bill, null until then), `markupPercent` (per-expense override), `notes`.

**Invariants enforced in the entity:**
- `update()` throws `IllegalStateException` once `invoiceId` is set — billed expenses are immutable (`Expense.java:163`).
- `writeOff()` requires currently billable + unbilled; `restore()` is its inverse (`Expense.java:183-208`).
- `markBilled(invoiceId)` rejects null invoice, non-billable expenses, and double-billing (`Expense.java:217-229`).
- `unbill()` only valid when currently billed — used when an invoice is voided/lines removed (`Expense.java:236-242`).

## 3. REST surface

`ExpenseController` (`backend/.../expense/ExpenseController.java:34`) — ~7 endpoints (A1 reports ~6 ; the `/write-off` + `/restore` PATCH endpoints make this seven — A1's count appears to predate them). All capability checks beyond `MEMBER` baseline are explicit on the method.

| Method | Path | Capability | Source |
|--------|------|------------|--------|
| `POST` | `/api/projects/{projectId}/expenses` | (default) | `ExpenseController.java:44` |
| `GET` | `/api/projects/{projectId}/expenses` | (default) | `ExpenseController.java:69` |
| `GET` | `/api/projects/{projectId}/expenses/{id}` | (default) | `ExpenseController.java:84` |
| `PUT` | `/api/projects/{projectId}/expenses/{id}` | (default) | `ExpenseController.java:92` |
| `DELETE` | `/api/projects/{projectId}/expenses/{id}` | (default) | `ExpenseController.java:117` |
| `PATCH` | `/api/projects/{projectId}/expenses/{id}/write-off` | `FINANCIAL_VISIBILITY` | `ExpenseController.java:124-125` |
| `PATCH` | `/api/projects/{projectId}/expenses/{id}/restore` | `FINANCIAL_VISIBILITY` | `ExpenseController.java:133-134` |
| `GET` | `/api/expenses/mine` | (default) | `ExpenseController.java:142` |

The `/api/expenses/*` namespace listed in `10-bounded-contexts.md:152` resolves in code to the single `/api/expenses/mine` "my work" endpoint. The project-scoped paths carry the rest of the surface.

Invoice linkage (`markBilled` / `unbill`) is **not** exposed on this controller — `invoiceId` is only mutated by the `invoicing` context when generating an invoice from unbilled items.

## 4. Frontend pages / components

Per `_discovery/A2-frontend-map.md:114` and `:358`, plus filesystem confirmation:

| Path | Role |
|------|------|
| `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` | Project detail shell with `expenses` tab. |
| `frontend/app/(app)/org/[slug]/projects/[id]/expenses/page.tsx` | Project-scoped expense list page (generic vertical label). |
| `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/disbursements.tsx` | Legal-vertical "Disbursements" tab variant. |
| `frontend/components/expenses/expense-list.tsx` | List with billing-status filter. |
| `frontend/components/expenses/log-expense-dialog.tsx` | Create + edit dialog. |
| `frontend/components/expenses/delete-expense-dialog.tsx` | Delete confirmation. |
| `frontend/components/expenses/expense-category-badge.tsx` | Category pill (consumes `ExpenseCategory` from `lib/types/expense.ts`). |
| `frontend/components/my-work/my-expenses.tsx` | "My work" view consuming `GET /api/expenses/mine`. |
| `lib/types/expense.ts` | `ExpenseResponse`, `ExpenseCategory` types (`A2:358`). |

Default expense markup (org-level) is configurable on the time-reminder settings page (`A2-frontend-map.md:211`).

## 5. Domain events

Published by `ExpenseService` via `ApplicationEventPublisher` (`expense/ExpenseService.java:159, 289, 490-515`). Both events are catalogued for the `expense` bounded context in `_discovery/A1-backend-map.md:463`.

| Event | Trigger | Source |
|-------|---------|--------|
| `ExpenseCreatedEvent` | After `create()` saves a new expense (`ExpenseService.java:159`). | `event/ExpenseCreatedEvent` |
| `ExpenseDeletedEvent` | After hard-delete of an unbilled expense (`ExpenseService.java:289`). | `event/ExpenseDeletedEvent` |

There is **no** `ExpenseUpdatedEvent` and **no** `ExpenseBilledEvent` at present — `update`, `writeOff`, `restore`, and `markBilled` mutate state in-place without a domain event. Audit captures the change instead (see §6).

## 6. Cross-cutting touchpoints

- **Capability gates.** `FINANCIAL_VISIBILITY` is required for `write-off` and `restore` — see §3. Project-row visibility is handled upstream by the `projects` context's project-membership check; expense reads are scoped by `projectId` resolved against the caller's project membership.
- **Audit.** Every change is audited (per `10-bounded-contexts.md:155`). Hooked into the same audit pipeline as other tenant entities — see [`30-modules/audit.md`](audit.md).
- **Receipts.** `receiptDocumentId` (`Expense.java:53`) links to a `Document` uploaded through the documents-templates context. The expense itself does not own file storage.
- **Invoice linking.** `invoiceId` is set only by `invoicing` when generating an invoice line of type `EXPENSE` (`backend/.../invoice/InvoiceLineType.java:6`). On invoice line removal or invoice voiding, `unbill()` is called to release the expense back to UNBILLED. `_discovery/A1-backend-map.md:206` shows `InvoiceLine.expenseId` and `disbursementId` as sibling FK columns.
- **Markup application.** Markup is **applied at invoice generation time**, not stored on the expense as a separate billable amount. `Expense.computeBillableAmount(orgDefaultMarkupPercent)` (`Expense.java:133-144`) is the canonical computation: per-expense `markupPercent` wins, falling back to the passed-in org default, falling back to zero. This implements ADR-115 (expense-markup-model).
- **Billing status derivation.** `ExpenseBillingStatus` is **not stored** — derived purely from `(billable, invoiceId)`. This implements ADR-114 (expense-billing-status-derivation) and avoids a denormalised status that could drift from `invoiceId`.
- **Reporting.** Read by `reporting` directly from the tenant `expenses` table (`10-bounded-contexts.md:263`).
- **`MyWork` view.** `GET /api/expenses/mine` joins by `memberId = caller`, used by the my-work page.

## 7. Vertical specifics

**Terminology overlay (legal-za).** Frontend `TerminologyProvider` rewrites the literal "Expense" → "Disbursement" wherever it appears in legal-vertical UI (`A2-frontend-map.md:465`, `glossary.md:306`). The backend table is still `expenses`, the entity is still `Expense`. The legal-vertical project tab is `disbursements.tsx` rather than `expenses/page.tsx` — but it can either (a) drive the same `Expense` API with relabelled headings or (b) wrap the sibling `LegalDisbursement` aggregate. See open questions §10.

**Sibling `LegalDisbursement` aggregate (legal vertical only).** `backend/.../verticals/legal/disbursement/` holds a separate aggregate — `LegalDisbursement.java`, `DisbursementService`, `DisbursementController`, plus its own enum cluster:
- `DisbursementCategory` — `SHERIFF_FEES, COUNSEL_FEES, SEARCH_FEES, DEEDS_OFFICE_FEES, COURT_FEES, ADVOCATE_FEES, EXPERT_WITNESS, TRAVEL, OTHER` (`glossary.md:107`).
- `DisbursementBillingStatus` — distinct from `ExpenseBillingStatus` (`glossary.md:106`, `:332`).
- `DisbursementApprovalStatus` — adds an approval workflow not present on generic expenses (`glossary.md:105`).
- `DisbursementPaymentSource` — firm / trust / advanced (`glossary.md:108`). This is the field that justifies the sibling aggregate: a generic `Expense` has no notion of "paid from trust account vs firm account", and trust-account semantics are non-negotiable in the legal vertical (see [`30-modules/trust-accounting.md`](trust-accounting.md)).

ADR-247 (legal-disbursement-sibling-entity) is canonical for why this is a sibling aggregate rather than a subclass / discriminator on `Expense`. Brief: legal disbursements participate in trust-accounting flows that generic expenses must not, so giving them a separate aggregate avoids leaking trust-only invariants up into the core `Expense` entity (`90-adr-index.md:383`, `:370`).

**Invoice line types reflect the duality.** `InvoiceLineType` includes both `EXPENSE` and `DISBURSEMENT` as distinct values (`invoice/InvoiceLineType.java:6, :11`), and `InvoiceLine` carries both `expenseId` and `disbursementId` FK columns (`A1-backend-map.md:206`). The unbilled-summary endpoint exposes `UnbilledDisbursementEntry` separately from the time/expense entries (`lib/types/invoice.ts:157`, `A2:344`).

## 8. Active ADRs

From `90-adr-index.md` "Project, task, time, expense" cluster (`90-adr-index.md:122-140`) and the legal cluster (`:370-:383`):

| ADR | Subject |
|-----|---------|
| ADR-114 | expense-billing-status-derivation — derive `ExpenseBillingStatus` from `(billable, invoiceId)`; do not store it. |
| ADR-115 | expense-markup-model — per-expense override falls through to org default at invoice time. |
| ADR-247 | legal-disbursement-sibling-entity — `LegalDisbursement` is a sibling aggregate, not an `Expense` subclass. |
| ADR-111 | project-completion-semantics — context for `projectId` lifecycle states the expense is anchored to. |

## 9. Key flows

- **Matter-to-cash** — expense logged → categorised → flagged billable → picked up by billing run → invoice line generated with markup → expense `markBilled(invoiceId)` → on payment, customer settles. See [`50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md).
- **Payment-receipt-to-trust-allocation** — for `LegalDisbursement` only, payment from trust touches the trust ledger. See [`50-flows/payment-receipt-to-trust-allocation.md`](../50-flows/payment-receipt-to-trust-allocation.md).

## 10. Open questions / known fragility

- **InvoiceLineType `EXPENSE` vs `DISBURSEMENT` divergence.** Two separate enum values back two separate FKs (`InvoiceLine.expenseId`, `InvoiceLine.disbursementId`). Open: in legal-vertical tenants, do invoice lines drawn from `Expense` rows render as `EXPENSE` (raw type) or are they re-typed `DISBURSEMENT` at invoice-generation time for terminology consistency? The glossary suggests legal-vertical UI replaces "Expense" entirely (`glossary.md:104, :123`), but the backend allows both rows to coexist, so the answer probably depends on whether the legal vertical's project tab drives `Expense` or `LegalDisbursement` (or both). Resolve when [`30-modules/invoicing.md`](invoicing.md) is filled in.
- **Markup precedence semantics.** `Expense.getBillableAmount()` (no args) ignores org default and returns `amount` when `markupPercent` is null — used for the entity's own preview. `Expense.computeBillableAmount(orgDefaultMarkupPercent)` is the invoice-time computation. Two separate methods on the same entity are a foot-gun; verify all call sites use `computeBillableAmount` for any persisted billable figure. (`Expense.java:115-144`).
- **No `ExpenseBilledEvent`.** Invoice→expense linkage emits no domain event, so any downstream that wants "expense was billed today" has to subscribe to invoice events instead. Acceptable today; flag if `automation` or `notifications` ever wants per-expense billing triggers.
- **Two project tabs in legal-vertical.** `frontend/app/(app)/org/[slug]/projects/[id]/expenses/page.tsx` and `…/(tabs)/disbursements.tsx` both exist. Confirm which renders for `legal-za` and whether the disbursement tab drives `Expense` API, `LegalDisbursement` API, or a unioned read.
- **`FINANCIAL_VISIBILITY` on write-off only.** Create/update/delete are gated by project-membership only — a non-FIN_VIS member can edit the `amount` field on someone else's expense if they share project access. Intentional or oversight? Worth confirming against role matrix in [`30-modules/identity-access.md`](identity-access.md).
