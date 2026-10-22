# ADR-348: FICA Status Source of Truth — Onboarding Checklist Instances First, Information-Request Projection as Fallback, No Persisted Column

**Status**: Accepted

**Context**:

Two FICA mechanisms coexist. `FicaStatusProjector.java:46-86` (extracted in 614A from `InformationRequestService.getFicaStatus`, GAP-L-46) derives `NOT_STARTED / IN_PROGRESS / DONE` **only** from `information_requests` whose template belongs to the `fica-onboarding-pack` request pack — the client-portal self-service variant that nothing in the 90-day walk creates (R2 §2 Q4). The product's live legal-za onboarding path is the **checklist**: Start Onboarding instantiates `legal-za-individual-onboarding` (9 items / 8 required) or `legal-za-trust-onboarding` (12 / 11) via `ChecklistInstantiationService`, and `ChecklistInstanceService.checkLifecycleAdvance:522-550` promotes the client to ACTIVE when every instance is COMPLETED — i.e. the product already treats "all onboarding checklists complete" as the FICA-done event, while every display reads the other store. Consumers of the projector: Home "FICA outstanding" (`DashboardService.getFicaOutstanding:417-455`), the Clients table column, `ficaStatus` filter and "FICA outstanding" system view (`CustomerService:308-362`), the matter `FicaStatusCard.tsx:84` ("No FICA onboarding request yet."), the KPI dashboard. `FicaStatusProjectorTest` has 17 unit cases; `ClientListRow` is capped at 10 statements. FICA status is purely derived — no column exists (`.arch-context.md` §10.8). Gaps: G-D11 c, G-D21 FICA, G-B10.

**Options Considered**:

1. **Checklist-first: the projector takes the client's onboarding checklist instances as a second input; any COMPLETED → DONE with `completed_at`, any IN_PROGRESS → IN_PROGRESS, none → fall through to the information-request rule (CHOSEN).**
   - Pros:
     - The display reads the same store the lifecycle hook reads.
     - The projector stays pure and its 17 cases keep passing because the fallback is untouched.
     - A client whose portal FICA pack is DONE but who has no checklist still reads DONE.
     - Batched via the existing `ChecklistInstanceRepository.findByCustomerIdIn` + one template-id lookup.
   - Cons:
     - Two more queries in `getFicaStatuses` (3 to 5, still batched).
     - The `ClientListRow` statement count must be re-verified against its cap.
     - A selector for "which checklist templates are onboarding" must be chosen (see Rationale).
2. **Lifecycle shortcut: `lifecycle_status = ACTIVE ⇒ DONE`.**
   - Pros:
     - Zero new queries.
     - The status is already on the row.
   - Cons:
     - Wrong for clients activated manually without a checklist (`CustomerLifecycleService.java:126-134` does not require completion), for OFFBOARDED/REACTIVATED clients, and for a client ACTIVE with an open portal FICA pack.
     - Loses `completed_at` and the deep link.
3. **Write-through: on checklist completion, create and complete a synthetic FICA information request so the projector sees it.**
   - Pros:
     - No projector change.
   - Cons:
     - Fabricates portal records the client never submitted, duplicates the checklist, and would email the client through the information-request notification path.
     - A POPIA audit trail with invented submissions. Rejected.
4. **Persisted `customers.fica_status` column maintained by both mechanisms.**
   - Pros:
     - One cheap read.
     - Filterable with an index.
   - Cons:
     - A migration plus two writers that must agree, exactly the dual-mechanism drift this phase is removing.
     - The arch-context notes FICA is deliberately derived, and no consumer today needs a column for performance.

**Decision**: Option 1 — `FicaStatusProjector` reads the client's checklist instances for templates with `auto_instantiate = true` (which covers both legal-za packs and any firm-authored onboarding template) first, ignoring cancelled instances; a COMPLETED instance yields DONE with `completed_at` and `checklistInstanceId`; an IN_PROGRESS instance yields IN_PROGRESS with `checklistInstanceId`; otherwise the existing information-request rule applies unchanged; `FicaStatusResponse` gains `checklistInstanceId` (nullable) beside `requestId`. An IN_PROGRESS onboarding instance outranks a COMPLETED one (an open obligation wins; consistent with `checkLifecycleAdvance`, which requires every instance COMPLETED), and the checklist branch outranks the information-request branch entirely.

**Rationale**:

1. **Follow the lifecycle hook.** The product already decides "FICA done" in `checkLifecycleAdvance` from checklist instances; making the display derive from the same rows is the root fix, and it is what the scenario (Day 2 nine items, Day 8 twelve) observes.
2. **Selector decision (beyond D-list):** `auto_instantiate` rather than the two pack ids. The pack ids are correct today but would silently exclude a firm's own onboarding template or a future legal-za pack; `auto_instantiate` is the attribute that *makes* a template an onboarding template (it is what Start Onboarding instantiates). A second decision: when both a completed checklist and an open portal FICA pack exist, the checklist wins (DONE) — the firm-driven verification is the authoritative act; the open portal pack remains visible on the client's Requests tab.
3. **Derived, not stored,** keeps the schema-per-tenant migration budget for V4's real columns and avoids a second writer; the batched read fits the statement caps set in 615A/615B.
4. **Fallback preserved** so the 17 projector cases, `CustomerFicaStatusIntegrationTest` and `FicaOutstandingIntegrationTest` information-request fixtures stay green and the portal self-service path keeps working for firms that use it.

**Consequences**:

- Positive:
  - After Day 2's checklist completes, the matter FICA card, Clients table, "FICA outstanding" view and Home all read complete / 0 outstanding (G-D11 c, G-D21 FICA, G-B10).
  - `FicaStatusCard` can deep-link to the checklist instance.
  - Copy becomes "No FICA onboarding started." when neither store has anything.
- Negative:
  - `FicaStatusProjectorTest` gains checklist-wins, fallback, cancelled-instance-ignored and batch/single-parity cases.
  - `FicaOutstandingIntegrationTest`, `CustomerFicaStatusIntegrationTest` and `ClientListRowIntegrationTest` gain checklist-created fixtures via `TestChecklistHelper.completeChecklistItems` (the "real service path" rule in the requirements).
  - `ClientListRowIntegrationTest`'s 10-statement cap is re-counted after the change.
  - If the extra lookup breaks it, the template-id set is cached per request, not the cap raised.
  - `ChecklistTemplateRepository` gains `findIdsByAutoInstantiateTrue` (the ADR records the selector; the repository method name matches the architecture document §13.8.4).
- Related:
  - [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md) (the sibling R2 cluster), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) (`checklist.*` audit literals unchanged), `tasks/phase88-research/R2-debtor-rule-fica-status.md` §3 B1, `tasks/phase88-gap-register.md` U4.
