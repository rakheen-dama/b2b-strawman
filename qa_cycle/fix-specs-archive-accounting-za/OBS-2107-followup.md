# OBS-2107 follow-up — Fix the entity initializer, not just the data

**Status**: SPEC_READY (about to ship)
**Severity**: Medium (latent — bug recurs on every newly provisioned tenant)
**Filed**: 2026-05-01 (slop-hunt audit of PR #1247, batch D)
**Branch**: `fix/OBS-2107-followup-entity-default`
**Source audit**: `qa_cycle/audits/slop-hunt-PR-1247.md` Finding #1 (HIGH)

## Symptom (the original OBS-2107)

`PortalDocumentNotificationHandler.process` skips every closure-pack / Statement-of-Account `DocumentGeneratedEvent` for tenants whose `org_settings.portal_notification_doc_types` is `'[]'::jsonb`, with the INFO log line:

```text
Skipping portal-document-ready: per-tenant allowlist empty (tenant=tenant_xxx)
```

PR #1247 (Flyway tenant migration V118) backfilled existing tenant rows. **The fix-spec's root-cause narrative was wrong**, however — and the bug class still recurs for every newly provisioned tenant.

## Real root cause (verified by reading the entity + the call sites)

Two layers conspire:

1. **`OrgSettings.java:245`** field initializer: `private List<String> portalNotificationDocTypes = new ArrayList<>();`
2. **`OrgSettings.java:249`** 1-arg constructor `OrgSettings(String defaultCurrency)` sets every other domain default (`accountingEnabled = false`, `taxInclusive = false`, `billingBatchAsyncThreshold = 50`, etc.) but **does not touch `portalNotificationDocTypes`** — it's left at the field initializer's empty list.

Hibernate persists the explicit empty list on every INSERT, **overriding the V117 SQL DEFAULT**. Postgres column DEFAULTs only apply when an INSERT omits the column; Hibernate always provides the column with the Java field's value, even when that value is the Java-default empty collection.

16 production call sites construct `new OrgSettings(currency)` for new-tenant provisioning — most importantly:
- `provisioning/TenantProvisioningService.java:251` and `:284` (the new-tenant path)
- `settings/OrgSettingsService.java:175,249,285,374,531,579,623,658,721,755,829`
- `reporting/StandardReportPackSeeder.java:46`
- `seeder/AbstractPackSeeder.java:122`
- `verticals/legal/trustaccounting/report/TrustReportPackSeeder.java:48`

Every single one persists `[]`, causing the per-event email skip for any tenant provisioned after V117 ran. V118's idempotent backfill repairs **existing** rows, but the next `compose/scripts/dev-up.sh --clean && keycloak-bootstrap` (or a real new-tenant onboarding) recreates the bug.

## What PR #1247 got wrong

The PR description (and `qa_cycle/fix-specs/OBS-2107.md:44-53`) claims:

> Postgres column DEFAULTs only apply to **new INSERTs**. Existing `org_settings` rows... had the column added populated with the DEFAULT value at migration time only if the migration explicitly used a backfill...

This is **factually wrong** for `ADD COLUMN ... NOT NULL DEFAULT X`. Per Postgres 11+ semantics, `NOT NULL DEFAULT` is atomic-with-backfill — pre-existing rows are rewritten with `X` at ALTER time. The fix-spec acknowledges the contradiction at lines 46-53 but speculates at two alternative explanations ("the row was inserted before V117 with an explicit `'[]'`" or "the row predates V117 and was backfilled to `'[]'` by some earlier seed/migration") rather than identifying the actual cause: the Java entity initializer.

The correct hypothesis — "Hibernate sends the Java field default, overriding SQL DEFAULT, on every INSERT" — would have led to a write-time fix instead of a read-time backfill. The fix-spec considered Option A (backfill, taken) and Option B (read-time fallback, rejected), but missed Option C: fix the write-time invariant. This is the missed Option C.

## Fix

Two-line change in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`:

1. Add a public constant near the existing `DEFAULT_LEGAL_MATTER_RETENTION_YEARS` (line 37):
   ```java
   public static final List<String> DEFAULT_PORTAL_NOTIFICATION_DOC_TYPES =
       List.of("matter-closure-letter", "statement-of-account");
   ```
2. In the 1-arg constructor `OrgSettings(String defaultCurrency)`, append:
   ```java
   this.portalNotificationDocTypes = new ArrayList<>(DEFAULT_PORTAL_NOTIFICATION_DOC_TYPES);
   ```

The field initializer at line 245 stays `new ArrayList<>()` — that path only runs during Hibernate's no-arg materialization, where Hibernate immediately overwrites the field from the DB row. No allocation waste, no behaviour change for entity loads.

## Why constructor, not field initializer

Putting the canonical default in the field initializer would allocate a 2-element list on every Hibernate `findById` (immediately overwritten by Hibernate from the DB row) — wasteful. The 1-arg constructor is the "create new" path; the no-arg constructor is the "materialize from DB" path. Mirroring the existing pattern (`accountingEnabled = false`, `taxInclusive = false`, etc. — all in the constructor) keeps the entity coherent.

## Why not @DynamicInsert + nullable + SQL DEFAULT

`@DynamicInsert` would tell Hibernate to omit columns with default values from INSERT, letting the SQL DEFAULT win. But `@DynamicInsert` is class-scoped — it would change the INSERT shape for every column on `OrgSettings` and has perf implications well beyond this column. Overkill for a single-column invariant.

## Test

Single unit test in `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsTest.java`:

```java
@Test
void defaultPortalNotificationDocTypes_matchesV117CanonicalDefault() {
  var settings = new OrgSettings("USD");
  assertThat(settings.getPortalNotificationDocTypes())
      .containsExactly("matter-closure-letter", "statement-of-account");
}
```

Pure unit test, no Spring context, fast. Fails on `main` before this fix; passes after. Catches regressions if anyone removes the constructor line in future.

## No new migration

V118 already backfilled existing tenants. The Java fix only changes the INSERT path for new tenants. No schema change, no data migration needed. Per the user mandate "no production data, backward compat not priority" — even if V118 hadn't been written, a `dev-down --clean && dev-up` would have repaired the affected tenant.

## Doc updates included in this PR

Per scope-widening authorization 2026-05-01:

1. **`qa_cycle/audits/02-flyway-default-drift.md`** — add a new bug class section: "Hibernate entity initializer overrides SQL DEFAULT". The audit's existing focus is on nullable `DEFAULT X` columns where pre-existing rows hold NULL. This is a different class — the schema is `NOT NULL DEFAULT` and PG correctly backfills, but the application layer (Java) overrides the DEFAULT on every INSERT. Future audits should grep for entity field initializers on `@JdbcTypeCode(SqlTypes.JSON)` collection columns where the constructor doesn't set the field.
2. **`qa_cycle/fix-specs/OBS-2107.md`** — append a "Misdiagnosis correction" section noting that the original root-cause narrative was wrong and pointing to this follow-up spec.

## Verification

1. **Inner-loop targeted test** (already run on `fix/OBS-2107-followup-entity-default`):
   `./mvnw test -Dtest='OrgSettingsTest'` — 8 tests / 0 failures (was 7 before this PR).
2. **Merge gate** — full `./mvnw verify` from `backend/`, marker written to `.claude/markers/verify-backend.json`.
3. **Manual repro** (post-merge, opportunistic):
   - `bash compose/scripts/dev-down.sh --clean && bash compose/scripts/dev-up.sh && bash compose/scripts/keycloak-bootstrap.sh`
   - Inspect a freshly provisioned tenant's `org_settings.portal_notification_doc_types` via psql — should be `["matter-closure-letter", "statement-of-account"]`, not `[]`.
   - The OBS-2106 closure-pack email path then exercises the listener end-to-end.

## Effort

XS — 2-line code change + 5-line unit test + 2 doc edits. ~15-20 minutes including verify.

## Out of scope (deliberate)

- V118 idempotency caveat — the backfill silently overwrites tenants who explicitly set `'[]'` to disable per-event sends. Audit Finding #3 (LOW). Defer.
- Wider Hibernate-vs-SQL-DEFAULT audit across other entities. Tracked in audit-02 update; revisit if a similar bug surfaces.
- Listener-registration consolidation (different bug class, batch A finding).
