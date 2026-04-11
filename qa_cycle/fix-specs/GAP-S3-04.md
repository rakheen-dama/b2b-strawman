# Fix Spec: GAP-S3-04 — Matter custom field group not auto-attached on template creation

## Priority
LOW–MEDIUM — defeats the legal-vertical purpose of the "SA Legal — Matter Details" group but
not a hard blocker. Users can manually attach via "Add Group".

## Problem
Creating a matter from the Litigation template produces a matter with "No custom fields
configured". The `SA Legal — Matter Details` field group (case_number, court, opposing_party,
advocate, date_of_instruction, estimated_value) is seeded in Settings but not auto-attached to
template-created matters.

## Root Cause (needs investigation — spec assumes fix)
Files:
- `backend/.../projecttemplate/ProjectTemplateService.java:547-572` — the `instantiateTemplate`
  flow creates Project + Tasks + TaskItems + Tags but does NOT attach any `FieldGroup` to the
  project.
- `backend/.../fielddefinition/FieldGroupService.java` — no `attachGroupToEntity` method
  confirmed; attaching is likely done via a separate `FieldGroupMember` or `EntityFieldGroup`
  join table. Needs one grep to confirm.
- `ProjectTemplate` entity likely has (or needs) a `fieldGroupIds: List<UUID>` column so that
  each template records which field groups to auto-attach.

## Fix Steps
1. **Data model** (if not already present): add a `project_template_field_groups` join table
   or a `field_group_ids` JSONB column on `project_templates`. Migration file in
   `db/migration/tenant/V{next}__add_template_field_groups.sql`.
2. **Seeder**: the legal-za project template seeder
   (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/ProjectTemplatePackSeeder.java` or
   similar) should link each template to the `SA Legal — Matter Details` field group at seed
   time.
3. **Instantiation**: in `ProjectTemplateService.instantiateTemplate()` (around line 572,
   after the tag loop), add:
   ```java
   for (UUID fieldGroupId : template.getFieldGroupIds()) {
     fieldGroupService.attachToEntity(fieldGroupId, "PROJECT", project.getId());
   }
   ```
4. **Test**: extend `ProjectTemplateServiceTest` to create a template with a field group and
   assert the group is attached to the instantiated project.

## Scope
- Backend only
- Files to modify:
  - `backend/.../projecttemplate/ProjectTemplateService.java`
  - `backend/.../projecttemplate/ProjectTemplate.java` (add `fieldGroupIds`)
  - `backend/.../seeder/ProjectTemplatePackSeeder.java` (wire field group)
  - `backend/.../fielddefinition/FieldGroupService.java` (expose `attachToEntity` if not
    already public)
- Files to create:
  - `backend/src/main/resources/db/migration/tenant/V{next}__add_template_field_groups.sql`
- Migration needed: yes

## Verification
1. Restart backend (seeder re-runs and attaches field groups).
2. Create a matter from the Litigation template. Open matter detail → Field Groups section
   should show "SA Legal — Matter Details" pre-attached with all 6 fields editable.
3. Re-run Session 3 step 3.20 — custom fields should be fillable without manual Add Group.

## Estimated Effort
M (~1.5 hr — migration + seeder + service + test). **Borderline**: if the `fieldGroupIds`
column does not yet exist on ProjectTemplate, consider deferring as this pushes into L territory.
Product-agent recommendation: **do this fix AFTER all HIGH/MED blockers are landed**.
