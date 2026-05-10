# Audit

**Bounded context:** see [`10-bounded-contexts.md` § audit](../10-bounded-contexts.md). One-liner there: "Append-only audit event log; field-level diff utility; query API; data-protection (DSAR/PAIA/anonymization)" (`10-bounded-contexts.md:217`).

## 1. Purpose

Append-only, in-transaction audit trail. Every state-mutating service in the codebase calls `auditService.log(...)` directly, **inside the source transaction** — there is no event-listener fan-out, no `@TransactionalEventListener`, no async queue. This is the only mutating concern in Kazi that is **not** event-bus driven (`_discovery/A6-cross-cutting.md:338` — "Audit emission is the one exception. Audit events are written *in* the same transaction as the change they describe…the 'audit cannot lie about a rolled-back operation' property is achieved by sharing the transaction.").

Why this design wins (ADR-029):
- An aspect/listener fires on a Hibernate session that may not be the one that did the work — flaky under the custom `TenantFilterTransactionManager` (ADR-029 §rationale 1).
- An aspect cannot see *which fields* changed — `AuditDeltaBuilder` needs both old and new values from the service's local scope (ADR-029 §rationale 2).
- Selective logging matters: `MemberSyncService.syncMembers()` does many writes per loop iteration; only some are auditable. Aspects cannot make that choice (ADR-029 §rationale 3).
- Non-entity events (login failure, access denied) live in filters/handlers, not service methods — explicit calls work there too (ADR-029 §rationale 4).

The architectural insight: **audit is the only mutating concern that is NOT event-bus driven**. Module pages elsewhere should reference this fact when they describe their own audit emission.

## 2. Entities owned

| Entity | Table | Notable | Anchor |
|---|---|---|---|
| `AuditEvent` | `audit_events` | `@Immutable` JPA + Postgres `BEFORE UPDATE` and `BEFORE DELETE` triggers. Append-only at two layers. | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java:29` |

**Two-layer immutability (belt + braces).** ADR-028 chose Option 2: application-level immutability AND database trigger. Concretely:
- JPA: `@Immutable` annotation on the entity tells Hibernate to skip dirty-check and never issue `UPDATE` (`AuditEvent.java:27`; the comment on `:22` reads "tells Hibernate to skip dirty-checking and never issue UPDATEs, which is…").
- Postgres: `prevent_audit_update()` trigger function in `V12__create_audit_events.sql:32`, registered as `audit_events_no_update` on `:47`. Companion `prevent_audit_delete()` trigger added later in `V74__prevent_audit_delete.sql:4` (per `_discovery/A6-cross-cutting.md:144-146`).
- Even raw SQL outside Hibernate cannot mutate the rows. ADR-028 frames the trigger as defending against application bugs and accidental modification — a determined DB superuser could disable it, but that is out of scope for the threat model. Hash chaining (ADR-028 Option 4) is documented as a future extension; not built.

Severity (`AuditSeverity`) and event grouping (`AuditEventGroup`) are **not** persisted columns. They are derived at read time from `AuditEventTypeRegistry` (ADR-261). The entity row carries `eventType` (string), `entityType`, `entityId`, `actorId`, `actorType`, `source`, `ipAddress`, `details (jsonb)` (`_discovery/A1-backend-map.md:198`).

## 3. REST surface

All endpoints under `/api/audit-events`. Capability gate: `TEAM_OVERSIGHT` (admin-only — see ADR-259 context, `AuditEventController.java:21`). Controller is `@RestController` at `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java:21`.

Core read pair (the list + get-by-anchor that the audit UI is built around):

| Method | Path | Anchor | Purpose |
|---|---|---|---|
| `GET` | `/api/audit-events` | `AuditEventController.java:45` | List with filters (date range, actor, event type, entity type, severity, group). Returns `AuditEventResponse` rows enriched at read time with label/severity/group/actor display name. |
| `GET` | `/api/audit-events/{entityType}/{entityId}` | `AuditEventController.java:99` | Per-entity history — the trail for a single project/customer/invoice etc. |

Adjacent endpoints (anchored on the same controller, all gated by `TEAM_OVERSIGHT`):

| Method | Path | Anchor | Purpose |
|---|---|---|---|
| `GET` | `/api/audit-events/metadata` | `:93` | Returns the `AuditEventTypeRegistry` catalogue (label/severity/group per event-type prefix) — frontend fetches once per session to render pills (ADR-261 §consequences). |
| `GET` | `/api/audit-events/export.csv` | `:65` | Streaming CSV export. Unbounded. Emits `audit.export.generated` reflexively (ADR-264). |
| `GET` | `/api/audit-events/export.pdf` | `:79` | PDF export, capped at 10 000 rows (ADR-263 §12.3.2 reference). Renders through the existing Tiptap → PDF pipeline (ADR-263). |
| `GET` | `/api/audit-events/facets/{actors,event-types,entity-types}` | `:117, :125, :133` | Facet feeders for filter UI (driven by `ActorFacetProjection`, `EventTypeFacetProjection`, `EntityTypeFacetProjection` in the package). |

`InternalAuditController` exists for cross-tenant operator queries (per ADR-025 §consequences — "internal endpoints that iterate over schemas, same pattern as `TenantMigrationRunner`"). Not part of the tenant-facing surface.

## 4. Frontend pages / components

| Route | File | Notes |
|---|---|---|
| `/settings/audit-log` | `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` | Admin-only audit list. Driven by the filter facets above. |
| `audit-log-client.tsx`, `actions.ts` | same dir | Client list + server actions for export. |

No portal route — audit log is a firm-side admin surface only (per ADR-259 §context: "There is no admin UI; the existing AuditEventController is gated by `TEAM_OVERSIGHT`").

Terminology: legal-za renames "Audit Log" to "Audit Trail" (`frontend/lib/terminology-map.ts:91`, per `glossary.md:48`).

## 5. Domain events

**None.**

This absence is meaningful. Audit does not publish `DomainEvent`s, does not subscribe to `DomainEvent`s, and is not on the bus. It is called directly by every other service, in-transaction. See `10-bounded-contexts.md:219` ("Depends on: Nothing") and `_discovery/A6-cross-cutting.md:338` for the explicit "audit emission is the one exception" framing.

## 6. Cross-cutting touchpoints

This module is **the** cross-cutting concern of the codebase. Every other module's mutating service calls into it. Specifics:

- **Direct synchronous emission.** Every audited service has `AuditService` injected via constructor and calls `auditService.log(AuditEventBuilder.builder()...build())` at the end of each mutating method, inside the source transaction. The fluent builder lives at `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java:32` (final class; `eventType()` `:208`, `entityType()` `:213`, `entityId()` `:218`, `actorId()` `:223`, `actorType()` `:229`, `source()` `:235`, `details()` `:241`). The builder auto-populates actor / source / IP / user-agent from `RequestScopes.MEMBER_ID` and `RequestContextHolder` (ADR-029 §rationale).

- **Field-level diffs via `AuditDeltaBuilder`.** Most `details` JSONB blobs are `{ field: { from, to } }` shapes produced by `AuditDeltaBuilder` (`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditDeltaBuilder.java:24`; `track(field, oldVal, newVal)` at `:33`, `trackAsString(...)` at `:48`). This is the canonical input shape for the generic diff viewer (ADR-260 §decision — "single `<AuditDetailsViewer>` component detects `AuditDeltaBuilder` shape and renders a diff").

- **Severity derived at read time.** `AuditSeverity` (`INFO | NOTICE | WARNING | CRITICAL`) and `AuditEventGroup` (`SECURITY | COMPLIANCE | FINANCIAL | DATA | STANDARD`) are computed by `AuditEventTypeRegistry.resolve(eventType)` on every read. No persisted column; no migration, no backfill. Policy lives in code (ADR-261 §decision). The classification of historical rows automatically updates when the registry changes.

- **PDF export via Tiptap pipeline.** The PDF export (`export.pdf` endpoint) renders through the existing Kazi Tiptap → PDF pipeline used by invoices, statements, proposals, engagement letters. Single PDF code path; no second library (ADR-263 §decision). 10 000-row cap is enforced server-side (ADR-263 §12.3.2 reference); CSV is unbounded.

- **Audit export is itself audited (ADR-264).** A successful CSV or PDF export emits an `audit.export.generated` event with `details.{filter, rowCount, format}`, written *after* the export completes so failed exports do not pollute the log. This is the **only** event that audits its own export — the recursion terminates because the second export's audit row is captured by the third export, and so on. Compliance posture: an auditor can answer "did anyone extract the firm's audit data?" from the firm's own audit log.

- **Tenant-scoped, per-tenant table.** `audit_events` lives in each tenant's schema (ADR-025 §decision). DDL is a tenant migration (`V12__create_audit_events.sql`). No global cross-tenant table. Cross-tenant operator queries iterate via `TenantScopedRunner.forEachTenant` (the same pattern as every other scheduled job, A6 §1).

- **Retention.** Configured via Spring properties (`AuditRetentionProperties`) — domain events default 1095 days (3 years), security events 365 days (ADR-027 §decision). The scheduled purge job is **not yet implemented** (ADR-027 §rationale point 1: "A newly-deployed system has no data to purge"). `purge-enabled: false` in the property defaults. This is a known deferred item — see Open Questions.

- **DSAR / PAIA exports.** The audit slice of a DSAR pack is **unsanitised** (ADR-262 §decision) — full `details`, IP addresses, user agents, justifications shipped intact. POPIA §23 is the determining constraint, not portal aesthetics. Same `audit_events` data; two channels (live portal vs DSAR pack); two sanitisation postures intentionally. Implementation: `AuditService` exposes a customer-streaming method (`AuditService.java:150` per the file's javadoc). DSAR pack assembly lives in `datarequest/DataExportService` — see `30-modules/customer-lifecycle.md` and `_discovery/A6-cross-cutting.md:160-167`.

## 7. Vertical specifics

None at the data-model layer — audit is a core platform concern. Every vertical writes to the same `audit_events` table with the same shape.

Two thin vertical seams:
- **Terminology.** Legal-za UI renders "Audit Log" as "Audit Trail" (`glossary.md:48`).
- **DSAR jurisdiction.** The PAIA pathway (ZA-only) bundles the unsanitised audit trail into the disclosure pack (ADR-262); non-ZA jurisdictions get the same flow under whatever generic regime applies (`datarequest/JurisdictionDefaults.java`).

Trust accounting writes audit rows like any other module — the trust-specific logic (dual approval, Section 86, hard guard) lives in trust-accounting; audit is the substrate that records its actions.

## 8. Active ADRs

The 200-series cluster is the canonical audit reading. Verified Active in `90-adr-index.md:199-216`:

| ADR | Title | Status |
|---|---|---|
| ADR-025 | audit-storage-location (per-tenant table) | Accepted |
| ADR-027 | audit-retention-strategy (configurable, purge job deferred) | Accepted |
| ADR-028 | audit-integrity-approach (`@Immutable` + Postgres trigger; hash-chain deferred) | Accepted |
| ADR-029 | audit-logging-abstraction (explicit `AuditService.log` over AOP/listeners) | Accepted |
| ADR-062 | anonymization-over-hard-deletion | Accepted |
| ADR-249 | retention-clock-starts-on-closure | Accepted |
| ADR-259 | audit-ui-read-only-no-write-changes | Accepted |
| ADR-260 | audit-generic-diff-over-event-templates-v1 (canonical audit shape) | Accepted |
| ADR-261 | audit-severity-derived-read-time | Accepted |
| ADR-262 | dsar-audit-trail-unsanitised | Accepted |
| ADR-263 | audit-pdf-via-tiptap-pipeline | Accepted |
| ADR-264 | audit-export-is-auditable | Accepted |

ADR-026 (audit-event-granularity) is functionally retired by ADR-260's generic-diff move — `90-adr-index.md:462` notes ADR-260+ "reshaped audit from per-event templates (ADR-026) to generic diff", but no explicit `Status: Superseded` marker has been applied.

## 9. Key flows

Audit is touched by **every** flow page in `50-flows/`. Rather than enumerating, the rule is: any flow that includes a state-changing service call has an `auditService.log(...)` step. Flow pages should:
- Show the audit emission point in the sequence (typically the last in-transaction step before commit).
- Reference back to this module for the canonical pattern.
- Note when a flow uses a non-`AuditDeltaBuilder` shape (e.g. matter-closure overrides carrying `details.justification`, audit-export events carrying `details.{filter, rowCount, format}`) — those are the events ADR-260's generic viewer renders as JSON-tree fallback rather than diff.

Notable flows that lean on audit heavily:
- `customer-lifecycle` transitions (PROSPECT → ACTIVE → DORMANT → ANONYMIZED) — every transition writes an audit row.
- `trust-accounting` approvals — every dual-approval step emits a row used by the legal compliance UI.
- `invoicing` state changes (DRAFT → APPROVED → SENT → PAID/VOID) and the trust-boundary export refusal (ADR-276) — refusal emits `integration.xero.push_blocked_trust`.
- `proposals-acceptance` lifecycle — ~7 events, each with audit.
- DSAR fulfilment (`datarequest/`) — every status transition audited; the export action itself audited per ADR-264.

## 10. Open questions / known fragility

- **Retention purge job is not implemented.** `purge-enabled: false` is the default (ADR-027 §decision; properties file referenced via `AuditRetentionProperties`). For long-running tenants this means unbounded growth of `audit_events`. ADR-027 acknowledged this and deferred the job; no slice has yet picked it up. At what tenant-age does this become a real problem? Index degradation, Neon storage cost, query latency under heavy filter scans — all need empirical re-checking once a tenant has 1+ years of data.

- **TRUNCATE bypass on the immutability triggers.** The Postgres triggers `prevent_audit_update` and `prevent_audit_delete` block `UPDATE` and `DELETE` row-by-row. Postgres `TRUNCATE` does not fire row-level triggers (it fires `BEFORE TRUNCATE` if defined, which is *not* defined in `V12` or `V74`). A DB-level actor with `TRUNCATE` privilege on the tenant schema can wipe `audit_events` without the triggers firing. **Verify**: read `V12__create_audit_events.sql` and `V74__prevent_audit_delete.sql` to confirm no `BEFORE TRUNCATE` clause is present, and either add one or document the gap as accepted residual risk.

- **Severity-derivation complexity if event types proliferate.** The registry is currently ~30 prefix entries (ADR-260 §decision; ADR-261 §rationale — "30-entry registry"). Phase 69 codified this. As event types grow (every new domain phase adds a few), the prefix-resolver has more buckets to walk, and severity-filtered queries do an `IN (...)` / `LIKE ANY (...)` predicate over a longer list. ADR-261 argues this remains microseconds, but if the registry grows past ~100 entries, the pre-flight pattern in §12.3.5 may need a tiered-resolver redesign.

- **Reflexive export recursion is theoretically infinite-but-practically-terminal.** ADR-264 §decision notes the recursion ("the second export's event is captured by the third…") terminates by usage cadence, not by code. If a tenant runs scheduled exports faster than they read them, the audit log accretes a self-reference chain. Not a correctness issue but a noise floor to be aware of.

- **The "audit is non-bus" insight is foundational.** Every module page that emits audit (which is most of them — `customer-lifecycle`, `projects`, `tasks`, `time-entry`, `expenses`, `documents-templates`, `invoicing`, `retainers`, `trust-accounting`, `proposals-acceptance`, `information-requests`, `automation`, `identity-access`, `settings-navigation`, `platform-administration`) should explicitly note that audit emission is **synchronous, in-transaction, direct-call** — not via the event bus — and link back to this module. If a future engineer reaches for `@TransactionalEventListener` or `@EventListener` to emit audit, ADR-029 § rationale points 1 + 4 are the sources to read first.
