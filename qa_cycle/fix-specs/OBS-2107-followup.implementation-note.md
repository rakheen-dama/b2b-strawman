# OBS-2107 Follow-up Implementation Note

## PR
- Number: **#1254** — `fix(OBS-2107 follow-up): seed canonical portal_notification_doc_types in OrgSettings constructor`
- Squash-merged to `main`: **672bbb0af**
- Date: 2026-05-01

## Files Changed
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — added `DEFAULT_PORTAL_NOTIFICATION_DOC_TYPES` constant; seeded it in the 1-arg `OrgSettings(String defaultCurrency)` constructor.
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsTest.java` — added `defaultPortalNotificationDocTypes_matchesV117CanonicalDefault()` unit test.
- `qa_cycle/fix-specs/OBS-2107-followup.md` — new spec capturing root cause, fix, and rejected alternatives.
- `qa_cycle/fix-specs/OBS-2107.md` — appended misdiagnosis correction notice; original (incorrect) narrative preserved for traceability.
- `qa_cycle/audits/02-flyway-default-drift.md` — added "Hibernate entity initializer overrides SQL DEFAULT" bug-class section + suspect-search heuristic + proposed CLAUDE.md convention.

## Verification
- Pre-merge full suite: `./mvnw verify` clean — 5012 / 0F / 0E / 26 skip — 12:26 min (baseline 5011 + 1 new unit test).
- Inner-loop targeted: `./mvnw test -Dtest='OrgSettingsTest'` — 8 / 0F / 0E (was 7 before).
- Marker: `.claude/markers/verify-backend.json` — exit 0, fresh.
- CodeRabbit: 1 actionable comment (MD040 missing language tag on fenced log block) — addressed in commit `4fa9d29e4`. No further findings.
- Qodana reported 349 baseline issues — none introduced by this PR; out of scope.
- Pre-PR-merge-gate hook accepted the marker on `gh pr merge 1254`.

## Mandate Compliance
- One fix per PR (Quality Gate rule #7) — entity initializer fix is the single substantive change. Doc cleanup bundled per explicit scope-widening authorization (user, 2026-05-01).
- Reproduce-before-fix (rule #4) — bug verified by reading entity + 16 call sites + V117/V118 migrations before writing the spec.
- Build & test bar (rule #1) — full `./mvnw verify`, not targeted globs.
- PASS means observed (rule #3) — pre-merge verify on the branch + post-merge verify on main (in flight at time of writing).
- Reproduce on a freshly-provisioned tenant via `dev-down --clean && dev-up && keycloak-bootstrap` is the post-merge opportunistic check; not a blocker for merge per scope of this PR.

## Source
- Slop-hunt audit: `qa_cycle/audits/slop-hunt-PR-1247.md` Finding #1 (HIGH).
- First fix shipped from the 23-PR slop-hunt completed 2026-05-01 (Task D in `qa_cycle/HANDOFF-2026-05-01.md`).

## Open follow-ups (deliberately not bundled)
- Append a convention to `backend/CLAUDE.md`: "When an entity field maps to a column with a non-trivial SQL DEFAULT, the constructor must set the field to the same canonical value." Drafted in `qa_cycle/fix-specs/OBS-2107-followup.md`. Tracked separately.
- Sweep other entities with `@JdbcTypeCode(SqlTypes.JSON)` collection columns for the same Hibernate-vs-SQL-DEFAULT pattern. Heuristic search in `qa_cycle/audits/02-flyway-default-drift.md`.
- V118 idempotency caveat (audit Finding #3, LOW) — silent overwrite of tenants who explicitly set `'[]'` to disable per-event sends. Defer per user mandate "no production data, backward compat not priority".
