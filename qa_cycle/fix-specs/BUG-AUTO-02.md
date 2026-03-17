# Fix Spec: BUG-AUTO-02 — All 11 seeded automation rules are DISABLED by default

## Problem

All 11 seeded automation rules (7 common + 4 accounting-za) are DISABLED by default. The dashboard Automations widget shows "Active Rules: 0". The test plan (T7.1.3) expects seeded vertical pack rules to be ENABLED by default, since they represent best-practice automations that should work out of the box.

Evidence: QA checkpoint T1.1 — all 11 rules had toggle switches in OFF position. QA had to manually enable each rule before trigger testing could proceed (T7.1 post-action).

## Root Cause (confirmed)

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java`, lines 93-95:

```java
// Constructor sets enabled=true; toggle to false for seeded templates
rule.toggle();
```

The `AutomationRule` constructor (line 76 of `AutomationRule.java`) sets `enabled = true`. The seeder then **explicitly toggles it to false**. The comment indicates this was an intentional design choice, but it contradicts the expected UX for vertical packs: firms should get working automations out of the box.

## Fix

Remove the `rule.toggle()` call in the seeder so that seeded rules default to ENABLED.

### Step 1: Remove the toggle call

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java`

Delete lines 94-95:
```java
// Constructor sets enabled=true; toggle to false for seeded templates
rule.toggle();
```

The constructor already sets `enabled = true`, so removing the toggle means seeded rules will be ENABLED by default.

### Step 2: Update the existing test (if any)

File: `/backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AccountingAutomationTemplatePackTest.java`

Check if any assertion expects `isEnabled() == false` for seeded rules. If so, flip to `assertTrue(rule.isEnabled())`.

### Step 3: Re-seed the E2E environment

After deploying the fix, the existing seeded rules in the E2E database will still be DISABLED (they were already persisted). The fix only affects future seeding. To fix the current E2E data:

```bash
bash compose/scripts/e2e-reseed.sh
```

This will wipe and re-seed the data, picking up the new seeder behavior.

## Scope

Backend + Seed

Files to modify:
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java`
- `/backend/src/test/java/io/b2mash/b2b/b2bstrawman/automation/AccountingAutomationTemplatePackTest.java` (if test asserts disabled)

Files to create: none

Migration needed: no (seed data change, not schema)

## Verification

Re-run Track 7 (T7.1.3):
1. After e2e-reseed, navigate to Settings > Automations
2. All seeded rules should show toggle in ON position
3. Dashboard Automations widget should show "Active Rules: 11"

## Estimated Effort

S (< 30 min) — remove 2 lines, update test assertion, reseed
