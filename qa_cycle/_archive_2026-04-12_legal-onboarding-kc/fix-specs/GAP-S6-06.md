# Fix Spec: GAP-S6-06 — Automation rule fails with "No recipients resolved for type: ORG_ADMINS"

## Priority
LOW — backend-log-only WARN; no user-visible symptom yet, but the "Matter Onboarding Reminder"
legal-za automation rule silently no-ops, which defeats its purpose.

## Problem
The legal-za automation seed uses recipientType `ORG_ADMINS`, but the resolver only knows about
`ALL_ADMINS` (and a few other keys). Every time the rule fires, a WARN is logged and no
notification is sent.

## Root Cause (confirmed via grep)
Files:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendNotificationActionExecutor.java:88-131`
  — `resolveRecipients(recipientType, context)` has cases for `TRIGGER_ACTOR`, `PROJECT_OWNER`,
  `PROJECT_MEMBERS`, `ALL_ADMINS`, `SPECIFIC_MEMBER`. No `ORG_ADMINS`.
  Line 115-119 shows the existing `ALL_ADMINS` case already does what `ORG_ADMINS` is supposed
  to — it queries `memberRepository.findByRoleSlugsIn(List.of("admin", "owner"))`.
- `backend/src/main/resources/automation-templates/legal-za.json` — contains the
  `ORG_ADMINS` key that the seed uses.

## Fix Steps
Choose the smaller fix:

**Option A (recommended — alias in resolver)**: Add an alias case to the switch statement:
```java
case "ORG_ADMINS", "ALL_ADMINS" -> {
  yield memberRepository.findByRoleSlugsIn(List.of("admin", "owner")).stream()
      .map(m -> m.getId())
      .toList();
}
```
Then remove the standalone `ALL_ADMINS` case (or keep both aliased via `case "A", "B" ->`).

**Option B (change the seed)**: Edit `automation-templates/legal-za.json` to use
`"recipientType": "ALL_ADMINS"` instead. Simpler, but won't auto-fix existing tenants unless
the pack version bumps and the seeder re-applies.

Prefer Option A — it fixes the bug for already-seeded tenants without needing to bump pack
versions.

## Scope
- Backend only
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendNotificationActionExecutor.java`
- Migration needed: no

## Verification
1. Restart backend.
2. Trigger the "Matter Onboarding Reminder" rule (e.g. create a new matter, wait for the cron
   if scheduled, or fire via a test endpoint).
3. Backend log should no longer show the WARN. Notification should be delivered to Bob (admin)
   and Thandi (owner).

## Estimated Effort
S (< 10 min)
