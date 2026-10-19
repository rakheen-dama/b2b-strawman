# Phase 84 — Client & Trust Take-On (Data Migration from a Firm's Previous System)

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-07-09 → 2026-07-11). This phase exists because of a **go-to-market decision, not a feature-roadmap one**: the next milestone is 2–3 design-partner law firms live on Kazi (see `.claude/ideas/gtm-design-partner-plan-2026-07-11.md`), and the pilot pitch — "we move your data for you" — is impossible today. A firm switching from GhostPractice / AJS / Lexpro / spreadsheets arrives with clients, open matters, debtor balances, and **live trust ledger balances**. Kazi has no way to receive any of that except one-by-one manual entry. This phase builds a **generic CSV take-on wizard** (upload → map → validate/preview → commit → report) for four datasets: clients, matters, trust ledger opening balances, and debtor opening balances. Source-system-specific mappers are deliberately deferred (see Out of Scope) — the founder's target firms span GhostPractice, AJS/Lexpro, and Excel/bookkeeper setups, so the generic column-mapping wizard is the v1 that covers all of them; presets come later once real export files from the design partners are in hand (building parsers against imagined export formats violates reproduce-before-fix).
>
> **Scouting note (verified against current code, 2026-07-11 — do NOT re-derive):**
> - **There is NO opening-balance concept anywhere in the trust domain.** `ClientLedgerCard` has no brought-forward field; `TrustTransaction.type` is a String constrained by `chk_trust_txn_type` in `V85__create_trust_accounting_tables.sql` (`DEPOSIT, PAYMENT, TRANSFER_IN, TRANSFER_OUT, FEE_TRANSFER, REFUND, INTEREST_CREDIT, INTEREST_LPFF, REVERSAL`); code-side `CREDIT_TYPES`/`DEBIT_TYPES` sets in `TrustTransactionService`. Adding `OPENING_BALANCE` means a migration extending the CHECK constraint + adding it to `CREDIT_TYPES`. **This is the single largest net-new domain concept in the phase.**
> - **Credits are recorded pre-approved.** `TrustTransactionService.recordDeposit(trustAccountId, RecordDepositRequest(customerId, projectId, amount, reference, description, transactionDate))` persists status `"RECORDED"` (not `AWAITING_APPROVAL`) and upserts the `ClientLedgerCard` immediately (FOR-UPDATE lock + `upsertNewLedgerCard` `ON CONFLICT DO NOTHING`). Only debit-side ops gate on `APPROVE_TRUST_PAYMENT`. Opening-balance credits can follow the deposit path's shape without touching the approval workflow.
> - **Debtor balances only exist as unpaid invoices.** There is no AR ledger; Statement of Account (`StatementService` / `StatementOfAccountContextBuilder`) *computes* balances per matter/period and snapshots them into `GeneratedDocument.contextSnapshot` — nothing is seedable there. Manual invoices ARE supported: `InvoiceCreationService.createDraft` with empty timeEntry/expense/disbursement ids + `addLineItem` with `InvoiceLineType.MANUAL` (description + unitPrice). Statuses: `DRAFT, APPROVED, SENT, PAID, VOID`. **Caveat:** `validateInvoicePrerequisites` requires the customer's legacy `status == "ACTIVE"` plus `CustomerLifecycleGuard` + `PrerequisiteService` (INVOICE_GENERATION — e.g. tax number). The take-on invoice path must decide how to satisfy or legitimately bypass this (see Section 5).
> - **`CustomerService.createCustomer` cannot set lifecycle status** — every overload pins `LifecycleStatus.PROSPECT` via the constructor. The direct entity constructor `Customer(name, email, phone, idNumber, notes, createdBy, customerType, LifecycleStatus)` DOES accept a status — this is exactly what `XeroCustomerImportService` uses. Take-on customers are existing clients and must land `ACTIVE`.
> - **Import precedents to imitate, not rebuild:** (1) `integration/accounting/xero/XeroCustomerImportService` — two-phase validate-then-persist via `TransactionTemplate`, email-case-insensitive dedup then name+taxNumber dedup, park unmapped data in `customFields`, `CustomerImportSummary(created, skippedDuplicate, skippedNoEmail, total)` counts, 10k-row in-memory-dedup safety valve, audit event on completion. (2) `verticals/legal/trustaccounting/reconciliation/parser/` — `BankStatementParser` interface (`canParse(fileName, headerLine)` / `parse(InputStream)`), `CsvBankStatementParser` abstract base (`splitCsvLine`, `parseAmount`, `parseDate`, malformed-line counting: skip-and-count, only all-lines-malformed is fatal), parsers are plain classes not Spring beans. (3) `billingrun/BillingRun` + `BillingRunItem` — batch lifecycle (`PREVIEW → IN_PROGRESS → COMPLETED/CANCELLED` with guarded transition methods) + per-item status (`PENDING, GENERATING, GENERATED, FAILED, EXCLUDED`) with `failureReason` + result-FK. **There is no generic CSV→entity framework and no XLSX parser** — CSV only.
> - **Upload pattern:** direct multipart-to-backend à la bank statements (`uploadBankStatement` action: FormData, 10 MB client-side cap, `.csv` + MIME validation; backend `MultipartFile`, archives raw bytes to S3 `trust-statements/{tenant}/...`). NOT the presigned-document flow — take-on files are parsed server-side, backend owns S3 archival.
> - **Custom fields at creation work for Customer and Project** (`customFields` map + `appliedFieldGroups`, validated by `CustomFieldValidator` per `EntityType`) — the intended mechanism for unmapped source columns and the source-row reference.
> - **Capabilities:** add an enum constant to `orgrole/Capability.java`; custom-role grants need an `org_role_capabilities` INSERT migration (precedent `V125__grant_admin_approve_trust_payment.sql`). **Module registry:** `VerticalModuleRegistry` hardcoded map; HORIZONTAL modules toggle via Settings → Features. Take-on is capability-gated only, NOT module-gated (founder decision below).
> - **Migrations:** after the Phase 86 squash (ADR-331) the latest tenant migration is **V3** (`V3__normalise_work_types.sql`) → next free tenant **V4**; latest global is **V1** (`V1__baseline.sql`) → next free global **V2** (take-on tables are tenant-series). Re-verify latest at build time.
> - **Project creation:** `ProjectService.createProject(name, description, createdBy, customFields, appliedFieldGroups, customerId, dueDate, referenceNumber, priority, workType)` — customer link + due date + reference number all supported at creation, writes `CustomerProject` + lead `ProjectMember` + `project.created` audit.

## System Context

Kazi is a mature multi-tenant B2B practice-management platform (Next.js 16 frontend + portal, Spring Boot 4 / Java 25 backend, Keycloak OIDC, schema-per-tenant via Hibernate + Flyway). 83 phases have shipped: full revenue engine (time → rates → invoicing → retainers → billing runs → payments → collections), legal vertical depth (LPA s86 trust accounting, court calendar, conflict checks, matter closure), compliance (POPIA, FICA/KYC, audit), an AI layer (skills, gates, MCP server), and vertical profile packs (legal-za, accounting-za, consulting-za).

What it cannot do is **receive a firm's existing data**. The only imports that exist are the one-time Xero customer pull (API-based) and trust bank-statement CSVs (reconciliation lines, not balances). For the design-partner motion this is the adoption blocker: a firm cannot run parallel books, so day one on Kazi requires its clients, its open matters, what its debtors owe, and — statutorily critical — each client's trust balance brought forward, reconcilable against the trust bank account from the first month.

### Founder decisions that constrain this phase (2026-07-09 → 2026-07-11 ideation)

- **Generic CSV wizard, not source-system parsers.** Four downloadable CSV templates (clients, matters, trust opening balances, debtor balances) + a column-mapping step so a firm's export (or bookkeeper's spreadsheet, saved as CSV) can be mapped without matching our template exactly. GhostPractice/AJS/Lexpro mapping *presets* are a later, data-only follow-up once design partners supply real export files.
- **Opening balances + master data only — no history.** Standard accounting take-on practice: cutover date, balances brought forward, master records. No historical transactions, no historical invoices, no WIP/unbilled-time import (v1; see Out of Scope).
- **Trust take-on must be auditor-defensible.** A new `OPENING_BALANCE` credit transaction type (recorded, pre-approved — consistent with the existing credits-don't-gate design), a **control-total check** before commit (sum of client opening balances must equal the trust bank balance at cutover, entered by the admin), rejection of negative client trust balances (a debit trust balance is a statutory violation — flag it, never import it), and an exportable take-on report.
- **Debtor balances become opening-balance invoices** — one per client (optionally per matter), single MANUAL line "Balance brought forward as at {cutover}", issue/due dates from the source system, landed in an outstanding (SENT-equivalent) state **without sending any email**. This makes AR aging, statements, dashboards, and Phase 83 collections work on day one with zero new AR concepts.
- **Capability-gated, not module-gated.** New capability (e.g. `MANAGE_TAKE_ON`, admin-level — a firm's bookkeeper may legitimately run it, so not OWNER_ONLY) guarding a Settings → Take-On page. No `VerticalModuleRegistry` entry; take-on is a one-time activity, not a daily surface.
- **Re-runnable, not one-shot.** Unlike Xero's `customersImported` one-time guard, firms will take on in several files (clients first, then matters, then balances — possibly per-department). Idempotency comes from dedup (email for clients, reference for matters, one-OPENING_BALANCE-per-ledger for trust) not from a global "already ran" flag. Each *batch* commits exactly once.

## Objective

Let a firm (or the founder, white-gloving a pilot) migrate onto Kazi via **Settings → Take-On**: upload a CSV per dataset, map columns interactively, see a full dry-run validation preview (row-level errors, duplicate detection, control totals), commit, and receive a per-row result report. Four datasets, in dependency order: **(1) Clients** → ACTIVE customers with dedup and custom-field parking; **(2) Matters** → projects linked to imported clients by client email or source reference; **(3) Trust opening balances** → one `OPENING_BALANCE` credit per client ledger, gated by a bank-balance control total; **(4) Debtor opening balances** → outstanding opening-balance invoices. Every commit is a `TakeOnBatch` with per-row `TakeOnRow` results, fully audited, tenant-isolated, with the raw source file archived to S3.

## Constraints & Assumptions

- **Reuse the three precedents; build no framework.** Batch lifecycle mirrors `BillingRun`/`BillingRunItem` (guarded transition methods, per-row status + `failureReason` + result-entity FK). CSV parsing follows the `CsvBankStatementParser` disciplines (tolerant line splitting, `parseAmount`/`parseDate` styles, malformed-line skip-and-count) but as a small take-on-owned parser — do NOT contort the bank-statement class hierarchy to fit. Dedup + summary counts + park-in-customFields mirror `XeroCustomerImportService`, including its two-phase validate-then-`TransactionTemplate` persist and an in-memory-dedup row-count safety valve.
- **One new tenant migration (V4 after the Phase 86 squash — re-verify)**: take-on tables (`take_on_batch`, `take_on_row`) + extend `chk_trust_txn_type` with `OPENING_BALANCE`. Global series untouched.
- **Customers land ACTIVE.** Use the direct entity constructor (Xero precedent) or a new service method that accepts `LifecycleStatus` — `/architecture` decides which; do not weaken the existing `createCustomer` overloads' PROSPECT default for the normal UI path. Email remains the primary dedup key (case-insensitive), then name+taxNumber; a customer row that matches an existing customer is `SKIPPED_DUPLICATE` (and, like Xero, may enrich `customFields` with the source reference).
- **Trust rules are non-negotiable.** `OPENING_BALANCE` joins `CREDIT_TYPES`, persists as `RECORDED`, updates the `ClientLedgerCard` through the same locked upsert path as deposits, carries the cutover date as `transactionDate` and a fixed description ("Balance brought forward — take-on"). At most one OPENING_BALANCE per ledger card (enforced, not advisory). Negative amounts are validation errors. **Commit is blocked until the control total matches**: Σ(client opening balances) == admin-entered trust bank balance at cutover, to the cent. The three-way reconciliation invariant (bank vs cashbook vs client-ledger total, `calculateReconciliation`) must hold immediately after take-on — this is the acceptance test that matters.
- **Opening-balance invoices must not trigger outbound side effects.** No email, no payment-link generation, no notification to the customer, no portal surprise (decide at `/architecture` whether they sync to the portal read-model like normal invoices — the default answer is yes, portal-visible, since the client genuinely owes it; but the *send* pipeline must not fire). How the prerequisite gate (`validateInvoicePrerequisites`: ACTIVE + tax-number etc.) is satisfied is an `/architecture` decision: imported customers are ACTIVE by construction, and take-on can either require the prerequisite fields in the clients CSV or use a dedicated creation path that documents why the INVOICE_GENERATION prerequisite check does not apply to brought-forward facts. Do not silently gut the gate for normal invoices.
- **Matters import stays simple.** `ProjectService.createProject` with customer link (resolved by client email or source ref), name, referenceNumber, dueDate, workType. **No template instantiation** — imported matters are in-flight; seeding template task lists onto them would be wrong. Matter rows referencing an unresolvable client fail with a clear per-row error, they do not create orphans.
- **Unmapped columns are parked, not dropped.** The mapping step lets the admin map any leftover source column to a custom field (existing `FieldDefinition`s for the entity type) or to a generic parked map in `customFields` — the Xero `xero_contact_id` pattern. Every imported row records its source-file reference (e.g. `takeon_source_ref`) for traceability.
- **Files:** CSV only (10 MB, multipart direct-to-backend, MIME + extension validation), raw file archived to S3 under a take-on prefix (`take-on/{tenant}/{batchId}/{fileName}`) — source files contain PII, so they live in tenant-scoped S3 exactly like trust statements, and the batch record keeps the S3 key. XLSX is out of scope (firms save-as-CSV; revisit with design-partner feedback).
- **Audit + POPIA.** New audit family (e.g. `takeon.batch.committed`, `takeon.batch.cancelled` — final names at `/architecture`) with counts and batch/entity refs only — **no client names, emails, or balances in audit params** (standing SAFE_TOKEN discipline). Note the standing registry gotchas: audit catalogue-count tests will need bumping; only the full `./mvnw verify` catches cross-package count assertions (CLAUDE.md §5).
- **Tenant isolation tests are mandatory** (batch of tenant A invisible to tenant B; committed entities land in the committing tenant's schema only).
- **Phase 83 interaction:** imported overdue opening-balance invoices WILL be picked up by the collections scan once Phase 83 lands — that is desired, but the QA capstone must observe it deliberately rather than be surprised by it (a freshly-taken-on firm should not instantly email dunning letters to every debtor; the collections policy's own gates/defaults govern, verify they do).
- **Test strategy.** Full `./mvnw verify` clean + `pnpm lint && pnpm build && pnpm test` (+ prettier `format:check`). **"PASS means observed"**: the QA capstone drives the browser through a realistic take-on — a prepared "previous system export" fixture set (clients + matters + trust balances + debtor balances for a fictional firm) uploaded through the wizard, control-total mismatch first (observe the block), corrected, committed, then: customer list shows ACTIVE clients, matter list links correctly, trust reconciliation shows the three-way tie, AR aging shows the debtors, and the take-on report downloads. Reproduce-before-fix for any bug.

---

## Section 1 — Take-On Foundation: Batch Entities, Capability, Migration

- `TakeOnBatch`: dataset type (`CLIENTS | MATTERS | TRUST_OPENING_BALANCES | DEBTOR_BALANCES`), status lifecycle mirroring BillingRun (`UPLOADED → MAPPED → VALIDATED → COMMITTING → COMPLETED / CANCELLED` — exact states at `/architecture`, with guarded transition methods), file metadata (fileName, s3Key, rowCount), column-mapping JSON, cutover date, control-total fields (trust dataset), roll-up counters (created / skippedDuplicate / failed), createdBy + timestamps.
- `TakeOnRow`: batchId, rowNumber, raw source values (jsonb), status (`PENDING → VALIDATED / ERROR → CREATED / SKIPPED_DUPLICATE / FAILED`), `failureReason`, result entity type + id.
- V4 migration: both tables + `OPENING_BALANCE` added to `chk_trust_txn_type`.
- New `Capability.MANAGE_TAKE_ON` (admin-level) + `org_role_capabilities` grant migration for existing admin-class custom roles (V125 precedent). `@RequiresCapability("MANAGE_TAKE_ON")` on every take-on endpoint.

## Section 2 — Upload, Column Mapping & Validation Engine

- `POST /api/take-on/batches` (multipart: file + dataset type): store to S3, parse header + rows tolerantly (CsvBankStatementParser disciplines), persist batch UPLOADED with raw rows.
- Downloadable CSV template per dataset (static or generated) so a bookkeeper can fill our shape directly; mapping step exists for firms uploading their system's export instead.
- Mapping endpoint: admin maps source columns → target fields (required/optional per dataset; leftovers → custom field or parked). Server persists the mapping on the batch (reusable as the seed for future source-system presets).
- Validation (dry-run) endpoint: applies the mapping, validates every row (required fields, formats via `parseAmount`/`parseDate` conventions, dedup lookups, cross-references like matter→client), writes per-row VALIDATED/ERROR + reasons, computes preview summary + control totals. **Nothing is committed at this step.** Validation is re-runnable after the admin fixes the file or the mapping.

## Section 3 — Clients Commit

- Commit endpoint (per batch, once): two-phase Xero-style — resolve + dedup in memory, persist inside `TransactionTemplate` batches, per-row results as it goes. Customers created ACTIVE with customerType, contact/address/tax fields per mapping, source ref + unmapped columns in `customFields`, notes "Imported via take-on ({fileName})".
- Summary mirrors `CustomerImportSummary` semantics (created / skippedDuplicate / failed / total) + audit event with counts only.

## Section 4 — Matters Commit

- Rows resolve their client by email or source ref (against both pre-existing and batch-created customers); unresolvable → per-row FAILED.
- `ProjectService.createProject(...)` with customer link, referenceNumber, dueDate, workType; no template instantiation. Duplicate detection by referenceNumber (or name+customer) → SKIPPED_DUPLICATE.

## Section 5 — Trust Opening Balances Commit

- Requires: an existing `TrustAccount` selected on the batch, a cutover date, and the admin-entered trust bank balance at cutover. Commit hard-blocked until Σ(rows) == control total exactly.
- Per row: resolve client (fail if unknown), reject negative/zero amounts, enforce one OPENING_BALANCE per ledger card, then create the `OPENING_BALANCE` `TrustTransaction` (status RECORDED, cutover transactionDate, fixed description + source ref as reference) and upsert/credit the `ClientLedgerCard` through the same locking discipline as `recordDeposit`.
- Acceptance invariant: immediately after commit, `calculateReconciliation` three-way tie holds given a bank statement whose opening balance equals the control total.
- Take-on report: exportable (CSV at minimum) list of every ledger + brought-forward amount + control total — the artifact the firm hands its trust auditor.

## Section 6 — Debtor Opening Balances Commit

- Per row: resolve client (and optionally matter), create a draft invoice with one MANUAL line ("Balance brought forward as at {cutover}" + source invoice ref), issueDate/dueDate from the source row, transition to the outstanding state **without** email/payment-link/notification side effects (mechanism per `/architecture` — see Constraints on the prerequisite gate).
- Result: AR aging, customer detail, dashboards, statements and (post-Phase-83) collections see these as ordinary outstanding invoices.

## Section 7 — Frontend: Settings → Take-On Wizard + Report

- New `settings/take-on/` page (admin settings pattern, capability-gated in nav): dataset picker with dependency guidance (clients → matters → balances), then per-batch wizard: upload → map columns (template download link) → validation preview (row table with statuses/errors, summary cards, control-total entry + live match indicator for trust) → commit confirmation (explicit, destructive-styled) → results screen (per-row outcomes, report download).
- Batch history list (past batches, statuses, reports). Multipart upload via the bank-statement server-action pattern (10 MB cap, extension/MIME check client-side).

## Section 8 — QA Capstone (Observed End-to-End)

- Fixture "previous system" export set for a fictional firm (≥20 clients incl. deliberate duplicates + bad rows, ≥15 matters incl. one orphan, trust balances with an initial control-total mismatch, debtor balances) executed through the browser per the Test Strategy above; gap report per house QA-capstone format.

---

## Out of Scope

- **Source-system-specific mappers/presets (GhostPractice, AJS, Lexpro)** — data-only mapping presets built AFTER design partners supply real export files. The mapping JSON persisted per batch is the designed seed for them.
- **XLSX/Excel parsing** — CSV only; firms save-as-CSV.
- **Historical transactions of any kind** — no past invoices, payments, trust movements, or time entries. Opening balances + master data only.
- **WIP / unbilled-time take-on** — real gap for billing continuity, deliberately deferred; revisit with design-partner feedback (likely a lump opening-WIP concept, its own phase).
- **Contacts/portal-user import, document/file migration, matter-task import** — later.
- **Rollback/undo of a committed batch** — v1 is preview-hard-before-commit + per-row skip; a delete-what-batch-N-created undo is meaningful extra machinery (batch provenance is recorded via `TakeOnRow` result FKs + source refs, so a future undo is not foreclosed).
- **API-based migration connectors** — file-based only.

---

## ADR Topics to Address

- **ADR-32x: Take-on architecture** — generic CSV wizard + persisted column mapping vs source-system parsers; batch/row entities mirroring BillingRun; re-runnable batches with dedup-based idempotency vs Xero's one-time guard; capability-gated (not module-gated).
- **ADR-32x: Trust opening balances as `OPENING_BALANCE` credit transactions** — why a transaction type (auditability, reconciliation-invariant preservation, rides existing ledger locking) rather than a ledger-card seed field; pre-approved RECORDED status consistency with the credits-don't-gate design; control-total hard block; one-per-ledger rule; negative-balance rejection.
- **ADR-32x: Debtor take-on as opening-balance invoices** — why no AR-ledger entity; how the outstanding state is reached without send side effects; the prerequisite-gate decision (satisfy vs documented bypass for brought-forward facts); portal visibility; Phase 83 collections interaction.

---

## Style & Boundaries

- Follow `backend/CLAUDE.md` and `frontend/CLAUDE.md`. New backend code lives in a `takeon/` package (horizontal, not under `verticals/`), except the `OPENING_BALANCE` additions which belong to the trust domain and must be reviewed against `TrustTransactionService`'s existing invariants.
- **Reuse over rebuild:** `XeroCustomerImportService` patterns (two-phase persist, dedup, summary, customFields parking, safety valve), `CsvBankStatementParser` parsing disciplines, `BillingRun` lifecycle shape, `ProjectService.createProject`, `InvoiceCreationService` manual lines, existing multipart-upload + S3 + settings-page patterns.
- **Hard boundaries to verify at review:** no email/notification/payment-link fires from any take-on commit; no PII in audit params; control-total block actually blocks; OPENING_BALANCE unreachable from normal trust UI/API entry points (take-on only); tenant-isolation tests present; full `./mvnw verify` clean.
- Estimated shape: **~6 epics / 12–15 slices** (foundation; upload+mapping+validation; clients; matters; trust balances; debtor invoices + frontend wizard + QA capstone) — within the phase-size bar.

---

## Next step

`/architecture requirements/claude-code-prompt-phase84.md` — generates the architecture section + ADRs, then `/breakdown 84`. In parallel (non-code, per the GTM plan): sign the pilot letters and request each design partner's actual system export — those files retro-feed the mapping presets and the QA fixture realism.
