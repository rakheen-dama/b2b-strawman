# Slop hunt — Batch A summary (notification pipeline / email dispatch)

**Reviewed**: 2026-05-01
**PRs**: #1230, #1232, #1233, #1236, #1246

## Per-PR verdicts

| PR | Title | Verdict |
|---|---|---|
| #1230 | OBS-502 portal envelope counter | CLEAN |
| #1232 | OBS-702 proposal expiry timezone | NIT |
| #1233 | OBS-703 portal email on ProposalSent | NEEDS-FOLLOW-UP |
| #1236 | OBS-1101 trust-deposit nudge formatting | NEEDS-FOLLOW-UP |
| #1246 | OBS-2106 closure-pack portal email | NEEDS-FOLLOW-UP |

## Findings by severity

| Severity | Count |
|---|---|
| HIGH | 6 |
| MEDIUM | 11 |
| LOW | 4 |
| **Total** | **21** |

(No `FUNCTIONAL BUG:` rows — all findings are slop, not correctness regressions, except the latent-double-send risk in #1233 which is flagged as HIGH workaround/idempotency rather than as a confirmed live bug.)

## Top-3 patterns

### 1. Targeted-test-only merges — pattern repeats across 3 of 5 PRs

PRs #1233, #1236, #1246 all shipped on `mvnw test -Dtest='*Foo*'` rather than full `mvnw verify`. This is the **exact pattern the OBS-2102 → OBS-2108 cascade traced to**, and it landed in 3 of 5 PRs in this batch — meaning the post-cycle-22 lockdown (PR #1251) was not in force when these merged. Each was a backend listener change with cross-package blast radius.

**Action**: independently re-run `./mvnw verify` against `main` at the merge commits to confirm no latent regressions. If there are, file as cycle-23+ regressions.

### 2. Listener-registration drift — confirmed, multiple axes

The 5 PRs in this batch (3 of which touch the listener pipeline directly) implement the listener-registration pattern **inconsistently**:

| Axis | PR #1233 (ProposalSentEmailHandler) | PR #1236 (PortalEmailNotificationChannel) | PR #1246 (PortalDocumentNotificationHandler context) |
|---|---|---|---|
| Tenant-null handling | silent skip | warn + skip | requireTenantId throws |
| Dedup | none | n/a (no listener added) | Caffeine 5-min `(tenant, customer, project)` |
| Test driver | HTTP via MockMvc | direct event publish | direct service call inside ScopedValue |
| Subject template location | inline Java `String.format` | Thymeleaf | Thymeleaf |
| `handleInTenantScope` helper | own copy | own copy | own copy |

`handleInTenantScope` is now duplicated **8+ times** across the codebase (verified via grep — see PR #1233 audit Finding 3). The drift is no longer trivial to undo.

**Action**: extract a canonical `TenantScopedRunner` (or static method on `RequestScopes`) and migrate all 8+ call-sites in a single follow-up PR. Decide subject-template policy (Java vs Thymeleaf) and apply uniformly.

### 3. Compensating-event / workaround-as-fix pattern

PR #1246 publishes a SECOND `DocumentGeneratedEvent` to compensate for the canonical emitter publishing **before** the visibility flip commits. The fix relies on the dedup cache coalescing the two — which works most of the time but doesn't address the underlying event-ordering bug in `GeneratedDocumentService.generateDocument`. The right fix is to make the canonical emitter publish AFTER the flip, or accept `visibility` as a parameter at the call-site.

This pattern (compensate-rather-than-fix) is also visible in PR #1232's local-scoped helper (the IIFE that should be a shared `localEndOfDayIso` helper but isn't — author acknowledges other call-sites have the same bug and defers).

**Action**: open a fix-the-cause PR for `GeneratedDocumentService` event ordering. Audit the codebase for other "publish before commit" call-sites in event-driven flows.

## NEEDS-FOLLOW-UP PRs

| PR | Reason | Suggested follow-up |
|---|---|---|
| #1233 (OBS-703) | (a) targeted-tests only, (b) no idempotency on listener | Re-run full `mvnw verify`. Add dedup to `ProposalSentEmailHandler` (Caffeine or EmailDeliveryLog pre-check). |
| #1236 (OBS-1101) | (a) currency-formatting business policy in notification channel, (b) try/catch around `findForCurrentTenant()` is dead-or-needed-everywhere drift, (c) targeted-tests only | Extract `CurrencyFormatter`, route `ExpenseService` + `RetainerPeriodService` + this channel through it. Re-run full verify. Decide on the try/catch policy file-wide. |
| #1246 (OBS-2106) | (a) compensating-event papers over the real race in `GeneratedDocumentService`, (b) test pattern (direct service call) drifts from #1233 (HTTP), (c) targeted-tests only | Fix the root cause in `GeneratedDocumentService` event ordering. Then delete `publishPortalReadyFollowUp`. Stabilise test driver pattern across batch A. |
| #1232 (OBS-702) | NIT only — formatting-helper drift, timezone-fragile test | Add `localEndOfDayIso` shared helper, audit other `T23:59:59Z` / `T00:00:00Z` call-sites, pin TZ in vitest. |

PR #1230 is CLEAN — no follow-up needed.

## Cross-PR observations on listener-registration

**Same-bug-class assessment vs OBS-AUDIT-N1**: The audit document `qa_cycle/audits/01-notification-listeners.md` filed `portal-proposal-expired.html` as orphaned. Verified at HEAD: there is still no listener wiring `portal-proposal-expired` to a portal contact. None of the PRs in batch A address this — it remains an open gap and should be picked up in the next slop-fix cycle (OBS-AUDIT-N1 is already in the handoff backlog as task F).

**Drift conclusion**: The listener-registration pattern is **not** consistent across the 3 listener-adjacent PRs in this batch. Tenant-null handling, dedup, test driver, and subject template location all diverge. The codebase is accumulating multiple "valid" ways to do the same thing, and the cost of the drift will be felt the next time someone wires a 9th listener — which path do they copy? They will pick one, lock in the wrong choice, and the codebase will go further out of alignment.

The recommended remediation is one consolidation PR that:
1. Extracts `TenantScopedRunner` (or RequestScopes static helper) and migrates 8+ call-sites.
2. Pins one canonical "new portal listener" template (handler shape, dedup mechanism, test pattern) in a single ADR.
3. Adds an ArchUnit rule that any class with `@TransactionalEventListener` writing to portal email must use the canonical helper, not a copy.

This single follow-up PR would prevent the next 5 PRs in this class from repeating the drift.

## Quality-gate observations (non-findings, but worth flagging)

- **Cycle 22 ran without the merge-gate hook.** PR #1251 (gate lockdown) merged at the end of the cycle. The 23 prior PRs (including the 5 in this batch) merged before the gate was in force. The merge-bar violations in #1233/#1236/#1246 (targeted tests only) would now be blocked at the hook layer. The batch-A re-verification (per Top-pattern #1) is the de-facto retroactive enforcement.
- **The fix-spec discipline is good.** PRs #1233, #1236, #1246 all reference `qa_cycle/fix-specs/OBS-*.md`; #1246 commits the fix-spec to the repo. Continue this practice — but the spec content should be authoritative and the source-code Javadoc should cite, not duplicate, it (Finding #7 on PR #1246).
- **No PR in this batch has a "MERGED-AWAITING-VERIFY" status marker.** They were all merged as if VERIFIED. Per the new contract, that requires evidence — and several did not run full verify. The new gate prevents this going forward.
