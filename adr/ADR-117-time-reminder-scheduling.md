# ADR-117: Time Reminder Scheduling Strategy

**Status**: Accepted

**Context**:

Phase 30 introduces unlogged time reminders: a scheduled notification for team members who have not logged sufficient time on working days. Each org configures a reminder time (e.g., 17:00), working days (e.g., Mon-Fri), and minimum hours (e.g., 4.0). The system must check each org's members at the configured time and create in-app notifications for those who have not met the threshold.

The challenge is timezone handling: orgs in different timezones have different "5 PM" times. The system needs to fire reminders at roughly the right time for each org. Additionally, the scheduled job must be tenant-aware — it iterates across all tenants and checks within each tenant's schema.

**Options Considered**:

1. **Single daily UTC run with timezone offset** — A single `@Scheduled` job runs once per day at a fixed UTC time (e.g., midnight). For each org, it calculates whether the current UTC time matches the org's configured reminder time in their timezone. Requires a timezone field on OrgSettings.
   - Pros:
     - Accurate timezone handling: each org gets reminders at their actual local time
     - Single job execution per day
   - Cons:
     - Requires a new `timezone` column on OrgSettings (not in the requirements spec)
     - A single daily run means orgs in different timezones can't all be served at the right time — an org at UTC+2 wanting 17:00 reminders needs the job to run at 15:00 UTC, but an org at UTC-5 wants it at 22:00 UTC. Single run cannot serve both.
     - Actually, this approach doesn't work for multi-timezone platforms without running multiple times per day

2. **Periodic polling (every 15 minutes)** — A `@Scheduled` job runs every 15 minutes. Each execution queries all orgs where `time_reminder_enabled = true` and `time_reminder_time` falls within the current 15-minute window (interpreted as UTC). For v1, orgs set their reminder time as the UTC equivalent of their desired local time.
   - Pros:
     - Simple implementation: `@Scheduled(fixedRate = 15 * 60 * 1000)`
     - No timezone column needed for v1 — orgs set time as UTC equivalent (South African firm sets 15:00 UTC for 17:00 SAST)
     - 15-minute granularity is sufficient for reminders (not time-critical)
     - Lightweight: the query `WHERE time_reminder_enabled = true AND time_reminder_time BETWEEN :windowStart AND :windowEnd` is cheap
     - Easily upgradeable: add a timezone column later and compute UTC windows dynamically
   - Cons:
     - Orgs must understand UTC offset when configuring (mitigated: frontend can show "your local time" helper)
     - 15-minute polling means up to 15 minutes of delay (acceptable for reminders)
     - Job runs 96 times/day even if no orgs have reminders in that window (very cheap — just a query returning 0 rows)

3. **Per-org scheduled tasks (dynamic scheduling)** — For each org with reminders enabled, register a dedicated `ScheduledFuture` at the org's configured time. Cancel and re-register when settings change.
   - Pros:
     - Exact timing: each org's reminder fires at exactly the configured time
     - No polling overhead when no reminders are due
   - Cons:
     - Complex lifecycle: must register/deregister tasks when orgs enable/disable reminders, change times, or are created/deleted
     - State management: the `TaskScheduler` holds in-memory references that are lost on restart (must re-register on startup)
     - No precedent in the codebase — all existing scheduled work uses simple `@Scheduled` annotations
     - Harder to test: must verify dynamic task registration and cancellation
     - Does not scale well with many tenants (thousands of registered tasks)

**Decision**: Option 2 — Periodic polling every 15 minutes, with reminder time interpreted as UTC for v1.

**Rationale**:

For a notification feature where 15-minute precision is acceptable, periodic polling is the simplest correct approach. The job is a single Spring `@Scheduled` method that queries the global `org_schema_mapping` table (or iterates known tenants) and, for each org with reminders enabled in the current window, switches to the tenant schema and checks member time entries.

The UTC-as-configured approach for v1 is a pragmatic simplification. DocTeams' initial market is South African professional services firms (single timezone, UTC+2). The frontend settings UI can display a helper: "Set to 15:00 for 5:00 PM SAST". When multi-timezone support becomes necessary, a `timezone` column can be added to OrgSettings and the polling job updated to compute UTC windows dynamically — the 15-minute polling architecture remains unchanged.

Dynamic per-org scheduling (Option 3) is over-engineered for a notification that tolerates 15 minutes of imprecision. The codebase has no dynamic task scheduling infrastructure, and introducing it for a single feature is not justified.

**Consequences**:

- New `TimeReminderScheduler` class with `@Scheduled(fixedRate = 900000)` (15 minutes)
- Job iterates tenants, checks `org_settings.time_reminder_enabled` and `time_reminder_time` against current 15-minute window
- For matching orgs: queries each member's total `time_entries.duration_minutes` for today, creates notification if below threshold
- `time_reminder_time` is interpreted as UTC for v1 — no timezone column needed
- Frontend settings UI should display a helper showing UTC offset for the user's browser timezone
- Future upgrade path: add `timezone VARCHAR(50)` to OrgSettings, compute UTC window from `time_reminder_time + timezone` — no architectural change to the polling approach
- Individual opt-out via existing `NotificationPreference` infrastructure (preference type: `TIME_REMINDER`)
- Related: [ADR-036](ADR-036-sync-fanout.md) (notification fanout pattern), Phase 6.5 notification infrastructure
