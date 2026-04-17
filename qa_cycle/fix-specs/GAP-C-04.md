# Fix Spec: GAP-C-04 — Team Utilization widget 500s because module not enabled

> **Shipped as PR #1055** (merge `7c64d3ee`). Actual migration on this branch: `V97__enable_consulting_za_modules.sql` (batched with GAP-C-07's `automation_builder` enablement into the same migration + JSON change). Any `V96` references below are pre-implementation spec notes; see V97 for what actually ran. `enabledModules` in `vertical-profiles/consulting-za.json` is now `["resource_planning", "automation_builder"]`.

## Problem

`TeamUtilizationWidget` renders "Unable to load utilization data." on every dashboard render for every user on the consulting-za tenant (Zolani/Bob/Carol). Observed during Day 0 checkpoint 0.17 and re-confirmed on Day 1–7 after 9.0h of time was logged (data present ≠ widget works). Each dashboard render generates one 500 in the browser console for the utilization server action. This will be especially painful by Day 75 when the widget is the wow moment of the scenario.

The initial hypothesis was a rate-resolution dependency (cascade of GAP-C-05/C-06). That is wrong. The actual root cause is simpler and is not a rate problem.

## Root Cause (confirmed via grep)

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/UtilizationService.java`

Line 29: `private static final String MODULE_ID = "resource_planning";`
Lines 58 + 108: both `getMemberUtilization(...)` and `getTeamUtilization(...)` call `moduleGuard.requireModule(MODULE_ID)` as their very first statement.

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuard.java` (lines 23–27) throws `ModuleNotEnabledException` (HTTP 403) when the module is not in `enabled_modules`.

File: `backend/src/main/resources/vertical-profiles/consulting-za.json` line 7: `"enabledModules": []` — resource_planning is NOT enabled for consulting-za tenants.

Meanwhile, `frontend/components/dashboard/team-utilization-widget.tsx` lines 15–19 gates the widget render on `profile === "consulting-za"` — so the widget shows on the dashboard for consulting-za orgs BUT its backing endpoint is unconditionally gated behind `resource_planning`. The 403 propagates through the Next.js server action as a generic error, which the widget catches and renders as "Unable to load utilization data.".

This is a **blocker-severity design mismatch** — the widget is force-rendered for consulting-za, but the profile doesn't enable the module it depends on. Also breaks Bob's and Carol's dashboards (which is why they saw 500s on every render during Day 1–7).

## Fix

The correct fix is to add `resource_planning` to consulting-za's `enabledModules`. This is a pure JSON data change. One line.

1. Edit `backend/src/main/resources/vertical-profiles/consulting-za.json`:

   Change line 7 from:
   ```json
   "enabledModules": [],
   ```
   to:
   ```json
   "enabledModules": ["resource_planning"],
   ```

2. Persist the change for **existing** tenants that have already been provisioned with the old profile. Since GAP-C-01 was only fixed on 2026-04-17 and the current Day 8 QA tenant (`tenant_2a96bc3b208b`) was provisioned AFTER that fix, we need a one-time SQL update for any consulting-za tenant whose `org_settings.enabled_modules` is empty. Add this to a new tenant Flyway migration (next sequential V-number):

   File to create: `backend/src/main/resources/db/migration/tenant/V96__enable_resource_planning_for_consulting_za.sql`
   (Confirmed next V-number: V95 is the highest at 2026-04-17.)

   ```sql
   -- Backfill: add resource_planning module to consulting-za tenants that were
   -- provisioned before the profile JSON was corrected.
   UPDATE org_settings
   SET enabled_modules = jsonb_build_array('resource_planning')
   WHERE vertical_profile = 'consulting-za'
     AND (enabled_modules IS NULL OR enabled_modules = '[]'::jsonb);
   ```

   Look at existing `backend/src/main/resources/db/migration/tenant/` to pick the next V-number (read `ls backend/src/main/resources/db/migration/tenant/ | sort -V | tail -3` first).

3. (Optional polish, can be deferred) Make `team-utilization-widget.tsx` distinguish a 403 "module not enabled" from a real fetch failure and render a "module not enabled" empty state instead of "Unable to load utilization data." — but this is cosmetic once (1)+(2) are in. Skip for Cycle 1.

## Scope

Backend + Config
Files to modify:
- `backend/src/main/resources/vertical-profiles/consulting-za.json`
Files to create:
- `backend/src/main/resources/db/migration/tenant/V96__enable_resource_planning_for_consulting_za.sql`

Migration needed: yes (one tenant migration to backfill existing tenant)

## Verification

1. Restart backend (Flyway runs the new tenant migration against the existing `tenant_2a96bc3b208b` schema).
2. Hit `GET /api/utilization/team?weekStart=2026-04-13&weekEnd=2026-04-20` with Zolani's JWT — expect 200 (not 403).
3. Navigate to `http://localhost:3000/org/zolani-creative/dashboard` as Zolani, Bob, Carol — expect `TeamUtilizationWidget` to render a KpiCard (with `0%` or a number, NOT the error copy). No 500 in the browser console for the dashboard server action.
4. Re-run the Day 0 checkpoint 0.17 assertion and close GAP-C-04.

Depends on: none. Independent of GAP-C-05/C-06 (rate data is irrelevant — the widget reads planned vs capacity, not rate × hours).

## Estimated Effort

S (< 30 min)
