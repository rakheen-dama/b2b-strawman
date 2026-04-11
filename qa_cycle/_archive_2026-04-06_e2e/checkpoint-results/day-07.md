# Day 7 — First Real Work

## Executed: 2026-03-16T00:20Z (cycle 2)
## Actor: Carol (Member) -> Alice (Owner)

### Prerequisite Setup

Before Day 7 testing, several prerequisite gaps from previous days were addressed:

1. **Kgosi Construction project was missing** — never created in Day 1 cycle 2 (checkpoint 1.8 was PARTIAL, project creation skipped). Created "Kgosi Construction — Monthly Bookkeeping 2026" as Alice. Project ID: `07e9b80e-108d-4b0e-80e3-46a67e73ec80`.

2. **Project member access** — Carol (Member role) saw 0 projects on the projects page because she was not a member of any project. Members must be explicitly added to each project via the Members tab. Added Alice, Bob, and Carol to all 3 client projects (Kgosi, Vukani, Naledi). This is by design (project-level access control) but creates friction for small firms where all staff should see all projects.

3. **Task required for time logging** — Time entries are linked to tasks, not directly to projects. The Time tab says "Log time on tasks to see project time summaries here" with no standalone "Log Time" button. A task "January 2026 Bank Statement Capture" was created on the Kgosi project by Alice.

### Checkpoint 7.1 — Carol's My Work page loads
- **Result**: PASS (empty)
- **Evidence**: Signed in as Carol (Member). Navigated to `/org/e2e-test-org/my-work`. Page loads with sidebar showing "CM / Carol Member". Main content area is empty (no tasks assigned to Carol). Console has TypeError and React #418 hydration mismatch (pre-existing GAP-029). My Work page is functional but shows no content because no tasks are assigned to Carol.
- **Gap**: — (expected — no tasks assigned yet)

### Checkpoint 7.2 — Carol logged 3.0hr on Kgosi Construction at R450/hr = R1,350.00
- **Result**: FAIL (BLOCKER)
- **Evidence**: Carol initially saw 0 projects on the projects page (needed to be added as project member first — prerequisite fixed). After being added as a member, navigated to Kgosi Construction project. The Time tab shows "No time tracked yet" with message "Log time on tasks to see project time summaries here" — no standalone Log Time button. Time entries require tasks. After creating a task ("January 2026 Bank Statement Capture") as Alice (Carol cannot create tasks as Member), the task row shows a "Log Time" button. Clicking "Log Time" **crashes the entire page** with error boundary: "Something went wrong: Unable to load projects. Please try again." Console error: `RangeError: Invalid currency code : null`. This crash is **100% reproducible** on every click of Log Time.
- **Root cause**: The Log Time dialog attempts to format a billing amount using the org's currency code, but the org has no currency configured (value is `null`). This is related to GAP-008A (org settings page shows "Coming Soon" — cannot set currency) and GAP-019 (currency displays as USD not ZAR). The null currency propagates to `Intl.NumberFormat` which throws `RangeError: Invalid currency code`.
- **Gap**: GAP-030 (NEW — BLOCKER — Log Time crashes with null currency code)

### Checkpoint 7.3 — Carol logged 2.0hr on Vukani Tech at R450/hr = R900.00
- **Result**: FAIL (BLOCKED by GAP-030)
- **Evidence**: Not tested. Log Time crash affects all projects since the currency is configured at the org level.

### Checkpoint 7.4 — Bob added comment on Kgosi project
- **Result**: NOT TESTED (BLOCKED by GAP-030 cascade)
- **Evidence**: While comments could potentially work independently of currency, the time logging portion of this checkpoint is blocked. The comment sub-flow was not tested.

### Checkpoint 7.5 — Bob logged 1.0hr on Kgosi at R850/hr = R850.00
- **Result**: FAIL (BLOCKED by GAP-030)

### Checkpoint 7.6 — Document uploaded to Kgosi project
- **Result**: NOT TESTED

### Checkpoint 7.7 — Alice logged 0.5hr on Naledi at R1,500/hr = R750.00
- **Result**: FAIL (BLOCKED by GAP-030)

### Checkpoint 7.8 — Rate snapshots correct for all 3 members
- **Result**: FAIL (BLOCKED by GAP-030)
- **Evidence**: Cannot verify rate snapshots because time entries cannot be created.

### Checkpoint 7.9 — Cross-project time summary accessible
- **Result**: NOT TESTED (BLOCKED by GAP-030)

---

## Summary

| Checkpoint | Result | Gap |
|-----------|--------|-----|
| 7.1 — Carol's My Work page loads | PASS (empty) | — |
| 7.2 — Carol logged 3.0hr on Kgosi at R450/hr | FAIL (BLOCKER) | GAP-030 |
| 7.3 — Carol logged 2.0hr on Vukani at R450/hr | FAIL (BLOCKED) | GAP-030 |
| 7.4 — Bob comment on Kgosi | NOT TESTED | — |
| 7.5 — Bob logged 1.0hr on Kgosi at R850/hr | FAIL (BLOCKED) | GAP-030 |
| 7.6 — Document uploaded to Kgosi | NOT TESTED | — |
| 7.7 — Alice logged 0.5hr on Naledi at R1,500/hr | FAIL (BLOCKED) | GAP-030 |
| 7.8 — Rate snapshots correct | FAIL (BLOCKED) | GAP-030 |
| 7.9 — Cross-project time summary | NOT TESTED | — |

**Totals**: 1 PASS, 5 FAIL, 3 NOT TESTED

## New Gaps

### GAP-030 — Log Time crashes with RangeError: Invalid currency code null (BLOCKER)
- **Severity**: BLOCKER
- **Description**: Clicking "Log Time" on any task crashes the page with error boundary "Something went wrong: Unable to load projects." Console shows `RangeError: Invalid currency code : null`. The Log Time dialog attempts to format a billing amount using `Intl.NumberFormat` with the org's currency code, but the org has no currency configured (null). 100% reproducible.
- **Root cause**: Org currency is not set. The org settings page shows "Coming Soon" (GAP-008A), so there is no way to set the currency via the UI. The E2E seed data does not provision a currency for the org.
- **Impact**: Blocks ALL time logging, which cascades to block all billing/invoicing (Day 30+), profitability (Day 30+), and budget tracking (Day 30+). This is the most critical gap found so far — without time logging, the platform cannot function as a practice management tool.
- **Fix approach**: Either (a) set a default currency in the org seed data (e.g., USD or ZAR), or (b) add null-safe handling in the Log Time dialog's currency formatting, or (c) both.
- **Related gaps**: GAP-008A (org settings "Coming Soon"), GAP-019 (currency display as USD)

## Observations

1. **Project access control model**: Members (Carol's role) see only projects they are explicitly added to. In a 3-person firm, all staff should see all projects. There is no "auto-add all members" setting. This creates setup friction — every project needs manual member assignment. Not a bug, but a UX gap for small firms.

2. **Time entries require tasks**: The platform does not support logging time directly against a project. A task must be created first, then time is logged against the task. This is architecturally sound but adds friction for simple bookkeeping work where the "task" is effectively just the monthly engagement. For the lifecycle script, a generic task per project would suffice.

3. **Carol cannot create tasks**: As a Member role, Carol does not see the "New Task" button on the Tasks tab. Only Owner/Admin roles can create tasks. This means Carol cannot self-organize her workload — she depends on Alice or Bob to create tasks for her. This is a common RBAC design but may not suit a flat accounting firm structure.

4. **Session/display inconsistency**: After switching users via mock-login, the sidebar user info sometimes shows stale user data until a full page navigation. The initials badge and full name can show different users momentarily. This is a cosmetic issue in the mock-auth flow, not a security concern.
