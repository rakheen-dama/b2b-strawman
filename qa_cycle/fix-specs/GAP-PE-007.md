# Fix Spec: GAP-PE-007 — No email notifications for proposal lifecycle

## Problem
No emails arrive in Mailpit after proposal send, accept, or decline actions. The `ProposalSentEvent` IS published and the `NotificationEventHandler.onProposalSent()` handler DOES fire. In-app notifications are created (type `PROPOSAL_SENT`, `PROPOSAL_ACCEPTED`, `PROPOSAL_DECLINED`). But no emails are sent.

Evidence from QA Cycle 2: "Mailpit empty after all three proposal lifecycle actions" (T4.3.4, T4.6.4, T4.7.7).

## Root Cause (confirmed)

The `NotificationDispatcher.dispatch()` method (`backend/src/main/java/.../notification/channel/NotificationDispatcher.java` lines 54-58) has this logic:

```java
// Email channel: only dispatch if explicitly enabled (default is false)
boolean emailEnabled = preference != null && preference.isEmailEnabled();
if (emailEnabled) {
    dispatchToChannel("email", notification, recipientEmail);
}
```

**Email is opt-in, not opt-out.** Each user needs a `NotificationPreference` row with `email_enabled = true` for each notification type. Without it, `preference` is null, so `emailEnabled` evaluates to `false`. The Thornton & Associates org has no notification preference rows configured, so ALL email notifications are suppressed.

Additionally, the `dispatchAll()` method in `NotificationEventHandler` (line 499) passes `null` for `recipientEmail`:
```java
notificationDispatcher.dispatch(notification, null);
```
Even if email was enabled, the email channel would skip delivery because `recipientEmail` is null (see `EmailNotificationChannel.deliver()` line 69-72).

This is a **two-part issue**:
1. No `NotificationPreference` rows exist → email opt-in is never true
2. `recipientEmail` is always null → email channel would skip even if enabled

## Fix

This is **by design** — notification email delivery requires explicit opt-in via preferences. The test plan expected emails but the org has no email preferences configured.

However, two improvements can be made:

### 1. Fix recipientEmail resolution in NotificationEventHandler
The `dispatchAll()` method should resolve the recipient's email from the `MemberRepository` before dispatching:

In `NotificationEventHandler.java`, change `dispatchAll()`:
```java
private void dispatchAll(List<Notification> notifications) {
    for (var notification : notifications) {
        String recipientEmail = memberRepository
            .findById(notification.getRecipientMemberId())
            .map(Member::getEmail)
            .orElse(null);
        notificationDispatcher.dispatch(notification, recipientEmail);
    }
}
```
Add `MemberRepository` as a constructor dependency.

### 2. (Optional) Seed default NotificationPreference for proposal types
For new orgs, seed a default preference row with `email_enabled = true` for critical types like `PROPOSAL_ACCEPTED` and `PROPOSAL_DECLINED`. This is a product decision — not strictly a bug.

For this QA cycle, fix #1 is the code bug. Fix #2 is a data/config issue that can be addressed by inserting preference rows for the test org.

## Scope
Backend only
Files to modify:
- `backend/src/main/java/.../notification/NotificationEventHandler.java` — inject `MemberRepository`, resolve email in `dispatchAll()`
Migration needed: no (preference data can be seeded via API or SQL for testing)

## Verification
1. Insert a `NotificationPreference` row for thandi@thornton-test.local with `email_enabled = true` for type `PROPOSAL_ACCEPTED`
2. Send a proposal and accept it via portal API
3. Check Mailpit — acceptance notification email should arrive
4. Re-run T4.3.4, T4.6.4, T4.7.7

## Estimated Effort
S (< 30 min) for the code fix. Seeding preferences is a separate data concern.
