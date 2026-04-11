# Fix Spec: BUG-AUTO-01-deep -- SendEmail automation action silently fails (no email template for AUTOMATION_EMAIL)

## Problem

When an automation rule with a `SEND_EMAIL` action fires, the `SendEmailActionExecutor` creates a `Notification` with type `"AUTOMATION_EMAIL"` and calls `emailChannel.deliver(notification, recipientEmail)`. The `EmailNotificationChannel.deliver()` method calls `resolveTemplateName("AUTOMATION_EMAIL")`, which has no mapping in the switch statement and falls through to the `default` branch, returning `null`. The channel then logs a warning and returns silently -- the email is never sent.

Critically, `SendEmailActionExecutor` does NOT check the return value of `emailChannel.deliver()` (which is `void`). It unconditionally returns `ActionSuccess` with `emailSentTo` or `emailsSent`, causing the execution log to show "Completed" even though zero emails were delivered.

This means:
1. No automation email is ever actually sent via Mailpit or any provider.
2. The execution record falsely reports success, hiding the bug from operators.
3. All T3.2 and T4.1-T4.8 test tracks are blocked.

## Root Cause (confirmed)

Two independent issues combine to produce the silent failure:

### Issue 1: Missing template mapping in `EmailNotificationChannel.resolveTemplateName()`

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/EmailNotificationChannel.java` (lines 202-226)

The `resolveTemplateName()` switch covers 20+ notification types but has no case for `"AUTOMATION_EMAIL"`. The `default` branch yields `null`, and lines 87-93 skip delivery when `templateName == null`.

### Issue 2: False success reporting in `SendEmailActionExecutor`

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendEmailActionExecutor.java` (lines 84, 117)

`emailChannel.deliver()` returns `void` and does not throw on template-mapping failures (it catches and logs internally). The executor has no way to know delivery failed, so it always returns `ActionSuccess`.

### Design gap

Automation emails are fundamentally different from notification emails. Notification emails have known, predictable content (task assigned, invoice paid, etc.) with purpose-built templates. Automation emails have user-defined subjects and bodies from the rule config. The `AUTOMATION_EMAIL` type was introduced by the automation system but never integrated into the notification template pipeline.

## Fix

**Approach**: Add an `AUTOMATION_EMAIL` template and fix the false success reporting. The automation email template should be a generic wrapper that renders the user-defined subject and body, since automation email content is dynamic and specified in the rule config.

### Step 1: Create the automation email template

File to create: `/backend/src/main/resources/templates/email/notification-automation.html`

```html
<div xmlns:th="http://www.thymeleaf.org">
  <h2 style="margin: 0 0 16px 0; font-size: 20px; font-weight: 600; color: #111827;"
      th:text="${notificationTitle != null ? notificationTitle : 'Notification'}">Notification</h2>
  <p style="margin: 0 0 16px 0; font-size: 14px; line-height: 1.6; color: #374151;">
    Hi <span th:text="${recipientName != null ? recipientName : 'there'}">there</span>,
  </p>
  <div style="margin: 0 0 16px 0; font-size: 14px; line-height: 1.6; color: #374151;"
       th:utext="${notificationBody != null ? notificationBody : ''}"></div>
  <p style="margin: 0; font-size: 14px; line-height: 1.6; color: #374151;">
    Best,<br/>
    <span th:text="${orgName != null ? orgName : 'DocTeams'}">DocTeams</span>
  </p>
</div>
```

Key decisions:
- Uses `th:utext` for the body to allow variable-resolved content that may contain HTML (automation bodies support `{{variable}}` syntax resolved by `VariableResolver`).
- No CTA button -- automation emails are generic; the rule author controls content.
- Uses the standard base layout (two-pass rendering via `EmailTemplateRenderer`), so branding (logo, brand color, footer) is inherited automatically.

### Step 2: Add template mapping in `EmailNotificationChannel.resolveTemplateName()`

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/EmailNotificationChannel.java`

Add a case in the `resolveTemplateName()` switch (after the proposal cases, before `default`):

```java
case "AUTOMATION_EMAIL" -> "notification-automation";
```

### Step 3: Fix false success reporting in `SendEmailActionExecutor`

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendEmailActionExecutor.java`

The `deliver()` method is `void` and catches exceptions internally, so the executor cannot detect failure without changing the channel interface. Two options:

**Option A (recommended -- minimal change)**: Change `EmailNotificationChannel.deliver()` to return a `boolean` indicating success.

This requires updating the `NotificationChannel` interface:

```java
// NotificationChannel.java
boolean deliver(Notification notification, String recipientEmail);
```

Then in `EmailNotificationChannel.deliver()`:
- Return `true` after successful send (line 162-167)
- Return `false` for all skip/error paths (lines 70, 77, 92, 117, 169, 177)

And in `SendEmailActionExecutor`:
```java
boolean sent = emailChannel.deliver(notification, recipientEmail);
if (!sent) {
    return new ActionFailure("Email delivery failed for recipient: " + recipientEmail, null);
}
```

**Option B (smaller change, less clean)**: Keep `deliver()` as `void` but add a separate `canDeliver(String notificationType)` method to `EmailNotificationChannel` that checks template mapping. Call it before `deliver()` in the executor.

**Recommendation**: Option A. The `void deliver()` contract is fundamentally wrong for any caller that needs to know if delivery succeeded. The `InAppNotificationChannel` can always return `true` (it just saves to DB). The `NotificationDispatcher` can ignore the return value since it already logs failures.

### Step 4: Handle context enrichment for AUTOMATION_EMAIL

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/EmailNotificationChannel.java`

In `buildNotificationContext()` (lines 228-318), the `default` switch branch already handles unmapped types gracefully (no additional context). The base context (`notificationTitle`, `notificationBody`, `recipientName`, etc.) is already populated by lines 233-235, which is sufficient for the automation template.

No changes needed here -- the existing `default` case works correctly because the automation template only uses `notificationTitle`, `notificationBody`, `recipientName`, and `orgName`, all of which come from the base context.

## Scope

Backend only.

Files to modify:
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/EmailNotificationChannel.java` -- add template mapping + return boolean
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/NotificationChannel.java` -- change `deliver()` to return `boolean`
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/InAppNotificationChannel.java` -- update signature to return `boolean` (always `true`)
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/channel/NotificationDispatcher.java` -- ignore return value (already logs)
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/executor/SendEmailActionExecutor.java` -- check `deliver()` return value

Files to create:
- `/backend/src/main/resources/templates/email/notification-automation.html`

Migration needed: no

## Verification

1. **T3.2 re-test**: Create a SEND_EMAIL rule for TASK_STATUS_CHANGED->COMPLETED. Complete a task. Check Mailpit -- email should arrive with the resolved subject/body and org branding.
2. **False success regression**: Create a SEND_EMAIL rule with a recipient type that resolves to a non-existent email. Verify execution log shows "Failed" (not "Completed").
3. **Existing notification emails**: Send a task assignment (TASK_ASSIGNED). Verify the existing notification-task template still works correctly.
4. **Variable resolution**: Create a SEND_EMAIL rule with `{{task.title}}` in subject. Verify the variable is resolved in the delivered email.
5. **ALL_ADMINS path**: Test multi-recipient SEND_EMAIL. All admins should receive branded emails.

## Estimated Effort

S (< 30 min) -- template file creation + switch case + interface return type change + executor check
