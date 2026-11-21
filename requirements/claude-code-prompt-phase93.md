# Phase 93 — Client & Trust Take-On: Move a Firm Onto Kazi With Books That Reconcile From Day One

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-11-19 → 2026-11-20). **Supersedes Phase 84** (`requirements/claude-code-prompt-phase84.md`, written 2026-07-11, never architected or built). Phase 84's *product* decisions carry over. Its scouting note and trust mechanics are void: they predate Phases 86, 87 and 92, and a code trace on 2026-11-19 found the trust half of 84 wrong in every mechanism it relied on (see § Why 84 was superseded).
>
> **Why this phase, why now.** The design-partner pitch closes on "we move your data for you" (`.claude/ideas/gtm-design-partner-plan-2026-07-11.md`). A competitor review on 2026-11-19 (`.claude/ideas/phase93-ideation-2026-11-20.md`) found that the dominant SA incumbent (AJS) offers a bulk import tool only at its 50+-user Enterprise tier. Its small and mid-size firms, who are Kazi's target, have never had self-service take-on. A firm cannot run parallel books, so without take-on a pilot means the founder keys in every client, matter and trust balance by hand.
>
> **Founder decisions (2026-11-19 / 2026-11-20):**
> 1. **New phase number (93), not an amended 84.** 84 is marked superseded, not deleted.
> 2. **Trust scope = every trust creditor group** the Phase 92 equation sums: client and matter ledgers, the Fund interest-and-charges ledger, held unidentified deposits (itemised), s86(3) and s86(4) investments, plus the bank-reconciling items outstanding at cutover. A partial take-on would fail the firm's first reconciliation.
> 3. **Imported clients land ACTIVE with a FICA attestation.** They do not re-run onboarding checklists. The attestation records CDD performed by the firm in its prior system (fields per § Legal basis). Clients with gaps go to a review queue.
> 4. **Generic wizard, CSV and Excel (.xlsx).** No source-system presets. Building parsers against export formats we have not seen violates reproduce-before-fix (root CLAUDE.md §4). The persisted column mapping is the seed for presets once a design partner supplies a real export. Apache POI is already a dependency (DOCX only today).
> 5. **Master data and opening balances at a cutover date. No history.** No historical transactions, fee notes, payments or time.
> 6. **Capability-gated** (`MANAGE_TAKE_ON`, admin-level, not OWNER_ONLY), with no `VerticalModuleRegistry` entry. Batches are re-runnable; idempotency comes from dedup keys, not a global "already ran" flag.
> 7. **Sequencing: 92 → 93 → 91** (founder decision 2026-11-20). This amends Phase 92 founder decision #1 ("92 → 91 → 84"). Take-on is the pilot close, and a firm that cannot get its data in is not won by a better settings shell. Phase 91 follows. Before its `/architecture`, refresh its trust sub-page scope for the Phase 92 surfaces (creditor accounts, investments, Rule 54.15 list) and the Phase 93 take-on tab.
>
> **Scouting note.** Code trace on 2026-11-19 against `main` `b08a8916a`. Paths are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`; trust paths are relative to `verticals/legal/trustaccounting/`. **Re-read file:line at HEAD before relying on any of it.**
> - **Trust balances are derived, not stored.** `ledger/ClientLedgerCard` is now only a per-client lock row (`ClientLedgerCard.java:5-9`). Balances come from `trust_transactions.ledger_effect` and `cashbook_effect` via `TrustLedgerQueryRepository`. `TrustAccount` has no balance field. No opening-balance transaction or brought-forward field exists anywhere. The Phase 92 "opening-balance snapshot" was designed but explicitly not built (`architecture/phase92-trust-books-provably-right.md:262, :2858`). Only per-period computed openings exist, for statements (`ClientTrustStatementContext.java:24,51,119`) and on `BankStatement` (`:33-34`).
> - **Transaction types are a registry** (ADR-378): `transaction/TrustTransactionType.java`, 25 constants. Each carries cashbookSign, ledger, ledgerSign, creditorAccountType, approval, reversal, origin, clientVisible, auditKey and labels (`:361-371`). The CHECK constraint was last redefined in `V18__phase92_trust_creditor_accounts.sql:71-77` and is pinned to the enum by `TrustTransactionTypeSchemaTest`. The column is `varchar(20)`. `CREDIT_TYPES`/`DEBIT_TYPES` no longer exist.
> - **Posting.** `TrustPostingService.record` (`:112-118`, approval-NONE types) and `lockClient` (`:97-108`: `insertLockAnchorIfAbsent` + `findByAccountAndCustomerForUpdate`). **Every client posting needs a matter linked to that client** (`TrustClientMatterRule.java:15-19,51`). Recorded deposits publish `TrustTransactionRecordedEvent`, which syncs to the portal (`TrustTransactionService.java:417-428`).
> - **The equation.** `reconciliation/TrustReconciliationService.recalculate` (`:1071-1105`) via `calculateReconciliation` (`:916`): balanced ⇔ adjustedBank == cashbook (EQ-1) ∧ cashbook == creditorTotal (EQ-2). creditorTotal = clientLedgers + investmentCreditors + fundLedger + unidentifiedDeposits + otherNonClient + interAccount (`books/TrustBooksService.java:160-166`). adjustedBank = statement closing + outstanding deposits − outstanding payments. Completion also needs EQ-4/EQ-5 and no unresolved lines (gates G1–G4, `:1113`). **Hazard:** a +cashbook row with no matching bank line counts as an outstanding deposit (`TrustLedgerQueryRepository.java:483-507`). A naive opening credit would therefore be double-counted against a bank closing balance that already contains the money, and EQ-1 would fail on the first reconciliation.
> - **Non-client creditors and investments.** `creditor/TrustCreditorAccount` (FUND_INTEREST_AND_CHARGES, UNIDENTIFIED_DEPOSITS); `creditor/UnidentifiedDeposit` items (HELD / ALLOCATED / PAID_TO_FUND); `investment/TrustInvestment` on INVESTMENT-type trust accounts (V22/V23), with `InvestmentBasis` FIRM_DISCRETION (s86(3)) or CLIENT_INSTRUCTION (s86(4)); `TrustInvestmentInterest`; and the non-posting Rule 54.28 `TrustInterestMemo`. The interest run and LPFF rates were dropped (V27, V28).
> - **Clients.** The entity is `Customer`; the wire is `/api/clients` + `clientId` (Phase 87). Every `CustomerService.createCustomer` overload pins PROSPECT (`:388-495`). The 8-arg constructor accepts a status (`Customer.java:152-162`) but skips `CustomerCreatedEvent`, which provisions portal contacts (`CustomerService.java:550`). **`XeroCustomerImportService` creates PROSPECT, not ACTIVE** (`:261`), so do not copy it blindly. Normal activation goes PROSPECT → ONBOARDING (auto-creates FICA checklists) → ACTIVE (checklists + LIFECYCLE_ACTIVATION prerequisites), in `CustomerLifecycleService.java:127-153`. **Hazard:** the 02:00 `DormancyScheduledJob` moves ACTIVE clients with no activity to DORMANT, and "no activity" includes having no rows at all; trust postings don't count (`CustomerLifecycleService.java:61-71`). Freshly imported clients would go dormant overnight.
> - **Matters.** `ProjectService.createProject` keeps its 10-arg signature (`:616-626`). It writes the lead `ProjectMember`, `CustomerProject`, `CustomerProjectLinkedEvent` (portal) and the `project.created` audit. Matters are blocked for PROSPECT, OFFBOARDING and OFFBOARDED clients (`ProjectFieldService.java:127`, `CustomerLifecycleGuard.java:19-24`). Plain create instantiates no template or checklist. Conflict checks are not enforced on create.
> - **Fee notes.** There is no AR ledger. `InvoiceCreationService.createDraft` (`:154`) + `addLineItem` (`:472`, `InvoiceLineType.MANUAL`). Statuses DRAFT, APPROVED, SENT, PARTIALLY_PAID, PAID, VOID. `validateInvoicePrerequisites` (`:799-819`): the legacy `status` field is ACTIVE/ARCHIVED; INVOICE_GENERATION needs address, tax number, and an email or portal contact (`PrerequisiteService.java:171-196`). **SENT is reachable only through `send()`**, which generates a payment link (`InvoiceTransitionService.java:259`) and publishes `InvoiceSentEvent`. That event drives the client email with PDF, the in-app notification, Xero sync and the INVOICE_STATUS_CHANGED automation; `InvoiceSyncEvent` drives the portal. The wire is still `/api/invoices`; "fee note" is the UI word.
> - **Collections (Phase 83) is live but gated.** Daily scan at 06:00 over SENT/PARTIALLY_PAID fee notes past due. `collections_enabled` defaults to false (`V1:456`). Reminder emails go only through a human-approved AI gate (`CollectionReminderSendService.java:33-34`). The weekly owner `CashDigest` is ungated (`CashDigestService.java:64`), so imported overdue fee notes will appear there.
> - **Precedents to imitate.** Batch lifecycle: `billingrun/BillingRun` + `BillingRunItem`. Dedup, summary and customFields parking: `XeroCustomerImportService` (`ClientImportSummary`, 10k-row valve `:41`). Tolerant CSV: `reconciliation/parser/CsvBankStatementParser` (plain classes, malformed-line skip-and-count; it already skips "OPENING BALANCE" rows, `:41-44`). Upload: `uploadBankStatement` server action (`frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/actions.ts:72-98`, 10 MB) → `POST /api/trust-accounts/{accountId}/bank-statements` (`TrustReconciliationController.java:41-48`), S3 key `trust-statements/{tenant}/{accountId}/{uuid}/{fileName}`. Spring multipart limit is 10 MB per file / 15 MB per request (`application.yml:59-60`). Custom fields at creation: `CustomFieldValidator` (CUSTOMER, PROJECT, INVOICE).
> - **Capabilities.** Owner and admin receive every capability in code, with admin excluding OWNER_ONLY (`OrgRoleService.java:68-76`), so a new constant needs no grant migration for them. Custom roles need explicit grants (MCP_WRITE precedent, ADR-321).
> - **UI placement already reserved.** ADR-341 reserves a placeholder `settings/matters?tab=take-on` tab (`frontend/app/(app)/org/[slug]/settings/matters/_tabs/index.ts:14-16`). Nothing take-on-like exists in backend or frontend.
> - **Phase 92 is code-complete with exit verdict FAIL** (652F, PR #1822). Its open defects are being fixed as follow-up PRs FU-1..FU-16, with FU-17..FU-27 proposed (`tasks/phase92-trust-books-provably-right.md` § Closure report). None changes the registry, the equation or the creditor model, so architecture can proceed. **These follow-ups touch take-on and must be read at HEAD by `/architecture` and merged or ruled before E5 is built:**
>   - **FU-14** (postings before the account's `opened_date`: refuse or flag). Unidentified deposits keep their *original* deposit date (s87(4)), which will predate a Kazi account opened at cutover. Hold the original date as an item attribute, or have FU-14 exempt take-on, and say which.
>   - **FU-6** (duplicate, future-dated and replace rules on statements). Affects the first post-cutover statement import.
>   - **FU-16** (auto-match pair rules). Affects matching the outstanding cutover items to later bank lines.
>   - **FU-9** (cashbook cover and `CASHBOOK_IN_DEBIT`). Relates to the deficit block.
>   - **FU-22** (idempotency keys on trust writes, an architecture call). Affects batch-commit idempotency.
>   - **FU-23** (portal read-model recompute). Take-on posts many trust entries at once.
> - **Migrations.** The latest tenant migration is `V29` (trust_recon_unique), so the next free is **V30**. The latest global is `V7`, so the next free is **V8**. Take the next free number at build time (see memory: migration numbers come from the code, not the docs).

## Why 84 was superseded (do not resurrect its mechanics)

| Phase 84 said | Reality on `main` (2026-11-19) |
|---|---|
| Control total = Σ client opening balances == bank balance | EQ-2 sums six creditor terms. Firms with Fund, unidentified or investment balances could never pass. |
| Add `OPENING_BALANCE` to `CREDIT_TYPES` + CHECK; upsert the ledger card | No `CREDIT_TYPES`; a type is a full registry entry. The card holds no balance. Every posting needs a matter. |
| An opening credit leaves the three-way tie intact | A +cashbook row with no bank line is an *outstanding deposit*, so EQ-1 double-counts it. |
| Imitate Xero: customers land ACTIVE | Xero creates PROSPECT. PROSPECT blocks matter creation. The constructor path skips portal provisioning. |
| Opening fee notes reach "SENT-equivalent" quietly | The only route to SENT fires email, payment link, Xero sync, automation and notification. |
| Datasets: clients, matters, client trust, debtors | Missing: Fund ledger, unidentified deposits, s86(3)/(4) investments, outstanding reconciling items. |
| V4 migration; V125 grant precedent; Settings → Take-On page | V30/V8; no grant migration for owner/admin; ADR-341 reserves a tab under Settings → Matters. |

## System Context

Kazi is legal-only SA practice-management SaaS (Phase 86): Next.js 16 frontend and portal, Spring Boot 4 / Java 25, Keycloak, schema-per-tenant Postgres. Phase 92 made the trust books provably right: statement-driven posting, non-client trust creditors, investments on the books, the EQ-1/EQ-2 equation, the Rule 54.15 monthly creditor list, and the Rule 54.28 schedule. Kazi can now *keep* a firm's books correctly. It still cannot *receive* them. The only imports are the API-based Xero client pull and trust bank statements (reconciliation lines, not balances).

## Objective

A firm, or the founder white-gloving a pilot, migrates via **Settings → Matters → Take-On**. For each dataset it uploads a CSV or XLSX file, maps the columns, reviews a full dry-run validation (row errors, duplicates, control totals, statutory blocks), commits, and receives a per-row result report. Datasets go in dependency order:

1. **Clients**: ACTIVE, with a FICA attestation.
2. **Matters**: linked to imported or existing clients.
3. **Trust opening position**: every creditor group plus outstanding reconciling items, gated by the reconciled cutover position and signed off.
4. **Debtor opening balances**: brought-forward fee notes, issued quietly.

**Exit bar:** on a fresh stack, a fictional firm is taken on through the browser. Then its **first post-cutover month reconciles** (EQ-1, EQ-2, EQ-4/5, gates G1–G4) against a bank statement whose opening balance is the cutover balance. Both are observed, not inferred (root CLAUDE.md §3).

## Legal basis (Fable legal advisor + Perplexity, 2026-11-20; founder sign-off)

- **FICA.** Relying on CDD already performed by the *same* accountable institution is compliant; a software change is not a new business relationship. s21 is triggered by a prospective client, which a migration is not. Ongoing due diligence continues: s21C at a risk-based frequency set in the RMCP (s42, Guidance Note 7), and s21D when doubts arise. Records: s22 requires the information and copies of or references to the verification documents; s23 requires five years after the relationship ends; s24 permits third-party storage, but access must be "free and easy", and s24(3) requires FIC notification if the old vendor keeps hosting. **The attestation is a pointer to CDD records that must still exist.** Confidence: high on the principle, medium on the field list.
- **Trust.** No LPA/LPC rule requires transaction history to live in one system; s87(1) and Rule 54.6 require records that explain the trust position. Retention is **7 years from the last entry** (Rule 54.9.1). Hosted records must be secured and immediately accessible to the firm and the Council (54.9.2). Loss of records must be reported forthwith (54.14.2). Monthly balancing (54.10) and the monthly creditor list compared to trust bank, investments and cash (54.15.1) both apply. Under the IRBA Guide (Rev. March 2020), auditors test opening balances. No LPC notification is required for a system change (54.16 covers bank-account changes only). Confidence: high on the rules, medium on the absence of a notification duty.
- **Debit balances.** Rule 54.14.9 says no trust creditor may be in debit. Rule 54.14.11 says the firm must immediately report a debit to the Council in writing, with the reason and proof of rectification. Rules 54.14.8 and 54.14.10 cover an overall trust deficit. Confidence: high.
- **s86(4) investments.** Interest goes to the client, with 5% to the Fund (s86(5)). Written confirmation is required (54.17.2), the account must be endorsed as opened under s86(4) (54.17.3), and bank commission must be disclosed (54.18). For s86(3), interest goes to the Fund and no client instruction applies. Confidence: high on the rules, medium on making the reference mandatory.
- **Unidentified deposits.** Under s87(4)(a), money whose owner is unknown or unclaimed is paid to the Fund after the second annual closing following the **deposit date**. The original date must therefore survive take-on, so the clock is not reset. Confidence: high.

## Assumptions register (sign-off wanted, not blocking; design so a different answer changes config or copy, not the data model)

- **A1 (attorney / FICA compliance officer):** the attestation wording, and who may attest. Default: a member holding `MANAGE_COMPLIANCE` attests, not merely the importing admin.
- **A2 (attorney):** whether Rule 54.9.2 Council consent or a s24(3) FIC notification arises when the old vendor keeps the archive or the hosting location changes. Default: a go-live checklist item with explanatory copy; Kazi does not file anything.
- **A3 (trust auditor):** the take-on reconciliation format, and whether a mid-month cutover is acceptable. Default: month-end cutover, with mid-month allowed behind an explicit warning plus a reconciliation to that date.
- **A4 (trust auditor):** whether any override of the debit-balance block can ever be allowed. Default: **never**; fix it in the old system first.
- **A5 (auditor):** treatment of accrued, uncapitalised s86(4) interest and Fund-share status at cutover.
- **A6 (before final spec / at `/architecture`):** check the current LPC Rules for post-2020 amendments, and the FIC's GN 7B (June 2026).

## Constraints & Assumptions

- **Reuse, no framework.** The batch lifecycle mirrors BillingRun (guarded transitions, per-row status, `failureReason`, result FK). Parsing follows the CsvBankStatementParser disciplines in a small take-on-owned reader, with XLSX read via POI into the same row model. Do not contort the bank-statement hierarchy. Dedup, summary and parking follow the Xero import, including two-phase validate-then-`TransactionTemplate` persist and a row-count safety valve.
- **Trust opening entries go through the Phase 92 registry and posting service**, never around them. Any new `TrustTransactionType` constant(s) need complete registry attributes, a migration extending the CHECK, and `TrustTransactionTypeSchemaTest` updated. **Opening entries must be treated as cleared at cutover**: they never appear as outstanding deposits, and the first post-cutover reconciliation balances against a statement opening at the cutover balance. The mechanism is an ADR decision: a registry attribute, the deferred Phase 92 opening-balance snapshot, or both. Opening types are **unreachable from normal trust UI/API entry points**. Hand-posting a brought-forward balance must be impossible.
- **Hard blocks (no override):** any trust creditor in debit; an overall deficit (creditor total > bank + investments + cash); creditor total ≠ reconciled cutover position to the cent; an unidentified deposit without its original deposit date; a client trust row without a resolvable matter of that client. **The Rule 54.14.11 duty is shown** in the block message.
- **Imported clients must not trip lifecycle side effects.** They land ACTIVE through a dedicated, documented entry path. Do not weaken `createCustomer`'s PROSPECT default for the normal UI. Required decisions:
  - **Portal contacts:** default *not* auto-invited. Provisioning is an explicit later action, so no surprise emails.
  - **Dormancy:** take-on must count as activity, or imported clients must be exempt until a set date. Verify this against the 02:00 job.
  - **FICA checklists:** not created. The attestation replaces them.
- **Quiet issue for brought-forward fee notes.** They reach an outstanding state (SENT semantics for aging, statements, dashboards and collections) with **no** email, no payment link, no in-app notification to the client, no Xero sync, and no automation trigger. Portal visibility defaults to *visible*, since the client genuinely owes the money. How the INVOICE_GENERATION prerequisite is satisfied, or legitimately not applied to brought-forward facts, is an ADR decision. Do not gut the gate for normal fee notes.
- **Collections.** It remains governed by `collections_enabled` (default off) and the human-approved send gate. The QA capstone observes that a fresh take-on sends nothing and that the CashDigest shows the imported debtors.
- **Files.** CSV and XLSX, 10 MB, multipart direct to the backend, extension and MIME validation. The raw file is archived to S3 at `take-on/{tenant}/{batchId}/{uuid}/{fileName}`. It contains PII and is treated like trust statements. Multi-sheet XLSX means the user picks a sheet.
- **Audit and POPIA.** Add a new `takeon.*` audit family carrying counts and refs only: no names, emails, ID numbers or amounts in params (SAFE_TOKEN discipline). Audit catalogue-count tests will need bumping; only a full `./mvnw verify` catches the cross-package assertions.
- **Tenant isolation tests are mandatory.** Batches are invisible across tenants; committed entities land only in the committing tenant's schema.
- **Test bar.** Full `./mvnw verify` clean (`backend/scripts/verify.sh`), plus `pnpm lint && pnpm build && pnpm test && pnpm format:check`, plus the legal Playwright suites (UI change). Reproduce-before-fix for any bug found.

---

## Epic specifications

### E1 — Take-on foundation
- `TakeOnBatch` holds:
  - dataset (`CLIENTS | MATTERS | TRUST_POSITION | DEBTOR_BALANCES`; the trust dataset may be split per creditor group at `/architecture`)
  - guarded status lifecycle (`UPLOADED → MAPPED → VALIDATED → COMMITTING → COMPLETED / CANCELLED`, final states per ADR)
  - file metadata, S3 key, sheet name, column-mapping JSON, cutover date
  - control-total fields, roll-up counters, createdBy and timestamps
- `TakeOnRow` holds the raw values (jsonb), row number, status (`PENDING → VALIDATED / ERROR → CREATED / SKIPPED_DUPLICATE / FAILED`), `failureReason`, and the result entity type and id.
- Tenant migration (next free, ~V30); add `Capability.MANAGE_TAKE_ON` with `@RequiresCapability` on every endpoint; S3 archival.
- The CSV/XLSX reader produces one row model, using the tolerant parsing disciplines.

### E2 — Mapping and dry-run validation
- Upload (`POST /api/take-on/batches`, multipart file + dataset). Downloadable template per dataset.
- The mapping step covers required and optional target fields. Leftover columns go to an existing custom field or are parked in `customFields`. The source-row reference (`takeon_source_ref`) is always recorded.
- Dry-run validation applies the mapping and validates every row (formats, dedup, cross-references, statutory blocks). It writes per-row VALIDATED/ERROR results and computes the summary and control totals. **Nothing is committed.** It can be re-run after the file or mapping is fixed.

### E3 — Clients with FICA attestation
- Two-phase commit. Dedup is email (case-insensitive), then name + tax/ID number; a match is SKIPPED_DUPLICATE and may enrich the source ref.
- Clients land ACTIVE through the dedicated entry path (§ Constraints: portal contacts, dormancy, no checklists).
- **FICA attestation record** per client, distinct from "verified in Kazi". Its fields:
  - CDD completion date and date of last review
  - risk rating, or `UNRATED`
  - client type
  - beneficial-ownership status (done / not applicable / outstanding)
  - PEP/sanctions screening status and date
  - source-of-funds status where the RMCP requires it
  - **location of the underlying CDD records** (old-system or archive reference)
  - the attesting member, role and timestamp
  - the source-system reference
- The attestation is given per batch by a `MANAGE_COMPLIANCE` holder (A1) and applied to each row.
- **The next review date is derived from the risk rating.** `UNRATED`, or outstanding beneficial ownership, puts the client in a **FICA review queue** (an existing compliance surface or a filtered client list, decided at `/architecture`), not a clean pass.

### E4 — Matters
- Each row resolves its client by email, source ref or ID number, against both existing clients and clients created in the batch. An unresolvable client is a per-row FAILED; no orphan matters.
- Created with `createProject` (customer link, referenceNumber, dueDate, workType). **No template instantiation**: imported matters are in flight. Dedup is referenceNumber, then name + client.

### E5 — Trust opening position
- **Inputs on the batch:** trust account(s), the cutover date (month-end by default, A3), and the **reconciled cutover position from the old system**: bank statement balance at cutover, outstanding deposits and outstanding payments at cutover, and the resulting cashbook balance. These are the old system's Rule 54.15.1 list totals.
- **Rows by creditor group:**
  - **Client and matter ledger balances.** A debit balance is blocked.
  - **Fund interest-and-charges ledger** balance (a debit is allowed but flagged, consistent with Phase 92 decision 9; confirm at `/architecture`).
  - **Held unidentified deposits.** One item each, with the original deposit date, amount and bank reference. A single lump suspense balance is rejected.
  - **s86(3) and s86(4) investments.** Typed separately. Each records the client and matter (s86(4)), the bank and account, principal, accrued uncapitalised interest and Fund-share status (A5), and for s86(4) the **instruction or Rule 54.17.2 confirmation reference and date** plus endorsement confirmed.
  - **Outstanding reconciling items at cutover.** Unpresented payments and deposits in transit, as items that later bank lines can match.
- **Gate.** Commit is hard-blocked unless EQ-1 and EQ-2 hold at cutover to the cent, there is no debit creditor and no deficit (§ Constraints), and **a partner or director has signed off** the take-on (Rule 54.19 places responsibility on them).
- **Take-on report.** Generated at commit and stored immutably (`GeneratedDocument` or an S3 PDF, decided at `/architecture`). Contents: the cutover-date Rule 54.15.1-style creditor list by group, control totals, the investments register, the unidentified-deposit items, the sign-off, and the source file refs. Exportable as PDF and CSV. **This is the artefact the firm hands its trust auditor**, and our introduction to auditors (GTM channel #3).
- **Acceptance invariant:** the first post-cutover reconciliation balances with no manual adjustment.

### E6 — Debtor opening balances
- One brought-forward fee note per row (client, optionally matter). It has a single MANUAL line, "Balance brought forward as at {cutover}" plus the source invoice ref, and issue/due dates from the source.
- It reaches the outstanding state through the **quiet issue path** (§ Constraints). PARTIALLY_PAID is not used at import; the source balance is the outstanding amount.
- Afterwards, AR aging, client detail, dashboards, statements, the CashDigest and (if enabled) collections see these as ordinary outstanding fee notes.

### E7 — Frontend wizard and go-live checklist
- The `settings/matters?tab=take-on` tab (ADR-341 placeholder; confirm placement at `/architecture`) is capability-gated.
- **Dataset picker** with dependency guidance: clients → matters → trust → debtors.
- **Per-batch wizard:**
  1. Upload (CSV/XLSX, sheet picker).
  2. Map columns, with a template download.
  3. Validation preview: row table, summary cards, and for trust a **live EQ-1/EQ-2 indicator** and statutory-block messages.
  4. Attestation or sign-off step where required.
  5. Explicit, destructive-styled commit confirmation.
  6. Results and report download.
- **Batch history.**
- **Go-live checklist.** The firm records acknowledgements:
  - old trust records retained for 7 years and accessible (54.9.1/54.9.2)
  - FICA records retained for 5 years after termination (s23)
  - the s24(3) / 54.9.2 question, with copy (A2)
  - the auditor informed of the cutover

### E8 — QA capstone (observed end-to-end)
- **Fixture "previous system" export set** for a fictional firm:
  - ≥20 clients, including duplicates, bad rows and an `UNRATED` client
  - ≥15 matters, including one orphan
  - a trust position with one client in debit (observe the block), then corrected
  - a Fund balance, two held unidentified deposits with original dates, one s86(3) and one s86(4) investment, and outstanding cutover items
  - debtor balances with some overdue
  - at least one dataset delivered as XLSX
- **Observed in the browser on a fresh stack:**
  - the control-total and debit blocks firing
  - the sign-off step
  - the commit and the take-on report download
  - active clients in the FICA review queue where expected
  - matters linked correctly
  - **no email in Mailpit** from any commit
  - collections silent (default off)
  - the CashDigest showing the debtors
  - imported clients **not dormant** after the 02:00 job runs (trigger it)
  - **the first post-cutover month reconciled and balanced** against a statement whose opening balance equals the cutover balance
- The three legal Playwright suites green. Gap report in the house format.

---

## Out of Scope

- **Source-system presets** (AJS, GhostPractice, Lexpro): data-only follow-up once a design partner supplies a real export. The persisted mapping JSON is the seed.
- **History of any kind:** past transactions, fee notes, payments, trust movements, time entries.
- **WIP / unbilled-time take-on.** A real gap for billing continuity; revisit with design partners (likely a lump opening-WIP concept).
- **Migrating documents and FICA documents.** The attestation *points to* where they live (s22). Moving the files is later.
- **Contact/portal-user import, task import, conflict-register import.**
- **Undo of a committed batch.** Provenance via `TakeOnRow` result FKs keeps a future undo possible.
- **API-based migration connectors.**
- **Beneficiary bank-details register and changed-details hold** (payment-fraud control; Kazi captures no beneficiary bank details today). Candidate for its own phase.
- **Auditor portal / read-only auditor access.** Candidate later phase; the take-on report serves auditors in v1.
- **POPIA s22 breach/security-incident register.**
- Separate bug, **not this phase**: the `trust_account.updated` audit omits old/new account number (`TrustAccountService.java:310-325`). File and fix as its own PR.

---

## ADR topics for `/architecture` (next free number at build; last on disk ADR-384)

1. **Take-on architecture.** Generic mapping wizard vs presets; batch/row entities on the BillingRun shape; one CSV/XLSX row model; re-runnable batches with dedup idempotency; capability-gated, not module-gated; placement under Settings → Matters (ADR-341).
2. **Trust opening entries in the Phase 92 registry.** New type(s) and their registry attributes; cleared-at-cutover semantics vs the deferred opening-balance snapshot; outstanding-item take-on; the per-creditor-group model; unreachable from normal entry points; the hard blocks and their legal basis.
3. **Investments and unidentified deposits at cutover.** s86(3)/(4) typing, instruction refs, accrued interest and Fund share (A5), original deposit dates and the s87(4) clock.
4. **FICA attestation model.** Attested vs verified-in-Kazi, fields, who attests (A1), risk-driven review date, review queue; s22/s23 record-pointer semantics.
5. **Imported-client lifecycle entry path.** ACTIVE without onboarding; portal-contact provisioning deferred; dormancy interaction; events that must or must not fire.
6. **Quiet issue of brought-forward fee notes.** Outstanding without send side effects; prerequisite-gate treatment; portal visibility; collections and CashDigest interaction.
7. **Take-on report and sign-off.** Contents, immutability, who signs (Rule 54.19), and its relation to the Rule 54.15 monthly list.

## Style and boundaries

- Follow `backend/CLAUDE.md` and `frontend/CLAUDE.md`. Backend code lives in a horizontal `takeon/` package. Trust opening-entry code lives in the trust domain and is reviewed against `TrustPostingService` and registry invariants. FICA attestation code lives with compliance.
- **Hard boundaries to verify at review:**
  - no email, notification, payment link, Xero sync or automation fires from any commit
  - no PII in audit params
  - every hard block actually blocks
  - opening types are unreachable outside take-on
  - tenant-isolation tests are present
  - the first-month reconciliation balances
  - full `./mvnw verify` is clean
- **Estimated shape:** 8 epics, ~16–20 slices; slice by verify cost (BE+FE together per gap, minimise backend verifies). The trust epic (E5) is the largest and riskiest: architect it first and consider splitting it per creditor group.

## Next step

1. `/architecture requirements/claude-code-prompt-phase93.md` now (Phase 92 is closed), then `/breakdown 93`. Gate the E5 build slices on the trust follow-ups listed in the scouting note.
2. Then Phase 91 (refresh its trust scope first; see decision 7).
3. In parallel (non-code): request a real export from a design-partner firm (for presets), and put A1–A5 to a practising attorney and a trust auditor.
