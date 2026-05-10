# Audit and Compliance

**Status:** filled (Phase D part 1).

## 1. What this concern covers

The audit-log emission pattern that threads through every state-mutating service in the codebase, plus the data-protection (DSAR / PAIA) and retention / anonymisation flows that lean on it. The single load-bearing fact: **audit emission is the only secondary effect in Kazi that is NOT event-bus driven** (`30-modules/audit.md:7`; `_discovery/A6-cross-cutting.md:338`). Every other after-effect — email, integration push, read-model projection, portal notifications — uses `@TransactionalEventListener(AFTER_COMMIT)`. Audit alone runs synchronously inside the source transaction, by deliberate design.

Companion pages: [`30-modules/audit.md`](../30-modules/audit.md) (the module itself), [`20-cross-cutting/data-protection.md`](data-protection.md) (deeper DSAR / PAIA mechanics), [`30-modules/customer-lifecycle.md`](../30-modules/customer-lifecycle.md) (anonymisation seam, `customer-lifecycle.md:120`).

## 2. Why audit is non-bus

Every other secondary effect uses `@TransactionalEventListener(AFTER_COMMIT)` because emitting on rollback would lie: an email cannot be un-sent, an integration push cannot be retracted, a read-model row would diverge from durable state. The catalogue is in `_discovery/A6-cross-cutting.md:327-339` — `NotificationService.java:50` (~16 events), `PortalDocumentNotificationHandler.java:114`, four handlers in `PortalEmailNotificationChannel.java` (`:115, :152, :182, :209`). They all wait for commit precisely so a rollback erases the intent.

Audit inverts this. The audit row must share the source change's atomicity: if the change rolls back, the audit row must roll back too — otherwise the audit log records actions that never durably happened. To get that property, audit is called **directly, synchronously, inside the source transaction** — not via a listener. ADR-029 documents the four supporting reasons (flaky session under `TenantFilterTransactionManager`, listeners can't see field-level diffs, selective per-loop-iteration logging, non-entity events live in filters). The takeaway for module authors: never reach for `@TransactionalEventListener` to emit audit (`30-modules/audit.md:144`).

## 3. The emission pattern

Every mutating service injects `AuditService` and calls it directly within the transaction (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java:16`).

Shape:

1. `AuditService` constructor-injected into the service.
2. `AuditEventBuilder` fluent API constructs the row (`AuditEventBuilder.java:32`): `eventType()` (`:208`), `entityType()` (`:213`), `entityId()` (`:218`), `actorId()` (`:223`), `actorType()` (`:229`), `source()` (`:235`), `details()` (`:241`). Actor / source / IP / user-agent are auto-populated from `RequestScopes.MEMBER_ID` and `RequestContextHolder` (per ADR-029 §rationale; `30-modules/audit.md:73`).
3. `AuditDeltaBuilder` produces `{ field: { from, to } }` JSONB shape for PATCH-style diffs (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditDeltaBuilder.java:24`; `track(...)` `:33`, `trackAsString(...)` `:48`). This is the canonical input for the generic diff viewer (ADR-260).
4. **No listener.** No `@EventListener`, no `@TransactionalEventListener`, no `ApplicationEventPublisher.publishEvent(...)`. Synchronous call only.

Severity is **not** passed in — it is derived later (see §5).

## 4. Storage

| Layer | Mechanism | Anchor |
|---|---|---|
| JPA | `@Immutable` annotation — Hibernate skips dirty-check, never issues UPDATE. | `AuditEvent.java:27` |
| Postgres | `prevent_audit_update()` trigger function; trigger `audit_events_no_update`. | `db/migration/tenant/V12__create_audit_events.sql:32, :47` |
| Postgres | Companion `prevent_audit_delete()` trigger (added later). | `db/migration/tenant/V74__prevent_audit_delete.sql:4` |

Two-layer immutability is the explicit ADR-028 choice (Option 2 — application + DB trigger). Even raw SQL outside Hibernate cannot mutate audit rows. Hash chaining (ADR-028 Option 4) is documented as a future extension; not built. The `audit_events` table lives in each tenant's schema (ADR-025) — there is no global cross-tenant audit table.

## 5. Severity derivation (read-time)

`AuditSeverity` (`INFO | NOTICE | WARNING | CRITICAL`) and `AuditEventGroup` (`SECURITY | COMPLIANCE | FINANCIAL | DATA | STANDARD`) are **not persisted columns**. They are computed on every read by `AuditEventTypeRegistry.resolve(eventType)` (ADR-261; `30-modules/audit.md:28`, `:77`).

Why read-time:

- A severity-policy change requires no migration, no backfill, no call-site edit.
- The classification of historical rows automatically updates when the registry changes.
- Adding a new auditable event type requires touching the registry once, not every audit-producing service.

The trade-off: severity-filtered queries do an `IN (...)` / `LIKE ANY (...)` predicate over the (~30-entry) registry on every list call. ADR-261 argues this remains microseconds; if the registry grows past ~100 entries it may need a tiered resolver (open question, §10).

## 6. DSAR / PAIA

Cross-link to [`20-cross-cutting/data-protection.md`](data-protection.md) and `30-modules/customer-lifecycle.md` § Compliance/data-protection sub-surface (`customer-lifecycle.md:64-74`). Anchored summary:

- `datarequest/DataRequestController.java:27` — `/api/data-requests` CRUD + status transitions + export staging + execute-deletion + deadline check.
- `datarequest/PaiaManualGenerationService.java` — generates PAIA (Promotion of Access to Information Act, ZA-only) manuals.
- `datarequest/DataAnonymizationService.java` — anonymisation-over-deletion sweep (ADR-062).
- `datarequest/DataExportService.java` — DSAR export bundling, must stage **before** anonymisation (ADR-196 per `customer-lifecycle.md:146`).
- `datarequest/JurisdictionDefaults.java` — per-jurisdiction retention defaults; ZA fully implemented, others fall through to a generic set.
- `AnonymizationController.java:16` — `POST /api/customers/{id}/anonymize`, `GET /api/customers/{id}/anonymize/preview`.

DSAR audit-trail handling: per ADR-262, the audit slice of a DSAR pack is **unsanitised** — full `details`, IP addresses, user agents, and justifications ship intact (POPIA §23 is the determining constraint). Same `audit_events` data, two channels (live portal vs DSAR pack), two intentional sanitisation postures (`30-modules/audit.md:87`).

## 7. Retention

Two retention clocks, both anchored to entity terminal states (ADR-249):

| Clock | Anchor | Source |
|---|---|---|
| Customer | `Customer.offboardedAt` (set on `OFFBOARDING → OFFBOARDED`). | `Customer.java:78`, `:196`; `customer-lifecycle.md:121` |
| Project | `Project.retentionClockStartedAt` (set on `complete()` `:259` and `close()` `:303`; preserved on reopen `:311`). | ADR-249 |

The clocks are independent — closing every project on a customer does not auto-offboard the customer (deliberate; `customer-lifecycle.md:164`).

Driver: `DormancyScheduledJob` runs daily at 02:00 (`backend/.../compliance/DormancyScheduledJob.java:38`), iterating tenants via `TenantScopedRunner.forEachTenant` (the canonical scheduler pattern, A6 §7). It transitions idle customers `ACTIVE → DORMANT`. The downstream anonymisation pipeline transitions `OFFBOARDED → ANONYMIZED` when retention has elapsed, replacing PII fields with placeholders while retaining structural rows for audit linkage (ADR-062, ADR-193).

**No purge of `audit_events` itself.** ADR-027 makes retention a config (`AuditRetentionProperties` — domain events 1095 days, security events 365 days), but `purge-enabled: false` is the default and the scheduled purge job is **not yet implemented** (`30-modules/audit.md:85`, `:136`). Open question — see §10.

## 8. Modules affected

Every module that mutates state emits audit. The canonical list per `30-modules/audit.md:144`: `customer-lifecycle`, `projects`, `tasks`, `time-entry`, `expenses`, `documents-templates`, `invoicing`, `retainers`, `trust-accounting`, `proposals-acceptance`, `information-requests`, `automation`, `identity-access`, `settings-navigation`, `platform-administration`. Each of those module pages should explicitly state: "audit emission is synchronous, in-transaction, direct-call (not via the event bus)" and link back to [`30-modules/audit.md`](../30-modules/audit.md).

Heavy lean-in flows (per `30-modules/audit.md:127-132`):

- Customer-lifecycle transitions (PROSPECT → ACTIVE → DORMANT → ANONYMIZED) — every transition writes an audit row (`customer-lifecycle.md:115`).
- Trust-accounting dual-approval steps — every approval emits a row used by the legal compliance UI.
- Invoicing state transitions (DRAFT → APPROVED → SENT → PAID/VOID) and the trust-boundary export refusal — refusal emits `integration.xero.push_blocked_trust` (ADR-276; `architecture/phase71-xero-accounting-integration.md:296`).
- Proposals/acceptance lifecycle — ~7 events.
- DSAR fulfilment — every status transition audited; the export action itself is audited (ADR-264).

## 9. Active ADRs

The 200-series cluster is the canonical reading. All Active per `90-adr-index.md:199-216`:

| ADR | Title |
|---|---|
| ADR-025 | audit-storage-location (per-tenant table) |
| ADR-027 | audit-retention-strategy (configurable; purge job deferred) |
| ADR-028 | audit-integrity-approach (`@Immutable` + Postgres trigger; hash-chain deferred) |
| ADR-029 | audit-logging-abstraction (explicit `AuditService.log` over AOP/listeners) |
| ADR-062 | anonymization-over-hard-deletion |
| ADR-249 | retention-clock-starts-on-closure |
| ADR-259 | audit-ui-read-only-no-write-changes |
| ADR-260 | audit-generic-diff-over-event-templates-v1 (canonical audit shape) |
| ADR-261 | audit-severity-derived-read-time |
| ADR-262 | dsar-audit-trail-unsanitised |
| ADR-263 | audit-pdf-via-tiptap-pipeline |
| ADR-264 | audit-export-is-auditable |

ADR-026 (audit-event-granularity) is functionally retired by ADR-260's generic-diff move (`90-adr-index.md:462`) but no `Status: Superseded` marker has been applied.

## 10. Known fragilities / open questions

- **No audit-events purge / compaction.** `purge-enabled: false` is the default (ADR-027); the scheduled purge job has not been implemented. For long-running tenants this means unbounded growth of `audit_events` — index degradation, Neon storage cost, and filter-scan latency are all open empirical questions (`30-modules/audit.md:136`).

- **TRUNCATE could bypass `@Immutable` + triggers.** `prevent_audit_update` and `prevent_audit_delete` are row-level (`BEFORE UPDATE` / `BEFORE DELETE`). Postgres `TRUNCATE` does not fire row-level triggers — it fires `BEFORE TRUNCATE` if defined, which is **not** defined in `V12` or `V74`. A DB-level actor with `TRUNCATE` privilege on the tenant schema could wipe `audit_events` without the triggers firing. **Verify**: read `V12__create_audit_events.sql` and `V74__prevent_audit_delete.sql` to confirm the absence, then either add a `BEFORE TRUNCATE` trigger or document as accepted residual risk (`30-modules/audit.md:138`).

- **Severity-resolver does not scale linearly.** The `AuditEventTypeRegistry` is currently ~30 prefix entries (ADR-261 §rationale). Each new domain phase adds a few. If the registry grows past ~100 entries the prefix-resolver may need a tiered redesign — currently flagged as a watching brief, not a remediation (`30-modules/audit.md:140`).

- **PAIA jurisdiction handling is ZA-only.** `JurisdictionDefaults.java` has ZA fully implemented; non-ZA tenants fall through to a generic default set (`customer-lifecycle.md:131`). The expansion model (adding GDPR / HIPAA / etc.) is untested — what changes besides the defaults map? Likely the manual-generation template, status transition deadlines (ADR-195/197), and possibly the unsanitised-export posture (ADR-262 turns on POPIA §23 reasoning specifically).

- **Anonymisation is one-way.** Per ADR-062, anonymisation replaces PII with placeholders while retaining structural rows for audit linkage — there is no "undo". If a customer returns post-anonymisation, the operator must create a new customer; the historical row is preserved for audit linkage but is not re-hydratable. The pre-anonymisation export bundle (ADR-196) is the only path to re-create the record state from durable storage. This is deliberate but worth surfacing to operators (`30-modules/audit.md:144`; `customer-lifecycle.md:157`).

- **Reflexive audit-export recursion is theoretically infinite.** ADR-264 mandates that a successful CSV/PDF export emits `audit.export.generated` — so the next export captures the previous export's audit row, and so on. Termination is by usage cadence, not by code. Not a correctness issue but a noise floor on long-running tenants with frequent scheduled exports (`30-modules/audit.md:142`).
