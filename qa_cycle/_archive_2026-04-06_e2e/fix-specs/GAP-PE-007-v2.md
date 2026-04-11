# Fix Spec: GAP-PE-007-v2 — Email dispatch path unreachable for proposal events

## Status
SPEC_READY (v2 — supersedes GAP-PE-007.md)

## What v1 Fixed (PR #837)
`NotificationEventHandler.dispatchAll()` now resolves recipient email via `MemberRepository` before dispatching to `NotificationDispatcher`. This fixed the null-email problem for all notification types that use Pattern A.

## What v1 Did NOT Fix
Proposal event handlers use a completely different code path (Pattern B) that never calls `dispatchAll()`. The v1 fix is correct but unreachable for proposal events.

## Root Cause (confirmed via code review)

The notification system has two distinct dispatch patterns:

### Pattern A — Full multi-channel dispatch (tasks, comments, documents, invoices, schedules, etc.)
1. `NotificationEventHandler.onXxx(event)` calls `notificationService.handleXxx(event)`
2. `handleXxx()` returns `List<Notification>` (saved DB records)
3. `dispatchAll(notifications)` iterates, resolves email, calls `notificationDispatcher.dispatch()`
4. `NotificationDispatcher.dispatch()` checks preferences, routes to in-app + email channels

### Pattern B — In-app only (proposals, billing runs)
1. `NotificationEventHandler.onProposalSent(event)` calls `notificationService.notifyAdminsAndOwners()`
2. `notifyAdminsAndOwners()` calls `createNotification()` for each admin/owner
3. **Stops here** — no return value, no `dispatchAll()`, no dispatcher invocation

For PROPOSAL_ACCEPTED and PROPOSAL_DECLINED, the code path is even different:
- `ProposalAcceptedEventHandler.onProposalAccepted()` calls `notificationService.createNotification()` directly
- `ProposalService.declineProposal()` calls `notificationService.createNotification()` directly
- Neither invokes `dispatchAll()` or `notificationDispatcher.dispatch()`

### Affected notification types
| Type | Handler Location | Dispatch Pattern |
|------|-----------------|-----------------|
| PROPOSAL_SENT | `NotificationEventHandler.onProposalSent()` | Pattern B (notifyAdminsAndOwners) |
| PROPOSAL_ACCEPTED | `ProposalAcceptedEventHandler.onProposalAccepted()` | Direct createNotification |
| PROPOSAL_DECLINED | `ProposalService.declineProposal()` | Direct createNotification |
| BILLING_RUN_COMPLETED | `NotificationEventHandler.onBillingRunCompleted()` | Pattern B (notifyAdminsAndOwners) |
| BILLING_RUN_FAILURES | `NotificationEventHandler.onBillingRunFailures()` | Pattern B (notifyAdminsAndOwners) |
| BILLING_RUN_SENT | `NotificationEventHandler.onBillingRunSent()` | Pattern B (notifyAdminsAndOwners) |

### Evidence
- Email template mapping EXISTS for all proposal types (`EmailNotificationChannel.resolveTemplateName()` line 222 maps `PROPOSAL_SENT`, `PROPOSAL_ACCEPTED`, `PROPOSAL_EXPIRED`, `PROPOSAL_DECLINED` to `"notification-proposal"`)
- Email channel correctly skips when recipientEmail is null (line 69-72)
- The v1 fix correctly resolves email in `dispatchAll()` — but `dispatchAll()` is never called for proposal events

## Fix

### Strategy: Add `dispatchAll()` calls after notification creation

The cleanest fix is to collect the notifications created by each proposal handler and pass them through `dispatchAll()`. This follows the established pattern and requires minimal code changes.

### 1. `NotificationEventHandler.onProposalSent()` (lines 416-432)

Change `notifyAdminsAndOwners()` to return the created notifications and pipe them through `dispatchAll()`.

**Option A (preferred)**: Add a new method `notifyAdminsAndOwnersWithDispatch()` or change `notifyAdminsAndOwners()` to return `List<Notification>`.

In `NotificationService.java`, change `notifyAdminsAndOwners()`:
```java
// BEFORE (returns void)
public void notifyAdminsAndOwners(String type, String title, String body, String entityType, UUID entityId) {
    var adminsAndOwners = memberRepository.findByRoleSlugsIn(List.of("admin", "owner"));
    for (var member : adminsAndOwners) {
        createNotification(member.getId(), type, title, body, entityType, entityId, null);
    }
}

// AFTER (returns List<Notification>)
public List<Notification> notifyAdminsAndOwners(String type, String title, String body, String entityType, UUID entityId) {
    var adminsAndOwners = memberRepository.findByRoleSlugsIn(List.of("admin", "owner"));
    var created = new ArrayList<Notification>();
    for (var member : adminsAndOwners) {
        var notification = createNotification(member.getId(), type, title, body, entityType, entityId, null);
        created.add(notification);
    }
    return created;
}
```

Then in `NotificationEventHandler.onProposalSent()`:
```java
// BEFORE
notificationService.notifyAdminsAndOwners("PROPOSAL_SENT", title, null, "PROPOSAL", event.entityId());

// AFTER
var notifications = notificationService.notifyAdminsAndOwners("PROPOSAL_SENT", title, null, "PROPOSAL", event.entityId());
dispatchAll(notifications);
```

### 2. `ProposalAcceptedEventHandler.onProposalAccepted()` (lines 38-60)

This handler calls `notificationService.createNotification()` directly. It needs to collect the results and dispatch them.

Inject `NotificationDispatcher` and `MemberRepository` into `ProposalAcceptedEventHandler`:
```java
// In onProposalAccepted(), collect and dispatch:
var notifications = new ArrayList<Notification>();

// 1. Notify creator
var creatorNotif = notificationService.createNotification(
    event.creatorMemberId(), "PROPOSAL_ACCEPTED", ...);
notifications.add(creatorNotif);

// 2. Notify team members
for (var memberId : event.teamMemberIds()) {
    var teamNotif = notificationService.createNotification(memberId, "PROJECT_ASSIGNED", ...);
    notifications.add(teamNotif);
}

// 3. Dispatch all through multi-channel (email + in-app)
for (var notification : notifications) {
    String recipientEmail = memberRepository
        .findById(notification.getRecipientMemberId())
        .map(Member::getEmail).orElse(null);
    notificationDispatcher.dispatch(notification, recipientEmail);
}
```

### 3. `ProposalService.declineProposal()` (lines 716-723)

Same pattern — collect the notification and dispatch it.

Inject `NotificationDispatcher` and `MemberRepository` into `ProposalService` (MemberRepository likely already injected):
```java
// BEFORE
notificationService.createNotification(proposal.getCreatedById(), "PROPOSAL_DECLINED", ...);

// AFTER
var notification = notificationService.createNotification(proposal.getCreatedById(), "PROPOSAL_DECLINED", ...);
String recipientEmail = memberRepository.findById(notification.getRecipientMemberId())
    .map(Member::getEmail).orElse(null);
notificationDispatcher.dispatch(notification, recipientEmail);
```

### 4. Billing run handlers (bonus — same pattern, lower priority)

Apply the same pattern to `onBillingRunCompleted()`, `onBillingRunFailures()`, `onBillingRunSent()` in `NotificationEventHandler`. These are already in the same file and use the same `notifyAdminsAndOwners()` call, so the `notifyAdminsAndOwners()` return-type change automatically enables them.

## Scope

### Files to modify
1. `backend/src/main/java/.../notification/NotificationService.java` — change `notifyAdminsAndOwners()` return type from `void` to `List<Notification>`
2. `backend/src/main/java/.../notification/NotificationEventHandler.java` — add `dispatchAll()` after `notifyAdminsAndOwners()` calls in `onProposalSent()`, `onBillingRunCompleted()`, `onBillingRunFailures()`, `onBillingRunSent()`
3. `backend/src/main/java/.../proposal/ProposalAcceptedEventHandler.java` — inject `NotificationDispatcher` + `MemberRepository`, collect notifications, dispatch
4. `backend/src/main/java/.../proposal/ProposalService.java` — inject `NotificationDispatcher`, dispatch after `createNotification()` in `declineProposal()`

### Callers of `notifyAdminsAndOwners()` that need updating (return type change)
All callers currently ignore the return value (void), so changing to `List<Notification>` is backward-compatible. But verify all call sites:
- `NotificationEventHandler` (4 calls: onProposalSent, 3x billingRun) — add `dispatchAll()`
- `RecurringScheduleService` — verify (may or may not need dispatch)
- `DataSubjectRequestService` — verify
- `RetentionService` — verify
- `ChecklistInstanceService` — verify
- `RetainerPeriodService` — verify
- `RetainerAgreementService` — verify
- `RetainerConsumptionListener` — verify
- `AutomationEventListener` — verify
- `ProposalOrchestrationService` (prerequisite notification) — verify
- `PaymentReconciliationService` — verify
- `EmailWebhookService` — verify

For this fix, only proposal + billing-run callers in `NotificationEventHandler` need `dispatchAll()`. Other callers can continue to ignore the return value — they are unaffected.

### Migration needed
No.

## Verification
1. Enable email preferences for PROPOSAL_SENT, PROPOSAL_ACCEPTED, PROPOSAL_DECLINED for thandi@thornton-test.local
2. Create + send a proposal
3. Check Mailpit — PROPOSAL_SENT email should arrive for thandi (org owner)
4. Accept the proposal via portal
5. Check Mailpit — PROPOSAL_ACCEPTED email should arrive for the proposal creator
6. Create + send another proposal, decline it
7. Check Mailpit — PROPOSAL_DECLINED email should arrive for the proposal creator

## Estimated Effort
S-M (1-2 hours). The `notifyAdminsAndOwners()` return-type change is safe. The `ProposalAcceptedEventHandler` and `ProposalService` changes are straightforward dependency injections + dispatch calls.

## Risk
LOW. The change is additive — existing in-app notifications are unaffected. Email dispatch is gated by user preferences (opt-in), so no unexpected emails will be sent to users who haven't enabled email notifications.
