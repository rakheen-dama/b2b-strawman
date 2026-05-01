# Slop hunt — PR #1250: fix(OBS-2108): test regression from OBS-2102

**Batch**: E — bookkeeping/test-fix
**Reviewed**: 2026-05-01
**Verdict**: CLEAN (test fix is genuine, not weakening)

## PR description vs diff

Description claims a test-only fix that switches the failing test's customer from `INDIVIDUAL` (factory default) to `COMPANY`, so `tax_number` remains a `LIFECYCLE_ACTIVATION` prerequisite per the OBS-2102 (PR #1237) per-field skip. Diff matches: 1 test file changed +13/-1 (Customer constructor + CustomerType import), plus QA bookkeeping append-only updates to `qa_cycle/checkpoint-results/day-90-exit.md` and `qa_cycle/status.md` header. Scope honest. No production code touched.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Anti-pattern | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecyclePrerequisiteTest.java:313-322` | Uses raw `new Customer(...)` constructor in a test, contrary to backend/CLAUDE.md anti-pattern: "Never create `Customer` objects in tests using the raw constructor without explicit `LifecycleStatus` — use `TestCustomerFactory` instead." The fix DOES pass an explicit `LifecycleStatus.ONBOARDING`, satisfying the spirit of the rule, BUT the cleaner alternative is to extend `TestCustomerFactory` with a `createCustomerWithStatusAndType(...)` overload (PR description acknowledges no overload exists). The current path is justifiable for a single-call test, but invites more raw constructors in future. | Optional follow-up: add `TestCustomerFactory.createCustomerWithStatusAndType(name, email, createdBy, type, status)` and switch this test to use it. Not blocking. |

## (#1250 only) Test fix verdict

**Genuine fix, NOT weakening.** Both critical assertions are preserved:

1. **Line 336**: `assertThat(beforeUpdate.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING)` — the "auto-transition is BLOCKED while tax_number missing" assertion. UNCHANGED.
2. **Line 366**: `assertThat(afterUpdate.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE)` — the "auto-transitions to ACTIVE once tax_number filled" assertion. UNCHANGED.

The fix changes the **test setup** (customer type) so the test's premise (tax_number is a real LIFECYCLE_ACTIVATION prerequisite) holds against the production behaviour PR #1237 introduced (tax_number is skipped for INDIVIDUAL at LIFECYCLE_ACTIVATION). With CustomerType.COMPANY, `StructuralPrerequisiteCheck` does not skip tax_number, so the auto-transition is correctly blocked when tax_number is missing — matching the test's intent.

Comparison with the alternative "Option B" (asserting ACTIVE at line 336 to match new INDIVIDUAL semantics): that would have been weakening — it would erase the "auto-transition blocked when prerequisite missing" coverage entirely. The chosen Option A keeps the coverage of the prerequisite-blocking semantics by using a customer type for which tax_number IS a prerequisite.

Test scope is unchanged: still exercises the `updateCustomer → fills tax_number → auto-transitions to ACTIVE` flow end-to-end. No `@Disabled`, no `assumeTrue`, no try/catch swallowing.

## Notes

PR #1250 is the corrective for the PR #1237 (OBS-2102) miss caught when the new mandate forced a fresh `./mvnw verify`. The `qa_cycle/checkpoint-results/day-90-exit.md` E.15 / OBS-2108 entries are honest: they document that the cycle 21/22 dispatches reported E.15 PASS without running backend verify, the deferred fresh verify caught the failure, the failure was correctly diagnosed as test-side, and the fix preserves test intent. This is the kind of post-hoc honesty that the new Quality Gates require.

PR #1237's original miss (forgetting to update the sister test) is a real example of the "targeted test scope" anti-pattern (Quality Gate rule #5) — and is appropriately referenced as a class in the OBS-2108 status entries.
