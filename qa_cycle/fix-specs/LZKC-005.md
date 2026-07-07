# Fix Spec: LZKC-005 — Deal-won notification is in-app only, no owner email

## Problem
Day 10 / 10.2c: dragging DEAL-0001 to Won created the in-app notification but no "You won a deal" email reached the deal owner in Mailpit. Backend `DealWonEventHandler` logs "notification sent" even though no email was dispatched.

## Root Cause (verified)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/EmailNotificationChannel.java:205-230` — `resolveTemplateName(...)` has no `DEAL_WON` case; the `default -> yield null` branch triggers the skip at lines 87-93 ("Skipping email ... unmapped type 'DEAL_WON'").
- The handler is otherwise correct: `crm/event/DealWonEventHandler.java:55-57` already resolves the owner's email via `memberRepository.findById(event.ownerId()).map(Member::getEmail)` and calls `notificationDispatcher.dispatch(notification, recipientEmail)`.
- Member-facing email infra exists (this is a small extension, not a new subsystem): `resolveTemplateName` already maps `MEMBER_INVITED -> notification-member`, `TASK_ASSIGNED -> notification-task`, `INVOICE_SENT -> notification-invoice`.
- Context: email channel is opt-in per notification preference (`notification/channel/NotificationDispatcher.java:54-58`) — DEAL_WON should follow the same opt-in rule as all member types; not part of the defect.

## Fix
1. `EmailNotificationChannel.java:205`: add `case "DEAL_WON" -> "notification-deal-won";`
2. `buildNotificationContext` (same file, ~line 244): add a `DEAL_WON` branch supplying the deal URL (`appUrl + "/crm/deals/" + referenceEntityId`).
3. Create `backend/src/main/resources/templates/email/notification-deal-won.html`, modelled on `notification-task.html` (extends `email/base.html`).

## Scope
Backend only
Files to modify: `EmailNotificationChannel.java`
Files to create: `backend/src/main/resources/templates/email/notification-deal-won.html`
Migration needed: no

## Verification
Move a scratch deal to Won on the Keycloak stack; observe Mailpit email to the deal owner's address plus the existing in-app notification. Extend the email-channel test (GreenMail singleton :13025) to assert a DEAL_WON dispatch renders and sends.

## Estimated Effort
S (< 30 min)
