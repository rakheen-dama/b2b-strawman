# ADR-336: Engagement Letter = Generated Document + Phase 28 Acceptance — `Proposal` and `Retainer` Entities Removed, "Mandate" Attaches to This Flow

**Status**: Accepted

**Context**:

Phase 32 built the proposal pipeline: a standalone `Proposal` entity with embedded Tiptap content (ADR-124), acceptance orchestration that converts an accepted proposal into a project (ADR-125), milestone invoicing (ADR-126), portal rendering (ADR-127), numbering (ADR-128) and a `FeeModel` (ADR-129). Phase 80 added a CRM deal pipeline on top (ADR-313..318). Phase 17 built retainer agreements — hour banks with periods, consumption and overage billing (ADR-072..075) — with a billing-run leg and a portal read-model. For `legal-za`, the terminology map (ADR-182/185) relabels `Proposal → Engagement Letter` and `Retainer → Mandate`; neither is a legal concept in its own right, both are labels over generic pipeline objects.

Phase 28 document acceptance (ADR-107..109) predates proposals and works on any `GeneratedDocument`: an `AcceptanceRequest` addressed to a `PortalContact`, a token link, `POST /api/portal/acceptance/{token}/accept`, a certificate of acceptance (ADR-108), an `acceptance.*` audit event and a `DocumentAcceptedEvent` that the automation engine consumes as `DOCUMENT_ACCEPTED`. The legal-za template pack already ships three engagement-letter `DocumentTemplate`s (`legal-za/engagement-letter-general` among them, Phase 65 packs; ADR-251 acceptance-eligible flag).

The founder decision (`requirements/claude-code-prompt-phase86.md`): `proposal`, `crm` and `retainer` are removed; "after removal an engagement letter is a `DocumentTemplate`-generated document sent through Phase 28 acceptance … This replacement path is an acceptance test of this phase, not an assumption"; "the legal word [Mandate] survives but attaches to the engagement-letter/acceptance flow, not to an hour-bank entity." The architecture doc (`architecture/phase86-subtraction-legal-only.md` §11.3.2, §11.3.3, §11.5.2) fixes the proof: `EngagementLetterWithoutProposalIntegrationTest` and the amended `legal-lifecycle/day-01-03-onboarding.spec.ts`. The `Retainer → Mandate` map entry stays as a word that nothing renders after slice 4; Phase 87 decides the label when it deletes the mechanism.

**Options Considered**:

### A. How an engagement letter is represented

1. **A `GeneratedDocument` from a legal-za `DocumentTemplate`, sent through Phase 28 acceptance; no pipeline entity (CHOSEN)** — generate from the template for a customer (and optionally a matter), create an `AcceptanceRequest` for a portal contact, client accepts in `portal/`, certificate rendered and stored, `acceptance.accepted` audited, `DOCUMENT_ACCEPTED` rules fire. Fee basis (hourly, tariff, contingency, fixed) is template content and, where billable, an invoice line type (`TIME`/`TARIFF`/`FIXED_FEE`).
   - Pros: every step already exists and is exercised by the lifecycle; nothing reads `proposals`, `proposal_milestones`, `portal.portal_proposals` or the `PORTAL_NEW_PROPOSAL` emails; the certificate and audit trail are the compliance artefacts a law firm actually needs from an engagement letter; `FIXED_FEE` (born from the proposal fee model) stays as a generic line type, so fixed-fee fee notes are unaffected; the path is proven by a `@SpringBootTest` with `proposal/**` absent from the classpath.
   - Cons: no priced proposal lines, no `FeeModel`, no milestones, no proposal numbering, no "send → accepted → project" auto-conversion (ADR-125) — the attorney creates the matter separately; no proposal stats/summary endpoints, so no pipeline reporting; the Phase 28 flow has no "withdraw" or "expire with portal notice" semantics beyond the acceptance request's expiry.
2. **Keep the `Proposal` entity and relabel it "Engagement Letter"** (the status quo via the terminology map).
   - Pros: milestones, fee model, numbering and conversion survive; no lifecycle spec amendment.
   - Cons: keeps 34 files, 12 tests, four tables, a portal page pair, a stale in-staff-app portal copy whose only live import is `portal-proposals.ts`, and a `DatabaseAuditService` fan-out — for a label; five of `proposal`'s six importers are in `crm`, which is deleted first, so the entity would survive with one consumer; the terminology mechanism it depends on is Phase 87's to delete.
3. **A new slim `EngagementLetter` entity** (fee basis, status, link to `GeneratedDocument`).
   - Pros: a first-class legal object; room for engagement-specific fields later.
   - Cons: a new entity, table, controller and portal page in a subtraction phase — forbidden ("any new feature"); it would be `V2` in the baseline series and collide with Phase 84's sequencing; the fields it would hold (fee basis, matter) are template content and matter fields today.
4. **Keep `Retainer` as the legal "Mandate"** hour-bank.
   - Pros: some firms do sell hour blocks; consumption tracking exists.
   - Cons: a South African mandate is an authority to act, not a prepaid hour bank — the label was always a stretch; `retainer` drags a billing-run leg, five notification types, a portal read-model (global V20), `invoice_lines.retainer_period_id` and `InvoiceLineType.RETAINER` through every verify; no design partner asked for hour banks.

### B. Where the "mandate" wording lands

1. **The word is reserved for the engagement-letter/acceptance flow; Phase 87 renders it (CHOSEN)** — `Retainer → Mandate` stays in `terminology-map.ts` as an entry nothing renders after slice 4; Phase 87, which deletes the mechanism and builds the legal-native shell, decides whether "Mandate" labels the accepted engagement letter, the acceptance request, or neither.
   - Pros: no pre-emption of Phase 87's IA; no dangling label in the Phase 86 UI; the requirements' intent ("attaches to the engagement-letter/acceptance flow") is recorded without inventing a surface for it.
   - Cons: between slice 4 and Phase 87 the word appears nowhere in the product; a reader of the map sees an orphan entry.
2. **Drop the word now.**
   - Pros: no orphan entry.
   - Cons: discards the one legal term the founder explicitly wanted kept; Phase 87 would have to re-derive it.
3. **Attach it now** — e.g. relabel the Documents tab's acceptance badge "Mandate accepted".
   - Pros: the word is visible immediately.
   - Cons: a label change in a surviving surface is Phase 87 scope; it would be made against the generic shell and redone when the shell becomes legal-native.

**Decision**: An engagement letter is a `GeneratedDocument` produced from a legal-za `DocumentTemplate` and sent through Phase 28 acceptance; the `Proposal`, `ProposalCounter`, `ProposalMilestone`, `ProposalTeamMember`, `RetainerAgreement` and `RetainerPeriod` entities (and the `crm` pipeline that fed proposals) are deleted with their tables (A1). The "Mandate" wording is reserved for this flow and rendered by Phase 87 (B1). The path is proven by `EngagementLetterWithoutProposalIntegrationTest` (slice 3) and the amended legal lifecycle Playwright spec, both observed green. The two `PROPOSAL_SENT` automation rules are deleted and an `ACCEPTANCE_REQUEST_SENT` trigger is deferred (ADR-334).

**Rationale**:

1. **The legal artefact is the signed document, not the pipeline object.** What a law firm must be able to show is an engagement letter the client accepted, when, by whom, with a certificate and an audit row. Phase 28 produces exactly that; `Proposal` added pricing lines, milestones and conversion that serve agencies quoting projects, not attorneys taking instructions.
2. **Zero production tenants makes this a deletion, not a migration.** No proposal, deal or retainer period exists outside dev volumes; four proposal tables, two retainer tables, three deal tables and three global read-model tables are dropped by the owning slice's throwaway migration and omitted from the squashed baseline (ADR-331) (ADR-331) with nothing to carry over.
3. **Dependency order makes it cheap.** Deleting `crm` first leaves `proposal` with one external consumer (`DatabaseAuditService`); `retainer`'s seams are enumerated (billing run, customer delete guard, invoice line, portal settings, module registry). The replacement path needs no new endpoint, event or page — the requirements and §11.10 forbid adding any.
4. **The design-partner GTM sells engagement letters, fee notes and trust accounting.** The kazi-legal-za plugin and the legal lifecycle scenarios never touch proposals or retainers; the MCP tool contract (`get_matter`, `list_correspondence`, …) is untouched by this removal.
5. **Full-verify cost.** `proposal` (34 files, 12 tests), `retainer` (25 files, 11 tests incl. `V31MigrationTest`) and `crm` (35 files, 12 tests) are three of the larger removal packages; `ProposalExpiredEmailIntegrationTest` is also pinned to a migration version the squash deletes.

**Consequences**:

- Positive: the engagement-letter path has no `proposal` dependency; `EngagementLetterWithoutProposalIntegrationTest` asserts generation → acceptance request → portal accept → certificate → `acceptance.*` audit → `DOCUMENT_ACCEPTED` trigger with `proposal/**` off the classpath; `qa/testplan/portal-experience-proposal-acceptance.md` is superseded and deleted; the stale `frontend/app/portal/**` copy (10 pages) goes with its only import.
- Positive: `InvoiceLineType` keeps `FIXED_FEE`, `TIME`, `TARIFF`, `DISBURSEMENT`, `EXPENSE`, `MANUAL`; billing runs keep time/expense/disbursement legs (`BillingRunWithoutRetainersIntegrationTest`); `prerequisite/*` mechanism (ADR-130/131) stays, with the `PROPOSAL` context/category value dropped only if nothing legal reads it.
- Negative: **no priced proposal lines, fee model, milestones or milestone invoicing**; a fixed-fee engagement is a template clause plus a `FIXED_FEE` line on the fee note, authored by hand.
- Negative: **no pipeline reporting** — proposal stats/summary, deal stages, win/loss and `PipelineSummaryWidget` are gone; intake volume is visible only through customers/matters and the AI intake skill.
- Negative: no auto-conversion from accepted letter to matter (ADR-125); the attorney opens the matter and links the document; no hour-bank consumption or "approaching capacity" notices for firms that sell blocks of hours.
- Negative: the engagement-letter follow-up task (`PROPOSAL_SENT` rule) is lost until an `ACCEPTANCE_REQUEST_SENT` trigger exists; "Mandate" is invisible in the product until Phase 87.
- Negative: `email_delivery_log.chk_email_delivery_reference_type` loses `PORTAL_NEW_PROPOSAL`, `PORTAL_PROPOSAL_EXPIRED`, `PORTAL_RETAINER`; notification catalogue loses nine proposal/retainer types; `OrgSettingsSchemaSnapshotTest` takes an authorised pin update for `portal_retainer_member_display`.
- Related: [ADR-107](ADR-107-acceptance-token-strategy.md) / [ADR-108](ADR-108-certificate-storage-and-integrity.md) / [ADR-109](ADR-109-portal-read-model-sync-granularity.md) (Phase 28 acceptance — the surviving path), [ADR-251](ADR-251-acceptance-eligible-template-manifest-flag.md) (acceptance-eligible templates), [ADR-124](ADR-124-proposal-storage-model.md) / [ADR-125](ADR-125-acceptance-orchestration-transaction-boundary.md) / [ADR-126](ADR-126-milestone-invoice-creation-strategy.md) / [ADR-129](ADR-129-fee-model-architecture.md) (Phase 32 proposals, superseded via [ADR-330](ADR-330-archive-tag-not-fork.md)), [ADR-072](ADR-072-admin-triggered-period-close.md) / [ADR-075](ADR-075-one-active-retainer-per-customer.md) (Phase 17 retainers, superseded), [ADR-315](ADR-315-win-proposal-conversion-reuse.md) (Phase 80 CRM, superseded), [ADR-182](ADR-182-terminology-override-mechanism.md) / [ADR-185](ADR-185-terminology-switching-approach.md) (the map that holds "Mandate" until Phase 87), [ADR-334](ADR-334-automation-engine-without-builder.md), [ADR-331](ADR-331-migration-squash-at-zero-tenants.md).
