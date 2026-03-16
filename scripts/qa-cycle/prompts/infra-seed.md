You are the **Infra Agent** for the QA cycle on branch `bugfix_cycle_2026-03-15`.

## Your Job
Fix the E2E seed so the accounting vertical profile (`accounting-za`) is properly provisioned, then start the E2E stack and verify all accounting packs are seeded.

## Context
The E2E stack seeds an org but does NOT set the accounting vertical profile. As a result, the accounting **template pack** is not seeded (only 3 generic templates appear instead of 7 accounting-specific ones). However, other packs (custom fields, request templates, automations, compliance groups) ARE seeded — so the issue may be template-pack-specific.

## Steps

1. Read `qa_cycle/status.md` to confirm GAP-008 is your target.
2. Read the relevant backend CLAUDE.md for conventions.
3. Investigate why the template pack seeder skips accounting-za templates while other seeders work:
   - Check `backend/src/main/resources/` for accounting-za template pack JSON
   - Check `TemplatePackSeeder` vs `FieldPackSeeder` for filtering differences
   - Check the E2E seed script (`compose/seed/`) for how provisioning works
4. Fix the root cause. Options:
   - Fix the template pack seeder filtering logic
   - Fix the template pack JSON metadata
   - Update the E2E seed to pass `industry: "Accounting"` if that's what's needed
   - Add a post-seed SQL fix if quickest
5. Rebuild the E2E stack: `bash compose/scripts/e2e-up.sh`
6. Verify via the backend API or a quick Playwright check that all 7 accounting templates are present.
7. Update `qa_cycle/status.md`:
   - Set GAP-008 status to FIXED
   - Update Current State → E2E Stack: Running
   - Add a log entry with timestamp
8. Commit all changes to `bugfix_cycle_2026-03-15` and push.

## Guard Rails
- Do NOT create a separate branch for this fix — commit directly to `bugfix_cycle_2026-03-15` (this is infra setup, not a feature)
- Do NOT touch code unrelated to the seed/pack issue
- Run backend tests if you change seeder code: `cd backend && ./mvnw test -Dtest="*PackSeeder*" -q`
- Read backend/CLAUDE.md before making changes
