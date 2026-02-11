# ADR-021: Time Tracking Model

**Status**: Accepted

**Context**: Phase 5 adds time tracking to the platform. Staff need to record time spent on tasks for two purposes: (1) internal capacity tracking and (2) billable time summaries for future client invoicing. The system must decide how time entries relate to other entities and what granularity of time data to capture.

**Options Considered**:

1. **Time entries attached directly to projects** — A `time_entries` table with `project_id` (no task reference). Staff logs time against a project with a free-text description.
   - Pros: Simplest model. Doesn't require tasks to exist. Staff can log time even before tasks are created.
   - Cons: No attribution to specific work items. Reporting is limited to "how much time on this project?" without knowing what the time was spent on. Loose accountability — free-text descriptions are inconsistent and unsearchable. Makes it impossible to aggregate time per task or show time on task detail views.

2. **Time entries attached to tasks** — A `time_entries` table with `task_id` (NOT NULL). All time is attributed to a specific task.
   - Pros: Every minute of tracked time is attributed to a unit of work. Enables per-task time reporting, task-level cost analysis, and "is this task over budget?" checks. Natural UX: staff claim a task, work on it, log time against it. Project totals are derived via `JOIN tasks`. Forces structured time recording.
   - Cons: Requires a task to exist before time can be logged. "Overhead" or "admin" time needs a catch-all task. Staff must create tasks for ad-hoc work.

3. **Hybrid — time entries with optional task reference** — `task_id` is nullable. Time can be logged against a project (task_id = NULL) or a task.
   - Pros: Maximum flexibility. Captures both structured (task-linked) and unstructured (project-only) time.
   - Cons: Two data paths for the same concept. Reporting must handle both cases. Encourages unstructured logging (the easy path), undermining the value of task-linked attribution. UI complexity: two different "log time" flows.

4. **Timer-based model (start/stop)** — Track `start_time` and `end_time` instead of `date` + `duration`. Staff starts a timer, works, stops it.
   - Pros: Exact time capture. No manual entry needed during work. Natural for real-time tracking.
   - Cons: Requires a persistent timer state (in-progress entries). Awkward for retrospective logging ("I forgot to start the timer"). More complex UI (running timers, pause/resume). Many professional services firms use retrospective logging, not timers.

**Decision**: Time entries attached to tasks (Option 2) with date + duration (not timer-based).

**Rationale**: The platform already has a task model (Phase 4). Requiring time to be logged against a task creates structured, attributable time data from day one. The "overhead task" pattern is well-established in professional services tools (Harvest, Toggl, Clockify) — staff create a "General / Admin" task on each project for non-task-specific time. This is a one-time setup cost per project that yields permanently structured data.

The date + duration model (not start/stop timers) matches the primary use case: professional staff logging time retrospectively at the end of the day or during a weekly timesheet review. A timer feature can be layered on top later — it would create the same `time_entries` record, just with the duration calculated from start/end times instead of manual entry.

Duration is stored in **minutes** (integer), not hours (decimal). This avoids floating-point rounding issues. 1h 30m = 90 minutes, unambiguously. Formatting for display (hours:minutes) is a frontend concern.

**Consequences**:
- `time_entries.task_id` is NOT NULL — all time is attributed to a task.
- Projects that need to track general overhead time should create a catch-all task (e.g., "Admin / Meetings").
- Time reporting is fully structured: per-task, per-project (via JOIN), per-member, per-date-range.
- No timer infrastructure needed now. A future timer feature would be a UI enhancement that writes the same `time_entries` table.
- `duration_minutes` is an integer — no floating-point edge cases.
- `date` is a DATE (not TIMESTAMPTZ) — represents the calendar day the work was performed, not the moment it was logged.
