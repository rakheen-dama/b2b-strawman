# Slop hunt — Batch D summary (Trust accounting / bulk billing / SQL / data)

**Reviewed**: 2026-05-01
**PRs**: #1235, #1238, #1240, #1241, #1247
**Reviewer**: code-reviewer subagent (slop hunt pass)

## Per-PR verdicts

| PR | Title | Verdict | HIGH | MEDIUM | LOW |
|---|---|---|---|---|---|
| #1235 | OBS-1001 — combobox replaces UUID inputs | NIT | 0 | 0 | 4 |
| #1238 | OBS-2104 — NULL currency + legal disbursements | NEEDS-FOLLOW-UP | 0 | 2 | 2 |
| #1240 | OBS-2104b — CTE rewrite (Cartesian fix) | NIT | 0 | 0 | 4 |
| #1241 | OBS-2104c — surface legal disbursements | NIT | 0 | 0 | 5 |
| #1247 | OBS-2107 — backfill empty doc-types | **NEEDS-FOLLOW-UP** | **2** | 0 | 3 |

**Totals**: 2 HIGH, 2 MEDIUM, 18 LOW.

## NEEDS-FOLLOW-UP PRs

- **#1247 (OBS-2107)** — Two HIGH findings:
  - Java entity initializer (`OrgSettings.java:245 portalNotificationDocTypes = new ArrayList<>()`) overrides the SQL DEFAULT on every INSERT, so the bug class **will recur** on any tenant provisioned after V118. The data-only V118 backfill does not address the invariant.
  - PR description and fix-spec misdiagnose the root cause (claiming "Postgres DEFAULTs only apply to NEW INSERTs"). Audit `02-flyway-default-drift.md` correctly distinguishes NOT-NULL-DEFAULT (atomic backfill) from nullable-DEFAULT (no backfill); V117 is the former. The misdiagnosis will mislead future debugging.
- **#1238 (OBS-2104)** — Two MEDIUM findings:
  - NULL-currency tolerance is a documented Path A workaround (per fix-spec). Time entries without a rate card are silently billed at R 0,00. Should be tracked as known debt — the upstream fix is preventing un-priced billable time entries, not accommodating them at billing time.
  - Test-scope drift introduced the Cartesian-product bug repaired ~75 minutes later in #1240. Targeted `-Dtest='*BillingRun*'` did not cover multi-source aggregate cardinality.

## Top-3 patterns

### 1. Test-scope drift — every backend PR in this batch

`#1238`, `#1240`, `#1241`, `#1247` all run targeted `./mvnw test -Dtest='...'` per PR description, none show evidence of `./mvnw verify`. CLAUDE.md §1 requires full verify for any backend production-behaviour change. Per cycle-23 the orchestrator confirmed full verify clean (5011 tests, 0F/0E), so the rule was eventually satisfied at the cycle level — but at the per-PR level it is bent every time. The lockdown hook (PR #1251) is what enforces this going forward; pre-lockdown PRs in this batch were on the honour system.

This pattern caused the OBS-2104 → OBS-2104b cascade: #1238 passed targeted tests but introduced a Cartesian product. The fix (#1240) wrote a cardinality regression suite that the prior PR should have anticipated.

### 2. Workarounds documented as fixes

- `#1238`: NULL-currency time entries silently billed at R 0,00. The fix-spec calls this "Path A — relax NULL-currency on time entries" and ties it to OBS-2101 WONT_FIX. Acknowledged as workaround.
- `#1247`: Data-only backfill repairs symptoms but leaves the Java entity initializer to recreate the empty `'[]'` state on every newly provisioned tenant.
- `#1235` (LOW): Empty try/catch swallowing customer-fetch failures and silently rendering empty pickers.

The pattern: "fix the symptom that QA observed today" rather than "fix the invariant that produces the symptom class." Per the user mandate ("no workarounds, fix actual flows and bugs as they are found"), all three of these would have been better addressed at the invariant layer.

### 3. Misleading or AI-flavoured comments narrating SQL/Flyway behaviour

- `#1238` and `#1240` both ship 8-18 line block comments narrating the bug, the fix, and the OBS-2104 invariants. Useful but redundant with the fix-specs. Smells of "explain to the reviewer / future maintainer that I understood the bug" rather than "tell me something the code does not."
- `#1241` ships a comment claiming `legal_disbursements` only exists in legal-vertical tenant schemas. **False** — V100 is unconditional. The table exists for every tenant; only the row population is module-gated.
- `#1247`'s migration header narrative is factually wrong about Postgres DEFAULT semantics.

These misleading comments are a long-tail risk: they teach future readers wrong mental models. The code is correct; the comments are not.

## CTE row-set equivalence verdict (#1240)

**The CTE rewrite is row-set equivalent and aggregation-correct. No silent narrowing.**

Reasoning (full detail in `slop-hunt-PR-1240.md`):

- **OLD query** started with INNER JOIN `customers → customer_projects → projects` then LEFT JOINed tasks/time_entries/expenses/legal_disbursements, GROUP BY c.id, c.name, HAVING ≥1 of the per-source DISTINCT counts > 0. Counts were correct (DISTINCT). SUMs were inflated by sibling-LEFT-JOIN cardinalities (e.g. 1 disbursement × 9 tasks = 9× disbursement amount).
- **NEW query** has `customer_filter` CTE = active customers (no project filter), 3 per-source aggregation CTEs each joining only `customer_projects → its source table` and aggregating per customer in isolation, then a final SELECT with LEFT JOINs and a WHERE filter requiring ≥1 of the 3 counts > 0.

Edge cases verified:
- Customer with no `customer_projects`: OLD excluded by INNER JOIN; NEW included in customer_filter, but each agg CTE requires a `customer_projects` JOIN → all aggs return null → COALESCE-to-0 → filtered by WHERE. **Equivalent.**
- Customer with `customer_projects` but no in-period activity: OLD excluded by HAVING; NEW filtered by WHERE. **Equivalent.**
- `customer_projects` UNIQUE on `(customer_id, project_id)` per `V8__create_customer_projects.sql:14-16` — no double-counting from the junction table itself.
- Pre-existing edge case (NOT introduced by this PR): a project linked to two customers via `customer_projects` would have its time/expense/disbursement amounts contribute to BOTH customers' totals. Both queries share this property; if it is a defect it is a pre-existing modelling issue, not a regression.

The 3 cardinality regression tests in `BillingRunPreviewCardinalityTest` pin the headline scenarios with exact-amount assertions. **Strong test design**, end-to-end coverage of the new SQL via the wizard preview endpoint.

**Cross-reference to `qa_cycle/audits/04-sql-cartesian.md` suspects:**
- `ProjectRepository.java` — still needs spot-check; not changed by this PR.
- `AuditEventRepository.java` — still needs spot-check; not changed by this PR.

If either matches the OLD #1238 pattern, the CTE rewrite from #1240 is the canonical template.

## Schema-vs-data verdict (#1247)

**Data-only fix; schema invariant left permissive; bug class will recur on every newly provisioned tenant.**

Detail (full in `slop-hunt-PR-1247.md`):

- V117 declared `ADD COLUMN ... NOT NULL DEFAULT '[matter-closure-letter, statement-of-account]'::jsonb`. Per Postgres 11+ semantics, this **does** atomically backfill existing rows with the DEFAULT. So pre-V117 rows would have been populated correctly when V117 ran.
- The actual root cause is in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:245`, which initializes the field as `new ArrayList<>()`. When Hibernate persists a new `OrgSettings` row (e.g. during tenant provisioning), it sends `[]` to the DB, overriding the SQL DEFAULT.
- V118 repairs existing wrong data via `UPDATE ... WHERE col IS NULL OR col = '[]'::jsonb`. Idempotent on existing rows. **But** any tenant provisioned in the future will:
  1. INSERT into `org_settings` with `portal_notification_doc_types = '[]'` (Java initializer wins over SQL DEFAULT).
  2. The handler will skip with "per-tenant allowlist empty" exactly as before.
  3. V118 will not run again (Flyway tracks completed migrations).
- The schema (column nullability) is correct (NOT NULL, with a DEFAULT). It does not need to be tightened. What needs to change is **the application layer** — the Java entity initializer must respect the canonical default, OR be marked as `@Column(insertable = false)` so Hibernate omits it on INSERT and the DB DEFAULT wins.

The fix-spec considered Option A (V118 backfill — chosen) and Option B (Java read-time fallback — rejected). It did **not** consider Option C: fix the Java write-time initializer. Option C is the simplest correct fix and was missed.

**Recommended follow-up**: a one-line PR changing `OrgSettings.java:245` to either initialize with the canonical default or set to null + mark `@Column(insertable = false)`. ~30 minutes including a unit test asserting a freshly constructed `OrgSettings` has the expected list. **Status (2026-05-01): Completed in PR #1254** — the canonical default is now seeded in the 1-arg `OrgSettings(String defaultCurrency)` constructor.

## Cross-cutting recommendations (for the orchestrator)

1. ~~**File a follow-up bug for OrgSettings.java initializer drift.**~~ Done — see `qa_cycle/fix-specs/OBS-2107-followup.md` (PR #1254 merged 2026-05-01). The wider audit of "Hibernate-managed entity initializer races SQL DEFAULT" — other `OrgSettings` fields (and `field_definitions` per audit-02 suspects) — remains open.

2. **Update `qa_cycle/audits/02-flyway-default-drift.md`** to add the new bug class. **Done** — see the "Hibernate entity initializer overrides SQL DEFAULT" section added in PR #1254.

3. **Spot-check `ProjectRepository.java` and `AuditEventRepository.java`** per audit-04 — both still flagged as suspects after #1240's fix. ~5 min each.

4. **Future SQL-aggregation rewrites must include row-cardinality regression tests in the same PR.** #1238 → #1240 was 75 minutes of broken-on-main behaviour because the cardinality test suite did not exist before the PR rewrote the SQL. CLAUDE.md §5 already says this, but adding it explicitly to the QA-cycle skill template would make the requirement enforceable.

5. **Audit OBS-2101 cascade.** PR #1238's NULL-currency tolerance is acknowledged as tied to OBS-2101 WONT_FIX. The right fix is upstream — preventing billable time entries without a rate card, OR clearly marking such entries as un-billable in the wizard rather than billing at R 0. This is product-strategy adjacent; flag for triage rather than auto-PR.

6. **No urgent follow-up for #1235, #1240, #1241** — the LOW findings are cosmetic (comment cleanup, test-style consistency, defensive-catch logging). Address opportunistically when next touching those files.
