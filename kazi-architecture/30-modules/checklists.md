# Checklists

**Bounded context:** see [`10-bounded-contexts.md` § checklists](../10-bounded-contexts.md#checklists).

## Purpose

Per-customer (and potentially per-project) structured compliance / onboarding checklists. The module owns two layers: admin-managed `ChecklistTemplate`s (with their `ChecklistTemplateItem` rows) and per-customer `ChecklistInstance`s (with `ChecklistInstanceItem` rows). Templates are either tenant-authored or pack-seeded (compliance packs); instances are auto-created on customer creation when a template's `customerType` matches the new customer (`ANY` is a wildcard, but the most-specific-match rule short-circuits the wildcard when a typed template exists). Sibling to [`customer-lifecycle.md`](customer-lifecycle.md), which references this module from its onboarding flow.

`→ kazi-architecture/10-bounded-contexts.md:303` — bounded-context entry
`→ _discovery/A1-backend-map.md:301` — backend `checklist` package map
`→ _discovery/A6-cross-cutting.md:224` — pack SPI cross-cutting note
`→ glossary.md:70` — Checklist Instance / Template glossary entries

## Entities owned

- `ChecklistTemplate` — admin-managed template; `name`, `slug`, `customerType` (`INDIVIDUAL | COMPANY | TRUST | ANY`), `source`, `packId`, `packTemplateKey`, `active`, `autoInstantiate`, `sortOrder`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplate.java:16`
- `ChecklistTemplateItem` — item under a template; carries `sortOrder`, `required`, `requiresDocument` flags. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateItem.java:33`
- `ChecklistInstance` — per-customer running instance; `templateId`, `customerId`, `status` (`IN_PROGRESS | COMPLETED | CANCELLED`), `startedAt`, `completedAt`, `completedBy`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstance.java:14`
- `ChecklistInstanceItem` — instance-level item snapshotted from its template item, plus `status`, `notes`, `documentId`, `dependsOnItemId`, KYC verification fields (`verificationProvider`, `verificationStatus`, `verifiedAt`, `verificationMetadata`). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java:19`

Item status lives as a string column (`status`) on `ChecklistInstanceItem` rather than a JPA enum; the literal vocabulary (`PENDING`, `COMPLETED`, `SKIPPED`) is enforced by service code (`ChecklistInstanceService.completeItem`/`skipItem`/`reopenItem`). The glossary's "ChecklistItemStatus" name is conceptual — there is no enum class with that exact name in the backend (verified by grep). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java:49` (column), `:197/:264/:299` (transitions in `ChecklistInstanceService`).

## REST surface

### `ChecklistTemplateController` — `/api/checklist-templates`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplateController.java:24`

| Method | Path | Purpose | Capability |
|---|---|---|---|
| GET | `/api/checklist-templates` | List active templates (optional `?customerType=` filter) `:32` | (read) |
| GET | `/api/checklist-templates/{id}` | Get one template `:39` | (read) |
| POST | `/api/checklist-templates` | Create custom template `:44` | `CUSTOMER_MANAGEMENT` |
| PUT | `/api/checklist-templates/{id}` | Update template `:53` | `CUSTOMER_MANAGEMENT` |
| DELETE | `/api/checklist-templates/{id}` | Deactivate template (soft) `:60` | `CUSTOMER_MANAGEMENT` |
| POST | `/api/checklist-templates/{id}/clone` | Clone a template `:67` | `CUSTOMER_MANAGEMENT` |

### `ChecklistInstanceController` — mixed bases (`/api/customers/{id}/checklists`, `/api/checklist-instances`, `/api/checklist-items`)

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceController.java:23`

| Method | Path | Purpose | Capability |
|---|---|---|---|
| GET | `/api/customers/{customerId}/checklists` | List instances + items for one customer `:31` | (read) |
| GET | `/api/checklist-instances/{id}` | Get instance + items `:38` | (read) |
| POST | `/api/customers/{customerId}/checklists` | Manually instantiate from a template `:44` | `CUSTOMER_MANAGEMENT` |
| PUT | `/api/checklist-items/{id}/complete` | Mark item complete (notes + optional document) `:54` | `CUSTOMER_MANAGEMENT` |
| PUT | `/api/checklist-items/{id}/skip` | Skip an item with reason `:64` | `CUSTOMER_MANAGEMENT` |
| PUT | `/api/checklist-items/{id}/reopen` | Reopen a completed/skipped item `:73` | `CUSTOMER_MANAGEMENT` |

A1's "~8 endpoints" count refers to the combined surface of both controllers; it lands at 6 + 6 = 12 once template-list/get and the customer-scoped instance-list are counted, but A1's grouping bucketed "instance endpoints" only. Anchor: `_discovery/A1-backend-map.md:412`.

## Frontend pages / components

- `/{slug}/settings/checklists` — admin landing for templates. `→ frontend/app/(app)/org/[slug]/settings/checklists/page.tsx`
- `/{slug}/settings/checklists/new` — create template wizard. `→ frontend/app/(app)/org/[slug]/settings/checklists/new/page.tsx`
- `/{slug}/settings/checklists/[id]` and `/edit` — template detail + editor (item rows). `→ frontend/app/(app)/org/[slug]/settings/checklists/[id]/page.tsx` ; `.../[id]/edit/page.tsx`
- Server actions / queries for the settings flow: `actions.ts`, `queries.ts` in the same directory.
- Customer detail page renders an instance tab (`/customers/{id}` shows checklists alongside profile, documents, KYC). `→ _discovery/A2-frontend-map.md:123` ; `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- Frontend types: `ChecklistTemplateResponse`, `ChecklistInstanceResponse`, `ChecklistItemStatus`. `→ frontend/lib/types/customer.ts:140` (per A2 map at `_discovery/A2-frontend-map.md:335`).

## Domain events

The `checklist` package does **not** publish Spring `ApplicationEvent`s of its own (verified — `grep -n "publishEvent\|ApplicationEvent\|DomainEvent"` against `backend/.../checklist/` returns no matches). State changes are recorded by direct `AuditService.log(...)` calls inside `ChecklistInstanceService` (5 call sites at `:143`, `:247`, `:281`, `:331`, `:464`) rather than via an event bus. The customer-lifecycle module reads checklist progress through `CustomerReadiness` via service-call composition — there is no checklist-completion event today.

> **Open question:** an explicit `ChecklistInstanceCompletedEvent` (or similar) would let `customer-lifecycle` and `automation` react to "all required items done" without polling readiness. See [`30-modules/domain-events.md`](domain-events.md) for the event-publication conventions used elsewhere; adopting them here is the natural follow-on if FICA/AML automation grows.

## Cross-cutting touchpoints

- **Capability gates.** All write endpoints sit behind `@RequiresCapability("CUSTOMER_MANAGEMENT")` — see controller anchors above. Read endpoints are member-level. Cross-link: [`30-modules/identity-access.md`](identity-access.md).
- **Audit.** Every item state change writes an audit row via `AuditService` (anchors above). The audit log is authoritative for "who completed item X, when". Cross-link: [`30-modules/audit.md`](audit.md).
- **Auto-instantiation on customer creation.** Driven by `ChecklistInstantiationService.instantiateForCustomer(Customer)` — it queries `findByActiveAndAutoInstantiateAndCustomerTypeIn(true, true, [customerType, "ANY"])`, applies a most-specific-match rule (typed template wins; `ANY` is a fallback only when no typed template matches), and creates one instance per matching template idempotently (skips if `existsByCustomerIdAndTemplateId`). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationService.java:35`. The `cancelActiveInstances` companion is invoked on offboarding (`:83`).
- **Pack-seeded templates (compliance packs).** Templates with `source != USER` and a non-null `packId` were materialised by a `PackInstaller` for a `COMPLIANCE` pack; the template-edit setters `setPackId`, `setPackTemplateKey`, `setSortOrder` exist for the seeder's use (`ChecklistTemplate.java:169-179`). Cross-link: [`30-modules/packs.md`](packs.md). The customer-lifecycle module summarises the matching rule at `30-modules/customer-lifecycle.md:118` and `:129`.
- **KYC verification fields on instance items.** `verificationProvider`, `verificationReference`, `verificationStatus`, `verifiedAt`, `verificationMetadata` (jsonb) are populated by the KYC adapter via `KycVerificationService`, which looks items up by ID. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationService.java:84`. Cross-link: [`30-modules/integration-ports.md`](integration-ports.md).
- **Reverse-lookup orphans.** Switching a tenant's vertical does not remove instantiated FICA/AML instances — see the "Reconciliation seeder is one-way" note at `30-modules/customer-lifecycle.md:168`.

## Vertical specifics

The checklist *engine* is vertical-neutral; the *content* (which packs ship which templates, with which items, for which `customerType`) is vertical-specific:

- **legal-za** ships FICA onboarding packs with typed variants (`legal-za-individual-onboarding`, `legal-za-trust-onboarding`) which fully replaced the legacy `ANY` pack (`legal-za-client-onboarding`) per PR #996; the most-specific-match rule in `ChecklistInstantiationService:45-58` enforces that intent.
- **accounting-za** ships AML packs along the same shape.
- **consulting-generic** has minimal/no compliance pack content by default.

Detail of pack contents per vertical lives in [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md), [`60-verticals/legal-za.md`](../60-verticals/legal-za.md), and [`60-verticals/accounting-za.md`](../60-verticals/accounting-za.md). Do not enumerate items here.

## Active ADRs

- **ADR-061** — checklist-first-class-entities. Active. The `ChecklistTemplate`+`ChecklistInstance` split is canonical; checklists are not a `Task` extension. Reinforced by ADR-134. `→ adr/ADR-061-checklist-first-class-entities.md` ; `_discovery/A4-adr-triage.md:70`.
- **ADR-134** — dedicated-entity-vs-checklist-extension. Active. Confirms checklists stay first-class for engagement/task contexts that might otherwise extend them. `→ adr/ADR-134-dedicated-entity-vs-checklist-extension.md` ; `_discovery/A4-adr-triage.md:143`.
- **ADR-063** — compliance-packs-bundled-seed-data. **Superseded-by ADR-240** (per A4 triage). Historical: original "bundle compliance content as Java seeders" approach. `→ adr/ADR-063-compliance-packs-bundled-seed-data.md` ; `_discovery/A4-adr-triage.md:72` ; `:415`.
- **ADR-240** — unified-pack-catalog-install-pipeline. Active; canonical. Compliance packs now ship through the same `PackCatalogService`/`PackInstaller` SPI as field/document/automation packs; `ChecklistTemplate.packId`/`packTemplateKey` are the back-references. `→ adr/ADR-240-unified-pack-catalog-install-pipeline.md` ; `_discovery/A4-adr-triage.md:249` ; `:413`.

See [`90-adr-index.md`](../90-adr-index.md) for the consolidated ADR list.

## Key flows

- **Customer onboarding + FICA/KYC.** Customer creation → `ChecklistInstantiationService.instantiateForCustomer` → per-template `ChecklistInstance` rows → portal-driven item completion → `CustomerReadiness` reflects progress → lifecycle transition guards consume that signal. Pointer: [`50-flows/customer-onboarding-and-kyc.md`](../50-flows/customer-onboarding-and-kyc.md).
- **Pack install / vertical onboarding.** When a tenant's vertical is set, the pack pipeline materialises `ChecklistTemplate` rows tagged with `packId`. Pointer: [`50-flows/pack-install-and-vertical-onboarding.md`](../50-flows/pack-install-and-vertical-onboarding.md).
- **Customer offboarding.** `ChecklistInstantiationService.cancelActiveInstances` flips `IN_PROGRESS → CANCELLED` (`:83`). Pointer: [`50-flows/customer-onboarding-and-kyc.md`](../50-flows/customer-onboarding-and-kyc.md) (offboarding tail).

## Open questions / known fragility

1. **Template versioning / snapshot semantics.** When a pack-seeded `ChecklistTemplate` is updated (new item added, item removed, copy edited) by a later pack release, what happens to existing `ChecklistInstance`s that already snapshotted the old `ChecklistTemplateItem` rows into `ChecklistInstanceItem` rows? `ChecklistInstanceItem` already snapshots `name`, `description`, `sortOrder`, `required`, `requiresDocument` and links back via `templateItemId` (`ChecklistInstanceItem.java:28-47`) — so existing in-flight instances see the *old* copy. There is no migration path defined for "the FICA pack changed, propagate item N to all running instances." This is a deliberate snapshot model but the policy is not written down.
2. **Instance archival when a customer is offboarded.** `cancelActiveInstances` flips status to `CANCELLED` but the instance and its items remain in the schema indefinitely. There is no retention/purge sweep, and no tombstone for "instance belongs to an anonymised customer." This will become visible during DSAR/anonymisation (see `customer-lifecycle.md`) — items may carry `notes` containing personal data that need redaction.
3. **No explicit completion event.** As noted under *Domain events* above, downstream reactors must poll `CustomerReadiness` rather than subscribe to a `ChecklistInstanceCompletedEvent`. Surfacing one would simplify automation triggers.
4. **`ANY` wildcard semantics are documented in code comments only.** The most-specific-match rule (`ChecklistInstantiationService:45-58`) is enforced by code + a long comment block, not by a schema constraint or ADR. A future "universal" pack (e.g., sanctions screening) is explicitly steered toward typed variants by that comment, but nothing prevents a pack author from re-introducing an `ANY` template that overlaps with typed ones.
5. **Project-scoped checklists are out of scope today.** Discovery mentions checklists are "also used by projects" (`10-bounded-contexts.md:64`) but `ChecklistInstance.customerId` is `nullable = false` (`ChecklistInstance.java:23`). There is no `projectId` column. If/when project-onboarding checklists are added, this entity needs a polymorphic anchor (or a sibling `ProjectChecklistInstance`).
