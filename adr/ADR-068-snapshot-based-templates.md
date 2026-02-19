# ADR-068: Snapshot-Based Templates

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Phase 16 introduces project templates — reusable blueprints that capture a project's structure (tasks, tags, default settings) and allow rapid creation of new projects. When a user creates a project from a template, the system must decide whether the resulting project tasks maintain a live reference back to the template definition (reference-based) or whether task data is copied at creation time and exists independently thereafter (snapshot-based).

This decision has significant implications for auditability, predictability, and operational safety. Firms using recurring schedules will auto-create projects from templates on a weekly, monthly, or quarterly basis. If a template change retroactively altered the structure of already-created projects — or changed the meaning of future scheduled projects mid-cycle — the results would be unpredictable and potentially dangerous in a compliance context. The platform already established a precedent with checklist templates (Phase 14) and document templates (Phase 12), both of which use snapshot semantics.

**Options Considered**:

1. **Snapshot-based templates (chosen)** — Template tasks are copied at instantiation time. The created project and its tasks have no ongoing relationship with the template.
   - Pros: Predictable behavior — changing a template never affects existing projects; safe for recurring schedules — each execution creates an independent project; simple implementation — copy fields, no reference tracking; aligns with existing patterns (checklist templates Phase 14, document templates Phase 12); auditability is straightforward — the project records exactly what was created.
   - Cons: Template improvements do not propagate to existing projects; if a template had an error, all projects already created from it retain the error; no way to see "which projects would be affected" by a template change.

2. **Reference-based templates** — Projects maintain a live FK to the template. Tasks are generated dynamically or synced when the template changes.
   - Pros: Template updates propagate to all linked projects; single source of truth for project structure.
   - Cons: Dangerous in a recurring schedule context — changing a template silently alters future auto-created projects; complex sync logic for in-progress projects (what happens to tasks that are already completed?); breaks auditability — the project's structure at creation time is not preserved; no precedent in the existing codebase; requires conflict resolution when a template changes after tasks have been modified.

3. **Hybrid — snapshot with optional re-sync** — Copy at creation (snapshot), but provide a manual "Update from Template" action that re-syncs.
   - Pros: Best of both worlds in theory — predictable by default, updatable on demand.
   - Cons: Re-sync logic is complex (merge new tasks, update existing, handle deleted tasks with time entries); UI complexity to show diff between project and current template; marginal value — if the template was wrong, it is simpler to fix the individual project directly.

**Decision**: Option 1 — snapshot-based templates.

**Rationale**:

Templates in a professional services context are configuration, not contracts. A firm's monthly bookkeeping template might evolve over time — adding a new tax reconciliation task, adjusting estimated hours — but those changes should only affect *future* projects, not the February engagement that is already in progress. The snapshot pattern provides this natural boundary: the template defines what gets created, and the project lives independently from that point forward.

This is the same principle applied in Phase 14's checklist templates (checklist instances are snapshots of the template items at instantiation time) and Phase 12's document templates (the rendered document captures the template content at generation time, not at viewing time). Consistency across these three template-like features reduces cognitive load for both developers and users. The recurring schedule feature amplifies the importance of this decision — a daily scheduler that auto-creates projects must produce predictable, self-contained results regardless of whether someone edited the template yesterday.

**Consequences**:

- Positive:
  - Existing projects are immune to template changes — no unexpected mutations
  - Recurring schedule execution is deterministic and auditable
  - Implementation is straightforward: copy fields from TemplateTask to Task at creation time
  - Consistent with Phase 12 (document templates) and Phase 14 (checklist templates) patterns
  - No complex sync, merge, or conflict resolution logic required

- Negative:
  - Template improvements require manual updating of existing projects (acceptable — most firms prefer this)
  - No automated way to "push" template changes to existing projects (mitigated by the fact that in-progress projects are actively managed and should not be silently altered)
  - Slight data duplication (task definitions exist in both TemplateTask and Task tables) — negligible storage impact
