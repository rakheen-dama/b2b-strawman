# Fix Spec: GAP-D0-01 — No legal matter templates seeded by legal-za profile

## Problem
Settings > Project Templates shows "No project templates yet." The 4 legal matter templates (Litigation, Deceased Estate Administration, Collections, Commercial) are not present despite the legal-za profile being applied. The template JSON exists at `backend/src/main/resources/project-template-packs/legal-za.json` with all 4 templates and their task lists.

## Root Cause (hypothesis)
The `PackReconciliationRunner` runs on backend startup. At that point the E2E tenant has the `accounting-za` vertical profile (set by the seed script). The `AbstractPackSeeder.doSeedPacks()` method at lines 118-129 checks `settings.getVerticalProfile()` against `pack.verticalProfile`. Since the tenant's profile is `accounting-za` and the pack's profile is `legal-za`, the legal-za project template pack is **skipped**.

When the QA agent later manually applies the `legal-za` profile via Settings > General, the profile change updates `OrgSettings.verticalProfile` but does NOT re-trigger the `PackReconciliationRunner`. The existing profile-apply endpoint triggers field packs, compliance packs, template packs, clause packs, and tariff seeders — but NOT the `ProjectTemplatePackSeeder`.

**Evidence**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` lines 118-129 (profile filter). The profile application endpoint does not call `projectTemplatePackSeeder.seedPacksForTenant()`.

## Fix
Add `ProjectTemplatePackSeeder` to the list of seeders invoked when a vertical profile is applied.

### 1. Find the profile application endpoint
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileService.java` (or wherever `applyProfile` is implemented).

### 2. Add ProjectTemplatePackSeeder invocation
After the existing pack seeders (field, template, clause, compliance) are called, add:
```java
projectTemplatePackSeeder.seedPacksForTenant(tenantId, orgId);
```

This is safe because the seeder is idempotent — it checks `isPackAlreadyApplied()` before applying.

### Alternative fix (simpler, addresses GAP-D0-07 too)
Change the E2E seed script to provision with `legal-za` profile instead of `accounting-za`. Then the `PackReconciliationRunner` will seed the legal project templates on backend startup. See GAP-D0-07 spec for details.

Even with the seed fix, the profile application endpoint should still be fixed to call the project template pack seeder, as this is a general bug affecting any profile switch.

## Scope
Backend
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` — add `projectTemplatePackSeeder.seedPacksForTenant(tenantId, orgId);` after line 714 (alongside the existing `ratePackSeeder` and `schedulePackSeeder` calls). Also inject `ProjectTemplatePackSeeder` as a constructor dependency.
Files to create: none
Migration needed: no

## Verification
1. Apply legal-za profile via Settings > General
2. Navigate to Settings > Project Templates
3. Should see 4 templates: Litigation, Deceased Estate Administration, Collections, Commercial
4. Each template should have pre-populated tasks (9 tasks for Litigation, 9 for Estates, 9 for Collections, 9 for Commercial)

## Estimated Effort
S (< 30 min) — single line addition to the profile application service
