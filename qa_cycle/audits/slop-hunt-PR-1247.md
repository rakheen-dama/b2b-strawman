# Slop hunt — PR #1247: fix(OBS-2107): backfill empty portal_notification_doc_types

**Batch**: D — SQL/billing/data
**Reviewed**: 2026-05-01
**Verdict**: NEEDS-FOLLOW-UP

## PR description vs diff

PR description claims: "Adds Flyway tenant migration `V118__backfill_portal_notification_doc_types.sql` that UPDATEs rows where the column is NULL or `'[]'::jsonb` to the canonical default seed declared in V117 (`['matter-closure-letter', 'statement-of-account']`). Idempotent... No code changes." Diff confirms exactly that — a 23-line single-file migration with one UPDATE statement. Clean and minimal.

The PR description's **root-cause narrative is factually wrong**, however. It says: "Postgres DEFAULTs only apply to NEW INSERTs — pre-existing org rows (e.g. Mathebula's `tenant_5039f2d497cf`) retained empty allowlists." This is the inverse of how Postgres `ADD COLUMN ... NOT NULL DEFAULT X` actually works. Per Postgres 11+ semantics, `NOT NULL DEFAULT` is atomic-with-backfill — pre-existing rows ARE rewritten with X at ALTER time. The fix-spec `qa_cycle/fix-specs/OBS-2107.md:46-53` even acknowledges this and speculates at two alternative explanations ("(a) the row was inserted before V117 with an explicit `'[]'`, or (b) the row predates V117 and was backfilled to `'[]'` by some earlier seed/migration").

The actual root cause is in the Java entity, not the migration. See Finding #1.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | **HIGH** | Schema-data fix without invariant fix — bug class will recur | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:245` | The Java entity initializer `private List<String> portalNotificationDocTypes = new ArrayList<>();` is the actual root cause. When Hibernate persists a NEW `OrgSettings` row (e.g. for a freshly provisioned tenant created AFTER V117), it sends `[]` to the DB, **overriding the SQL DEFAULT**. V117's `NOT NULL DEFAULT '[...]'::jsonb` only applies when the SQL `INSERT` does not specify the column — but Hibernate always specifies it, with the empty Java collection. The V118 backfill repairs existing rows but does not prevent recurrence. Any tenant provisioned tomorrow will still get `'[]'` and the same handler-skip bug. | Two viable options: (a) Change the field initializer to the canonical default (e.g. `List.of("matter-closure-letter", "statement-of-account")` or copy `OrgSettingsService` to populate it on construction), or (b) Mark the field nullable and let the SQL DEFAULT win (`@Column(insertable = false)` or remove the Java initializer + accept null + treat null as "use canonical default" at read time). The fix-spec rejected (b) as Option B; (a) is unaddressed and is the simpler, more invariant-respecting fix. **Open follow-up PR.** |
| 2 | **HIGH** | Misdiagnosis in PR description and fix-spec — wrong root-cause narrative will mislead future debugging | PR #1247 body + `qa_cycle/fix-specs/OBS-2107.md:44-49` | The narrative "Postgres DEFAULTs only apply to NEW INSERTs" is **factually wrong** for `ADD COLUMN ... NOT NULL DEFAULT`. Postgres backfills existing rows atomically (PG 11+). Audit `qa_cycle/audits/02-flyway-default-drift.md` correctly distinguishes "NOT NULL DEFAULT (PG auto-backfills)" from "nullable DEFAULT (does NOT backfill)." V117 falls into the first category. The bug class is "Java entity initializer overriding SQL DEFAULT," not "Flyway DEFAULT drift." | Update `qa_cycle/audits/02-flyway-default-drift.md` to add a new bug class: "Hibernate-managed entity initializer races SQL DEFAULT." Audit other `OrgSettings` fields with similar Java-initializer patterns. The 6 nullable-DEFAULT suspect columns audit-02 lists may have a parallel issue if their Java getters return non-null defaults. |
| 3 | LOW | Idempotency claim is correct but narrow | `V118__backfill_portal_notification_doc_types.sql:18-22` | The UPDATE matches `IS NULL OR = '[]'::jsonb`. Tenants who **explicitly set** the allowlist to a non-empty list keep it. Tenants who explicitly set it to `'[]'` (to disable per-event sends, per the V117 comment "Empty list disables per-event sends entirely for the tenant") get **silently overwritten** to the canonical default. This is a behavioural assumption: there is no way for an admin to opt out of per-event sends because V118 will undo their `'[]'` setting on next backend startup. | Document the assumption explicitly, or add a sentinel (`SELECT updated_at < some_timestamp`) so only legacy `'[]'` rows are touched. Per the user mandate "no production data, backward compat not priority" this is acceptable but worth documenting for future migration design. |
| 4 | LOW | Test scope drift — targeted verify, no `./mvnw verify` | PR description test plan | `./mvnw test -Dtest='*OrgSettings*,*PortalDocument*,*MatterClosureEmail*'` (64) + `*FlywayTest*,*MigrationTest*` (30). No full verify per CLAUDE.md §1. | Per cycle 23 the orchestrator confirmed full verify clean. Specific to this PR: a SQL-only data migration is low-risk, but the same rule applies. |
| 5 | LOW | Migration adds no integration test exercising it against a fresh tenant | New file `V118__...` | The Flyway migration is exercised only by the existing `*FlywayTest*,*MigrationTest*` suite (which probably just verifies the migration runs to completion, not that the resulting data is correct). No assertion that after V118 runs, `SELECT portal_notification_doc_types FROM org_settings` returns the canonical default for a previously-empty row. | Add an integration test or mark the verification step (`bash compose/scripts/svc.sh restart backend` + manual psql inspection) as the test of record. |

## Test scope check

- Targeted: `./mvnw test -Dtest='*OrgSettings*,*PortalDocument*,*MatterClosureEmail*'` (64) + `*FlywayTest*,*MigrationTest*` (30). **Not full `./mvnw verify`.**
- Did the test exercise the new behaviour? **No.** None of the existing tests seed a tenant with `portal_notification_doc_types = '[]'::jsonb` and assert that V118 backfills it. The Flyway test suite likely only confirms that V118 syntax is valid and the migration applies. The PR description's `[ ]` Backend restart applies V118 to all tenant schemas; manual verification that ... is no longer empty.` is checked off as deferred ("manual"). The actual end-to-end verification was a Day 85 retest in the QA cycle.
- This is a data-only migration, so the test surface is limited. Acceptable, given the QA-cycle retest evidence.

## Schema-vs-data verdict

**Data-only fix; schema invariant left permissive; bug class will recur.**

The schema (V117) declares `NOT NULL DEFAULT '[matter-closure-letter, statement-of-account]'::jsonb` — strong invariant. But the Java entity initializer (`OrgSettings.java:245`) sets the field to `new ArrayList<>()`, which Hibernate sends to the DB on every INSERT, overriding the SQL DEFAULT. So the schema-level invariant is **not enforced at the application layer**.

V118 repairs existing wrong data but:
1. Does not change the schema (column nullability is already NOT NULL, so no change needed there).
2. Does not change the Java entity to align with the schema's intent.
3. Will be necessary again for every tenant provisioned after V118 — unless the dev environment is re-provisioned cleanly, the bug recurs at every new-tenant onboarding.

Per the user mandate ("no production data, backward compat not priority, prefer re-provision over migration paths"), the V118 backfill is overcompensating — a `dev-down --clean && dev-up` would have repaired the affected tenant without a migration. But V118 is harmless and idempotent, so it is not wrong.

The **real fix** is to change `OrgSettings.java:245` to either initialize the field with the canonical default, or set it to null and accept SQL-DEFAULT behaviour. **This was not done in PR #1247 and remains open.**

## Notes

- The fix-spec `qa_cycle/fix-specs/OBS-2107.md` explicitly considered "Option B: Java fallback" and rejected it. Fair enough — a read-time fallback is wrong. But the spec did not consider **Option C: fix the write-time initializer**, which is the simplest correct fix and would close the bug class permanently. This is a process gap in the fix-spec authoring (Path A vs Path B was a false dichotomy).
- The audit-02 hypothesis (nullable DEFAULT does not backfill on existing rows) does NOT apply to V117 because V117 is `NOT NULL DEFAULT`. The fix-spec confused itself by adopting that hypothesis verbatim. The correct hypothesis ("Hibernate sends the Java field default, overriding SQL DEFAULT, on every INSERT") would have led to a different fix.
- Recommended follow-up: a one-line PR changing `OrgSettings.java:245` from `new ArrayList<>()` to `new ArrayList<>(List.of("matter-closure-letter", "statement-of-account"))` (or equivalent), plus a unit test that creates a fresh `OrgSettings` and asserts the field is populated. ~30 minutes.
