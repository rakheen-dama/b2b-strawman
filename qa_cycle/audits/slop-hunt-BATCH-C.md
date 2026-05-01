# Slop hunt — Batch C: Customer/matter form UX

**Reviewed**: 2026-05-01
**PRs**: #1226 (OBS-201 wizard dedupe), #1227 (OBS-301 maxLength + field errors), #1229 (OBS-501 FICA route), #1237 (OBS-2102 customer activation Edit gate), #1245 (OBS-2105 header layout)

## Per-PR verdicts

| PR | Title | Verdict | High | Med | Low |
|---|---|---|---|---|---|
| #1226 | OBS-201 wizard dedupe + accordion UX | NIT | 0 | 0 | 4 |
| #1227 | OBS-301 maxLength alignment + field errors | NIT | 0 | 0 | 4 |
| #1229 | OBS-501 FICA Status Card route | CLEAN | 0 | 0 | 0 |
| #1237 | OBS-2102 customer activation Edit + tax skip | **NEEDS-FOLLOW-UP** | 2 | 3 | 1 |
| #1245 | OBS-2105 matter header layout | CLEAN | 0 | 0 | 0 |
| **Total** | | | **2** | **3** | **9** |

NEEDS-FOLLOW-UP PRs: **#1237** only.

## Top-3 patterns across the batch

1. **Verbose AI-style commentary** (LOW × 5 across #1226, #1227, #1237). New code is preceded by 4–8-line "OBS-XXX: …" comments that re-state what the variable name and a 1-line javadoc would already say. The pattern is harmless individually but adds up — the rationale comment is often longer than the change itself. Future convention: one canonical Javadoc per behaviour change, inline comments capped at one line referencing the Javadoc.

2. **Test-scope gaps for new behaviours** (LOW × 3 across #1226, #1227). The full vitest run (`pnpm test`) passes, but the new behaviours (`mirroredIdNumber`, all-optional accordion auto-open, inline `fieldErrors`) are not asserted in dedicated tests — they ride the suite's "no regression" coverage. None HIGH because the changes are local and visible, but a single behaviour test per fix would close the gap cheaply.

3. **Type-cast trust of error-shape contracts** (LOW × 1, #1227). The frontend trusts the backend `ProblemDetail.detail.fieldErrors` shape via a bare `as` cast. Same shape-of-error-trust pattern likely exists in many `actions.ts` files. Worth a one-time hardening pass if an OBS class around bad error rendering ever surfaces. Not a per-PR fix.

## #1237 Test-scoping failure — root cause and recommendation

### What happened

The OBS-2102 dev agent ran `./mvnw test -Dtest='*StructuralPrerequisite*'` (23 tests, package `prerequisite/`) as the backend gate. The change (per-field skip of `tax_number` for `CustomerType.INDIVIDUAL` at `LIFECYCLE_ACTIVATION` context inside `StructuralPrerequisiteCheck.check()`) silently invalidates a sibling test in a different package: `compliance/CustomerLifecyclePrerequisiteTest.updateCustomer_fillsMissingPrerequisite_triggersAutoTransitionToActive`. That test creates an INDIVIDUAL customer (factory default), fills all fields except `tax_number`, completes checklists, and asserts the customer remains `ONBOARDING`. After OBS-2102 the assertion is wrong — the customer correctly auto-transitions to `ACTIVE` because INDIVIDUAL no longer needs `tax_number` for activation.

The regression sat on main for ~12 hours until a fresh post-merge `./mvnw verify` caught it. PR #1250 fixed it forward (test-only — switch the customer to `CustomerType.COMPANY`).

### Why preventable from the diff alone

Two flags in #1237's own diff should have alerted reviewer or dev:
1. The dev agent edited `StructuralPrerequisiteCheckTest.lifecycleActivation_mirrorsInvoiceGenerationFields` to switch from factory default to `CustomerType.COMPANY`, with the inline comment "Use COMPANY here so the OBS-2102 INDIVIDUAL tax_number skip does not apply". They **knew** the factory default was now incompatible with old assertions — they fixed one test and missed the sibling.
2. The implementation note (`OBS-2102.implementation-note.md`) explicitly documents "factory default is INDIVIDUAL, which now skips tax_number at activation". The cross-package risk was articulated, then ignored.

A 30-second grep — `grep -rn "TestCustomerFactory.createCustomer\b\|TestCustomerFactory.createActiveCustomer\b" backend/src/test/java | xargs grep -l "ONBOARDING"` — would have surfaced the candidate sibling tests.

### Recommended convention to prevent recurrence

I would NOT auto-fix this — it's a Task for human triage. The candidates are:

**Option A — Cheap, agent-side rule** (recommended, no tooling change):
Add to `qa-cycle-kc/SKILL.md` and the other dev-agent skills (Task E in HANDOFF):
> Any change to a static utility, factory, default constant, or annotation on a DTO/entity must run `./mvnw verify` (full suite) before PR. The targeted-test glob is for inner-loop iteration only. If the change touches a class with `static` methods or `public static final` constants, the full verify is non-negotiable. **Do not cite a targeted-test gate in the PR description for this class of change.**
This is enforceable today via the merge-gate hook (PR #1251) which already requires the verify marker for backend changes. The gap is the dev-agent skill prompt allowing a targeted run to be cited as the gate.

**Option B — Tooling, slightly more work** (defer unless A doesn't bite):
A pre-PR git-hook or CI check that finds, for any modified Java file, every test class that imports it (transitively, one hop) and warns if those tests were not in the cited test run. This is what Bazel/Pants give for free and what Maven's surefire `-Dtest=` gives up. Effort: 1–2 days for a robust implementation.

**Option C — Make it impossible to skip** (already in place via PR #1251):
The pre-PR-merge-gate hook refuses `gh pr merge` without a current `<24h` `verify-backend.json` marker. **For PRs going forward, this is sufficient.** The OBS-2102 → OBS-2108 cascade happened *before* the lockdown. The Task is to confirm the lockdown is being respected on subsequent PRs — that's a 5-minute audit, not a code change.

**Recommendation**: file as a Task entry under "convention update" — add Option A to the dev-agent skills (Task E in HANDOFF). The merge-gate hook (Option C) already enforces the right behaviour for new PRs; what's missing is the agent-prompt-level "you must not even *cite* a targeted run as the merge gate". Don't auto-fix; raise it for user authorization.

### Bug-class entry (for `qa_cycle/bug-classes.md`, Task H)

> **Test-scope drift** — Cross-package test failures from changes to shared utilities, static methods, or factory defaults.
> Instances: OBS-2102 → OBS-2108.
> Canonical signal: change touches a `static` method, a `public static final` constant, or a test factory default.
> Canonical fix: run `./mvnw verify` (full suite). Do not narrow the test-glob to the changed class's package.
> Lint rule that would prevent recurrence: pre-PR check that for each modified `.java` file in `src/main/`, the targeted tests in the cited PR run include every test class that imports the modified class. Maven does not provide this natively; an alternative is to require full verify for any change matching the signal heuristic above.
> Where to enforce: `qa-cycle-kc/SKILL.md` Per-Day Workflow + the `pre-pr-merge-gate.sh` hook (the latter already enforces, the former does not yet articulate the rule for the dev agent).

## Other notable observations (not per-PR findings)

- **PR description quality varies dramatically** in this batch. #1245 ("Per qa_cycle/fix-specs/OBS-2105.md. Frontend CSS-only. Single file.") and #1237 ("Per qa_cycle/fix-specs/OBS-2102.md. Two stacked bugs blocking Day 28 Bulk Billing. Frontend (1 file) + backend (1 file + tests). No migration.") are both terse, but #1245 is appropriately terse for a 3-line CSS fix while #1237's terseness hides material behaviour change. Convention: PR description verbosity should scale with surface area, not with diff size.
- **CodeRabbit auto-summaries are not safe to lean on** — the #1237 auto-summary describes a SARS-correctness boundary change as "Tax number validation behavior updated", which is too vague for a reviewer to triage. The PR author's own description should not delegate to it.
- **The OBS-2102 fix bundled two independent bugs** (frontend Edit gate + backend tax_number skip). The fix-spec offered "two-PR fix or one combined PR" without recommending one. The merged PR took the bundled option, which made the OBS-2108 cross-package test regression less visible (the targeted backend gate happened to pass on the modified test class, but the bundled scope normalized "I ran the targeted gate" as adequate). Quality Gate rule #7 (one fix per PR) was tightened in PR #1251 in response to exactly this pattern.
