# Day 26 — Checkpoint Results (Accounting ZA)

**Date**: 2026-05-15
**Agent**: QA
**Branch**: main
**Scenario**: accounting-za-90day-keycloak-v2.md

## Day 26 Checkpoints

### 26.1 — Add comment on Moroka AFS with @Carol mention; Carol sees notification and responds

**Part A: Bob posts comment (PASS)**
- **Actor**: Bob (Admin)
- **Action**: Navigated to Moroka Trust AFS engagement (ID: `0a39ccb1-070d-4078-9240-4a4fab254017`), Client Comments tab. Posted comment: "@Carol Please review the trust deed notes and distribution schedule working papers. We need your input on the beneficiary allocation methodology before finalising the AFS."
- **Expected**: Comment posted with @Carol mention visible
- **Observed**:
  - Comment appears in Client Comments tab with author "Bob Ndlovu", timestamp "now"
  - Full text rendered correctly
- **Result**: **PASS**

**Part B: Carol sees notification and responds (PARTIAL)**
- **Actor**: Carol (Member)
- **Action**: Signed out as Bob, signed in as Carol via Keycloak (`carol@thornton-test.local` / `SecureP@ss3`). Navigated to Trust AFS engagement directly.
- **Expected**: Carol sees notification of @mention, navigates to engagement, posts response
- **Observed**:
  - Carol cannot access the Trust AFS engagement (error page: "Something went wrong") -- Carol is not a member of this engagement (only Thandi and Bob are members)
  - Carol's notifications page shows older notifications (task assignments, document uploads from earlier days) but NO notification about the @Carol comment on Trust AFS
  - This is correct access control behavior: Carol should not see engagement data she's not a member of
  - **Scenario gap**: Day 26 assumes Carol can see and respond to a comment on an engagement she's not a member of. To execute as intended, Carol would need to be added as a member first.
- **Result**: **PARTIAL** (comment posted successfully, but Carol response not possible without engagement membership)

---

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 26.1a Bob posts @Carol comment on Trust AFS | **PASS** | Comment visible in Client Comments tab |
| 26.1b Carol sees notification and responds | **PARTIAL** | Carol not a member of Trust AFS engagement; cannot access. Correct access control. Scenario gap. |

**Day 26 Result**: 1 PASS / 0 FAIL / 1 PARTIAL / 0 DEFERRED. No new gaps (scenario assumption issue, not a product bug).
**Note**: To fully execute Day 26, Carol would need to be added as a member of the Trust AFS engagement first. This is not a product defect -- it's correct access control. The scenario should be amended to either (a) add Carol to the engagement before commenting, or (b) use a team member who is already on the engagement (Thandi or Bob).
