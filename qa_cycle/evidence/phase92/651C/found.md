# 651C found — items outside the slice (reported, not fixed here)

- **F-651C-1 — LOW (product copy; 648A investments register) — s86(3) sub-label "LPFF share Arrangement".**
  `frontend/app/(app)/org/[slug]/trust-accounting/investments/page.tsx:249-256` renders "LPFF share" with
  "5% (statutory)" for a Section 86(4) (client-instruction) investment and "Arrangement" otherwise. The
  "Arrangement" wording refers to the arrangement rate that the retired LPFF-rate table configured (removed from
  the UI in 651C; the `/lpff-rates` endpoint goes in 651E). Pinned by
  `frontend/__tests__/trust-investment-basis.test.tsx` (the "LPFF share Arrangement" assertion, 648A).
  Rewording it is a 648A product-copy question, outside 651C. Kept as an acceptance-grep KEEP (651C D-3).

- **F-651C-2 — INFO (hand-off to 651A / 651E) — audit labels `interest_run` / `lpff_rate` kept.**
  `frontend/components/audit/entity-type-labels.ts:48-49`. The backend still emits both literals
  (`InterestService.java:193,391,512`; `TrustAccountService.java:449`), and
  `components/audit/__tests__/entity-type-labels.test.ts` ("has an explicit label for every audit entity literal
  the backend emits") fails if either label is removed while they are emitted. Recommendation: keep them after
  651A / 651E as well so historical audit rows stay readable (651C D-2).

- **F-651C-3 — LOW (comment only; portal, outside 651C's frontend-only scope) — stale Day 60 comment.**
  `portal/e2e/tests/portal-client-90day/day-14-30.spec.ts:24` still says the Day 60 interest run is "calculated
  but" not posted. The run no longer exists in the firm UI after 651C (engine removed in 651A). Comment-only;
  reword in 651A (which removes the run's portal projection `onInterestPosted`) or 651B. Raised by review A
  (Medium) on PR #1810.
