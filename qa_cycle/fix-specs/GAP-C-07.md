# Fix Spec: GAP-C-07 — Automations page gated behind disabled feature flag

> **Shipped as PR #1055** (merge `7c64d3ee`), batched with GAP-C-04 into a single JSON change + `V97__enable_consulting_za_modules.sql` migration. Pre-implementation `V96` references below are spec notes; V97 is the actual module-enable backfill.

## Problem

Day 0 checkpoint 0.51: Settings > Automations shows "Automation Rule Builder is not enabled. This feature is not enabled for your organization. An admin can enable it in Settings → Features." Even though `automation-consulting-za` pack installed 6 rules into the DB (verified via checkpoint 0.52), the UI hides them. Admin cannot see, edit, enable, or disable the pack-installed rules. A consulting-za tenant cannot actually manage their own automation rules without a manual feature-flag enable step.

## Root Cause (confirmed via grep)

File: `frontend/app/(app)/org/[slug]/settings/automations/page.tsx` lines 18–21:

```tsx
if (!(await isModuleEnabledServer("automation_builder"))) {
  return <ModuleDisabledFallback moduleName="Automation Rule Builder" slug={slug} />;
}
```

File: `backend/src/main/resources/vertical-profiles/consulting-za.json` line 7:
`"enabledModules": []` — `automation_builder` is NOT enabled, even though the profile's pack list (`packs.automation: ["automation-consulting-za"]`) installs 6 automation rules.

This is an inconsistency: the profile ships with automation rules but does not enable the UI to manage them.

## Fix

Enable the `automation_builder` module for consulting-za tenants (and ideally all profiles that ship with an automation pack — but scope to consulting-za for this cycle).

### Step 1 — Update profile JSON

Edit `backend/src/main/resources/vertical-profiles/consulting-za.json`:

Change line 7 from:
```json
"enabledModules": [],
```
to include both `resource_planning` (from GAP-C-04 spec) AND `automation_builder`:
```json
"enabledModules": ["resource_planning", "automation_builder"],
```

Note: if GAP-C-04 ships first, extend its list instead of replacing.

### Step 2 — Combine with GAP-C-04 migration

The same `V96` migration from GAP-C-04 should enable BOTH modules. Update the V96 SQL to:

```sql
UPDATE org_settings
SET enabled_modules = jsonb_build_array('resource_planning', 'automation_builder')
WHERE vertical_profile = 'consulting-za'
  AND (enabled_modules IS NULL OR NOT (enabled_modules @> '["automation_builder"]'::jsonb));
```

If GAP-C-04 is fixed FIRST and ships V96 with just `resource_planning`, GAP-C-07 needs a separate V97 migration that appends `automation_builder`:

```sql
UPDATE org_settings
SET enabled_modules = COALESCE(enabled_modules, '[]'::jsonb) || '["automation_builder"]'::jsonb
WHERE vertical_profile = 'consulting-za'
  AND NOT (enabled_modules @> '["automation_builder"]'::jsonb);
```

**Preferred**: ship both in a single V96 migration to minimize risk. Dev should combine them at implementation time.

## Scope

Config + Migration
Files to modify:
- `backend/src/main/resources/vertical-profiles/consulting-za.json`
- `backend/src/main/resources/db/migration/tenant/V96__...sql` (combine with GAP-C-04 if possible)

Migration needed: yes (same migration as GAP-C-04 ideally)

## Verification

1. Restart backend.
2. Navigate to `/org/zolani-creative/settings/automations` as Zolani (owner).
3. Expect: page renders "Automations" heading, a "View Execution Log" link, and a `RuleList` showing all 6 pack-installed rules (Project Budget Alert 80%, Project Budget Exceeded 100%, Retainer Period Closing, Task Blocked 7 days idle, Unbilled Time > 30 days, Proposal Follow-Up 5 days). Each row should be editable / toggleable.
4. Re-run Day 0 checkpoint 0.51 and close GAP-C-07.

## Estimated Effort

S (< 15 min). Same migration as GAP-C-04 — one extra item in the JSON array.
