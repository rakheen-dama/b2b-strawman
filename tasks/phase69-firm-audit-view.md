# Phase 69 — Firm Audit View (Admin Surface)

> Architecture doc: `architecture/phase69-firm-audit-view.md`
> ADRs: [ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md), [ADR-260](../adr/ADR-260-audit-generic-diff-over-event-templates-v1.md), [ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md), [ADR-262](../adr/ADR-262-dsar-audit-trail-unsanitised.md), [ADR-263](../adr/ADR-263-audit-pdf-via-tiptap-pipeline.md), [ADR-264](../adr/ADR-264-audit-export-is-auditable.md)
> Starting epic: 501 · Last completed: 500 (Phase 68 QA capstone)
> Migration high-water at phase start: tenant **V119** (`V119__add_portal_new_proposal_and_proposal_expired_reference_types.sql`); global portal read-model **V22** (`V22__portal_notification_preference.sql`). **Phase 69 ships ZERO migrations** — severity is derived at read time from an in-code registry, the registry itself is a Java record-based catalogue, and DSAR integration extends an existing service. ADR-261 codifies the no-migration constraint.

Phase 69 ships a read-only admin surface over the existing `audit_events` infrastructure. The data has been written immutably since Phase 6; nobody has been able to read it without a `psql` session. This phase closes that gap with a global filterable log page, a reusable per-entity timeline component on seven sensitive detail pages, compliance-grade CSV + PDF export, integration with the Phase 50 DSAR pack, and a small dashboard widget for recent CRITICAL/WARNING events.

Three constraints govern the entire phase: (1) no new write paths — any missing event coverage discovered along the way goes into the gap report, not into Phase 69 (ADR-259); (2) no migrations — severity, group, and label are derived at read time from an in-code `AuditEventTypeRegistry` (ADR-261); (3) PDF export rides the existing Tiptap → PDF pipeline rather than introducing a new rendering library (ADR-263). The capability gate is the existing `TEAM_OVERSIGHT` — no new capability is introduced.

The portal-side activity trail ("when did my firm view my files?") is **explicitly out of scope** — founder call 2026-04-20.

The existing global audit log page at `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` is a placeholder shell with paginated rows only — Phase 69 replaces its body with the full filter/preset/expansion/export experience.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 501 | Audit Metadata Registry + Severity / Group Foundation | Backend | -- | M | 501A | **Done** (PR #1273) |
| 502 | Audit Facets API + Severity-Filtered List | Backend | 501 | M | 502A, 502B | **Done** (PRs #1274, #1275) |
| 503 | Audit Export — CSV Streaming + Reflexive Audit | Backend | 501, 502A | M | 503A | **Done** (PR #1276) |
| 504 | Audit Export — PDF via Tiptap Pipeline | Backend | 501, 502A, 503A | M | 504A | **Done** (PR #1277) |
| 505 | DSAR Audit-Trail Folder Integration | Backend | 501 | M | 505A | **Done** (PR #1278) |
| 506 | Global Audit Log Page — Shell, Filters, Row Expansion | Frontend | 502B | L | 506A, 506B | |
| 507 | `<AuditTimeline>` Component + 3 Detail Page Tabs (Customer / Project / Invoice) | Frontend | 502B, 506A | M | 507A | |
| 508 | `<AuditTimeline>` — Remaining 4 Detail Page Tabs (TrustTx / MatterClosure / Proposal / InfoRequest) | Frontend | 507A | M | 508A | |
| 509 | Sensitive-Events Dashboard Widget | Frontend | 502B, 506B | S | 509A | |
| 510 | Admin-POV 30-Day QA Capstone + Screenshots + Gap Report | E2E / Process | 501–509 | L | 510A, 510B | |

Slice count: **12 slices across 10 epics**. Backend and frontend are always split into separate slices. Severity-foundation work in 501 blocks every read-time enrichment surface (502, 503, 504, 506, 507, 508, 509). Backend export and DSAR work parallelises after 502A. Frontend pages all wait on 502B (the API surface they consume). Epic 509 is the cuttable epic if scope tightens — the audit log page (506) and per-entity timeline (507/508) carry the phase.

---

## Dependency Graph

```
PHASES already complete:
  Phase 6  (audit_events table + AuditEvent + AuditService + AuditEventBuilder + AuditDeltaBuilder)
  Phase 12 (document templates)
  Phase 31 (templates and partials)
  Phase 41 (roles + capabilities + TEAM_OVERSIGHT + <CapabilityGate>)
  Phase 42 (PDF engine cutover — Tiptap → PDF pipeline)
  Phase 43 (i18n message catalogue + <EmptyState>)
  Phase 44 (frontend information architecture — settings hub layout)
  Phase 50 (DataExportService + DSAR ZIP pack)
  Phase 67 (matter-closure override emits override_used + justification into details JSONB)
                                 │
                                 ▼
                  ┌──────────────────────────────────┐
                  │ [E501A  AuditSeverity / Group enums      │
                  │        + AuditEventTypeMetadata record   │
                  │        + AuditEventTypeRegistry          │
                  │        + AuditEventMetadataResolver      │
                  │        + /metadata endpoint              │
                  │        + actor display resolver          │
                  │        + unit + integration tests]       │
                  └──────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┬──────────────────┐
                │                │                │                  │
        [E502A AuditEventFilter  [E503A CSV        [E504A PDF         [E505A DSAR
         severities + facets     export +          export via         buildAuditTrail
         service + repo prefix   reflexive audit   Tiptap +           + findEventsForCustomer
         predicate + tests]      + tests]          10k cap + tests]   + ZIP streaming + tests]
                │                                                    
        [E502B Controller wiring of facets,                            
         severities filter, /metadata,                                
         /facets/* routes + integration tests]                        
                │
                ├────────────────┬────────────────┬─────────────────┐
                │                │                │                 │
         [E506A Page shell,  [E506B Presets +  [E509A Sensitive    │
          filters,           export download    events widget +    │
          paginated rows,    UX (CSV+PDF)       deep-link to       │
          row expansion,     + Playwright       audit page +       │
          shared             smoke + tests]     capability gate]   │
          components         │                  │                  │
          (SeverityPill,     │                  │                  │
          AuditDetails       │                  │                  │
          Viewer,            │                  │                  │
          ActorDisplay,      │                  │                  │
          entity deep-link)] │                  │                  │
                │            │                  │                  │
                └────┬───────┘                  │                  │
                     │                          │                  │
              [E507A AuditTimeline component +  │                  │
               Customer / Project / Invoice     │                  │
               detail-page tabs + tests]        │                  │
                     │                          │                  │
              [E508A TrustTransaction /         │                  │
               MatterClosure / Proposal /       │                  │
               InformationRequest tabs +        │                  │
               Playwright smoke for closure]    │                  │
                     │                          │                  │
                     └──────────┬───────────────┘                  │
                                │                                  │
                                └──────────────┬───────────────────┘
                                               │
                                  ┌────────────┴────────────┐
                                  │                         │
                          [E510A 30-day admin POV      [E510B Run + screenshot
                           Keycloak script draft +      baselines + gap
                           seed scenarios +             report]
                           Playwright scaffold]
```

**Parallel opportunities:**
- After **501A** merges, **502A**, **503A**, **504A**, **505A** can run in parallel — distinct sub-domains within the audit package, no contention.
- **502B** must follow 502A because controller wiring depends on the service-layer methods. Most frontend work is gated on 502B (the frontend consumes the controller surface, not the service).
- **506A** and **506B** sequence within Epic 506 — shell + filters first, then presets + export + Playwright. **509A** can run in parallel with 506B because it depends only on 502B's `severities` filter and the deep-link URL format which is fixed in 506A.
- **507A** depends on **506A** for the shared primitives (`<SeverityPill>`, `<AuditDetailsViewer>`, `<ActorDisplay>`, `auditEventsClient`). **508A** depends on **507A** to keep tab-integration patterns consistent across all seven entities.
- **510A** scaffolds the admin lifecycle script while 506/507/508/509 run; **510B** blocks on everything green.

---

## Implementation Order

### Stage 1: Foundation (blocks everything)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 1a | 501 | 501A | `AuditSeverity` + `AuditEventGroup` enums; `AuditEventTypeMetadata` record; `AuditEventTypeRegistry` (in-code, longest-prefix-wins resolver); `AuditEventMetadataResolver`; `GET /api/audit-events/metadata` endpoint; actor display resolver in `DatabaseAuditService`; unit + integration tests. Pure code, no DB, no migration. **Done** (PR #1273) |

### Stage 2: Backend domains parallelise (after 501A)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 2a | 502 | 502A | `AuditEventFilter.severities` record expansion; severity pre-flight in `AuditService.findEvents` (registry → exact + prefix sets → DB filter); `FacetSnapshot` record + `AuditService.facets()`; repository extension with `findByFilterWithEventTypes` + facet projection queries. Service-layer + repo only. **Done** (PR #1274) |
| 2b | 502 | 502B | Controller wiring: `/facets/actors`, `/facets/event-types`, `/facets/entity-types` endpoints on `AuditEventController`; severity multi-value query param parsing on the existing `/api/audit-events`; integration tests covering all four routes + the capability gate. **Done** (PR #1275) |
| 2c (parallel after 502A) | 503 | 503A | `AuditCsvExporter` (`StreamingResponseBody`); `GET /api/audit-events/export.csv` endpoint; reflexive `audit.export.generated` emission; integration tests (CSV shape, streaming, reflexive event, capability gate). **Done** (PR #1276) |
| 2d (parallel after 503A) | 504 | 504A | `AuditPdfExporter` (chunked Tiptap pipeline binding); `audit-export` Tiptap template registration under existing template pack; `GET /api/audit-events/export.pdf` endpoint; 10 000-row pre-flight + 413 ProblemDetail; reflexive `audit.export.generated` emission; integration tests (golden hash, cap, capability gate). **Done** (PR #1277) |
| 2e (parallel after 501A) | 505 | 505A | `AuditService.findEventsForCustomer(UUID)` streaming method; `DataExportService.buildAuditTrail(customer, zip)` extension writing `audit-trail/events.json` + `events.csv` + `README.txt`; integration tests (folder presence, cross-customer isolation, no breakage of existing Phase 50 pack tests). **Done** (PR #1278) |

### Stage 3: Global audit log page (after 502B + relevant export endpoints)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 3a | 506 | 506A | `app/(app)/org/[slug]/settings/audit-log/page.tsx` rebuilt: filter UI (date range / severity / actor / event type / entity type), URL query string state, paginated rows, row expansion. Shared primitives created: `<SeverityPill>`, `<AuditDetailsViewer>` (diff + JSON tree), `<ActorDisplay>`, entity-cell deep-link helper. `frontend/lib/api/audit-events.ts` extended with the six new endpoints. Frontend tests for filter mapping + row expansion. |
| 3b (parallel) | 506 | 506B | Filter presets (Sensitive / Compliance / Security / Financial approvals); export dropdown wired to `/export.csv` + `/export.pdf`; PDF cap pre-check + disabled-with-tooltip; Playwright smoke spec. Depends on 506A; depends on 503A + 504A for the export endpoints. |
| 3c (parallel after 506A) | 507 | 507A | `frontend/components/audit/audit-timeline.tsx` reusable component reusing 506A primitives; "Audit" tab added to Customer / Project / Invoice detail pages; capability-gated; terminology key `audit.tab` (legal-za → "Audit Trail"). Frontend tests for render + expansion + empty state. |
| 3d | 508 | 508A | "Audit" tab dropped into TrustTransaction / Matter Closure / Proposal / Information Request detail pages reusing 507A's `<AuditTimeline>`. Playwright smoke for the matter-closure detail page asserting the override event with justification visible on expand. |
| 3e (parallel after 502B) | 509 | 509A | `<SensitiveEventsWidget>` on the firm admin dashboard: three count pills (NOTICE / WARNING / CRITICAL last 7 days) + top-5 list of CRITICAL+WARNING + "View all" deep link to Sensitive preset. Capability-gated. |

### Stage 4: QA capstone

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 4a | 510 | 510A | `qa/testplan/demos/admin-audit-30day-keycloak.md` drafted (8 checkpoints per requirements §6.1); seed-scenario fixtures for the lifecycle (login, matter creation, trust deposit, deadline seeding, retainer setup, permission denial, trust approval, closure override, DSAR); `/qa-cycle-kc` compatibility verified. |
| 4b | 510 | 510B | Full lifecycle run; curated screenshots to `documentation/screenshots/phase69/` (per requirements §6.2 — 12 shots); `tasks/phase69-gap-report.md` authored covering UX rough edges, missing event coverage, performance observations, Phase 70+ proposals. |

### Timeline

```
Stage 1: [501A]                                          <- foundation
Stage 2: [502A] -> [502B]
         [502A] // [503A] // [505A]                      <- backend domains parallel
                  [504A]    (after 503A wires reflexive)
Stage 3: [506A] -> [506B]
         [506A] -> [507A] -> [508A]
                   [509A]   (parallel with 507/508)
Stage 4: [510A] -> [510B]
```

---

## Parallel Tracks

- **Foundation track (501)** — 501A unblocks everything. The registry, resolver, severity enum, and actor display logic land together; no upstream dependency on any other Phase 69 work.
- **Backend fan-out (502 / 503 / 504 / 505)** — once 501A merges, the four backend epics run in parallel. 502 splits into a service slice (502A) and a controller slice (502B) so the controller-test surface area stays bounded. 503 (CSV) and 504 (PDF) share the reflexive-audit pattern but live in independent exporter classes; 504 sequences after 503A so the second exporter inherits the proven reflexive-emission shape.
- **Frontend fan-out (506 / 507 / 508 / 509)** — all wait on 502B. 506A creates the shared primitives that 507 consumes, so 507A sequences after 506A. 508A sequences after 507A to keep the seven entity-tab integrations using the exact same wiring shape. 509A is independent of 507/508 and can run in parallel with them.
- **QA capstone (510)** — 510A drafts the script while frontend epics finish; 510B requires every preceding epic merged + green.

A realistic day-by-day cadence: 501A days 1–3; 502A + 503A + 505A days 3–7 (parallel); 502B days 5–8; 504A days 7–10; 506A days 9–13; 506B + 507A + 509A days 12–16 (parallel); 508A days 15–18; 510A days 17–19; 510B days 19–22.

---

## Epic 501: Audit Metadata Registry + Severity / Group Foundation

**Goal**: Introduce the in-code metadata registry that drives every read-time enrichment in Phase 69. Define the `AuditSeverity` and `AuditEventGroup` enums; define the `AuditEventTypeMetadata` record; define the `AuditEventTypeRegistry` with longest-prefix-wins resolution; ship the `AuditEventMetadataResolver` service used by every downstream slice; expose the static catalogue at `GET /api/audit-events/metadata`; centralise actor display resolution (single LEFT JOIN to `members`, with soft-delete fallback) so all consuming slices share the same logic.

**References**: Requirements §1.3, §1.4; architecture §12.2.3, §12.3.3, §12.3.4; [ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md), [ADR-260](../adr/ADR-260-audit-generic-diff-over-event-templates-v1.md), [ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md).

**Dependencies**: None. The existing `AuditEvent`, `AuditEventRepository`, `AuditService`, `DatabaseAuditService`, and `AuditEventController` provide the substrate.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **501A** | 501.1–501.8 | 8 backend files (4 new types + 1 endpoint + 2 service modifications + 1 test class) | Pure code, no DB. `AuditSeverity` + `AuditEventGroup` enums; `AuditEventTypeMetadata` record; `AuditEventTypeRegistry` Spring `@Component` with longest-prefix-wins `resolve(eventType)`; `AuditEventMetadataResolver` for callers; centralised actor display logic in `DatabaseAuditService`; `GET /api/audit-events/metadata` controller endpoint returning the static catalogue; integration tests + unit tests covering registry resolution and actor fallback. **Done** (PR #1273) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 501.1 | Create `AuditSeverity` enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditSeverity.java` | covered by 501.7 | n/a (new) | `INFO`, `NOTICE`, `WARNING`, `CRITICAL` only — no `Display` annotations, no severity-numeric-rank, just the four constants. JavaDoc describing the meaning of each. ADR-261: severity is **derived at read time**, never persisted. |
| 501.2 | Create `AuditEventGroup` enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventGroup.java` | covered by 501.7 | 501.1 | `SECURITY`, `COMPLIANCE`, `FINANCIAL`, `DATA`, `STANDARD`. JavaDoc describing what kind of events each groups. |
| 501.3 | Create `AuditEventTypeMetadata` record | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeMetadata.java` | covered by 501.7 | existing audit-package records (e.g. `AuditEventRecord.java`) | Java record `(String eventType, String label, AuditSeverity severity, AuditEventGroup group)`. `eventType` is either an exact string (`security.permission.denied`) or a glob prefix terminating in `.*` (`security.*`). |
| 501.4 | Create `AuditEventTypeRegistry` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistry.java` | 501.7 | follow Spring `@Component` pattern in `audit/DatabaseAuditService.java` | `@Component`. Loads the static catalogue (verbatim from architecture §12.3.3 — 18 entries plus the default fallback) once at construction time into a `Map<String, AuditEventTypeMetadata>`. Public methods: `resolve(String eventType)` (longest-prefix-wins per architecture §12.3.3 pseudocode), `entries()` (returns the full unmodifiable list for `/metadata` endpoint and severity pre-flight in 502A), `entriesMatching(Set<AuditSeverity>)` (returns the subset of entries whose `severity` is in the set — used by 502A's pre-flight). The walk-down-prefix algorithm walks `s.substring(0, s.lastIndexOf('.'))` until no `.` remains; per-iteration check `registry.get(s + ".*")`. Default fallback synthesises a metadata with title-case label and `severity=INFO` `group=STANDARD` for unknown event types. |
| 501.5 | Create `AuditEventMetadataResolver` service | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventMetadataResolver.java` | 501.7 | `audit/DatabaseAuditService.java` (collaborator pattern) | `@Service`. Wraps `AuditEventTypeRegistry` and `AuditService.resolveActorDisplay`. Public methods: `enrich(AuditEvent event)` returns a record `EnrichedAuditEvent(AuditEvent event, AuditEventTypeMetadata metadata, String actorDisplayName)`. Used by every read-time consumer (facets, exports, list endpoint enrichment). Centralises the join between event + registry + actor so no caller re-implements the lookup. |
| 501.6 | Centralise actor display resolution in `DatabaseAuditService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` | 501.7 | `audit/DatabaseAuditService.java` existing query patterns | Add `Map<UUID, String> resolveActorDisplayNames(Collection<UUID> actorIds)` to `AuditService` interface and implement in `DatabaseAuditService` via a single LEFT JOIN to `members`. Fallback rules per architecture §12.3.4: live `Member` → `Member.name`; missing → `"Former member ({actorId})"`; non-USER `actorType` → static label per the table (`"Portal Contact"`, `"System"`, `"Automation"`, `"API Key"`). Tenancy via existing `search_path` (no `@Filter` quirks needed — `AuditEvent` is `@Immutable`). |
| 501.7 | Add `GET /api/audit-events/metadata` endpoint | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 501.8 | existing `AuditEventController.listAuditEvents` shape | One-liner delegate per Backend Controller Discipline. Returns `List<AuditEventTypeMetadata>` from `auditEventTypeRegistry.entries()`. Capability-gated `@RequiresCapability("TEAM_OVERSIGHT")`. No query params. ADR-260: this is the static catalogue; the `/facets/event-types` endpoint (502B) carries the date-ranged distribution — the two are not redundant. |
| 501.8 | Registry + actor + metadata-endpoint integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistryTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceActorDisplayIntegrationTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventMetadataEndpointIntegrationTest.java` | ~9 | `audit/AuditEventControllerIntegrationTest.java` (existing) for `*IntegrationTest` shape; `@Import(TestcontainersConfiguration.class)`, embedded Postgres | Registry test (~4 unit tests, no Spring context): exact-match (`matter.closure.override_used` → CRITICAL); longest-prefix wins (`matter.closure.something_else` → NOTICE via `matter.closure.*`); deep prefix beats shallow (`security.login.failure` → WARNING beats `security.*` NOTICE); default fallback for unknown (`foo.bar.baz` → INFO + Standard, label = "Foo Bar Baz"). Actor display test (~3 integration): live member → `Member.name`; soft-deleted → `"Former member ({uuid})"`; `actorType=SYSTEM` → `"System"`. Metadata endpoint test (~2): authed owner → 200 with full catalogue; non-owner missing TEAM_OVERSIGHT → 403 ProblemDetail. |

### Key Files

**Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditSeverity.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeMetadata.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventMetadataResolver.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceActorDisplayIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventMetadataEndpointIntegrationTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — interface gains `resolveActorDisplayNames(Collection<UUID>)`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — implement actor display resolution via LEFT JOIN
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` — add `/metadata` one-liner

**Read for context:**
- `backend/.../audit/AuditEvent.java` — entity shape
- `backend/.../audit/AuditEventFilter.java` — record to be extended in 502A
- `backend/.../audit/AuditEventRepository.java` — JPQL conventions
- `backend/.../orgrole/RequiresCapability.java` — capability annotation pattern

### Architecture Decisions

- **Registry is in-code, not table-backed** ([ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md)). Severity / group / label classifications evolve via code review, not DDL. Zero migration footprint at this phase. If a future phase needs per-tenant overrides, the registry can be promoted to a table at that time without breaking the resolver contract.
- **Longest-prefix-wins, not first-match** — registered prefixes form a forest; `matter.closure.override_used` (CRITICAL) is reachable even when `matter.closure.*` (NOTICE) is also registered, because the resolver walks down from most-specific to least-specific.
- **Severity / group / label are derived, never persisted on `AuditEvent`** — the row stays `@Immutable`, the trigger keeps rejecting UPDATEs, and historical rows reclassify automatically when the registry changes.
- **Actor display lives in the audit service**, not in callers. One LEFT JOIN per request batch, no N+1, soft-delete-tolerant fallback.

### Non-scope

- No new write paths ([ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md)).
- No `audit-export` template (504A) or facets (502).
- No frontend consumption (506+).
- No table migration. Builder explicitly does NOT touch `db/migration/tenant/` or `db/migration/global/` in this slice — high-water remains V119 / V22.

---

## Epic 502: Audit Facets API + Severity-Filtered List

**Goal**: Extend the existing list endpoint to honour a `severities` multi-value filter (resolved through the registry pre-flight pattern), and add three facet endpoints (`/facets/actors`, `/facets/event-types`, `/facets/entity-types`) so filter dropdowns can be populated. Both surfaces back onto the registry from 501A. The facet snapshot is a single transactional read for all three lists, not three separate calls.

**References**: Requirements §1.1, §1.5; architecture §12.3.1, §12.3.5; [ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md), [ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md).

**Dependencies**: 501A (registry, severity enum, actor display resolver).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **502A** | 502.1–502.5 | 7 backend files | `AuditEventFilter.severities` record expansion; severity pre-flight in `AuditService.findEvents` (registry → exact + prefix sets → DB filter); `FacetSnapshot` record; `AuditService.facets(from, to)` method; `AuditEventRepository.findByFilterWithEventTypes` + facet projection queries; service-layer integration tests. No controller changes — wiring lands in 502B. **Done** (PR #1274) |
| **502B** | 502.6–502.10 | 5 backend files | Controller wiring of three facet endpoints (`/facets/actors`, `/facets/event-types`, `/facets/entity-types`); severity multi-value parsing on `/api/audit-events`; controller integration tests covering all four routes + capability gate + edge cases (empty range, populated range, 500-row actor cap, severity prefix vs exact conflict). **Done** (PR #1275) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 502.1 | Extend `AuditEventFilter` record with `severities` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventFilter.java` | covered by 502.5 | existing record | Add `Set<AuditSeverity> severities` field. `null` or empty Set means "no severity filter". Update existing call-sites (the controller's existing `listAuditEvents` constructs a filter without severities — pass `null` until 502B wires the query param). |
| 502.2 | Create `FacetSnapshot` + facet records | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/FacetSnapshot.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/ActorFacet.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/EventTypeFacet.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/EntityTypeFacet.java` | covered by 502.5 | architecture §12.3.1 record shape | Records: `FacetSnapshot(List<ActorFacet> actors, List<EventTypeFacet> eventTypes, List<EntityTypeFacet> entityTypes)`; `ActorFacet(UUID actorId, String actorDisplayName, String actorType, long eventCount)`; `EventTypeFacet(String eventType, String label, AuditSeverity severity, AuditEventGroup group, long count)`; `EntityTypeFacet(String entityType, String label, long count)`. |
| 502.3 | Add `findByFilterWithEventTypes` + facet queries to `AuditEventRepository` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` | 502.5 | existing `AuditEventRepository.findByFilter` + Spring Data Specifications | (a) `Page<AuditEvent> findByFilterWithEventTypes(filter params, Set<String> exactTypes, Set<String> prefixPatterns, Pageable)` — JPQL or Specifications. Walks `eventType IN :exact OR eventType LIKE ANY(:prefix)` per architecture §12.3.5 — JPQL doesn't support `LIKE ANY` directly; the implementation builds the `OR LIKE ?` chain dynamically via `Specification` or `EntityManager.createQuery` with parameter binding. (b) `List<ActorFacetProjection> projectActorFacets(Instant from, Instant to)` (top 500 by count, LEFT JOIN to `Member`). (c) `List<EventTypeFacetProjection> projectEventTypeFacets(Instant from, Instant to)`. (d) `List<EntityTypeFacetProjection> projectEntityTypeFacets(Instant from, Instant to)`. Spring Data projection interfaces define the shapes. |
| 502.4 | Implement severity pre-flight + facets in `DatabaseAuditService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` | 502.5 | architecture §12.3.5 pseudocode + actor-display work from 501.6 | (a) Extend `findEvents` to honour `filter.severities()`: when non-empty, walk `auditEventTypeRegistry.entriesMatching(severities)`, split into exact strings vs prefix patterns (`*` → `%`), then re-run `resolve()` per candidate to handle prefix-vs-exact conflicts (e.g. `matter.closure.*` is NOTICE but `matter.closure.override_used` is CRITICAL — when filtering for NOTICE, the prefix is included but the override exact-string is excluded). Delegate to `findByFilterWithEventTypes`. (b) Add `FacetSnapshot facets(Instant from, Instant to)` method to `AuditService` interface; implement by calling the three projection queries from 502.3 and the registry to enrich `EventTypeFacet.{label, severity, group}`. Actor facet rows enrich `actorDisplayName` via the resolver from 501.6. Single transaction. |
| 502.5 | Service-layer integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceFacetsIntegrationTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceSeverityFilterIntegrationTest.java` | ~7 | `audit/AuditEventControllerIntegrationTest.java` for `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` shape | Facets test (~4): empty-range returns three empty lists; populated range returns expected counts including registry-enriched labels; actor facet caps at 500 (seed 600 distinct actorIds); event-type facet enriches with registry severity/group. Severity filter test (~3): `severities={CRITICAL}` returns only `matter.closure.override_used` rows (not `matter.closure.something_else`); `severities={NOTICE}` includes prefix-matched rows but **excludes** the override exact-string; `severities={INFO}` includes default-fallback rows that don't match any registered prefix. |
| 502.6 | Add facet controller endpoints | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 502.10 | existing `listAuditEvents` shape (one-liner delegate per Backend Controller Discipline) | Three new endpoints, one method each: `GET /api/audit-events/facets/actors`, `GET /api/audit-events/facets/event-types`, `GET /api/audit-events/facets/entity-types`, each accepting `from` + `to` query params. All three call `auditService.facets(from, to)` then return the relevant slice from the snapshot. `@RequiresCapability("TEAM_OVERSIGHT")` on each. Default `from` = `now() - 30d`, default `to` = `now()` (per filter ergonomics). |
| 502.7 | Add severity multi-value parsing to existing `listAuditEvents` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 502.10 | Spring `@RequestParam` Set parsing | Add `@RequestParam(required = false) Set<AuditSeverity> severities` to `listAuditEvents`. Spring auto-binds comma-separated query params to `Set<Enum>`. Pass through to `AuditEventFilter`. Backwards compatible — existing callers omit the param and get the current behaviour. |
| 502.8 | Enrich list-endpoint response with label / severity / group | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventResponse.java` (existing record), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 502.10 | existing `AuditEventResponse.from(AuditEvent)` factory | Extend `AuditEventResponse` with `String label`, `AuditSeverity severity`, `AuditEventGroup group`, `String actorDisplayName`. The controller's `events.map(AuditEventResponse::from)` becomes `events.map(e -> AuditEventResponse.from(e, metadataResolver.enrich(e)))` so every list response carries the registry-derived enrichment. |
| 502.9 | Update existing per-entity endpoint enrichment | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 502.10 | `listAuditEventsByEntity` existing shape | Apply the same `AuditEventResponse.from(e, enriched)` pattern so the per-entity timeline (consumed by 507A's `<AuditTimeline>`) also receives label/severity/group/actor display in its rows. No new endpoint — only the enrichment-on-read change. |
| 502.10 | Controller integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerFacetsIntegrationTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerSeverityFilterIntegrationTest.java` | ~7 | existing `audit/AuditEventControllerIntegrationTest.java` | Facets routes (~4): all three `/facets/*` endpoints return populated shapes; non-owner → 403 ProblemDetail on each; default range applied when `from`/`to` omitted; soft-deleted member surfaces in actor facet as "Former member" string. Severity filter (~3): `?severities=CRITICAL,WARNING` returns rows whose registry-resolved severity matches; `?severities=` (empty) returns all; non-owner → 403. |

### Key Files

**Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/FacetSnapshot.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/ActorFacet.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/EventTypeFacet.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/EntityTypeFacet.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceFacetsIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditServiceSeverityFilterIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerFacetsIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerSeverityFilterIntegrationTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventFilter.java` — add `Set<AuditSeverity> severities`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — add `facets(Instant, Instant)` method to interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — implement severity pre-flight + facets
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — `findByFilterWithEventTypes` + projection queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventResponse.java` — add label/severity/group/actorDisplayName fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` — add three facet routes + severities param + enrichment

**Read for context:**
- `backend/.../audit/AuditEventTypeRegistry.java` (501A) — registry contract for pre-flight
- `backend/.../audit/AuditEventMetadataResolver.java` (501A) — enrichment contract
- `backend/.../audit/AuditEvent.java` — `eventType` column shape
- `backend/.../member/MemberRepository.java` — member name resolution (already used by 501.6)

### Architecture Decisions

- **Severity pre-flight, not in-memory post-filter** ([ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md), architecture §12.3.5). The DB filters; pagination is honoured. The registry walk is once-per-request (registry is ~30 entries — milliseconds), not once-per-row.
- **Prefix-vs-exact conflict resolution runs per candidate** — if `matter.closure.*` is NOTICE and `matter.closure.override_used` is CRITICAL, a `severities={NOTICE}` request includes the prefix but excludes the exact override. The resolver re-runs `resolve()` on each candidate to confirm final classification.
- **One service method backs all three facets** (`facets(from, to)` returns `FacetSnapshot`). Three controller endpoints, one transactional read. Avoids fragmentation per requirements §1.1.
- **List + per-entity endpoints both enrich** — the per-entity endpoint feeds 507A's `<AuditTimeline>`, so enrichment must apply uniformly. No new per-entity endpoint; existing route gains response fields.

### Non-scope

- No write-path changes ([ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md)). Existing emitters stay exactly as-is.
- No frontend consumption (506+).
- No export endpoints (503 / 504).
- No DSAR integration (505).
- No migrations.

---

## Epic 503: Audit Export — CSV Streaming + Reflexive Audit

**Goal**: Implement the streaming CSV export endpoint that auditors / regulators / subpoena responders use to extract a date-ranged slice of audit events. The endpoint must stream — a 100 000-row CSV is a constant-memory operation. Every export emits a reflexive `audit.export.generated` event so an admin investigating "did anyone extract our log?" can answer it from the log.

**References**: Requirements §1.2, §1.5; architecture §12.3.2, §12.8.1; [ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md), [ADR-264](../adr/ADR-264-audit-export-is-auditable.md).

**Dependencies**: 501A (registry for label/severity columns), 502A (severities filter, actor display resolver).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **503A** | 503.1–503.5 | 6 backend files (1 exporter, 1 controller method, 1 service method, 1 export-folder package init, 2 test classes) | New `audit/export/` sub-package. `AuditCsvExporter` writes RFC 4180 CSV row-by-row via `StreamingResponseBody`. `GET /api/audit-events/export.csv` controller method. Reflexive `audit.export.generated` emission via existing `AuditEventBuilder`. Integration tests covering CSV header shape, row count vs filter, large-result streaming (no OOM on 50k rows), reflexive event emission, capability gate. **Done** (PR #1276) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 503.1 | Create `AuditCsvExporter` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditCsvExporter.java` | 503.5 | `audit/AuditEventBuilder.java` for package-style; new pattern for streaming export | `@Component`. Method `void writeCsv(AuditEventFilter filter, OutputStream out)` — opens a `BufferedWriter`, writes the header row (per architecture §12.3.2: `occurredAt, eventType, label, severity, entityType, entityId, actorId, actorDisplayName, actorType, source, ipAddress, userAgent, detailsJson`), then iterates `auditService.streamEvents(filter)` writing one CSV row at a time. RFC 4180 escaping: wrap fields containing commas/quotes/newlines in double quotes; escape internal quotes by doubling. `detailsJson` is `objectMapper.writeValueAsString(event.details())` (compact, no whitespace). Each row also resolves label/severity through `AuditEventMetadataResolver.enrich()` from 501A. **Streaming**: do NOT buffer the full result set — flush every N rows (e.g. 1024) to keep memory bounded. |
| 503.2 | Add `Stream<AuditEvent> streamEvents(AuditEventFilter)` to `AuditService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` | 503.5 | Spring Data `Stream<T>` query method patterns | Add `Stream<AuditEvent> streamEvents(AuditEventFilter filter)` to the interface. Implementation uses Spring Data's `@QueryHints({@QueryHint(name="org.hibernate.fetchSize", value="100")})` + `Stream<>` return type on the repository, scoped inside `@Transactional(readOnly=true)` so the cursor stays open. Caller is responsible for closing the stream (try-with-resources). The CSV exporter runs inside a transaction-aware execution context per Spring's `OpenEntityManagerInViewFilter` or by wrapping the streaming write in a `TransactionTemplate.execute`. |
| 503.3 | Add `GET /api/audit-events/export.csv` endpoint | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 503.5 | existing `listAuditEvents` query-param shape; Spring `StreamingResponseBody` for streaming response | One-liner-ish controller method (≤8 lines per Controller Discipline allowance for HTTP-shaping logic). Accepts the same filter params as `listAuditEvents` (incl. `severities`). Returns `ResponseEntity<StreamingResponseBody>` with `Content-Type: text/csv; charset=utf-8`, `Content-Disposition: attachment; filename="audit-events-{tenantSlug}-{fromDate}-{toDate}.csv"` (date format `YYYY-MM-DD`; tenant slug resolved via existing `RequestScopes.currentTenantSlug()` or equivalent). Body delegates to `auditCsvExporter.writeCsv(filter, outputStream)`. After the body completes, emit the reflexive event (503.4). `@RequiresCapability("TEAM_OVERSIGHT")`. |
| 503.4 | Reflexive `audit.export.generated` emission | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` (or a small helper `audit/export/AuditExportEventEmitter.java`) | 503.5 | existing `AuditEventBuilder` usage in audit emitters across the codebase | Synthesise an `AuditEvent` after the export streams: `eventType="audit.export.generated"`, `entityType="audit_export"`, `entityId=UUID.randomUUID()` (synthetic — exports have no persistent entity), `details` includes `filter` (as `Map`), `rowCount` (running counter from the exporter), `format="CSV"`. Emit via `auditService.log(...)`. **Important**: emit AFTER streaming completes so `rowCount` is accurate. Use a `LongAdder` shared between exporter and controller, or have the exporter return the final count. ADR-264 records the rationale. |
| 503.5 | CSV export integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditCsvExporterIntegrationTest.java`, `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditExportReflexiveAuditEventTest.java` | ~6 | `audit/AuditEventControllerIntegrationTest.java` for `@SpringBootTest` shape; `MockMvc` for HTTP assertions | CSV exporter test (~4): header row matches the spec verbatim; row count matches filtered event count on a seeded fixture; RFC 4180 escaping correct (event with comma in `userAgent` round-trips through CSV parsing); 50 000-row test fixture streams without `OutOfMemoryError` (run with `-Xmx256m` or assert via heap snapshot). Reflexive event test (~2): running an export emits exactly one `audit.export.generated` event with `details.format="CSV"`, `details.rowCount`, `details.filter`. Capability test rolls into 503.5 — non-owner → 403 ProblemDetail (no rows leaked). |

### Key Files

**Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditCsvExporter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditExportEventEmitter.java` (optional helper if controller stays tight)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditCsvExporterIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditExportReflexiveAuditEventTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — add `streamEvents(AuditEventFilter)`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — implement streamed query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — add `Stream<AuditEvent>` query method with fetch-size hint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` — add `/export.csv` route + reflexive emission

**Read for context:**
- `backend/.../audit/AuditEventBuilder.java` — fluent builder pattern for the reflexive event
- `backend/.../audit/AuditEventMetadataResolver.java` (501A) — enrichment for label/severity columns
- `backend/.../multitenancy/RequestScopes.java` — current tenant slug for filename

### Architecture Decisions

- **CSV streams; no row cap** ([ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md) — read-only constraint preserves existing semantics). RFC 4180 streaming is constant-memory; a 100k-row CSV is bounded by the buffer size, not the result set.
- **Reflexive emission** ([ADR-264](../adr/ADR-264-audit-export-is-auditable.md)). Every export writes its own audit event. Emission happens *after* streaming completes so `rowCount` is accurate.
- **`StreamingResponseBody`, not `ByteArrayOutputStream`** — buffering would defeat the streaming guarantee.
- **Filename format is fixed** — `audit-events-{tenantSlug}-{fromDate}-{toDate}.csv`. Date format `YYYY-MM-DD`. Auditors quote URLs against this filename pattern.

### Non-scope

- No PDF (504).
- No new write paths beyond the reflexive event (which uses the existing `AuditService.log` infrastructure — no new emitter, no new event source-of-truth).
- No filter-summary header in CSV (the PDF gets that; CSV is column-data only).

---

## Epic 504: Audit Export — PDF via Tiptap Pipeline

**Goal**: Ship the compliance-grade PDF export. Reuses the existing Tiptap → PDF pipeline (Phase 12 / 31 / 42) — no new rendering library. PDFs are the legal artefact; CSVs are for internal review. The PDF carries org branding, filter summary, generation timestamp, exporting actor, paginated table rows with severity badges, and a footer with page numbers + tenant hash. Capped at 10 000 rows; over-cap requests get a 413 ProblemDetail.

**References**: Requirements §1.2, §1.5; architecture §12.3.2, §12.8.1; [ADR-263](../adr/ADR-263-audit-pdf-via-tiptap-pipeline.md), [ADR-264](../adr/ADR-264-audit-export-is-auditable.md).

**Dependencies**: 501A (registry for label/severity badges), 502A (severities filter), 503A (reflexive emission pattern, controller-level export wiring conventions).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **504A** | 504.1–504.6 | 7 backend files (1 exporter, 1 Tiptap template JSON, 1 controller method, 1 cap-check + 413 problem, 1 reflexive emission, 1 integration test class) | `AuditPdfExporter` binds the existing Tiptap pipeline to a new `audit-export.tiptap.json` template under `backend/src/main/resources/templates/`. `GET /api/audit-events/export.pdf` controller method. 10k-row pre-flight via `auditService.countEvents(filter)` returning 413 ProblemDetail when over cap. Reflexive `audit.export.generated` emission with `details.format="PDF"`. Integration tests including golden-hash baseline. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 504.1 | Create `audit-export.tiptap.json` template | `backend/src/main/resources/templates/audit-export.tiptap.json` | 504.6 | existing Tiptap templates under `backend/src/main/resources/templates/` (e.g. `invoice-preview.html` is HTML; the actual Tiptap JSON template pack lives in DB seeds — confirm by reading `template/ComplianceTemplatePackSeeder.java` shape and the existing seeded templates). May instead need to seed via `template/DocumentTemplateService` registration in 504.2 | Tiptap document JSON: header (org logo placeholder via `{{ org.logoUrl }}`, org name, `Audit Log Export — {{ filter.dateRange }}`, generation timestamp `{{ generatedAt }}`, exporting actor `{{ exportedBy }}`, filter summary lines per non-default filter); body landscape table with columns `occurredAt`, `eventType` (rendered with `{{ row.label }}` and a coloured severity badge), `entityType`, `entityId`, `actorDisplayName`, `details` (compact JSON); footer `Page {{ pageNumber }} of {{ pageCount }} · {{ tenantHash }} · Generated by Kazi audit export`. Loop over `{{ rows }}` collection. |
| 504.2 | Create `AuditPdfExporter` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditPdfExporter.java` | 504.6 | `template/PdfRenderingService.java`, `template/TiptapRenderer.java`, `template/PdfConversionService.java` | `@Component`. Constructor injects `TiptapRenderer`, `PdfConversionService`, `PdfRenderingService`, `AuditEventMetadataResolver`. Method `void writePdf(AuditEventFilter filter, long rowCount, String exportedBy, OutputStream out)`: (a) build context Map (`org`, `filter` summary, `generatedAt`, `exportedBy`, `tenantHash`, `rows` — list of enriched row Maps), (b) load the `audit-export` Tiptap template via `DocumentTemplateService.findByName("audit-export")` or equivalent (verify name/registration mechanism — Tiptap templates are seeded into `document_templates`), (c) call `TiptapRenderer.renderToHtml(template, context)`, (d) call `PdfConversionService.htmlToPdf(html)` (chunked output streaming to `out`). Memory-bounded by the 10k row cap. Re-resolves label/severity per row via `AuditEventMetadataResolver`. |
| 504.3 | Add `long countEvents(AuditEventFilter)` to `AuditService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` | 504.6 | Spring Data count-query patterns | Pre-flight count for the 10k cap. Uses the same `findByFilterWithEventTypes` predicate from 502A but as a `count()` query. Repository gains `long countByFilterWithEventTypes(...)`. Returns total matching rows. |
| 504.4 | Add `GET /api/audit-events/export.pdf` endpoint with cap | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` | 504.6 | 503.3 for filename + auth pattern; new pattern for ProblemDetail 413 | Accept the same filter params as `listAuditEvents`. Pre-flight `long count = auditService.countEvents(filter)`. If `count > 10_000`, return `ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "PDF export limited to 10,000 events. Narrow the date range or filters."))` with extra `rowCount` + `cap` fields per architecture §12.3.2. Otherwise call `auditPdfExporter.writePdf(...)` via `StreamingResponseBody`, content type `application/pdf`, `Content-Disposition: attachment; filename="audit-events-{tenantSlug}-{fromDate}-{toDate}.pdf"`. `@RequiresCapability("TEAM_OVERSIGHT")`. |
| 504.5 | Reflexive emission with PDF format | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` (or extend `AuditExportEventEmitter` from 503.4 with format param) | 504.6 | 503.4 reflexive pattern | Emit `audit.export.generated` with `details.format="PDF"`, `details.rowCount`, `details.filter`. Emission happens after the PDF stream completes so `rowCount` reflects actual rendered rows. ADR-264 unchanged. |
| 504.6 | PDF export integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditPdfExporterIntegrationTest.java` | ~5 | `audit/AuditEventControllerIntegrationTest.java` (auth shape); existing PDF golden-hash patterns under `template/` test packages — verify | Tests: (1) golden-hash comparison against a baseline PDF rendered from a fixed seeded scenario (assert byte-level hash; baseline committed under `backend/src/test/resources/audit/golden-audit-export.pdf` or similar); (2) 10 001-row request returns 413 ProblemDetail with `rowCount` and `cap` fields and the expected `type` URI; (3) reflexive `audit.export.generated` event with `format=PDF`; (4) capability gate (non-owner → 403); (5) filter summary header reflects non-default filters (e.g. `severities=CRITICAL` shows up in the rendered "Filter: severity=CRITICAL" line). The golden-hash test may be lenient if PDF metadata varies (e.g. creation timestamp) — pin via fixed clock + canonicalised metadata, or relax to "contains expected text" extraction with `pdfbox` if golden hash is fragile. |

### Key Files

**Create:**
- `backend/src/main/resources/templates/audit-export.tiptap.json`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditPdfExporter.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditPdfExporterIntegrationTest.java`
- `backend/src/test/resources/audit/golden-audit-export.pdf` (baseline fixture)

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — add `countEvents(AuditEventFilter)`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — implement count
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — add `countByFilterWithEventTypes`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` — add `/export.pdf` route with cap check
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditExportEventEmitter.java` (if extracted in 503) — accept `format` param

**Read for context:**
- `backend/.../template/TiptapRenderer.java` — rendering surface
- `backend/.../template/PdfConversionService.java` — HTML → PDF
- `backend/.../template/PdfRenderingService.java` — pipeline orchestration
- `backend/.../template/DocumentTemplate.java` + `DocumentTemplateService.java` — template registration mechanism (verify whether the new template is registered via seed migration or in-code DB-seed; ADR-261 disallows migrations, so prefer in-code registration via `ComplianceTemplatePackSeeder`-style runtime seeding)

### Architecture Decisions

- **No new PDF library** ([ADR-263](../adr/ADR-263-audit-pdf-via-tiptap-pipeline.md)). The Tiptap pipeline already carries Phases 12 / 31 / 42 invoice and closure-letter rendering. Adding the audit template here keeps the rendering surface unified.
- **10k cap is firm but cheap to widen** — over-cap returns a 413 ProblemDetail explicitly instructing the user to narrow the range. The cap is configurable via `application.yml` if a future tenant complains, but the default ships at 10k.
- **Reflexive emission inherits 503's pattern** — same `AuditExportEventEmitter` helper, parameterised by `format`. No drift between CSV and PDF audit shapes.
- **No new template-registration migration** ([ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md) zero-migration constraint). Template loaded as classpath resource or registered in-code via the existing seeder pattern.

### Non-scope

- No new write paths beyond the reflexive event ([ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md)).
- No PDF customisation per tenant beyond branding (which the existing pipeline already handles).
- No streaming PDF beyond chunked output — within the 10k cap, in-memory rendering is fine.

---

## Epic 505: DSAR Audit-Trail Folder Integration

**Goal**: Extend Phase 50 `DataExportService` to add an `audit-trail/` folder to the DSAR pack ZIP. Three files: `events.json` (machine-readable), `events.csv` (human-readable), `README.txt` (static explanation). The audit trail is **unsanitised** per POPIA §23 — contrast with Phase 68's portal sanitisation. Compatibility: do not break any existing Phase 50 tests; the change is purely additive.

**References**: Requirements §4.1–4.5; architecture §12.6, §12.8.2; [ADR-262](../adr/ADR-262-dsar-audit-trail-unsanitised.md).

**Dependencies**: 501A (registry for enrichment), independent of 502 / 503 / 504 (writes its own folder via streaming, not via the export endpoints).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **505A** | 505.1–505.5 | 7 backend files (1 service method, 1 builder method, 1 README, 1 helper, 2 test files, 1 modified existing service) | `AuditService.findEventsForCustomer(UUID)` streams the customer-scoped event set per architecture §12.6.2 (entityType=customer, child entities, JSONB-path `details.customerId` reference). `DataExportService.buildAuditTrail(customer, zip)` writes the three files to the existing pack ZIP. Static `README.txt` resource. Integration tests covering folder presence, cross-customer isolation, and Phase 50 backwards-compatibility. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 505.1 | Add `Stream<AuditEvent> findEventsForCustomer(UUID)` to `AuditService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` | 505.5 | 503.2 streaming pattern | Streams events scoped per architecture §12.6.2: (a) `entityType="customer"` AND `entityId=customerId`; (b) `entityType IN ("project","invoice","proposal","information_request","document","trust_transaction","acceptance_request")` AND child entity belongs to customer (reuse `DataExportService` existing customer-entity resolution — query `CustomerProjectRepository`, `InvoiceRepository`, etc. for the customer-scoped IDs and pass to a single audit query); (c) JSONB-path `details->>'customerId' = :customerId` (best-effort, no dedicated index). The implementation can do (a) + (b) as a `WHERE entityType IN (...) AND entityId IN (...)` query, then UNION with (c). Filtered already by `AuditRetentionProperties` (existing). Returns `Stream<AuditEvent>` that callers close in try-with-resources. |
| 505.2 | Add `buildAuditTrail(DataSubjectRequest, Customer, ZipOutputStream)` to `DataExportService` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java` | 505.5 | existing `DataExportService` ZIP-writing methods (e.g. existing `customer.json` writer pattern) | New step in the pack-build flow (called as the LAST step before closing the ZIP, per architecture §12.6.4). Opens three `ZipEntry`s in sequence: `audit-trail/events.json`, `audit-trail/events.csv`, `audit-trail/README.txt`. For events.json: write a streaming JSON array opening `[`, iterate the stream emitting `objectMapper.writeValueAsString(event)` separated by commas, close with `]`. For events.csv: same column shape as 503.1 (call into `AuditCsvExporter.writeCsv` against the customer-scoped stream — refactor 503.1's exporter to accept a `Stream<AuditEvent>` arg if not already shaped that way). For README.txt: copy from a static classpath resource (`backend/src/main/resources/audit/dsar-audit-trail-readme.txt`). **Unsanitised** per ADR-262 — write the raw `details` JSONB; no `[internal]` stripping. |
| 505.3 | Create static `README.txt` resource | `backend/src/main/resources/audit/dsar-audit-trail-readme.txt` | covered by 505.5 | n/a (new) | Plain text. Explains: what `events.json` contains (full JSON array of audit events relating to the data subject); what `events.csv` contains (same data, RFC 4180 CSV with the column header documented); how to read it; reference to POPIA §23. ~30 lines. |
| 505.4 | Wire `buildAuditTrail` into the existing pack-build flow | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java` | 505.5 | `DataExportService.buildPack` (or equivalent — verify entry method) | Locate the existing pack-build orchestration method (likely `buildPack(...)` or `processFulfillment(...)`). Insert `buildAuditTrail(...)` as the last step before `zip.close()`. Builder must verify exact method name by reading `DataExportService.java` first. |
| 505.5 | DSAR audit-trail integration tests | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportServiceAuditTrailIntegrationTest.java` | ~3 | `datarequest/DataExportServiceIntegrationTest.java` (existing — verify name) for `@SpringBootTest` shape + ZIP assertion patterns | (1) Happy path: DSAR pack for customer X includes `audit-trail/` folder containing `events.json`, `events.csv`, `README.txt`; events.json parses as a JSON array; row count in events.csv (excluding header) matches events.json length. (2) Tenant + customer isolation: events for unrelated customer Y in the same tenant are absent; events for a different tenant are absent (sanity — schema separation should already enforce). (3) Backwards-compat: the existing Phase 50 pack contents (`customer.json`, `projects/`, `invoices/`, `documents/`) are still present and unchanged. |

### Key Files

**Create:**
- `backend/src/main/resources/audit/dsar-audit-trail-readme.txt`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportServiceAuditTrailIntegrationTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — add `findEventsForCustomer(UUID)`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` — implement
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — add customer-scoped query (incl. JSONB-path `details->>'customerId'`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java` — add `buildAuditTrail` + wire into pack flow
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/export/AuditCsvExporter.java` — accept `Stream<AuditEvent>` argument so DSAR can reuse it (refactor only if 503.1 didn't already shape it this way)

**Read for context:**
- `backend/.../datarequest/DataExportService.java` — existing pack-build flow
- `backend/.../datarequest/DataSubjectRequest.java` — entity shape
- `backend/.../customer/CustomerProjectRepository.java` — customer→child-entity mapping
- `backend/.../audit/AuditEventRepository.java` — JPQL conventions

### Architecture Decisions

- **Unsanitised export** ([ADR-262](../adr/ADR-262-dsar-audit-trail-unsanitised.md)). POPIA §23 entitles the subject to their full record, including internal notes and raw `details` JSON. Contrast with Phase 68's portal sanitisation, which applies to live read-model data the client sees through the portal UI, not to one-off regulatory exports.
- **Streaming end-to-end**, per architecture §12.8.2 — `findEventsForCustomer` returns a `Stream<AuditEvent>`, the ZIP entry writes row-by-row, no buffering of the full event list.
- **Additive only** — does not modify existing pack contents, does not change existing Phase 50 tests. The new folder slot is `audit-trail/`, after every existing folder.
- **CSV exporter is reused** — 503.1's `AuditCsvExporter` is the source of truth for the CSV column shape; DSAR consumes the same exporter against a customer-scoped stream.

### Non-scope

- No portal-side surfacing of the DSAR audit trail (out of scope for Phase 69 — portal activity trail is deferred per founder call 2026-04-20).
- No retention-policy override for DSAR — `AuditRetentionProperties` already filters out events past the retention horizon.
- No migrations.

---

## Epic 506: Global Audit Log Page — Shell, Filters, Row Expansion, Presets, Export

**Goal**: Replace the placeholder `settings/audit-log/page.tsx` with the full filterable global audit log experience. Filter UI driven by the facet endpoints from 502B; URL query string is the source of truth for filter state (deep-linkable); pagination is page-numbered (auditors quote stable URLs); rows expand to show diff viewer / JSON tree + metadata footer; entity cells deep-link to existing detail pages; severity pills with the four-colour scheme; four built-in presets (Sensitive / Compliance / Security / Financial approvals); export dropdown wired to CSV + PDF endpoints with PDF cap pre-check.

**References**: Requirements §2.1–2.6; architecture §12.4.

**Dependencies**: 502B (controller surface), 503A + 504A (export endpoints).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **506A** | 506.1–506.8 | 9 frontend files (1 page rewrite, 1 client component, 4 shared components, 1 API client extension, 2 test files) | `app/(app)/org/[slug]/settings/audit-log/page.tsx` rewritten; new `audit-log-client.tsx` client component owning filter state + URL sync. Shared primitives created under `frontend/components/audit/`: `<SeverityPill>`, `<AuditDetailsViewer>` (diff + JSON tree), `<ActorDisplay>`, entity-cell deep-link helper. `frontend/lib/api/audit-events.ts` extended with the six new endpoint methods. Empty states + i18n keys. Frontend tests for filter mapping + row expansion + entity deep-link. |
| **506B** | 506.9–506.13 | 5 frontend files (preset logic + export dropdown component + Playwright spec + 2 tests) | Four built-in presets (Sensitive / Compliance / Security / Financial approvals) populating filters from URL query string; export dropdown component triggering CSV / PDF blob downloads; PDF cap pre-check via `?count=true` query param (debounced); Playwright smoke spec covering login → navigate → apply Sensitive preset → assert non-zero rows; frontend tests for preset selection + export trigger. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 506.1 | Extend `frontend/lib/api/audit-events.ts` with new endpoints | `frontend/lib/api/audit-events.ts` | covered by 506.8 | existing `listAuditEvents` shape | Add typed methods: `listFacetActors(from, to)`, `listFacetEventTypes(from, to)`, `listFacetEntityTypes(from, to)`, `getAuditMetadata()`, `downloadAuditCsv(filter)` (returns blob URL trigger), `downloadAuditPdf(filter)`, `countAuditEvents(filter)` (for cap pre-check). Extend `AuditEventResponse` interface with `label`, `severity`, `group`, `actorDisplayName` per 502.8. Use existing `frontend/lib/api/client.ts` `api.get/post` helpers. |
| 506.2 | Create `<SeverityPill>` shared primitive | `frontend/components/audit/severity-pill.tsx` | covered by 506.8 | architecture §12.4.6 colour table; existing pill components (`frontend/components/ui/badge.tsx`) | Tailwind-styled `<span>`. Props: `severity: AuditSeverity`, `size: "sm" \| "md"`. Class map: INFO grey (`bg-slate-100 text-slate-700`), NOTICE blue (`bg-blue-100 text-blue-700`), WARNING amber (`bg-amber-100 text-amber-800`), CRITICAL red (`bg-red-100 text-red-700`). `text-xs rounded-full px-2 py-0.5` baseline. |
| 506.3 | Create `<AuditDetailsViewer>` (diff + JSON tree) | `frontend/components/audit/audit-details-viewer.tsx` | 506.8 | existing diff/tree libraries already in repo (verify — if `react-json-view` or similar is already used in `frontend/components/`, reuse; otherwise hand-roll a simple recursive renderer) | Receives `details: Record<string, unknown> \| null`. If `details` matches the `AuditDeltaBuilder` shape (`{ before, after, changedFields }` or `{ field: { from, to } }`) per architecture §12.4.5, render side-by-side or inline diff. Otherwise render a collapsible JSON tree. **No heavy editor** (no Monaco / CodeMirror). For the diff, simple two-column layout with red/green tinting per change. Empty state: "No structured details for this event". |
| 506.4 | Create `<ActorDisplay>` primitive | `frontend/components/audit/actor-display.tsx` | 506.8 | existing tooltip components in `frontend/components/ui/` | Renders `actorDisplayName`. On hover, tooltip shows `actorId`, `actorType`, `source`, `ipAddress` (where non-null) per architecture §12.4.8. Strikethrough styling when display name starts with `"Former member"`. |
| 506.5 | Create entity-cell deep-link helper | `frontend/components/audit/entity-cell.tsx` | 506.8 | existing entity-link patterns in `frontend/lib/` | Maps `entityType` → frontend route per architecture §12.4.7 table. Renders the link or, for unknown entityTypes, the literal `{entityType}:{entityId.short}` (first 8 chars of UUID) as plain text. Soft-deleted entities (controller flags via response field if present, else fall back to literal text) render with `line-through`. `audit_export` entityType renders a no-link literal. |
| 506.6 | Rewrite `audit-log/page.tsx` as server component shell | `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` | covered by 506.8 | existing page (placeholder); architecture §12.4.1 | Server component. Fetches initial metadata catalogue + first page of events on the server (using initial filter from query string). Passes to client component for interactive filter / pagination state. Wrap in `<CapabilityGate capability="TEAM_OVERSIGHT">` (or equivalent server-side gate already used by the placeholder — confirm existing implementation via `lib/auth.ts`). |
| 506.7 | Create `audit-log/audit-log-client.tsx` client component | `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-client.tsx` | 506.8 | architecture §12.4.2 layout; existing client-component patterns in `frontend/app/(app)/` | `"use client"`. Owns filter state in URL query string via `useSearchParams` + `useRouter` (Next.js App Router pattern). Filter UI: date range picker (defaults to last 7 days), severity multi-select (populates from `/metadata`), actor select (populates from `/facets/actors`), event-type select (populates from `/facets/event-types`), entity-type + entity-id pickers (populates from `/facets/entity-types`). Paginated table using existing `<Table>` primitives. Each row renders `<SeverityPill>` + label + `<ActorDisplay>` + `<EntityCell>` + expand chevron. On expand, render `<AuditDetailsViewer>` + metadata footer. Empty states per architecture §12.4.5 — "No audit events in this range" / "The audit log is empty…". i18n via existing message catalogue. |
| 506.8 | Frontend tests — filter mapping, row expansion, entity deep-link | `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-page.test.tsx` (existing — extend), `frontend/components/audit/__tests__/severity-pill.test.tsx`, `frontend/components/audit/__tests__/audit-details-viewer.test.tsx` | ~6 | existing `audit-log-page.test.tsx` for `@testing-library/react` patterns + `afterEach(() => cleanup())` per `frontend/CLAUDE.md` | (1) filter combinations map to expected query string (severity + actor + entity → URL has `severities=CRITICAL&actorId=xxx&entityType=customer`); (2) row expansion toggles `<AuditDetailsViewer>` visibility; (3) entity cell renders correct `<Link>` for `entityType=customer`; (4) `<SeverityPill>` renders correct colour per severity; (5) `<AuditDetailsViewer>` renders side-by-side diff for `AuditDeltaBuilder` shape; (6) `<AuditDetailsViewer>` renders JSON tree for free-form `details`. Mock the API client. |
| 506.9 | Filter presets logic | `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-client.tsx` (extend) | 506.13 | architecture §12.4.4 preset table | Add a `<Select>` preset dropdown above the filters. Selecting Sensitive populates `severities=WARNING,CRITICAL` + `from=now-30d`; Compliance populates `eventType` from the `group=COMPLIANCE` slice of the metadata catalogue + `from=now-90d`; Security populates `eventType` from `group=SECURITY` + `from=now-7d`; Financial approvals populates the four-event-type set + `from=now-30d`. All client-side via URL query string mutation. No backend preset table. |
| 506.10 | Export dropdown component | `frontend/components/audit/export-dropdown.tsx` | 506.13 | existing dropdown patterns in `frontend/components/ui/dropdown-menu.tsx` | Renders "Download CSV" + "Download PDF" items. CSV download builds the filter URL and triggers a `<a download>` blob fetch via `downloadAuditCsv`. PDF download first calls `countAuditEvents(filter)` (debounced when filters change) — if `count > 10000`, the PDF item is disabled with tooltip per architecture §12.4.9; otherwise triggers `downloadAuditPdf`. In-flight spinner on each item during download. |
| 506.11 | Wire export dropdown into page | `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-client.tsx` (extend) | 506.13 | n/a (composition) | Place `<ExportDropdown filter={currentFilter} />` in the page header next to "Audit Log" title per architecture §12.4.2 layout. |
| 506.12 | Playwright smoke spec | `frontend/e2e/tests/audit-log/audit-log-smoke.spec.ts` | new spec | existing `frontend/e2e/tests/` patterns | Login as admin → navigate to `/org/{slug}/settings/audit-log` → assert page heading visible → apply Sensitive preset → wait for table re-render → assert `<tr>` count > 0 (requires the seed scenario to have at least one CRITICAL or WARNING event in the last 30 days; if seed is empty, builder must seed an event in setup). |
| 506.13 | Frontend tests — presets + export trigger | `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-page.test.tsx` (extend), `frontend/components/audit/__tests__/export-dropdown.test.tsx` | ~4 | 506.8 | (1) selecting Sensitive preset updates URL to `severities=WARNING,CRITICAL&from=...`; (2) selecting Financial approvals adds the four event types to the URL; (3) export-dropdown CSV item triggers blob fetch; (4) export-dropdown PDF item disables when mock `countAuditEvents` returns 10001. `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `frontend/components/audit/severity-pill.tsx`
- `frontend/components/audit/audit-details-viewer.tsx`
- `frontend/components/audit/actor-display.tsx`
- `frontend/components/audit/entity-cell.tsx`
- `frontend/components/audit/export-dropdown.tsx`
- `frontend/components/audit/__tests__/severity-pill.test.tsx`
- `frontend/components/audit/__tests__/audit-details-viewer.test.tsx`
- `frontend/components/audit/__tests__/export-dropdown.test.tsx`
- `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-client.tsx`
- `frontend/e2e/tests/audit-log/audit-log-smoke.spec.ts`

**Modify:**
- `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` — full rewrite (placeholder → server-component shell)
- `frontend/app/(app)/org/[slug]/settings/audit-log/audit-log-page.test.tsx` — extend with filter / preset / expansion tests
- `frontend/lib/api/audit-events.ts` — add facets + metadata + export + count methods
- `frontend/messages/*.json` (i18n catalogues) — audit-log copy keys; `audit.tab` terminology key

**Read for context:**
- `frontend/lib/api/audit-events.ts` (existing) — current shape
- `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` (existing placeholder) — what's being replaced
- `frontend/components/empty-state.tsx` — Phase 43 EmptyState pattern
- `frontend/lib/capabilities.tsx` — `<CapabilityGate>` pattern
- `frontend/components/ui/table.tsx`, `dropdown-menu.tsx`, `badge.tsx` — existing primitives

### Architecture Decisions

- **URL query string is filter state** — auditors quote URLs that pin to specific filtered views. No client-only state for filters.
- **Page-numbered pagination** (architecture §12.4.2) — stable references over time. Not infinite scroll.
- **Generic diff viewer, not template-per-event** ([ADR-260](../adr/ADR-260-audit-generic-diff-over-event-templates-v1.md)). v1 ships the registry + diff; per-event templates are a future polish.
- **Presets are client-side URL combinations** — no backend preset table per architecture §12.4.4. Saved filters are out of scope.
- **Cap pre-check is debounced** — avoids hammering `?count=true` on every keystroke when adjusting filters.

### Non-scope

- No saved/named filters (out of scope per requirements).
- No live tail / streaming.
- No alerting on sensitive events (deferred to integrations phase).
- No portal-side audit page.

---

## Epic 507: `<AuditTimeline>` Component + Customer / Project / Invoice Detail Tabs

**Goal**: Build the reusable `<AuditTimeline>` component, then drop it into Customer / Project / Invoice detail pages as a new "Audit" tab. Reuses 506A's primitives (`<SeverityPill>`, `<AuditDetailsViewer>`, `<ActorDisplay>`, entity-cell helper). Tab is capability-gated; legal-za tenants render the label as "Audit Trail" via terminology key.

**References**: Requirements §3.1–3.3; architecture §12.5.

**Dependencies**: 502B (per-entity endpoint enrichment landed in 502.9); 506A (shared primitives).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **507A** | 507.1–507.7 | 9 frontend files (1 component, 1 row primitive, 3 detail-page tab integrations, 1 i18n key, 2 test files, 1 component-tab wrapper) | `<AuditTimeline>` component reusing 506A primitives; rendered as a vertical timeline (one node per event) with chronological top→bottom ordering and click-to-expand details. "Audit" tab integrated into Customer Detail / Project Detail / Invoice Detail pages. Capability-gated. Terminology key `audit.tab` ("Audit" default, "Audit Trail" for legal-za). Frontend tests + `<AuditTimelineTab>` wrapper for the capability gate. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 507.1 | Create `<AuditTimeline>` component | `frontend/components/audit/audit-timeline.tsx` | 507.7 | architecture §12.5.1 spec; existing timeline-style components (none firm-side; verify and pattern after `frontend/components/dashboard/recent-activity-widget.tsx` for chronological feed shape) | Props: `entityType`, `entityId`, `initialPageSize` (default 20), `showFilters` (default false — compact mode for tabs), `severityPillSize` (default `"sm"`). Fetches `/api/audit-events/{entityType}/{entityId}` (existing endpoint, now enriched per 502.9). Vertical timeline: each node is a row with `<SeverityPill>` + label + `<ActorDisplay>` + relative timestamp ("3 hours ago") with full ISO 8601 in tooltip. On click, expands to show `<AuditDetailsViewer>` + metadata footer. Pagination: "Load more" button at bottom; manages `page` state internally. Empty state: reuse Phase 43 `<EmptyState>` with copy "No audit events for this {entityType}". |
| 507.2 | Create `<AuditTimelineTab>` capability-gated wrapper | `frontend/components/audit/audit-timeline-tab.tsx` | 507.7 | `<CapabilityGate>` from `frontend/lib/capabilities.tsx` | Wraps `<AuditTimeline>` in `<CapabilityGate capability="TEAM_OVERSIGHT">`. Members without the capability see no tab content (parent tab strip should also hide the tab — see 507.3). Exports a hook `useAuditTabVisible()` that returns `true` when the user has `TEAM_OVERSIGHT`, used by the parent tab strips to conditionally render the tab. |
| 507.3 | Add "Audit" tab to Customer Detail | `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`, `frontend/app/(app)/org/[slug]/customers/[id]/audit-tab.tsx` (new) | 507.7 | existing tab-strip pattern in customer detail page | Locate the existing tab strip in the customer detail page (read first to identify exact JSX shape). Add an "Audit" tab as the last tab. Tab content renders `<AuditTimelineTab entityType="customer" entityId={customer.id} />`. Tab label resolved via terminology key `audit.tab`. Tab visibility controlled by `useAuditTabVisible()` from 507.2. |
| 507.4 | Add "Audit" tab to Project Detail | `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` (and the `(tabs)/` directory if Next.js layout uses route groups) | 507.7 | existing tab-strip in `projects/[id]/page.tsx`; the `(tabs)/disbursements.tsx` + `(tabs)/statements.tsx` group pattern | Add "Audit" tab. `entityType="project"`. The project detail page uses a `(tabs)/` route group — add `(tabs)/audit.tsx` if that pattern requires a file per tab; otherwise wire inline in the existing tab list. Builder verifies the actual pattern by reading the project detail page first. |
| 507.5 | Add "Audit" tab to Invoice Detail | `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` | 507.7 | existing invoice detail tab strip | Add "Audit" tab. `entityType="invoice"`. Invoice detail page is simpler (no `(tabs)/` group — single page); audit tab added inline. |
| 507.6 | Add `audit.tab` terminology key | `frontend/messages/en.json`, `frontend/messages/en-ZA-legal.json`, `frontend/messages/en-ZA-accounting.json`, `frontend/messages/en-ZA-consulting.json` (paths/files verified by builder) | covered by 507.7 | existing terminology keys per Phase 48 | `audit.tab`: default "Audit"; legal-za: "Audit Trail"; accounting-za: "Audit"; consulting-za: "Audit". Render via existing `useTerminology()` hook. |
| 507.7 | Component tests | `frontend/components/audit/__tests__/audit-timeline.test.tsx`, `frontend/components/audit/__tests__/audit-timeline-tab.test.tsx` | ~5 | 506.8 + `frontend/CLAUDE.md` testing patterns | (1) `<AuditTimeline>` renders events in DESC order; (2) clicking a node expands the `<AuditDetailsViewer>`; (3) empty state renders correct copy when API returns 0 events; (4) "Load more" advances pagination; (5) `<AuditTimelineTab>` hides content when `TEAM_OVERSIGHT` is missing. Mock the API client. `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `frontend/components/audit/audit-timeline.tsx`
- `frontend/components/audit/audit-timeline-tab.tsx`
- `frontend/components/audit/__tests__/audit-timeline.test.tsx`
- `frontend/components/audit/__tests__/audit-timeline-tab.test.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/audit-tab.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/audit.tsx` (if route-group pattern requires it)

**Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — wire audit tab into tab strip
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — wire audit tab into tab strip
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` — wire audit tab inline
- `frontend/messages/*.json` — `audit.tab` terminology key

**Read for context:**
- `frontend/components/audit/severity-pill.tsx`, `audit-details-viewer.tsx`, `actor-display.tsx` (506A) — primitives to compose
- `frontend/lib/api/audit-events.ts` (506.1) — per-entity fetch
- `frontend/lib/capabilities.tsx` — `<CapabilityGate>` pattern
- `frontend/lib/terminology.ts` (or equivalent) — `useTerminology()` hook
- `frontend/components/empty-state.tsx` — Phase 43 empty state

### Architecture Decisions

- **Timeline, not table** — vertical chronological feed is the right shape for a per-entity history. The global page (506) is a table because it's a search surface; the per-entity is a story.
- **Reuses 506A primitives** — no duplicate `<SeverityPill>` / `<AuditDetailsViewer>` / `<ActorDisplay>` per the architecture's deduplication mandate (architecture §12.5.1).
- **Tab visibility, not content gating** — members without `TEAM_OVERSIGHT` see no tab at all (existing convention; no "you don't have permission" affordance).
- **Terminology-driven label** — legal-za renders "Audit Trail" while default renders "Audit"; the consuming page never knows the profile.

### Non-scope

- No filters in the timeline view (`showFilters={false}` always — the global page is for filtering).
- No export from the timeline (use the global page).
- No write actions (consistent with phase-wide read-only constraint per ADR-259).

---

## Epic 508: `<AuditTimeline>` — Trust Transaction / Matter Closure / Proposal / Information Request Detail Tabs

**Goal**: Drop `<AuditTimeline>` into the four remaining detail pages from 507. These are the legally-sensitive entities where compliance officers most need a history surface — matter-closure overrides, trust-transaction approvals, proposal lifecycle changes, information-request fulfilment. Identical wiring shape to 507; the only delta is the four entity types and the route paths. Includes a Playwright smoke spec verifying that the matter-closure override event renders with its `details.justification` visible on expand (the canonical compliance-question demo).

**References**: Requirements §3.2 (rows 4–7); architecture §12.5.2.

**Dependencies**: 507A (component shape + capability gate convention).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **508A** | 508.1–508.6 | 8 frontend files (4 detail-page tab integrations + 1 Playwright spec + tab-component files where required) | "Audit" tab integrated into Trust Transaction / Matter Closure / Proposal / Information Request detail pages. Same wiring as 507 — `<AuditTimelineTab entityType="..." entityId={...} />`. Playwright smoke for matter-closure-detail audit tab demonstrating override-with-justification visible. Frontend tests for each tab integration (each test verifying the tab renders and entityType is passed correctly). |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 508.1 | Add "Audit" tab to Trust Transaction Detail | `frontend/app/(app)/org/[slug]/trust-accounting/transactions/[id]/page.tsx` | 508.6 | 507.5 | `entityType="trust_transaction"`. Trust transaction detail page is single-page; audit tab added inline at the end of the existing tab strip. |
| 508.2 | Add "Audit" tab to Matter Closure Detail | `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/closure.tsx` (verify path — Phase 67's matter closure may live as a tab on the project detail or as a standalone `closure/page.tsx`; builder reads first) | 508.6 | 507.4 (project tabs pattern) | `entityType="matter_closure"`. If matter closure is already a tab inside the project detail's `(tabs)/` group, the audit tab here is for the *closure entity history* (filter by `entityType=matter_closure` AND `entityId={closureId}`), not the project. May render as a sub-tab within the closure detail panel. Builder confirms existing UX shape before wiring. |
| 508.3 | Add "Audit" tab to Proposal Detail | `frontend/app/(app)/org/[slug]/proposals/[id]/page.tsx` | 508.6 | 507.5 | `entityType="proposal"`. Add at end of existing tab strip. |
| 508.4 | Add "Audit" tab to Information Request Detail | `frontend/app/(app)/org/[slug]/information-requests/[id]/page.tsx` | 508.6 | 507.5 | `entityType="information_request"`. Add at end of existing tab strip. |
| 508.5 | Playwright smoke — matter closure override visibility | `frontend/e2e/tests/audit-log/matter-closure-audit-tab.spec.ts` | new spec | 506.12 + existing matter-closure E2E setup | Setup: seed a matter closure with `override_used=true` + `justification="Client returned funds — trust account zero"`. Login as admin → navigate to project detail → matter-closure section → expand → click Audit tab → assert the override event row is visible with severity = CRITICAL → click to expand → assert `details.justification` text is visible. This is the canonical Phase 69 demo per requirements §6.1 Day 15. |
| 508.6 | Frontend tests for each integration | `frontend/app/(app)/org/[slug]/trust-accounting/transactions/[id]/__tests__/audit-tab.test.tsx`, `frontend/app/(app)/org/[slug]/proposals/[id]/__tests__/audit-tab.test.tsx`, `frontend/app/(app)/org/[slug]/information-requests/[id]/__tests__/audit-tab.test.tsx` (matter closure covered by 508.5 Playwright) | ~3 | 507.7 | Each: tab strip renders the Audit tab when caller has `TEAM_OVERSIGHT`; tab is hidden when capability missing; `<AuditTimelineTab>` receives correct `entityType` + `entityId`. `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `frontend/e2e/tests/audit-log/matter-closure-audit-tab.spec.ts`
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/[id]/__tests__/audit-tab.test.tsx`
- `frontend/app/(app)/org/[slug]/proposals/[id]/__tests__/audit-tab.test.tsx`
- `frontend/app/(app)/org/[slug]/information-requests/[id]/__tests__/audit-tab.test.tsx`

**Modify:**
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/closure.tsx` (or wherever matter closure lives — verify first)
- `frontend/app/(app)/org/[slug]/proposals/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/information-requests/[id]/page.tsx`

**Read for context:**
- `frontend/components/audit/audit-timeline-tab.tsx` (507A) — composition contract
- Each of the four detail pages to identify the exact tab strip shape

### Architecture Decisions

- **Same wiring as 507** — no per-entity customisation. The `<AuditTimeline>` is uniform.
- **Matter closure entity scoping** — the audit tab on the closure detail filters by `entityType=matter_closure AND entityId={closureId}`, not by `entityType=project AND entityId={projectId}`. The override event is keyed to the closure entity, not the project.
- **Playwright smoke is load-bearing** — it's the demo for the "who approved closure of Matter 0042 and why?" question (requirements §0).

### Non-scope

- No new component work — all primitives shipped in 506A and 507A.
- No matter-closure UI changes — only the audit tab is added.

---

## Epic 509: Sensitive-Events Dashboard Widget

**Goal**: Add a small at-a-glance widget on the firm admin dashboard showing recent legally-sensitive events. Three count pills (NOTICE / WARNING / CRITICAL last 7 days; **INFO intentionally excluded** to keep the at-a-glance signal high). Top-5 list of recent CRITICAL+WARNING events, each clickable into the global audit log pre-filtered to that event. "View all" link to the Sensitive preset. Capability-gated. **This is the cuttable epic** if scope tightens (per architecture §12.7) — the audit log page (506) and per-entity timeline (507/508) carry the phase.

**References**: Requirements §5.1–5.3; architecture §12.7.

**Dependencies**: 502B (severities filter on list endpoint, facets endpoint for count aggregation), 506B (Sensitive preset URL format for "View all" deep link).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **509A** | 509.1–509.5 | 6 frontend files (1 widget, 1 dashboard wiring, 2 test files, 1 i18n keys block) | `<SensitiveEventsWidget>` placed on the firm admin dashboard. Three count pills. Top-5 list. Deep-link to global audit log Sensitive preset for "View all". Each row click deep-links to the global audit log filtered to that specific event. Capability-gated. Frontend tests for empty state + deep-link URL correctness. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 509.1 | Create `<SensitiveEventsWidget>` | `frontend/components/dashboard/sensitive-events-widget.tsx` | 509.5 | existing dashboard widgets in `frontend/components/dashboard/` (`recent-activity-widget.tsx`, `deadline-widget.tsx`, `incomplete-profiles-widget.tsx`) | `"use client"`. Fetches: (a) `listFacetEventTypes(now-7d, now)` for the count pills (aggregates by `severity` client-side; `INFO` excluded per architecture §12.7.1); (b) `listAuditEvents({severities: [WARNING, CRITICAL], from: now-7d, size: 5})` for the top-5 list. Renders three pills (using `<SeverityPill>` from 506A), then a list of 5 rows with severity pill + label + relative timestamp + entity cell. Each row click: navigates to `/org/{slug}/settings/audit-log?eventType={eventType}&from={occurredAt-1m}&to={occurredAt+1m}` (or similar single-event filter). "View all" link navigates to `/org/{slug}/settings/audit-log?preset=sensitive` (or the equivalent URL produced by 506B's Sensitive preset). |
| 509.2 | Wrap widget in `<CapabilityGate>` | `frontend/components/dashboard/sensitive-events-widget.tsx` (same file) | 509.5 | `frontend/lib/capabilities.tsx` | Members without `TEAM_OVERSIGHT` see no widget at all per architecture §12.7.3. |
| 509.3 | Place widget on firm admin dashboard | `frontend/app/(app)/org/[slug]/dashboard/page.tsx` (or `dashboard-header.tsx` if widgets compose at that level — verify) | 509.5 | existing widget placement in dashboard page; the dashboard already includes `recent-activity-widget`, `deadline-widget`, `team-capacity-widget`, etc. | Add `<SensitiveEventsWidget />` to the dashboard layout. Position: top-right or top-half priority (compliance officers want this visible on first paint). |
| 509.4 | i18n keys | `frontend/messages/*.json` | covered by 509.5 | existing dashboard i18n keys | Keys for "Sensitive events", "Last 7 days", "View all", "No sensitive events in the last 7 days." |
| 509.5 | Widget tests | `frontend/components/dashboard/__tests__/sensitive-events-widget.test.tsx` | ~3 | 506.8; existing widget tests in `frontend/components/dashboard/__tests__/` | (1) widget renders three count pills with mocked facet data; (2) empty state visible when zero CRITICAL+WARNING events in last 7 days; (3) row click navigates to correct deep-link URL (assert via mocked router); (4) widget hidden when `TEAM_OVERSIGHT` missing. `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `frontend/components/dashboard/sensitive-events-widget.tsx`
- `frontend/components/dashboard/__tests__/sensitive-events-widget.test.tsx`

**Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` — add widget
- `frontend/messages/*.json` — i18n keys

**Read for context:**
- `frontend/components/dashboard/recent-activity-widget.tsx` — sibling pattern
- `frontend/components/audit/severity-pill.tsx` (506A) — primitive
- `frontend/lib/api/audit-events.ts` (506.1) — facets + list APIs
- `frontend/lib/capabilities.tsx` — capability gating
- 506B Sensitive preset URL format

### Architecture Decisions

- **INFO excluded from count pills** (architecture §12.7.1) — INFO events are routine and would dominate the count, defeating the at-a-glance signal.
- **Reuses list endpoint with severities filter** — no new backend endpoint. The widget is a client of 502B.
- **Cuttable epic** (architecture §12.12 Epic E) — if scope tightens, drop 509 and ship the phase without it. The audit log page (506) covers the same data with more flexibility.

### Non-scope

- No push notifications (deferred to integrations phase per requirements out-of-scope).
- No widget customisation (size / position / which severities) — fixed shape.
- No portal-side equivalent.

---

## Epic 510: Admin-POV 30-Day QA Capstone + Screenshots + Gap Report

**Goal**: Validate the firm-admin audit experience end-to-end via a 30-day lifecycle script that simulates the kinds of questions a compliance officer asks. Captures screenshot baselines for every key surface. Produces the gap report cataloguing UX rough edges, missing event coverage, performance observations, and Phase 70+ proposals.

**References**: Requirements §6.1–6.4; architecture §12.11.4 testing strategy.

**Dependencies**: 501–509 (every preceding epic merged + green).

**Scope**: E2E / Process

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary |
|-------|-------|---------------|---------|
| **510A** | 510.1–510.4 | 5 files (1 lifecycle script, 1 seed-fixtures module, 2 Playwright spec scaffolds, 1 `/qa-cycle-kc` config check) | `qa/testplan/demos/admin-audit-30day-keycloak.md` drafted with 8 checkpoints (per requirements §6.1). Seed-scenario fixtures module producing the lifecycle's events deterministically. Playwright spec scaffolds under `frontend/e2e/tests/admin-audit-30day/`. `/qa-cycle-kc` compatibility verified (script structure matches the existing capstone format from Phase 67/68 — refer to `qa/testplan/demos/portal-client-90day-keycloak.md` and `qa/testplan/demos/legal-depth-30day-keycloak.md` shape). |
| **510B** | 510.5–510.8 | ~14 files (lifecycle run logs, 12 screenshots, 1 gap report, 1 TASKS.md update) | Full lifecycle run via `/qa-cycle-kc qa/testplan/demos/admin-audit-30day-keycloak.md`. Screenshot baselines under `documentation/screenshots/phase69/` (12 shots per requirements §6.2). `tasks/phase69-gap-report.md` authored covering UX rough edges, missing event coverage, performance observations on large filters, and Phase 70+ proposals (template registry, portal activity trail, alert routing). `TASKS.md` Phase 69 row updated. |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 510.1 | Draft lifecycle script | `qa/testplan/demos/admin-audit-30day-keycloak.md` | n/a (process doc) | `qa/testplan/demos/portal-client-90day-keycloak.md` (Phase 68); `qa/testplan/demos/legal-depth-30day-keycloak.md` (Phase 67) | 8 checkpoints per architecture §6.1 / requirements §6.1: (1) Day 0 — seed events + open audit log + verify Sensitive preset empty + export empty CSV. (2) Day 5 — simulate permission-denied → verify Security preset → WARNING. (3) Day 10 [legal-za] — owner approves trust transaction → verify Financial approvals preset. (4) Day 15 [legal-za] — closure override with justification → verify CRITICAL severity + justification readable + dashboard widget shows top-5. (5) Day 20 — open customer detail → Audit tab → verify Day 0–20 events for that customer. (6) Day 22 — export PDF of last 30 days → verify header + filter summary + page numbering + reflexive `audit.export.generated`. (7) Day 25 — DSAR fulfilment → open pack → `audit-trail/events.csv` → verify subject's events present + cross-customer events absent. (8) Day 30 — 2-year filter → 10k cap fires 413 → narrow → succeeds. |
| 510.2 | Seed-scenario fixtures | `frontend/e2e/tests/admin-audit-30day/fixtures.ts` (or `qa/fixtures/admin-audit-30day.ts` — verify existing fixtures pattern) | n/a (test infra) | existing capstone fixtures patterns | Programmatic seeding of all events the script depends on: login + matter creation + trust deposit + deadline + retainer setup + permission denial + trust approval + closure override + DSAR. Each fixture identifies the event in `audit_events` after seed. Reusable across the script's checkpoints. |
| 510.3 | Playwright spec scaffolds | `frontend/e2e/tests/admin-audit-30day/admin-audit-lifecycle.spec.ts` | new specs | 506.12, 508.5 + existing capstone spec patterns | Scaffold one Playwright file per checkpoint OR one file with describe blocks per checkpoint (mirror Phase 68 capstone format). At this slice, just scaffold + smoke; full assertions land in 510.5 with the run. |
| 510.4 | `/qa-cycle-kc` compatibility verification | n/a — verification only, no file change | n/a | review existing Phase 67/68 capstone scripts | Confirm the script's heading structure, checkpoint format, and frontmatter match what `/qa-cycle-kc` expects. Builder runs `/qa-cycle-kc qa/testplan/demos/admin-audit-30day-keycloak.md --dry-run` (or equivalent dry-run flag if supported) to validate parse-ability. |
| 510.5 | Full lifecycle run + iterate to green | run logs in PR description; updates to checkpoints / fixtures / Playwright specs as needed | n/a | Phase 67/68 capstones | `/qa-cycle-kc qa/testplan/demos/admin-audit-30day-keycloak.md` runs to "ALL CHECKPOINTS PASS". Any failure triggers fix-and-retry within this slice — fixes can land in either the test or the production code being tested (if the production gap was real). |
| 510.6 | Screenshot baselines | `documentation/screenshots/phase69/` — 12 PNG files | n/a (visual baseline) | `documentation/screenshots/phase68/` baseline shape | (1) Audit Log page — empty state. (2) Audit Log page — populated. (3) Audit Log page — expanded row showing override justification. (4–7) Audit Log page — each preset applied (Sensitive / Compliance / Security / Financial approvals). (8) Sensitive Events widget — populated on dashboard. (9) Per-entity Audit tab — Matter Closure detail (override visible). (10) Per-entity Audit tab — Customer detail. (11) PDF export — first page. (12) PDF export — middle page. |
| 510.7 | Author gap report | `tasks/phase69-gap-report.md` | n/a (process doc) | `tasks/phase67-gap-report.md`, `tasks/phase68-gap-report.md` | Sections: UX rough edges (any clunky interactions noticed during the run); missing event coverage (any domain flow where `AuditEventBuilder` was never called — record without fixing per ADR-259); performance observations on large filters (e.g. 100k-event tenants); Phase 70+ proposals (template registry per ADR-260; portal activity trail; alert routing). |
| 510.8 | Update `TASKS.md` Phase 69 row | `TASKS.md` | n/a | existing TASKS.md format from Phase 68 entry | Add Phase 69 row block with all 10 epics + status. Update the high-water-mark notes at the top. Confirm last-completed-epic is now 510. |

### Key Files

**Create:**
- `qa/testplan/demos/admin-audit-30day-keycloak.md`
- `frontend/e2e/tests/admin-audit-30day/fixtures.ts`
- `frontend/e2e/tests/admin-audit-30day/admin-audit-lifecycle.spec.ts`
- `documentation/screenshots/phase69/*.png` (12 files)
- `tasks/phase69-gap-report.md`

**Modify:**
- `TASKS.md` — Phase 69 epic block

**Read for context:**
- `qa/testplan/demos/portal-client-90day-keycloak.md` — Phase 68 capstone format
- `qa/testplan/demos/legal-depth-30day-keycloak.md` — Phase 67 capstone format
- `tasks/phase67-gap-report.md`, `tasks/phase68-gap-report.md` — gap report shape

### Architecture Decisions

- **Capstone validates the demo question** — "who approved the closure of Matter 0042 and why?" is the canonical question; checkpoints 4 + 5 + 6 collectively answer it from three different surfaces (global page, dashboard widget, per-entity tab, PDF export).
- **Gap report does not fix gaps** ([ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md)). Any missing event coverage discovered during the run is logged for Phase 70+, not patched here.
- **Screenshots as baselines** — Phase 70+ visual regression checks fork off these.

### Non-scope

- No portal-side capstone (separate Phase 68 capstone already exists; portal audit trail is deferred).
- No load testing beyond the 100k-row CSV streaming check (already covered in 503.5).
- No alert-routing testing (out of scope per requirements).

---

## Cross-cutting Notes

### ADR Cross-Reference

| ADR | Topic | Epics Affected |
|-----|-------|----------------|
| [ADR-259](../adr/ADR-259-audit-ui-read-only-no-write-changes.md) | Audit UI is a read layer; no new write paths | All epics — gap-report-not-fix discipline |
| [ADR-260](../adr/ADR-260-audit-generic-diff-over-event-templates-v1.md) | Generic diff viewer over template-per-event | 501 (registry shape), 506 (`<AuditDetailsViewer>`) |
| [ADR-261](../adr/ADR-261-audit-severity-derived-read-time.md) | Severity derived at read time, not persisted | 501 (registry), 502 (pre-flight); enforces zero migrations across the phase |
| [ADR-262](../adr/ADR-262-dsar-audit-trail-unsanitised.md) | DSAR audit-trail export unsanitised | 505 |
| [ADR-263](../adr/ADR-263-audit-pdf-via-tiptap-pipeline.md) | PDF via existing Tiptap pipeline | 504 |
| [ADR-264](../adr/ADR-264-audit-export-is-auditable.md) | Audit exports emit reflexive event | 503, 504 |

### Phase 69 Migration Footprint

**Zero migrations.** Tenant high-water remains V119. Global high-water remains V22. Builders explicitly do NOT touch `backend/src/main/resources/db/migration/tenant/` or `backend/src/main/resources/db/migration/global/` in any slice. ADR-261 codifies the rationale.

### Capability Gate

Every new endpoint (`/metadata`, `/facets/*`, `/export.csv`, `/export.pdf`, the severity-filtered list extension) is gated by the existing `@RequiresCapability("TEAM_OVERSIGHT")`. No new capability is introduced. Frontend tabs and the dashboard widget gate via the existing `<CapabilityGate>` component.

### Test Taxonomy

| Test type | Convention | Location |
|---|---|---|
| Backend integration | `*IntegrationTest.java`, `@SpringBootTest`, `@Import(TestcontainersConfiguration.class)`, embedded Postgres | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/` (and adjacent packages where the test belongs to a different domain like `datarequest/`) |
| Backend unit | Plain JUnit, no Spring context | Same locations |
| Frontend component | Vitest + Testing Library; `afterEach(() => cleanup())` per `frontend/CLAUDE.md` | Co-located `__tests__/` dirs |
| Playwright | TypeScript, `frontend/e2e/tests/` | Per-feature subdirs |

### Risk Register

| Risk | Mitigation |
|---|---|
| Severity pre-flight performance on prefix patterns with high-cardinality eventTypes | Registry is small (~30 entries); pre-flight is once per request. JPQL is `IN` + `LIKE ANY` — Postgres can index-scan; tested under 50k-event load in 502.5. |
| PDF golden hash brittle (timestamps, UUIDs, fonts) | Pin clock + canonical metadata in the test; fall back to text-extraction assertions if hash fragility persists (504.6). |
| `<AuditDetailsViewer>` JSON tree bundle bloat | Hand-rolled component; no Monaco/CodeMirror per architecture §12.4.5. Verified at PR review with bundle-size diff. |
| DSAR pack tests fail because Phase 50 test fixtures change shape | Tests scoped to "audit-trail/ folder is present and contains the expected three files"; existing assertions about other folders remain unchanged. Backwards-compat asserted explicitly in 505.5. |
| Frontend audit-log placeholder existing tests block the rewrite | `audit-log-page.test.tsx` exists and tests the placeholder shape; 506.6 rewrites the page so the existing test must be updated/replaced with the new test surface. |
| Per-entity tab integrations require reading 7 different page files for context | Mitigation: 507A handles 3 entities and establishes the wiring shape; 508A reuses the shape for the remaining 4. Each slice's context-reading stays under 15 files. |

### Critical Files for Implementation

The five files most critical for the foundation (Epic 501) and most likely to ripple into every other slice:

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventTypeRegistry.java` (NEW in 501.4) — registry + longest-prefix-wins resolver; consumed by every read-time enrichment surface.
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` (existing, modified across 501/502/503/504) — adds `/metadata`, `/facets/*`, `/export.csv`, `/export.pdf` plus severity param and per-row enrichment. Backend Controller Discipline forces every change to stay one-line.
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java` (existing, modified across 501/502/503/504/505) — implements the registry pre-flight, facet snapshot, streaming queries, customer-scoped query, and actor display resolution.
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx` (existing placeholder, fully rewritten in 506.6) — the global audit log page; fans out shared primitives into `frontend/components/audit/`.
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/audit/audit-timeline.tsx` (NEW in 507.1) — the reusable per-entity timeline; reused unchanged across all seven entity detail pages in 507A + 508A.