# ADR-069: Role-Based Assignment Hints

**Status**: Accepted
**Date**: 2026-02-19

**Context**:

Project templates include task definitions that can specify who should be assigned to each task when a project is instantiated. The system must decide how to represent this assignment intent in the template: should templates store specific member UUIDs (member-based assignment) or abstract role hints like "project lead" (role-based assignment)?

Templates are reused across customers, time periods, and potentially team configurations. A "Monthly Bookkeeping" template might be used for 50 different customers, each with a different project lead. A "Quarterly Audit" template might be used year after year as team members join and leave the firm. The assignment representation must be stable across these variations. Additionally, recurring schedules auto-create projects without human intervention — the assignment mechanism must resolve correctly in an automated context where no user is making real-time decisions.

**Options Considered**:

1. **Role-based assignment hints (chosen)** — Templates store an enum hint (`PROJECT_LEAD`, `ANY_MEMBER`, `UNASSIGNED`) per task. At instantiation, the system resolves the hint to the actual member based on the project's context.
   - Pros: Stable across time and team changes — roles outlast individual members; works naturally with recurring schedules — the project lead is set per-schedule, tasks auto-resolve; no stale member references when someone leaves the org; simple enum field, no FK management; forces the right abstraction — templates define roles, not people.
   - Cons: Limited granularity — cannot specify "assign to the senior accountant" or "assign to the person who did it last time"; `ANY_MEMBER` is effectively "unassigned with a flag" rather than true round-robin assignment.

2. **Member-based assignment** — Templates store specific member UUIDs per task.
   - Pros: Precise — the template author knows exactly who should do what; no ambiguity at instantiation time.
   - Cons: Fragile — members leave, change roles, or are unavailable; templates become org-specific rather than reusable blueprints; recurring schedules would assign to the same person indefinitely even if they are overloaded; requires FK management and validation (does this member still exist? still active? still in the org?); importing or sharing templates across orgs is impossible.

3. **Skill/tag-based assignment** — Templates store a skill tag (e.g., "bookkeeper", "senior auditor") per task. At instantiation, the system matches members by skill.
   - Pros: More granular than role hints; decouples from specific members while being more specific than "project lead".
   - Cons: Requires a skill/capability taxonomy that does not exist in the platform; skill matching is a resource planning feature (explicitly out of scope for Phase 16); adds significant complexity for marginal benefit; no existing entity or pattern to build on.

**Decision**: Option 1 — role-based assignment hints.

**Rationale**:

The primary use case for task assignment in templates is distinguishing between tasks the project lead should own (client communication, review, sign-off) and tasks that need to be distributed among the team (data entry, reconciliation, document preparation). The `PROJECT_LEAD` / `ANY_MEMBER` / `UNASSIGNED` enum captures this distinction with zero maintenance overhead. At instantiation time, `PROJECT_LEAD` resolves to the member set as project lead (either manually during creation or via the recurring schedule's `project_lead_member_id`). `ANY_MEMBER` and `UNASSIGNED` both result in an unassigned task — the difference is semantic, signaling to the project lead that `ANY_MEMBER` tasks need attention during assignment review.

This approach trades precision for durability. A firm with 20 monthly bookkeeping clients does not want to update 20 templates when an accountant leaves — they reassign the project lead on the relevant schedules, and the role resolution handles the rest. The lead time feature (creating projects N days before the period starts) gives the project lead time to review and refine assignments before work begins, further reducing the need for precise auto-assignment. If future phases introduce resource planning or capacity management, skill-based assignment can be added as a new hint type without breaking the existing enum.

**Consequences**:

- Positive:
  - Templates are reusable across customers and time periods without modification
  - No stale member references — role hints are always valid
  - Recurring schedule assignment is automatic and correct as long as `project_lead_member_id` is set
  - Simple implementation — enum column, switch statement at instantiation
  - Extensible — new hint types (e.g., `REVIEWER`) can be added to the enum later

- Negative:
  - Cannot auto-assign to specific team members based on skills or availability (mitigated by lead time for manual review)
  - `ANY_MEMBER` does not actually assign — it flags for manual assignment (acceptable for Phase 16 scope; true round-robin is a resource planning concern)
  - Templates cannot express "the same person who does task A should also do task B" (mitigated by project lead reviewing assignments after creation)
