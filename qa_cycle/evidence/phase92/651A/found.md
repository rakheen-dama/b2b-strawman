# 651A — found (outside the slice's scope; reported, not fixed here)

| id | Finding | Source | Disposition |
|---|---|---|---|
| F-651A-1 | `TrustLedgerQueryRepository.clientMatterBalances` has no main-code caller after `InterestService` is deleted; a 646A test still pins it. | builder (D-5) | Follow-up cleanup (delete the method and its pin together). |
| F-651A-2 | `CapabilityEndpointTest`: `memberId` on `/api/me/capabilities` — only the stale interest-run comment was updated here. | builder (D-9) | Follow-up. |
| F-651A-3 | `TrustNotificationHandler` / portal-sync listeners for `ReconciliationCompleted` and `InvestmentMaturing` exist, but the builder found no publisher for these events on this head. Not verified beyond a grep. | builder | Hypothesis — verify with a test before any change (dead listener vs a missing publish). |
| F-651A-4 | `portal/e2e/tests/portal-client-90day/day-14-30.spec.ts:24-25` comment still says "the Day 60 interest run is calculated but never posted". | review A (Medium) | Already F-651C-3; comment-only, for 651B / 651D. |
| F-651A-5 | `InterestRunRemovalTest.theEngineItsEventAndItsNotificationTypeAreGone` proves deletion with `Class.forName` on hard-coded names (binds to internals). | review B (Medium, optional) | Declined in-slice: the behavioural proofs (404 routes, absent tables, preferences API) are in the same class; the reflection check is kept as a cheap guard against a partial revert. |
