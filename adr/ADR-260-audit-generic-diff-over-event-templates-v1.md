# ADR-260: Generic Diff Viewer over Template-per-Event Registry for v1

**Status**: Accepted

**Context**:

Audit events carry a free-form `details` JSONB column. Most domain writers populate it via `AuditDeltaBuilder` ([ADR-029](ADR-029-audit-logging-abstraction.md)), producing a `{ field: { from, to } }` shape that lends itself to a generic before/after diff viewer. Some events carry richer or different shapes — `matter.closure.override_used` carries `details.justification` (a free-string explanation), `audit.export.generated` carries `details.{filter, rowCount, format}`, `member.role_changed` carries `details.{previousRole, newRole, overrides}`. The shapes vary because the events vary.

The Phase 69 admin UI must render every event meaningfully. Two ends of the design space:

- A **template-per-event-type registry** where each of the ~60 event types in the codebase has a hand-crafted React component that knows the precise shape of `details` for that event and renders it as a polished, human-readable summary ("Bob approved a trust transaction of R 12,450.00 from the Smith Estate matter"). Reading this is delightful; writing and maintaining sixty templates is not.
- A **generic viewer** that detects the `AuditDeltaBuilder` shape and shows a side-by-side diff, falling back to a JSON tree viewer for non-delta shapes. This is one component covering everything; the reading experience is functional rather than polished.

The trade-off lives in maintenance velocity vs. reading fidelity. Templates age — every time an event's `details` shape evolves, the template needs an update. There are sixty event types today; a year from now there will be more. A template registry that drifts out of sync with `details` shapes is worse than no registry, because users learn to distrust the rendering and start expanding the raw JSON anyway.

**Options Considered**:

1. **Full template registry now.** Hand-craft a React component for each of the ~60 event types, mapping `details` to a polished summary. Maintain a registry file mapping `eventType` → component.
   - Pros:
     - Best possible reading experience. A compliance officer scanning the audit log gets human-readable English on every row.
     - The registry is version-controlled — drift between event shape and template is at least visible in PR diffs.
     - Every event type can have bespoke icons, formatting, and contextual links.
   - Cons:
     - Sixty components is a phase-sized scope by itself, on top of the rest of Phase 69. /breakdown rejects this as a viable v1 slice list.
     - Template maintenance becomes a tax on every domain phase: introducing a new event type or changing an existing one means an audit-template PR. Not all phases will remember; drift is the steady state.
     - Some `details` shapes are too varied for a single template — the same event type might carry slightly different shapes depending on which writer called it (multiple writers emit `task.updated`, for example). Templates would need defensive null-guards on every field.
     - A template registry encodes the *current* understanding of each event's `details` — when shapes evolve to capture new information, the template silently drops it.

2. **Generic diff + JSON fallback + lightweight metadata registry (CHOSEN).** Single `<AuditDetailsViewer>` component. Detects `AuditDeltaBuilder` shape (`{ field: { from, to } }` or `{ before, after, changedFields }`) and renders a diff. Anything else falls back to a compact JSON tree viewer. A separate, much smaller registry (`AuditEventTypeRegistry` — ~30 entries covering prefixes, not full templates) supplies *label* and *severity* per event type, but not a render template.
   - Pros:
     - One component, one test surface. Adding a new event type is zero-effort for the audit UI — the generic viewer handles it.
     - The metadata registry covers what users actually need at the row level (label + severity pill + group filter), without committing to per-event rendering.
     - JSON tree fallback means *no* event is unrenderable. If a writer emits a weird shape, the user can still see what's there.
     - Reading experience is functional rather than polished — but for an audit log read by compliance officers (not customers), functional is the right floor.
     - Templates can be layered in later, per event type, based on actual demand. Phase 70+ might handcraft templates for the top-5 sensitive event types if the gap report flags reading-experience friction.
   - Cons:
     - Reading experience is plainer. A row that says "Trust Transaction Approved" with a `{ amount: { from: 0, to: 12450 } }` diff is less satisfying than "Bob approved a R 12,450 deposit from Smith Estate".
     - The diff viewer is generic — it doesn't format currencies, dates, UUIDs, or enums specially. A `from: "PROSPECT"` `to: "ACTIVE"` cell shows the literal enum names rather than human-readable status labels.
     - For events that don't use `AuditDeltaBuilder` (matter-closure override, exports), the JSON tree fallback is less polished than a bespoke template would be.

3. **Hybrid — handcraft templates for the top-5 sensitive event types only; generic for the rest.** Bespoke templates for `matter.closure.override_used`, `trust.transaction.approved`, `trust.transaction.rejected`, `security.permission.denied`, `member.role_changed`. Generic viewer everywhere else.
   - Pros:
     - Polished reading experience for the events that matter most to compliance officers.
     - Bounded scope — five templates, not sixty.
     - Templates can be added incrementally without committing to a full registry.
   - Cons:
     - "Top-5 sensitive" is a judgment call that won't survive contact with the gap report. The 30-day capstone may flag "trust.transaction.deposited" as a top-5 flow that the hybrid missed.
     - Two code paths for rendering means two test surfaces, two failure modes, and a non-obvious "why does this event have a nice template and that one doesn't" UX inconsistency.
     - Templates still age — five templates will drift just as readily as sixty.
     - The phase grows by five additional template slices, each with their own test coverage — non-trivial scope increase relative to Option 2.

**Decision**: Option 2 — generic diff viewer + JSON fallback + lightweight metadata registry.

**Rationale**:

The audit log is read by compliance officers and (occasionally) auditors. They are forensic readers, not casual browsers — they care that every event is *visible* and *truthful* far more than that every event is prose-formatted. A generic diff viewer that always shows the truth is more useful than a template registry that mostly shows polished prose but occasionally drops fields it doesn't know about.

Maintenance is the deciding factor. Sixty templates is a meaningful ongoing tax on every domain phase — each new event type or shape change is now a multi-PR change. Kazi's phases are tightly scoped; phases that introduce templates implicitly produce ongoing audit-template debt. A generic viewer offloads that maintenance to zero.

The hybrid (Option 3) is a tempting middle ground but doesn't actually solve the maintenance problem — it just shifts where it occurs. And the threshold "top-5 sensitive" is unstable; the 30-day capstone gap report ([ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md)) will surface which events deserve bespoke templates. Phase 70+ can layer those in selectively, with empirical justification.

The metadata registry (`AuditEventTypeRegistry`) is the right level of structure to commit to in v1: a small in-code catalogue (~30 entries) of label, severity, and group per event-type prefix. It covers grouping (the four filter presets in §12.4.4 of the architecture doc) and the severity-pill rendering, without claiming to know how to format every event's body.

**Consequences**:

- Positive:
  - Single rendering component — `<AuditDetailsViewer>` — covers every event. Adding a new event type elsewhere in the codebase requires zero changes here.
  - The metadata registry is the only audit-related artefact that adjacent phases need to maintain, and only when they introduce a new event-type prefix that deserves a non-default severity classification (most events default to INFO/Standard correctly).
  - JSON tree fallback guarantees no event is unrenderable; the audit log never has "we don't know how to show this row" gaps.
  - Phase 70+ has a clear runway to selectively introduce bespoke templates per event type, prioritised by the Phase 69 gap report.

- Negative:
  - Reading experience is plainer than a template registry would offer. Compliance officers see structured data rather than human-readable prose.
  - The generic viewer doesn't format currencies, enums, or related-entity references contextually. A `from: PROSPECT, to: ACTIVE` shows the enum literal, not "Prospect → Active".
  - Some events that lack the `AuditDeltaBuilder` shape (matter-closure overrides with `details.justification`, audit-export events with `details.filter`) display as JSON trees rather than as polished sentences. The justification text *is* visible — just not rendered as a quoted callout.

- Neutral:
  - The frontend bundle remains small — the JSON tree component is lightweight (`react-json-view` or a hand-rolled component, deliberately not a heavy editor like Monaco — see §12.4.5 of the architecture doc).
  - Templates can be retrofitted later without restructuring the registry — adding a `renderComponent` field to `AuditEventTypeMetadata` (currently `eventType, label, severity, group`) would extend the registry without breaking the resolver.

- Related: [ADR-259](ADR-259-audit-ui-read-only-no-write-changes.md) (read-only scope discipline — same "ship the v1 floor, layer polish later" stance), [ADR-029](ADR-029-audit-logging-abstraction.md) (`AuditDeltaBuilder` shape — the input the generic diff viewer is designed to render), [ADR-261](ADR-261-audit-severity-derived-read-time.md) (severity registry — the metadata catalogue this ADR commits to), [ADR-263](ADR-263-audit-pdf-via-tiptap-pipeline.md) (the PDF export uses the same generic-diff-rendered shape).
