# ADR-349: `project_id` in Audit Details Is a Publisher Obligation for Every Matter-Scoped Event; `findByProjectId` Gains an Entity-Branch Backfill; One Formatter with Legal Templates, Home Delegates

**Status**: Accepted

**Context**:

The matter Activity feed, Home "recent activity" and the portal Firm-actions trail all correlate on one key: `AuditEventRepository.findByProjectId:56-77` matches **only** `details->>'project_id'` (expression index `idx_audit_project`, `V1__baseline.sql:2026`; [ADR-035](ADR-035-activity-feed-direct-audit-query.md)). Publishers that know their matter but never write the key: `acceptance.*` (`AcceptanceService.java:213-246,377-389`), `trust_deposit/payment/transfer.recorded` and `trust_<type>.approved/rejected` (`TrustTransactionService.java:258-268,502-512,896-905` — even though `TrustTransaction.project_id` exists), `invoice.created/approved` (`InvoiceCreationService.java:222-236`, `InvoiceTransitionService.java:165-180`). Hence the estate matter's feed omits the R 50 000 deposit, the R 12 000 payment and the engagement-letter lifecycle (G-B8, G-D9). Rows that do reach the feed hit the raw fallback `"%s performed %s on %s"` (`ActivityMessageFormatter.java:153`) for ~40 reachable keys (R3 §2 table), and Home uses a second, cruder formatter `DashboardService.buildActivityDescription:920-938` ("performed invoice.sent", G-D10). G-D8: `matter_closure.closed` details lack the override `justification` (D3). Contrast `findMostRecentByProject:93-101`, which already ORs the `entity_type='project' AND entity_id` branch. ADR-342 pins the stored `entity_type` literals; wording may change only in rendering.

**Options Considered**:

1. **Publisher obligation: every matter-scoped publisher writes `project_id` (and `customer_id` where known) into details; `findByProjectId` additionally ORs `entity_type = 'project' AND entity_id = :projectId`; one formatter with legal templates; Home delegates (CHOSEN).**
   - Pros:
     - Keeps the ADR-035 design (direct query, one indexed key) and fixes the omissions at the source for every future row.
     - The entity branch backfills the matter's own history (`project.*`) without a data migration.
     - One sentence per key in one place.
     - The portal trail and Home get the fix for free.
   - Cons:
     - Six publisher sites change.
     - Historical `acceptance.*` / `trust_*` / `invoice.created` rows stay unreachable (they have neither key nor the project entity).
     - A convention needs a guard (see Consequences) or it drifts again.
2. **Query-side joins per entity type (acceptance → generated_document → document → project; trust_transaction.project_id; invoice_lines.project_id).**
   - Pros:
     - Backfills all history, including rows written before this phase.
   - Cons:
     - Turns the feed into a per-entity UNION with four joins, abandoning the expression-index design.
     - The portal trail and Home would need the same unions.
     - Every new entity adds a branch. Query cost on the audit table is unbounded.
3. **A correlation table (`audit_event_projects(event_id, project_id)`) written by the audit service.**
   - Pros:
     - Clean many-to-many.
     - Indexable.
   - Cons:
     - A migration, a second write per event, a backfill job — and the audit service cannot know the matter unless the publisher tells it, which is option 1 with an extra table.

**Decision**: Option 1 — the six publisher families write `project_id` (string, as existing publishers do) and `customer_id` where known; `findByProjectId` gains the `entity_type = 'project' AND entity_id = :projectId` branch; `ActivityMessageFormatter` gains templates for every reachable key in R3 §2 with legal wording and a legal verb/noun generic default; `DashboardService` delegates to the formatter; `matter_closure.closed` details gain `justification` (empty string when no override).

**Rationale**:

1. **The feed's contract is "publisher says which matter."** ADR-035 chose a direct query on `details->>'project_id'` precisely so the feed has no joins; publishers that already know the matter (`request.matterId()`, `transaction.getProjectId()`, invoice lines) must simply say so — the comments "load-bearing" in `ProjectService` and `MatterClosureService:262-266` show the convention exists and was missed, not rejected.
2. **The entity branch is cheap and bounded.** It is selective (`entity_type = 'project' AND entity_id = :id`), mirrors `findMostRecentByProject`, and only adds `project.*` rows (10 emit sites); the slice confirms the plan with `EXPLAIN` on the walked tenant before merge, as the requirements ask.
3. **Two formatters are the G-D10 cause.** Home's "performed invoice.sent" is not a wording bug but a second implementation; delegation removes the class.
4. **ADR-342 is respected**: stored keys and `entity_type` literals do not change; the legal nouns ("fee note", "matter", "trust deposit") live in the formatter and the UI label layer ([ADR-357](ADR-357-terminology-guard-extension.md)).
5. **Justification on the closed event** is firm-visible only — `PortalActivityEventResponse` never sends details — so D3's "copy it" leaks nothing to the portal.

**Consequences**:

- Positive:
  - The estate matter's feed shows the deposit, the payment, engagement-letter sent/viewed/accepted (actor Sipho, PORTAL), fee note created/approved/sent — no "performed x on y" (G-B8, G-D9, G-D10, G-D8).
  - U23's `acceptance.declined` and [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md)'s payment events are born with the key.
  - A convention guard: a new `MatterFeedIntegrationTest` holds the feed test through `AcceptanceService` and `TrustTransactionService`, and a source-scan assertion (sibling of `EntityTypeLiteralStabilityTest`) lists the publisher classes that must put `project_id` — a new matter-scoped publisher without it fails the suite.
- Negative:
  - `ActivityMessageFormatterTest:250-255` (`unknownEventTypeProducesFallbackMessage`) is rewritten to the legal generic wording.
  - `doesNotContain("performed")` cases extend.
  - Historical `acceptance.*`, `trust_*` and `invoice.created/approved` rows on the walked tenant remain outside the feed.
  - The re-walk creates fresh ones. No data migration is attempted (audit rows are immutable by design).
  - Trust, invoice and acceptance publisher packages are re-run (`Map.of` details are rarely asserted exactly).
  - `PortalActivityControllerIntegrationTest` and `PortalActivityServiceFirmFilterTest` re-run because the portal trail shares the key.
  - The N-6 hypothesis (portal whitelist `trust_transaction.approved` vs emitted `trust_<type>.approved`) is reproduced in this unit, not assumed.
- Related:
  - [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md), [ADR-035](ADR-035-activity-feed-direct-audit-query.md), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (acceptance lifecycle events), `tasks/phase88-research/R3-activity-feed-audit.md` §1–2, `tasks/phase88-gap-register.md` D3.
