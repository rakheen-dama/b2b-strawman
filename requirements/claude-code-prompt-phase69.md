# Phase 69 — Firm Audit View (Admin Surface)

## System Context

Kazi has been writing to an append-only audit log since **Phase 6** (Audit & Compliance Foundations, PRs #100–#106). The infrastructure is mature; the admin-facing surface has never been built.

What ships today:

- `audit_events` table — immutable, JSONB `details`, DB trigger blocks UPDATEs. Entity `AuditEvent` is `@Immutable`, has no setters, no `@Version`.
- `AuditService.findEvents(AuditEventFilter, Pageable)` — paginated query with prefix-match on `eventType` (e.g. `task.` matches `task.created`, `task.updated`, `task.deleted`).
- `AuditEventController` (`backend/.../audit/AuditEventController.java`) — two endpoints:
  - `GET /api/audit-events?entityType=&entityId=&actorId=&eventType=&from=&to=&page=&size=` — global search
  - `GET /api/audit-events/{entityType}/{entityId}` — per-entity history
  - Both gated by `@RequiresCapability("TEAM_OVERSIGHT")`.
- `AuditDeltaBuilder` — utility that writers use to produce a `{ before, after, changedFields }` shape into the `details` JSONB column. Used by most domain services.
- `AuditEventBuilder` — fluent builder used by writers (actor, source, ip, user-agent, details, entity coordinates).
- `AuditRetentionProperties` — retention config already wired; no UI for it yet.
- Writers across the codebase emit events for: projects, tasks, customers, invoices, proposals, documents, comments, time entries, rate cards, budgets, retainers, notifications, trust transactions, trust approvals, interest postings, reconciliations, matter-closure (incl. override + justification verbatim), disbursements, engagement prerequisites, information requests, acceptances, automations, data-protection actions (DSAR / export / anonymisation / retention), member role changes, capability changes, security events (login failure, permission denied).

What does **not** ship today:

- **No frontend audit UI.** `grep frontend/**/audit*` is empty. The data has never been read by an end-user.
- **No filter facet endpoints.** The controller accepts `actorId`, `entityType`, `eventType` but provides no way to enumerate what values exist in a date range — dropdowns cannot be populated without a secondary endpoint.
- **No export endpoint.** All queries paginate; there is no CSV / PDF export suitable for an auditor, regulator, or subpoena response.
- **No integration with DSAR.** Phase 50 (`DataExportService`, `DataAnonymizationService`) produces a data-subject export pack but **does not include** the subject's audit trail. Under POPIA a data subject may request "what has been done with my data" — that is precisely what `audit_events` records.
- **No surfacing of legally sensitive events.** Matter-closure overrides (Phase 67 — writes `override_used=true` + operator's justification into `details.justification`) are invisible to compliance officers. Trust-transaction approvals (Phase 60) are invisible. Data-protection actions (Phase 50) are invisible.

**Observed demand.** The Phase 67 gap report (PR #1081) flags `override_used` + justification as captured in `AuditEvent.details` JSONB — PASS at the write layer. The read layer does not exist. Any compliance review ("who approved closure of Matter 0042 and why?") requires raw `psql` access to the tenant schema today. This is precisely the kind of question that an admin UI exists to answer in under five seconds.

## Objective

Build the firm-side admin UI that exposes the audit event stream — a filterable global log page, a reusable per-entity timeline component drilled into the detail pages of legally-sensitive entities, compliance-grade CSV and PDF export for disclosure, and wire the subject's audit trail into the existing DSAR export pack. Ship a small number of targeted backend extensions (facet endpoints, export endpoints, lightweight event-type metadata) — no new write paths, no new event sources, no new entities beyond the metadata registry.

## Constraints & Assumptions

- **No new write paths.** Phase 69 does not add new audit event sources. The set of events being written is already correct and sufficient; this phase is a *read* project, not a *write* project.
- **No portal activity trail in this phase.** The portal side of "who viewed / downloaded / accepted what" is explicitly deferred. Phase 69 ships the firm-admin surface only. (See Phase 68 ideation 2026-04-18; founder decision 2026-04-20.)
- **No per-user activity heatmaps.** Not a behavioural analytics product; this is a forensic log reader.
- **No streaming / websocket live tail.** HTTP pagination is sufficient. Keep infrastructure simple.
- **No alert routing.** Email / Slack / webhook push on sensitive events is deferred to the integrations phase.
- **No retention-policy UI.** `AuditRetentionProperties` already governs retention. A UI for that belongs with broader compliance settings work, not here.
- **Capability gate is existing.** All new firm-side endpoints gate with `@RequiresCapability("TEAM_OVERSIGHT")` — same capability the existing controller uses. No new capability introduced.
- **v1 event rendering is generic, not templated.** The `details` JSONB column is shown via a generic JSON diff viewer (before / after / changed-fields when `AuditDeltaBuilder` shape is present; raw JSON tree otherwise). Event type is rendered as title-cased text (`task.status_changed` → "Task · Status Changed"). A handcrafted template-per-event-type registry is **deferred** (~60 event types, each requiring a template; not justified at v1).
- **Lightweight event-type metadata.** A small in-code registry supplies human labels, icon, severity (`INFO` | `NOTICE` | `WARNING` | `CRITICAL`), and optional group (`Security` | `Compliance` | `Financial` | `Data` | `Standard`) per event type — driven by the eventType string prefix where possible (`security.*` → Security / WARNING, `trust.*` → Financial / NOTICE, `matter.closure.override_used` → Compliance / CRITICAL). Covers grouping and the sensitive-events filter preset without committing to full per-event templates.
- **DSAR integration is additive, not rewritten.** Extend `DataExportService` to include an `audit-trail/` folder in the exported pack; do not rewrite the service.
- **PDF export is first-class.** CSV is for internal review; PDF is the legal artefact. Both are mandatory. PDF generation reuses the existing Tiptap → PDF pipeline (Phase 12 / 31 / 42) — no new rendering infrastructure.
- **Streamed export.** Exports can legitimately be large (a year of a busy tenant's events can exceed 100k rows). Exports must stream; do not buffer the full result set in memory.
- **No firm-side data migration.** At most one global migration (V108+) if the event-type metadata is table-backed; otherwise zero migrations. Phase 69 is overwhelmingly a Java code + React page phase.

---

## Section 1 — Backend Extensions

### 1.1 Facet Endpoints (for filter dropdowns)

The existing controller accepts filter parameters but provides no way to enumerate valid values. Add:

- `GET /api/audit-events/facets/actors?from=&to=` — list of distinct `actorId`s with their display names and event counts in the date range. Response: `[{ actorId, actorDisplayName, actorType, eventCount }]`. Capped at top 500 by count.
- `GET /api/audit-events/facets/event-types?from=&to=` — list of distinct `eventType` strings with counts, enriched by the event-type metadata registry (see 1.3). Response: `[{ eventType, label, severity, group, count }]`.
- `GET /api/audit-events/facets/entity-types?from=&to=` — list of distinct `entityType` strings with counts. Response: `[{ entityType, label, count }]`.

All three gated by `@RequiresCapability("TEAM_OVERSIGHT")`. All three hit a single service method `AuditService.facets(from, to)` — don't fragment.

### 1.2 Export Endpoints

- `GET /api/audit-events/export.csv?<same filter params as list endpoint>` — streams a CSV (RFC 4180) with one row per event. Columns: `occurredAt` (ISO 8601), `eventType`, `label` (from metadata registry), `severity`, `entityType`, `entityId`, `actorId`, `actorDisplayName`, `actorType`, `source`, `ipAddress`, `userAgent`, `detailsJson` (compact). `Content-Disposition: attachment; filename="audit-events-{tenantSlug}-{fromDate}-{toDate}.csv"`.
- `GET /api/audit-events/export.pdf?<same filter params>` — streams a compliance-formatted PDF. Header: org branding + date range + filter summary + generation timestamp + exporting actor. Table of events (landscape). Footer: page N of M + tenant hash + `generated by Kazi audit export`. Capped at 10 000 rows; a larger filtered set returns a 413 with a `ProblemDetail` suggesting a narrower date range.
- Both endpoints stream. CSV streams row-by-row. PDF uses the existing Tiptap → PDF pipeline (Phase 12 / 31 / 42) with a new `audit-export` template under the document template pack.
- Both capability-gated by `TEAM_OVERSIGHT`.
- Both write an audit event of their own: `audit.export.generated` with `details.filter`, `details.rowCount`, `details.format`. An audit export is itself an auditable action.

### 1.3 Event-Type Metadata Registry

New Java class `AuditEventTypeRegistry` under `backend/.../audit/`:

```java
public record AuditEventTypeMetadata(
    String eventType,       // exact string, may be a glob prefix like "security.*"
    String label,           // human label, e.g. "Matter Closure Override Used"
    AuditSeverity severity, // INFO | NOTICE | WARNING | CRITICAL
    AuditEventGroup group   // SECURITY | COMPLIANCE | FINANCIAL | DATA | STANDARD
) {}
```

Registry is populated in-code from a static list (no migration). Entries cover, at minimum:

| eventType / prefix | Label | Severity | Group |
|---|---|---|---|
| `security.login.failure` | Login Failed | WARNING | Security |
| `security.permission.denied` | Permission Denied | WARNING | Security |
| `security.*` | (fallback) Security Event | NOTICE | Security |
| `matter.closure.override_used` | Matter Closure Override Used | CRITICAL | Compliance |
| `matter.closure.*` | Matter Closure | NOTICE | Compliance |
| `trust.transaction.approved` | Trust Transaction Approved | NOTICE | Financial |
| `trust.transaction.rejected` | Trust Transaction Rejected | WARNING | Financial |
| `trust.*` | Trust Activity | NOTICE | Financial |
| `dataprotection.dsar.*` | Data Subject Request | NOTICE | Data |
| `dataprotection.export.*` | Data Export | NOTICE | Data |
| `dataprotection.anonymization.*` | Data Anonymization | NOTICE | Data |
| `dataprotection.*` | Data Protection | NOTICE | Data |
| `audit.export.generated` | Audit Log Exported | NOTICE | Compliance |
| `member.role.changed` | Member Role Changed | WARNING | Security |
| `orgrole.capability.changed` | Role Capabilities Changed | WARNING | Security |
| `invoice.*` | Invoice | INFO | Financial |
| `proposal.*` | Proposal | INFO | Financial |
| `(default)` | (title-case of eventType) | INFO | Standard |

Lookup is longest-prefix-wins. Frontend consumes via `/api/audit-events/facets/event-types` (enriched) or via a standalone `GET /api/audit-events/metadata` endpoint if the frontend wants the full catalogue. Builder picks one — do not ship both.

### 1.4 Actor Display Resolution

Audit events store `actorId` (a `Member.id`) but not a display name — the member may have been deleted, renamed, or soft-removed since the event. The backend resolves display names at read time via a single join query (not N+1). When a member is no longer resolvable, the response falls back to `"Former member ({actorId})"`. The portal / API-key / webhook actor types (`actorType != "MEMBER"`) render as `"Portal Contact"`, `"System"`, `"Automation"`, `"API Key"` per the existing `actorType` enum values.

### 1.5 Tests

- ~6 backend integration tests: facet endpoints (empty range, populated range, cap at 500), export endpoints (CSV shape, PDF shape via hash comparison, row-count cap, filter validation), event-type metadata registry longest-prefix resolution, actor display resolution with missing / soft-deleted members, audit event emitted for export action, capability gate (non-owner gets 403).

---

## Section 2 — Global Audit Log Page

### 2.1 Route & Shell

New page `frontend/app/(authenticated)/settings/audit-log/page.tsx`. Adds a sidebar entry under `Settings` → `Audit Log` (or, if the Settings layout from Phase 44 already has a hub, add it to that hub). Gated client-side by `TEAM_OVERSIGHT` capability via the existing `<CapabilityGate>`.

### 2.2 Layout

```
┌──────────────────────────────────────────────────────────┐
│ Audit Log                                [Export ▼]      │
├──────────────────────────────────────────────────────────┤
│ Filters:                                                 │
│  Date range: [Last 7 days ▾]   Severity: [All ▾]         │
│  Actor: [Any ▾]    Event type: [Any ▾]    Entity: [Any ▾]│
│  Preset: [None ▾]  (Sensitive | Compliance | Security…)  │
├──────────────────────────────────────────────────────────┤
│ Time        Severity  Event              Actor    Entity │
│ 14:02:13    WARNING   Permission Denied  Alice   Task 42 │
│ 13:58:07    CRITICAL  Closure Override   Bob     Matter 7│
│  ▼ (expanded)                                            │
│    Justification: "Client returned funds — all…"         │
│    Details (before / after): [diff viewer]               │
│    Source: UI   IP: 41.x.x.x   Agent: Firefox/…          │
│ …                                                        │
│ [Load more]                                              │
└──────────────────────────────────────────────────────────┘
```

- Table virtualised or simple pagination — builder picks; prefer pagination for audit semantics (stable page numbers over time).
- Row expansion shows `details` via a generic diff viewer when `AuditDeltaBuilder` shape is present (`{ before, after, changedFields }`), otherwise via a collapsible JSON tree viewer.
- Severity renders as a small coloured pill (INFO grey, NOTICE blue, WARNING amber, CRITICAL red). Group appears as a subtle label.
- Entity cell renders a deep-link to the entity's detail page when the entity type is known and the entity still exists (soft-deleted → render as text with a strikethrough). Unknown entity types render the `{entityType}:{entityId.short}` literal.
- Actor cell renders the member's name and, on hover, the full `actorId` + `actorType` + `source` + `ipAddress` (where not null).

### 2.3 Filter Presets

Four built-in presets exposed via the Preset dropdown. Selecting a preset populates the other filters.

| Preset | Filters applied |
|---|---|
| Sensitive (Last 30 days) | severity ∈ { WARNING, CRITICAL }, date=last 30 days |
| Compliance | group = COMPLIANCE, date=last 90 days |
| Security | group = SECURITY, date=last 7 days |
| Financial approvals | eventType ∈ { `trust.transaction.approved`, `trust.transaction.rejected`, `invoice.sent`, `invoice.voided` }, date=last 30 days |

These are client-side combinations of the existing filter params — no backend preset table, no persisted named-filters. Saved custom filters are out of scope for this phase.

### 2.4 Export UX

Export dropdown offers **Download CSV** and **Download PDF**. Both use the current filter + date range. Both show an in-flight indicator, then trigger a blob download. If the filter exceeds the 10 000-row PDF cap, the PDF option disables with a tooltip: "Narrow the date range — PDF export limited to 10,000 events."

### 2.5 Copy & Empty States

- Empty state (no events in range): "No audit events in this range. Try widening the date range or changing filters." — reuse `<EmptyState>` pattern (Phase 43).
- Empty state (no events ever — fresh tenant): "The audit log is empty. Activity is logged automatically once team members start working in Kazi."
- All copy goes through the i18n message catalogue (Phase 43 327A).

### 2.6 Tests

- ~4 frontend tests: filter combinations map to correct API params; row expansion shows diff viewer; preset selection populates filters; export dropdown triggers blob download.
- ~1 Playwright smoke: login as admin → navigate to audit log → apply Sensitive preset → verify row count > 0 after seeded scenario.

---

## Section 3 — Per-Entity Audit Timeline

### 3.1 Reusable Component

New React component `frontend/components/audit/audit-timeline.tsx`:

```tsx
<AuditTimeline
  entityType="matter"    // or customer, project, invoice, trust_transaction
  entityId={matter.id}
  initialPageSize={20}
  showFilters={false}    // compact mode for detail-page tabs
  severityPillSize="sm"
/>
```

Uses the existing `GET /api/audit-events/{entityType}/{entityId}` endpoint. Renders as a vertical timeline (not a table) — one node per event, chronological top→bottom, expandable details. Reuses the same event-type metadata, severity pills, diff viewer, and actor display logic as Section 2.

### 3.2 Integration Points

Drop `<AuditTimeline>` into existing detail pages as a new **"Audit"** tab (tab label comes through terminology so legal-za can alias to "Audit Trail"):

| Detail page | Tab position | entityType |
|---|---|---|
| Customer Detail (`/customers/{id}`) | Last tab | `customer` |
| Project / Matter Detail (`/projects/{id}`) | Last tab | `project` |
| Invoice Detail (`/invoices/{id}`) | Last tab | `invoice` |
| Trust Transaction Detail (`/trust/transactions/{id}`) [legal-za] | Last tab | `trust_transaction` |
| Matter Closure detail (`/matters/{id}/closure`) [legal-za, if a standalone page exists] | Last tab | `matter_closure` |
| Proposal Detail (`/proposals/{id}`) | Last tab | `proposal` |
| Information Request Detail (`/requests/{id}`) | Last tab | `information_request` |

Tab is capability-gated with `TEAM_OVERSIGHT` — same rule as the global log page. Members without the capability see no tab.

### 3.3 Tests

- ~3 frontend tests: timeline rendering, expansion, empty state.
- ~1 Playwright: open matter-closure detail page, verify "Audit" tab renders the override event with justification visible on expand.

---

## Section 4 — DSAR Pack Integration

### 4.1 Pack Structure

Phase 50 `DataExportService` produces a ZIP pack for a subject request. Extend the pack with a new top-level folder:

```
{pack-root}/
├── customer.json               (existing)
├── projects/                   (existing)
├── invoices/                   (existing)
├── documents/                  (existing)
├── ...
└── audit-trail/                (NEW)
    ├── events.json             — full event list as JSON array
    ├── events.csv              — same data in CSV shape (human-readable)
    └── README.txt              — short plain-text explanation of the file formats
```

### 4.2 Scope of Events

For a DSAR for customer X, include every `AuditEvent` where **any** of the following is true:

- `entityType="customer"` AND `entityId=X.id`
- `entityType IN {"project", "invoice", "proposal", "information_request", "document", "trust_transaction", "acceptance_request"}` AND the entity belongs to customer X (the export service already computes this set for its existing folders — reuse that customer-entity resolution).
- The event's `details` JSONB contains a reference to `customerId=X.id` (e.g. `details.customerId`). This is a best-effort JSONB-path query; a dedicated indexed query path is not required for DSAR volumes.

Events older than the retention horizon are not included (already filtered by `AuditRetentionProperties`).

### 4.3 Sanitisation

Unlike the portal sanitisation in Phase 68, **DSAR exports are not sanitised** — a data subject is entitled to the full internal record that pertains to them under POPIA §23. That includes internal notes and the full `details` JSON. This is a deliberate decision; document in ADR.

### 4.4 Wiring

Extend `DataExportService` with a `buildAuditTrail(customer, zipOutputStream)` step. The step:

1. Streams events via `AuditService.findEventsForCustomer(customerId)` (new service method — builder may reuse `findEvents` with a composite filter if simpler).
2. Writes `audit-trail/events.json` and `audit-trail/events.csv` directly to the ZIP stream — no buffering in memory.
3. Writes `audit-trail/README.txt` (static content).

### 4.5 Tests

- ~2 backend integration tests: DSAR pack includes `audit-trail/` folder with events in all three files; audit-trail excludes events belonging to other customers.

---

## Section 5 — Sensitive-Event Dashboard Widget

A small surface on the firm admin dashboard that provides an at-a-glance view of recent legally-sensitive events. This is the one section that can be cut if the phase is running hot — but the ROI is high and the slice count is low.

### 5.1 Widget

New dashboard tile `frontend/components/dashboard/widgets/sensitive-events-widget.tsx`. Appears on the company dashboard (Phase 9 76/78) for members with `TEAM_OVERSIGHT`. Shows:

- Event count in last 7 days by severity (three pills: NOTICE / WARNING / CRITICAL).
- Top 5 most recent CRITICAL + WARNING events, each clickable → opens the global audit-log page pre-filtered to that event.
- "View all" link → global audit-log page with the Sensitive preset applied.

### 5.2 Data

Reuses `GET /api/audit-events?severity=WARNING,CRITICAL&from=<7d>&size=5`. Requires the list endpoint to accept `severity` as a multi-valued filter — a small extension to `AuditEventFilter`. The existing filter only has `entityType`, `entityId`, `actorId`, `eventType`, `from`, `to`; adding `severity` is a record expansion + query filter change. The severity is derived at read-time from the metadata registry (Section 1.3) — severity is not a persisted column on `audit_events`.

### 5.3 Tests

- ~1 backend integration test: severity filter returns only events whose metadata resolves to the requested severity.
- ~2 frontend tests: widget renders empty state; widget deep-links with correct filter params.

---

## Section 6 — QA Capstone (Admin POV)

A 30-day admin-perspective lifecycle script to verify the audit surfaces actually work for the kinds of questions a firm owner asks.

### 6.1 Script

New file `qa/testplan/demos/admin-audit-30day-keycloak.md`. One script, all three verticals marked per checkpoint.

Checkpoints (rough; builder refines):

- **Day 0**: Seed audit events (login, matter creation, trust deposit [legal-za], deadline seeding [accounting-za], retainer setup [consulting-za]). Admin logs in, opens Audit Log page for the first time — verifies Sensitive preset is empty. Exports an empty CSV as sanity check.
- **Day 5**: Simulate a permission-denied event (member attempts owner-only action). Verify it appears under Security preset with WARNING severity.
- **Day 10 [legal-za]**: Owner approves a trust transaction. Verify event appears under Financial approvals preset.
- **Day 15 [legal-za]**: Owner triggers a matter-closure override with justification. Verify CRITICAL severity, justification readable in expanded detail, dashboard widget shows the event in top-5.
- **Day 20**: Admin opens a customer's detail page, Audit tab — verifies the event list matches Day-0-to-Day-20 activity for that customer.
- **Day 22**: Admin exports a PDF of the last 30 days. Opens the PDF, verifies header, filter summary, row formatting, page numbering, and that the export itself generated an `audit.export.generated` event (reflexive audit).
- **Day 25**: Data subject (customer) submits a DSAR. Admin fulfils via Phase 50 pipeline. Admin opens the generated pack, opens `audit-trail/events.csv`, verifies the subject's events are present and that events belonging to other customers are absent.
- **Day 30**: Admin triggers audit-log-limit test — filter for a 2-year window on a busy seeded tenant, verifies the 10 000-row PDF cap fires a graceful error. Narrows range, succeeds.

### 6.2 Screenshot Baselines

Under `documentation/screenshots/phase69/`:

- Audit Log page — empty state, populated state, expanded row with override justification visible (3)
- Audit Log page — each preset applied (4)
- Sensitive Events widget — populated on dashboard (1)
- Per-entity Audit tab on Matter Closure detail — 1 shot
- Per-entity Audit tab on Customer detail — 1 shot
- PDF export — first page + a middle page (2)

### 6.3 Gap Report

Deliverable: `tasks/phase69-gap-report.md`. Documents UX rough edges, missing event coverage (any domain flow where `AuditEventBuilder` was never called), performance observations on large filters, and proposals for Phase 70+ (template registry, portal activity trail, alert routing).

### 6.4 Execution

Via `/qa-cycle-kc qa/testplan/demos/admin-audit-30day-keycloak.md`. Iterate to green before merging.

---

## Section 7 — Proposed Epic / Slice Breakdown

Rough shape for `/breakdown`. Builder sanity-checks and resequences.

| Epic | Title | Scope | Slices |
|---|---|---|---|
| A | Audit backend extensions | Backend | A1 (facet endpoints + actor display resolution + event-type metadata registry), A2 (CSV + PDF export endpoints + audit-export event emission + severity filter support) |
| B | Global audit log page | Frontend | B1 (page shell, filters, paginated table, row expansion, generic diff / JSON viewer, deep links, empty states), B2 (filter presets + export dropdown + Playwright smoke) |
| C | Per-entity audit timeline | Frontend | C1 (`<AuditTimeline>` component + integration into Customer / Project / Invoice detail pages), C2 (integration into Trust Transaction / Matter Closure / Proposal / Information Request detail pages) |
| D | DSAR audit-trail integration | Backend | D1 (DataExportService extension + customer-scoped event query + ZIP streaming of audit-trail folder) |
| E | Sensitive-events dashboard widget | Frontend | E1 (widget + severity filter wiring + deep-link to preset) |
| F | Admin-POV 30-day QA capstone | E2E / Process | F1 (script draft + seed scenarios), F2 (run + screenshots + gap report) |

**~10 slices total.** Phase sits between "tight" (minimal 3-epic version) and "standard" (this breakdown). Epic E is the one to cut if scope is tight.

Epics A and B must sequence A → B (frontend depends on facets + exports). Epic C depends on A1 only (actor display resolution is shared). Epic D depends on A1 (shared query path). Epic E depends on A2 (severity filter). Epic F is last.

---

## Out of Scope

- **Portal activity trail** — clients seeing "when did my firm log in / view my documents / download my files". Explicitly deferred; founder call 2026-04-20.
- **Template-per-event-type registry** — full handcrafted human summary for each of ~60 event types. Generic viewer + title-case labels ships this phase; bespoke templates are a polish phase.
- **Streaming / websocket live tail.** HTTP pagination only.
- **Alert routing** (email / Slack / webhook on sensitive events). Deferred to integrations phase.
- **Retention-policy UI.** `AuditRetentionProperties` is config-driven; no UI for it in this phase.
- **Saved custom filters / named queries.** Built-in presets only.
- **Per-user activity heatmaps / behavioural analytics.** Not a behavioural analytics product.
- **Tamper-proof / hash-chained audit log.** `audit_events` is append-only at DB trigger level; cryptographic chaining is a future phase.
- **Audit-log-as-a-report-type** in the Phase 19 reporting engine. The dedicated page + export is sufficient; forcing audit into the generic report framework would compromise both surfaces.
- **Sensitive-event push notifications in-app.** Bell / toast surfacing is deferred.
- **Event aggregation / deduplication.** Each event is shown individually; no "10 similar events collapsed into one row" UX.
- **Free-text search across `details` JSONB.** Filtering by structured fields only.
- **Multi-tenant cross-tenant audit view** for platform admins. Each tenant sees its own log; platform-admin cross-tenant forensics is a separate Phase 39-adjacent surface.

---

## ADR Topics

- **ADR-??? — Audit UI is a read layer over existing writes.** Phase 69 introduces no new event sources. Writes are already correct; the gap is read. Records what that means (no write-path changes; any missing event coverage goes in the gap report as Phase 70 input, not Phase 69 scope creep).
- **ADR-??? — Generic diff viewer over template-per-event registry for v1.** Records the trade-off: template-per-event-type gives a nicer reading experience but costs ~60 template slices and ages fast as event shapes evolve. Generic viewer + metadata registry ships the utility now; templates can be layered in later per event type based on demand.
- **ADR-??? — Severity derived at read time, not persisted.** `audit_events` rows are immutable and pre-date the severity classification. A migration to add a severity column would require a backfill and lock in today's classification. Deriving from the metadata registry at read time means severity classifications can evolve without touching historical rows.
- **ADR-??? — DSAR audit-trail export is unsanitised.** POPIA §23 entitles the subject to their full record. Internal notes and raw `details` JSON are included. Contrast with Phase 68's portal sanitisation, which applies to live read-model data the client sees through the portal UI, not to one-off regulatory exports.
- **ADR-??? — PDF export rides the existing Tiptap pipeline.** New template under the document template pack (`audit-export` category). Rationale: reuse reduces drift with invoice / closure-letter rendering; cost is low; a dedicated PDF library would duplicate the Tiptap + PDF plumbing already carrying Phases 12 / 31 / 42.
- **ADR-??? — Audit export is itself auditable.** The `audit.export.generated` event ensures the log records its own disclosures. Rationale: an admin running a compliance export is itself a significant action — an auditor investigating "did anyone extract our data?" must be able to answer it from the log.

---

## Style & Boundaries

- Follow all conventions in `frontend/CLAUDE.md` and `backend/CLAUDE.md`.
- Backend controllers remain one-liner delegates (per `backend/CLAUDE.md` Controller Discipline). `AuditEventController` already conforms; new endpoints added to it must conform too. Do not expand business logic into the controller.
- Capability gate: every new backend endpoint is `@RequiresCapability("TEAM_OVERSIGHT")`. No new capability is introduced.
- All new frontend pages / tabs / widgets gate client-side via the existing `<CapabilityGate>` (Phase 41 318A).
- New code lives under `backend/.../audit/` (backend) and `frontend/components/audit/` + `frontend/app/(authenticated)/settings/audit-log/` (frontend). Do not fragment across packages.
- Migrations: at most one (V108) if the event-type metadata registry is chosen to be table-backed. Default is Java in-code registry — no migration needed.
- Event-type metadata registry is the single source of truth for label + severity + group. Frontend must not duplicate this mapping.
- Streaming exports: use Spring's `StreamingResponseBody` for CSV; use the existing PDF pipeline with chunked-output for PDF. Do not `ByteArrayOutputStream`-buffer multi-megabyte responses.
- JSON / diff viewer on the frontend: use a lightweight library (e.g. `react-json-view` or hand-rolled component) — do not pull in a heavy editor just to pretty-print JSON. Respect Phase 3 / 44 design tokens.
- Copy through the i18n message catalogue (Phase 43 327A). Labels like "Matters" (legal-za) vs "Projects" (default) flow through the terminology key.
- Empty states use the Phase 43 `<EmptyState>` pattern.
- Tests follow the taxonomy in `backend/CLAUDE.md` — integration via `@SpringBootTest` + `TestcontainersConfiguration`; never introduce new Docker containers.
- DSAR extension must not break existing Phase 50 tests. Add new assertions, do not rewrite the existing pack shape.
- Multi-vertical: the audit log must render for `legal-za`, `accounting-za`, `consulting-za`, and unprofiled tenants alike. Terminology substitution is the only variance between them.
- No changes to the portal (`portal/`) in this phase. Portal activity trail is explicitly out of scope.
- No changes to the event-writer side of the codebase. If a flow discovers it does not emit a needed event, log it in the gap report — do not fix it opportunistically in Phase 69.
