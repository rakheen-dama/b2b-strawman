# Slop hunt — Batch E summary

**Batch**: E — Branding + test-fix + QA bookkeeping
**Reviewed**: 2026-05-01
**PRs**: #1225, #1228, #1244, #1248, #1249, #1250

## Per-PR verdicts

| PR | Title | Verdict | HIGH | MEDIUM | LOW |
|----|-------|---------|------|--------|-----|
| #1225 | OBS-102 Trust Accounting sidebar | CLEAN | 0 | 0 | 0 |
| #1228 | OBS-404 DocTeams → Kazi rebrand + guardrail | NIT | 0 | 1 | 2 |
| #1244 | qa(cycle 2026-04-30) — 19 fixes + 20 days | NIT | 0 | 1 | 2 |
| #1248 | qa(cycle continuation) — Days 46/60/61/75/85/88/90 | NIT | 0 | 0 | 2 |
| #1249 | qa(cycle 22) — verify OBS-2107 + Day 90 exit gates | NIT | 0 | 1 | 1 |
| #1250 | OBS-2108 test fix | CLEAN | 0 | 0 | 1 |

**Total findings**: 0 HIGH / 3 MEDIUM / 8 LOW.

## DISPOSED_BUG / unjustified scenario amendments

**None.**

Every scenario amendment in PR #1244 and PR #1248 cites:
- A status.md tracker row with classification (LEGITIMATE_FIX / WONT_FIX feature-gap / scenario-mismatch)
- File:line evidence of intentional product design (e.g. `terminology-map.ts:67-76`, `ProposalContentSeeder.buildDefaultContent`, `PortalRequestView.java` field listing)
- An OBS- ticket for tracking
- For feature-gaps (OBS-701, OBS-2101): explicit cross-reference to the user-mandate exemption pattern (KYC/Payments precedent) and a thorough triage investigation in the fix-spec

The 9 OBS items disposed via amendment (OBS-001/002/101/103/302/401/402/403, plus feature-gaps OBS-701/2101) are individually justified. The cumulative pattern ("the scenario was wrong about 9 things") is notable but does not represent disposed bugs — each is a legitimate scenario-mismatch backed by code-level evidence.

**One marginal pattern** (NOT a disposed bug): Day 45/46 trust balance amendment R 70k → R 71k is a self-introduced state amendment, where the OBS-1101 verify cycle's R 1,000 deposit became part of subsequent assertions instead of being rolled back between cycles. This is test-state contamination, not bug disposition. Logged as MEDIUM in PR #1244 (Finding #1) and LOW in PR #1248 (Finding #1). Recommend rolling back fixture deposits between cycles or accepting the contamination explicitly.

## #1250 verdict — genuine fix vs weakened test

**Genuine fix. NOT weakening.**

The `CustomerLifecyclePrerequisiteTest.updateCustomer_fillsMissingPrerequisite_triggersAutoTransitionToActive` test asserts:
1. **Line 336**: `LifecycleStatus.ONBOARDING` BEFORE update (auto-transition blocked while tax_number missing) — UNCHANGED
2. **Line 366**: `LifecycleStatus.ACTIVE` AFTER update (auto-transition fires when tax_number filled) — UNCHANGED

The fix changes the **test setup** (CustomerType from factory-default INDIVIDUAL to explicit COMPANY) so the test's premise — tax_number IS a LIFECYCLE_ACTIVATION prerequisite — holds against the OBS-2102 production change (which made tax_number not-required for INDIVIDUAL). This is "Option A" from the day-90-exit triage: preserve test intent by changing the input to a value where the prerequisite still applies. "Option B" (asserting ACTIVE at line 336) would have weakened the test — it would erase the auto-transition-blocked semantics. Option A was correctly chosen.

No `@Disabled`, no `assumeTrue`, no try/catch, no narrowed scope. The test still exercises the full update→auto-transition flow.

One minor anti-pattern (LOW): the fix uses raw `new Customer(...)` constructor instead of extending `TestCustomerFactory`. Backend CLAUDE.md prefers factory usage but does explicitly allow raw constructor with explicit `LifecycleStatus` (which the fix supplies). Optional follow-up: add `TestCustomerFactory.createCustomerWithStatusAndType(...)` overload.

## #1228 guardrail — strong enough?

**No, has a coverage gap (MEDIUM).** Recommend extending before relying on it as the project's brand defence-in-depth.

The Vitest guardrail at `portal/lib/__tests__/brand.test.ts` walks `["app", "components", "lib"]` rooted at the portal root. It correctly excludes `node_modules` and `.next` (build output) and `__tests__` (the test file itself contains the string).

Gaps:
1. **Roots are too narrow.** Portal also has `hooks/`, `middleware.ts`, `e2e/`. A future "DocTeams" reintroduced in `portal/hooks/use-branding.ts` or `portal/middleware.ts` would NOT be caught.
2. **File extensions are TS/JS only** (`/\.(tsx?|jsx?)$/`). Does NOT scan `.json` (e.g., `manifest.json` brand metadata) or `.html` / `.svg` (alt text). Low-impact today.

Recommendation:
- Extend `roots` to include `hooks`, `middleware.ts` (handle as a single file), and consider `e2e/`.
- Optional: add `.json|.html|.svg` to the regex if the brand can leak via manifest / alt text.

The current guardrail will catch the most common reintroduction surface (app routes, components, lib utilities) but does not cover the entire portal source tree as the PR description implies.

## Evidence coverage for bookkeeping PRs

Sampling result: **strong evidence coverage overall**, with two specific weak spots.

**Strong (PRs #1244, #1248, #1249)**:
- Mailpit message IDs cited concretely (e.g. `WVVCHF6KxLFodNmUpcRWoG`, `SpJuVnSwWUzLdyWcy9RbSu`, `825imn2X9feksRFZX2GoRr`)
- Backend log lines quoted with PIDs, request UUIDs, tenant IDs, timestamps
- DB row UUIDs cited for matters, customers, requests, invoices
- S3 PDF byte counts and MD5 hashes (e.g. day-60 SoA `5489 bytes, MD5 52b1a3227eca8a6ee8228cfe8f1d9060`)
- 146 PNG screenshots + 16 JSON dumps + 1 actual PDF artefact across days
- Flyway migration log lines for V117/V118
- curl traces with status codes for KC integration

**Weak spots**:
- **E.15 PASS without backend verify** (PR #1249 Finding #1): "PASS for sub-gates run; backend verify in flight at writeup time" was claimed PASS but should have been DEFERRED per the new Quality Gates. This was the exact seam that caught OBS-2108 — already addressed by PR #1250 + the merge-gate hook + marker contract (PR #1251).
- **E.2 visual-baseline implicit coverage** (PR #1249 Finding #2): "no new visual regressions reported" without artefact backing. The Phase 68 Epic 500B Playwright baseline suite was not run; coverage is delegated to the frontend test-suite gate. Honest in disclosure but weak as a separate exit-gate claim.

No "visually confirmed without artefact" pattern in the sample. The agent did capture honest absence-evidence (e.g. day-60 OBS-2106 closure email FAIL with "Mailpit total = 13, latest is `Trust account activity 19:21:36`; closure committed at `19:31:42`").

## Recommendations

1. **#1250 is correct** — no rework needed. Optionally extend `TestCustomerFactory` with a status+type overload for future tests.
2. **#1228 brand guardrail** should be extended before being trusted as the project's brand defence: walk all of `portal/` source, not just `app/components/lib`. MEDIUM follow-up.
3. **Trust balance amendment carry-over** (Day 45/46): roll back the OBS-1101 verify R 1,000 fixture deposit between cycles, or document the test-state contamination explicitly in the scenario as a precondition. LOW-MEDIUM.
4. **E.15 / E.2 evidence weak spots** are already addressed by the Quality Gate lockdown — no further work, just confirm the merge-gate hook is enforcing on subsequent cycles.
5. **Scenario amendments are NOT being used to dispose of bugs** in this batch — the discipline of evidence-backed dispositions held. The clean slate is impressive given the volume (909-file PR, 17 amendments).

## Final verdict

**Batch E is clean enough to keep.** Zero HIGH findings, zero disposed-bug amendments, zero weakened tests. Three MEDIUM findings (brand guardrail gap, R 71k carry-over, E.15 deferred-as-PASS) and eight LOW findings — none invalidate the merged work.

The OBS-2102 → OBS-2108 cascade documented in PR #1250 is the canonical case study for the new Quality Gate rule #5 (test scoping). The PR description and day-90-exit.md openly attribute the miss to PR #1237's targeted-test scope, and the corrective work is correctly classified — exactly the kind of post-hoc honesty the new gates require.
