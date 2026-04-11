# Fix Spec: GAP-DI-07 — Expired proposals can be accepted via portal

## Problem

`PortalProposalService.acceptProposal()` checks that the proposal status is `SENT` (line 126) but does not check the `expiresAt` timestamp. A proposal with `expiresAt=2026-01-01` was successfully accepted on 2026-03-24 via the portal API, bypassing the expiry deadline entirely. The `ProposalExpiryProcessor` runs hourly to batch-transition expired proposals to `EXPIRED` status, but between expiry and the next processor run there is a race window where expired-but-not-yet-transitioned proposals remain in `SENT` status and can be accepted or declined.

The same gap exists in `declineProposal()` (line 157), which also only checks `status == "SENT"` without validating expiry. While declining an expired proposal has lower business impact, it should be guarded for consistency.

## Root Cause (hypothesis)

The `findPortalProposalRow()` private helper in `PortalProposalService.java` (line 172-190) only fetches `id`, `org_id`, `status`, and `portal_contact_id` from `portal.portal_proposals` — it does not fetch `expires_at`. Neither `acceptProposal()` nor `declineProposal()` perform any expiry check before delegating to the orchestration/domain service.

The `Proposal` entity (`Proposal.java`) has an `expiresAt` field (line 97) and `getExpiresAt()` getter (line 375), but no `isExpired()` convenience method — unlike `AcceptanceRequest`, `MagicLinkToken`, and `PendingInvitation` which all follow this pattern.

**Files confirmed via grep:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalService.java` — missing expiry guard in `acceptProposal()` (line 126) and `declineProposal()` (line 157)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java` — has `expiresAt` field but no `isExpired()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessor.java` — hourly batch processor creates the race window
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java` line 165 — `markAccepted()` only checks `requireStatus(SENT)`, no expiry guard at entity level

## Fix

### Step 1: Add `isExpired()` to `Proposal` entity

In `Proposal.java`, add a convenience method following the established pattern from `AcceptanceRequest.isExpired()`:

```java
/** Returns true if the proposal has an expiry date that is in the past. */
public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
}
```

Place it in the Guards section (after `isTerminal()`, around line 207).

### Step 2: Add `expires_at` to `PortalProposalRow` and its query

In `PortalProposalService.java`:

1. Update the `PortalProposalRow` record (line 231) to include `Instant expiresAt`.
2. Update the `findPortalProposalRow()` SQL query (line 176) to also select `expires_at`.
3. Update the row mapper to extract `expires_at` as an `Instant`.

### Step 3: Add expiry guard in `acceptProposal()`

After the status check (line 126-129), add:

```java
// Check if proposal has expired (race window: expiresAt passed but scheduled processor hasn't run yet)
if (portalRow.expiresAt() != null && Instant.now().isAfter(portalRow.expiresAt())) {
    throw new ResourceConflictException(
        "Proposal expired", "This proposal expired on " + portalRow.expiresAt());
}
```

### Step 4: Add expiry guard in `declineProposal()`

After the status check (line 157-159), add the same guard for consistency.

### Step 5: Add expiry guard in `Proposal.markAccepted()` (defense-in-depth)

In `Proposal.java` `markAccepted()` (line 165), after `requireStatus(SENT)`:

```java
if (isExpired()) {
    throw new InvalidStateException(
        "Proposal expired", "Cannot accept an expired proposal");
}
```

This provides defense-in-depth in case the portal service guard is bypassed (e.g., internal API call).

### Step 6: Add test for expired proposal acceptance rejection

In `PortalProposalControllerTest.java`, add a test that creates a SENT proposal with `expiresAt` in the past, seeds the portal read model, and verifies `POST /portal/api/proposals/{id}/accept` returns 409 Conflict.

The existing test `acceptProposal_expired_returns409()` (line 482-488) tests an EXPIRED-status proposal. A new test is needed for a SENT-status proposal with past `expiresAt` (the actual race window scenario).

## Scope

Backend only.

**Files to modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java` — add `isExpired()`, add expiry guard in `markAccepted()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalService.java` — add `expires_at` to `PortalProposalRow`, update query, add expiry checks in `acceptProposal()` and `declineProposal()`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/proposal/PortalProposalControllerTest.java` — add test for SENT-but-expired acceptance rejection

**Files to create:** None

**Migration needed:** No (portal schema already has `expires_at` column)

## Verification

Re-run QA checkpoint T1.3 (Proposal Guards) from `qa_cycle/checkpoint-results/financial-accuracy-cycle2.md`. Specifically:
1. Create a SENT proposal with `expiresAt` in the past
2. Attempt `POST /portal/api/proposals/{id}/accept` — expect 409 Conflict
3. Attempt `POST /portal/api/proposals/{id}/decline` — expect 409 Conflict
4. Confirm non-expired SENT proposals can still be accepted normally

## Estimated Effort

S (< 30 min) — Four targeted code changes in two files, one new test case, no migration.
