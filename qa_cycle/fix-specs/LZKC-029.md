# Fix Spec: LZKC-029 — Section 86 dual-approval never engages on a script-faithful run (DECISION_NEEDED)

## Problem
Day 60 / 60.13: the R 70 000 trust payout PAY/2026/001 went straight to APPROVED on Bob's single Approve. DB (read-only): this cycle's SECTION_86 trust account has `require_dual_approval = false`, `payment_approval_threshold = NULL`. The scenario's Day 1 step 1.5 never instructs enabling dual approval, and the product defaults it off even for SECTION_86 accounts — so the scripted "Section 86 dual-approval" constraint and the LZKC-016 1-of-2 approval-feedback UX are silently skipped on every script-faithful run. The product behaved correctly for its configuration; this is a scenario-gap and/or product-default question, not a code regression.

## Root Cause (confirmed)
Three cooperating defaults — all verified in code:

1. **Scenario omission** — `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md:209-215` (Day 1 step 1.5): the trust-account form fill lists Name/Bank/Branch/Account number/Type SECTION_86 but never "Require dual approval" or a threshold. (Prior cycle's QA enabled it beyond-script.)
2. **Frontend default** — `frontend/components/trust/CreateTrustAccountDialog.tsx:59` defaults `requireDualApproval: false` regardless of the selected `accountType`, and line 98 always sends the value **explicitly** — so a backend null-default alone would never fire for UI-created accounts.
3. **Backend default** — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java:130-131`: `dualApproval = request.requireDualApproval() != null ? request.requireDualApproval() : false` — type-blind; `TrustAccountType.SECTION_86` (enum at `.../trustaccounting/TrustAccountType.java`) gets the same `false` default as GENERAL/INVESTMENT.

The dual-approval feature itself is built and was verified working in the prior cycle (LZKC-016); the toggle exists in the create dialog (`CreateTrustAccountDialog.tsx:258-270`) and in `UpdateTrustAccountRequest` (`TrustAccountService.java:61-68`).

## Fix — OPTIONS (user/orchestrator must choose; do NOT auto-apply)

**Option (a) — Scenario amendment only (product unchanged).**
Amend Day 1 step 1.5 in `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` (after line 214) to add: "Tick **Require dual approval**; set **Payment approval threshold: R 50 000**" (threshold chosen so the R 70 000 Day-60 payout triggers dual approval while smaller ops stay single). Also amend Day 60 / 60.13-60.14 to expect the 1-of-2 → 2-of-2 flow with a second approver.
- Per CLAUDE.md §6 this is a **product decision requiring explicit user authorization** — flagged here, not applied.
- Consequence: product keeps dual approval opt-in; compliance posture is the firm's responsibility.

**Option (b) — Product default: SECTION_86 accounts default dual approval ON.**
Compliance rationale: LPC rules commonly expect dual authorisation controls on s86 trust payments; defaulting off makes the compliant posture opt-in.
Requires BOTH layers (see root cause 2 — the frontend always sends the field):
- Frontend: `CreateTrustAccountDialog.tsx` — when `accountType` changes to `SECTION_86`, default the `requireDualApproval` checkbox to checked (user can still untick). One `useEffect`/`watch` on the form.
- Backend: `TrustAccountService.createTrustAccount` (line 130-131) — when `request.requireDualApproval() == null`, default `true` for `SECTION_86` (API-created accounts get the safe default).
- Note: no sensible default exists for `paymentApprovalThreshold` (NULL = dual approval applies to all payments, which is arguably the correct s86-conservative default — confirm with user).
- Existing accounts are NOT migrated (no data migration proposed — changing an in-flight account's approval policy silently is worse).

**Option (c) — Both (a) and (b).** Scenario becomes explicit about the control AND the product defaults safe. Recommended by Product if the compliance-first posture is wanted, but this is the user's call.

## Scope
- (a): Scenario doc only (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`).
- (b): Frontend (`CreateTrustAccountDialog.tsx`) + Backend (`TrustAccountService.java` + integration test in `backend/src/test/.../trustaccounting/`). Migration needed: no.

## Verification
- (a): next cycle's Day 1 creates the account with dual approval; Day 60 exercises 1-of-2 feedback (LZKC-016 becomes testable again).
- (b): backend integration test — create SECTION_86 account with `requireDualApproval` omitted → response `requireDualApproval=true`; GENERAL account unchanged (`false`). Frontend test — selecting SECTION_86 in the dialog checks the box. Live: create a SECTION_86 account via UI, record a payment, verify AWAITING APPROVAL → first approve gives "1 of 2" feedback.

## Estimated Effort
(a) S · (b) M · (c) M

## Status requested
**DECISION_NEEDED** — orchestrator/user must pick (a), (b), or (c) before a Dev agent is dispatched.
