# Bug Classes — recurring patterns seen across the QA cycle

Defence-in-depth catalogue. Each entry documents a class of bug that has shown up more than once in this codebase, the mechanism behind it, and the architectural rule or tooling that prevents it. Use this when triaging a new finding — if it matches a class here, the canonical fix already exists.

**Last updated**: 2026-05-02
**Maintained by**: orchestrator after each cycle's slop-hunt audit. Add a new entry when a class repeats; update the "examples" list when a new occurrence ships.

---

## Class 1 — Notification-pipeline gaps

**Symptom.** A user-facing notification (email, in-app, portal) is silently not dispatched after a workflow that should trigger it. Logs are quiet at INFO. The trigger event fires; the listener runs; the listener returns without error. The user never learns the workflow completed.

**Mechanism.** The chain is event publication → AFTER_COMMIT listener → gate evaluation → dispatch. Any one of the gates can drop the event silently:

- **Visibility gate races a post-publish flip.** `GeneratedDocumentService.generateDocument` publishes the canonical event before the caller's `markSystemAutoShared` flip commits. The listener's DB-fallback read sees the still-INTERNAL row and skips. (OBS-2106 / cycle 47 Day 60.)
- **Allowlist mismatch on slug vs display name.** The listener gates on a per-tenant slug allowlist (`OrgSettings.portalNotificationDocTypes`). The event carries the *display* name (`"Matter Closure Letter"`) instead of the slug (`"matter-closure-letter"`). (GAP-L-97.)
- **Listener wired but template not registered.** The handler dispatches `email.send(template_slug)` but the template doesn't exist in the tenant's pack. The send fails silently or sends with the wrong body. (OBS-AUDIT-N1, OBS-2107 portal-notification-doc-types empty default.)
- **Best-effort flip swallows failure but caller already published a "we shipped" event.** When the flip happens in a try/catch that logs WARN and continues, the event listener fires but the underlying state is wrong.

**Detection.** Hard. Diagnostic logs are typically at DEBUG so production silently drops the path. Not caught by happy-path integration tests because the assertion is "email arrived" — the dedup or fallback may make it arrive even when the canonical path is broken.

**Prevention.**

- **Atomic event detail.** The publishing service must include all the gate-relevant metadata (`scope`, `visibility`, slug) in the event's `details` map at publish time. Mirrors the SoA pattern in `StatementService.java:217-246`. The listener never DB-falls-back on race-prone columns.
- **Single canonical emitter.** No second compensating event from a different class to "patch" the first. If the canonical emitter is wrong, fix it there.
- **Log-level discipline.** Skip-path log lines in the listener should be at INFO so on-call sees which gate dropped an event without flipping the package logger and re-running the scenario. PR #1246 introduced this for `PortalDocumentNotificationHandler`.
- **Exit checkpoint test.** The integration test must assert end-to-end: GreenMail singleton receives ≥1 email AND the canonical event has the gate-relevant details. PR #1255 + PR #1261's `MatterClosureEmailIntegrationTest.close_publishesSingleCanonicalEventWithExplicitPortalVisibility`.

**Examples (canonical fixes).**

| Occurrence | Fix |
|---|---|
| OBS-2106 (Day 60 closure-pack email dropped) | PR #1246 (workaround) → PR #1261 (structural fix: visibility hint atomic in canonical event) |
| OBS-2107 (portal-notification-doc-types empty default) | PR #1247 (V117 SQL DEFAULT) → PR #1254 (real fix: align entity initializer) |
| OBS-AUDIT-N1 (portal-proposal-expired email not wired) | PR #1255 |
| GAP-L-97 (allowlist mismatch on slug vs display name) | shipped earlier; canonical pattern documented in `DocumentGeneratedEvent.java:11-14` |

**Related rules.** `CLAUDE.md` Quality Gate #3 (PASS means observed); Quality Gate #4 (reproduce-before-fix).

---

## Class 2 — Schema/data drift

**Symptom.** A SQL migration sets a default or constraint, but the code path that creates the entity returns a different value. New tenants get the wrong default; existing rows are correct. Or: the entity in code says one thing, the DB says another, and the audit log captures the lie.

**Mechanism.** Two competing sources of truth:

- **JPA field initializer overrides SQL DEFAULT.** Hibernate `INSERT` includes every column (including the one with the SQL DEFAULT) using the value from the entity's field initializer. The DB never gets a chance to apply its DEFAULT for newly-instantiated entities. Existing rows already in the DB keep their DEFAULT, so a partial backfill behaves correctly while new tenants get the broken value. (OBS-2107.)
- **CHECK constraint added without registering all reference types.** A new `email_delivery_reference_type` value gets used in code but isn't in the `CHECK (... IN (...))` constraint. INSERT fails at runtime with a constraint violation. (PR #1255 V119.)
- **Flyway migration sets a column NOT NULL but the code path that backfills nulls hasn't run yet on the tenant.** Provisioning fails for existing tenants, succeeds for new ones (or vice versa).

**Detection.**

- **The provisioning round-trip test.** Provision a fresh tenant, immediately read back the relevant column. If it's not what the SQL DEFAULT says it should be, the field initializer is overriding.
- **ArchUnit-style schema-vs-entity comparator** (not yet built) — would compare `@Column` annotations against the migration's column types/defaults at build time.
- Slop-hunt audit catches it after the fact when it surfaces as a notification-pipeline gap (Class 1) downstream.

**Prevention.**

- **Entity field initializers must match the SQL DEFAULT exactly.** When a migration adds a DEFAULT, simultaneously update the entity's field initializer to the same value. (OBS-2107 → PR #1254.)
- **CHECK constraints are part of the contract, not advisory.** When you introduce a new enum value or reference type, the same migration adds it to every CHECK constraint that gates it. Don't ship the code first and the CHECK extension later.
- **Backfill migrations are cheap; do them when adding NOT NULL or a new DEFAULT.** Per the user mandate, "backward data compat is not a priority" but cheap backfills are still worth doing.

**Examples.**

| Occurrence | Fix |
|---|---|
| OBS-2107 (`OrgSettings.portalNotificationDocTypes` defaulted to `[]` in entity, SQL DEFAULT arrived but field initializer overrode it) | PR #1254 — seed canonical default in entity constructor |
| V119 — `chk_email_delivery_reference_type` missing PORTAL types | PR #1255 — add types to constraint same migration |

**Related rules.** `CLAUDE.md` Quality Gate #5 (test scoping — full `mvnw verify`).

---

## Class 3 — Radix `asChild` adjacency collisions

**Symptom.** A button renders, looks correct, but clicking does nothing. Or: a hydration mismatch warning fires on a page with adjacent dialog triggers. Or: an aria-controls attribute is missing on first paint and added post-mount. Symptom severity ranges from "click loss" (worst) to "hydration warning + workaround re-render" (annoying).

**Mechanism.** `<*Trigger asChild>{children}</*Trigger>` is Radix's Slot pattern — the trigger clones its child element and merges its own props (incl. `onClick`, `aria-controls`). Under React 19's reconciliation:

- **Click-loss variant.** Two adjacent `<Trigger asChild>` siblings render in the same flex/grid container. Both Slots `cloneElement` the inner Button at the same unkeyed sibling position. React 19 collapses one — the surviving Slot's `onClick` wins, the other's is silently dropped. (OBS-2103 / OBS-2103b.)
- **Hydration-mismatch variant.** Radix's older `useId` allocated `aria-controls` only post-mount. Server HTML lacked it, client added it after `useEffect`. The mount-gate workaround `if (!mounted) return <>{children}</>` skipped the entire Radix subtree on SSR. (OBS-704 v1/v2.)

**Detection.**

- **Manual eyeball.** Grep for `<*Trigger asChild` in components that render adjacent triggers. Audit-03 named 4 files. The 5-min eyeball check is "do these two Triggers share a flex parent?".
- **Vitest reproducer is unreliable.** `cloneElement(children).props === undefined` for lazy/RSC children — vitest+happy-dom doesn't always reproduce the RSC shape, so the bug can ship green from unit tests. PR #1239's regression test passed in vitest but failed in production.
- **SSR snapshot test.** `renderToString` + assert the trigger's `aria-controls` attribute. PR #1262 added one for `CreateProposalDialog`. A reusable harness for the dialog family is audit-03 recommendation #4 (deferred).
- **ESLint custom rule** that flags two `<*Trigger asChild>` siblings in the same parent JSX block. Audit-03 recommendation #2 (deferred, ~2h to build).

**Prevention.**

- **Dialog owns the button.** The canonical fix from PR #1242 → propagated by PR #1263. Dialog component renders `<Button>` directly; consumers pass `triggerLabel` / `triggerVariant` / `triggerSize` / `triggerIcon` / `triggerAriaLabel` props instead of `children`. No Slot wrapping at the call site, no cloneElement, no adjacency collision.
- **Don't use `suppressHydrationWarning`.** That silences the symptom without fixing the cause. Audit caught CodeRabbit suggesting it during OBS-704 v3 review.
- **Tooltip-only triggers are lower risk.** TooltipTriggers are hover-only (no onClick) — even if they collide, the hover handler is what's lost, not the click. Per audit-03, the 4 TooltipTrigger sites in `expense-list.tsx` were left alone.

**Examples.**

| Occurrence | Fix |
|---|---|
| OBS-2103 (customer Edit + Archive click loss in #1239's first attempt) | PR #1242 — dialog owns button refactor |
| OBS-704 v1 (timestamp misdiagnosis) | reverted in spirit by PR #1262 (`useNowMs` re-documented for what it actually guards) |
| OBS-704 v2 (mount-gate workaround) | PR #1262 — remove mount-gate, add SSR snapshot test |
| Audit-03 (4 files still carrying the pattern) | PR #1263 — propagate dialog-owns-button to comments, rates, expenses |

**Related rules.** Per audit, "dialog owns button" should be codified in `frontend/CLAUDE.md` (audit-03 recommendation #3, deferred).

---

## Class 4 — SQL Cartesian aggregates

**Symptom.** A report query returns inflated totals — `SUM(amount)` doubles, `COUNT(DISTINCT id)` looks right but `SUM` doesn't, dashboards show "1.5x" the expected number. Symptom only appears when the entity has multiple related rows (multi-tag, multi-document, multi-anything).

**Mechanism.** A query with two or more JOINs to one-to-many relations multiplies rows. The aggregate then sums each row N times where N is the cardinality of the joined sets. Example: `JOIN tags JOIN documents` on a project — if a project has 3 tags and 4 documents, the row count is 3×4=12, and any `SUM(amount)` over `12 × amount`.

**Detection.**

- **Test fixture must have non-trivial cardinality.** Tests that use `1 tag, 1 document` per project miss the bug entirely. Add fixtures with 2+ rows on each one-to-many relation.
- **Compare aggregate to a manual sum.** If `SUM(amount) WHERE project_id = X` from the report query doesn't match `SELECT SUM(amount) FROM expenses WHERE project_id = X` directly, you have multiplication.
- **JaCoCo branch coverage.** A query with 3 joins and a single SUM has very few branches; coverage hides the multiplication.

**Prevention.**

- **Pre-aggregate in subqueries.** `JOIN (SELECT project_id, SUM(amount) FROM expenses GROUP BY project_id) e` so `e` has at most one row per project. Same for tags / documents — aggregate to one row per parent before joining.
- **Or: separate queries.** Pull the parent rows once, then issue per-parent queries for related sets. More round-trips but no multiplication.
- **`COUNT(DISTINCT)` is not a substitute.** Distinct counts don't help with `SUM(amount)` — distinct rows still get summed.

**Examples.** Specific historical occurrences predate this catalogue. Add concrete PRs as new instances surface.

**Related rules.** `CLAUDE.md` Quality Gate #5 (test scoping — full `mvnw verify`); fixtures in `testutil/` should default to ≥2 cardinality on one-to-many relations.

---

## Class 5 — Test-scope drift

**Symptom.** A change ships green in targeted tests (`./mvnw test -Dtest='*Foo*'`). The merge happens. CI on `main` runs the full suite and a test in a different package fails — the change broke a class that the targeted run never loaded.

**Mechanism.** Targeted tests load only the named test classes' Spring contexts. A change to a shared class (say, `BillingRateService`) might be loaded in `BillingRateServiceTest`, but its consumers (e.g. `InvoiceServiceTest`, `RetainerServiceTest`) might not be in the `*Billing*` filter. The compile passes, the targeted test passes, the change ships. Then CI's full suite runs and a downstream consumer's assertion catches the break.

The OBS-2102 → OBS-2108 cascade was this: the dev only ran tests in the same package; the broken test was elsewhere.

**Detection.** The full `./mvnw verify` on `main` is the only reliable detector. Targeted runs are for inner-loop iteration only.

**Prevention.**

- **Quality Gate #1 — `./mvnw verify` is the merge bar.** Targeted runs are for "did my edit at least compile?" iteration, not for "is my PR ready to merge?".
- **Quality Gate #5 — targeted tests must include the failing test's package AND any package that imports the changed class.** If you do narrow, narrow correctly: trace `git grep` for imports.
- **Frontend equivalent: `pnpm test` (all 340 files), not `pnpm test -- create-proposal-dialog` filter.** Vitest filters don't catch cross-component breakage either.

**Examples.**

| Occurrence | Fix |
|---|---|
| OBS-2102 → OBS-2108 cascade | post-cascade tightening: `CLAUDE.md` Quality Gate #1 + the merge-gate hook (`pre-pr-merge-gate.sh`) requiring `verify-backend.json` marker |
| OBS-704 v3 SSR test discoverable only via full suite | PR #1262 — wrote test against `vitest run components/...` then verified with full `pnpm test` |

**Related rules.** `CLAUDE.md` Quality Gate #1, Gate #5; `.claude/hooks/pre-pr-merge-gate.sh` enforces marker presence at merge time.

---

## How to use this doc

When triaging a new finding:

1. **Compare to the Symptom column.** If the new bug looks like one of these, the class is known.
2. **Read the Mechanism + Prevention.** The canonical fix is named.
3. **Check the Examples table.** A previous PR may have shipped exactly this fix; look at its diff.
4. **Add the new occurrence.** When the new fix lands, append it to the Examples table — the catalogue grows with the codebase.

When you encounter a recurring pattern that ISN'T listed here:

1. **Did it happen twice?** Once is happenstance; twice is a class. Add an entry.
2. **Update the slop-hunt audit template** so the next batch flags the new class.
3. **If the class has a tooling-level prevention** (lint rule, schema check, ArchUnit constraint), that's the highest-leverage fix — propose it as a separate task.
