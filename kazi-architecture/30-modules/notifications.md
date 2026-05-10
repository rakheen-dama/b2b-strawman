# Notifications

**Bounded context:** see [`10-bounded-contexts.md` § notifications](../10-bounded-contexts.md).
**Owner packages:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/` (Notification + Preference + channels), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/` (Comment aggregate), `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/TimeReminderScheduler.java` (weekly reminder job).

## 1. Purpose

Three loosely-related concerns that live together because the bounded-contexts overview groups them as one leaf consumer of the event bus and because comments are not large enough to warrant a separate page (`10-bounded-contexts.md:248-257` mentions comments only as a dependent of `projects` — there is no `comments.md` module file).

1. **In-app + email notification fan-out.** Domain events are translated into per-recipient `Notification` rows and (where preferences allow) outbound emails. Fan-out is decoupled from publishers via Spring `@TransactionalEventListener(AFTER_COMMIT)` (ADR-036), so notifications fire only for committed transactions.
2. **Comment threading on projects/tasks.** A polymorphic `Comment` aggregate (anchored on `entityType`/`entityId`) with `INTERNAL` vs `SHARED` visibility (ADR-037) and a reserved-but-unused `parentId` for future nesting (ADR-034).
3. **Weekly time-logging reminders.** `TimeReminderScheduler` ticks every 15 minutes, gates by per-tenant `OrgSettings` window, and creates `TIME_REMINDER` notifications for members below the configured minimum minutes-logged threshold (`schedule/TimeReminderScheduler.java:60-152`).

The bounded-context overview asserts notifications are a "leaf consumer" — nothing reads from this module. That is true for in-app/email; comments are also a leaf in event terms but their `Comment` rows are read directly by `customer-portal`'s read-model and surfaced via `PortalProjectController.getComments` `→ A1-backend-map.md:423`.

## 2. Entities owned

| Entity | Source | Notes |
|---|---|---|
| `Notification` | `→ backend/.../notification/Notification.java:14` | Per-recipient in-app row. Fields: `recipientMemberId`, `type` (varchar(50) — see §2.1), `title`, `body`, `referenceEntityType` (varchar(20)) + `referenceEntityId` for deep-linking, `isRead` boolean. Tenant-scoped. |
| `NotificationPreference` | `→ backend/.../notification/NotificationPreference.java:13` | Per-`(memberId, notificationType)` opt-in pair: `inAppEnabled`, `emailEnabled`. Default-on; absence of a row implies "send by both channels". |
| `Comment` | `→ backend/.../comment/Comment.java:15` | Polymorphic aggregate. Fields per A1 §2 row 180: `entityType` (varchar — values include `PROJECT`, `TASK`, etc.), `entityId` (UUID), denormalised `projectId` (for `/api/projects/{projectId}/comments` collection scoping and audit), `authorMemberId`, `body`, `visibility` (varchar(20) — `INTERNAL` / `SHARED` per ADR-037), `parentId` (nullable UUID, **reserved but always NULL** per ADR-034), `source` (e.g. firm vs portal). |

**Not owned here.** `ActivityFeed` is **not an aggregate**. Per ADR-035, the activity feed is a direct read query over `audit_events` with service-layer message formatting. It lives in `dashboard/` (`A1-backend-map.md:401` — `GET /api/dashboard/activity`) and reads through `audit`. Glossary line 285 is consistent: visibility is a *field* on Comment/Document, not a separate aggregate.

### 2.1 Notification.type — the polymorphism axis

`type` is a free-form `VARCHAR(50)` set by the producer (`NotificationService.java:90`), not a Java enum. Observed values from `NotificationEventHandler` and `EmailNotificationChannel.java:224`:
`COMMENT_ADDED`, `TASK_ASSIGNED`, `TASK_CLAIMED`, `TASK_RECURRENCE_CREATED`, `INVOICE_APPROVED`, `INVOICE_SENT`, `INVOICE_PAID`, `INVOICE_VOIDED`, `DOCUMENT_UPLOADED`, `DOCUMENT_GENERATED`, `MEMBER_ADDED_TO_PROJECT`, `PROJECT_COMPLETED`, `PROJECT_ARCHIVED`, `BUDGET_THRESHOLD`, `ACCEPTANCE_ACCEPTED`, `TIME_REMINDER`, `AUTOMATION_EMAIL`, plus several scheduling/proposal/info-request types subscribed in `NotificationEventHandler.java` (24+ `@TransactionalEventListener` methods at lines 69–484).

The string-not-enum choice is fragile (typos compile) but lets `automation` and pack-installed actions invent new types without a backend release. It is the same axis the email channel uses to pick its template.

### 2.2 Comment.entityType — the polymorphism axis

`Comment.entityType` is also a free-form string (no enum). The collection endpoint is mounted under `/api/projects/{projectId}/comments` (`CommentController.java:26`), and `entityType`+`entityId` carry the actual anchor (Task, Document, etc.). Consequence: a comment is **always tied to a project** (the URL forces `projectId`) but can sub-target a child entity within that project. The portal mirror is `POST /portal/comments` `→ A1-backend-map.md:424`.

## 3. REST surface

**Notifications** — `NotificationController` `→ backend/.../notification/NotificationController.java:20`:

| Method + path | Handler | Purpose |
|---|---|---|
| `GET /api/notifications` | `→ NotificationController.java:28` | List notifications for current member (paginated). |
| `GET /api/notifications/unread-count` | `→ NotificationController.java:42` | Single-int unread count — the polling endpoint behind `NotificationBell` per ADR-038. |
| `PUT /api/notifications/{id}/read` | `→ NotificationController.java:49` | Mark one read. |
| `PUT /api/notifications/read-all` | `→ NotificationController.java:56` | Mark every notification read for the current member. |
| `DELETE /api/notifications/{id}` | `→ NotificationController.java:63` | Remove one. |

**Notification preferences** — `NotificationPreferenceController` `→ backend/.../notification/NotificationPreferenceController.java:15`, mounted at `/api/notifications/preferences`:

| Method + path | Handler | Purpose |
|---|---|---|
| `GET /api/notifications/preferences` | `→ :23` | Read current member's preferences (returns full grid; missing rows = both channels enabled). |
| `PUT /api/notifications/preferences` | `→ :31` | Bulk-upsert per-type `(inAppEnabled, emailEnabled)`. |

**Comments** — `CommentController` `→ backend/.../comment/CommentController.java:27`, mounted at `/api/projects/{projectId}/comments`:

| Method + path | Handler | Purpose |
|---|---|---|
| `POST /api/projects/{projectId}/comments` | `→ :35` | Create. Defaults to `INTERNAL` (ADR-037). |
| `GET /api/projects/{projectId}/comments` | `→ :56` | List for project; filter by `entityType` + `entityId` for sub-entity scoping. |
| `PUT /api/projects/{projectId}/comments/{commentId}` | `→ :76` | Edit body and/or visibility. INTERNAL→SHARED requires lead/admin/owner per ADR-037. |
| `DELETE /api/projects/{projectId}/comments/{commentId}` | `→ :91` | Hard-delete. Emits `CommentDeletedEvent`. |

Portal mirrors at `GET /portal/projects/{id}/comments` and `POST /portal/comments` (`A1-backend-map.md:423-424`) — the portal-side write enters the same table with `source` flagged.

## 4. Frontend pages / components

Per `_discovery/A2-frontend-map.md:184-205, 401, 443`:

| Surface | File | Notes |
|---|---|---|
| Notifications inbox | `frontend/app/(app)/.../notifications/page.tsx` `→ A2:184` | Paginated list; mark-read/mark-all-read. |
| Preference settings | `frontend/app/(app)/.../settings/notifications/page.tsx` `→ A2:204` | Grid of `(type × channel)` toggles backed by `NotificationPreferenceController`. |
| Bell + count badge | `components/notifications/notification-bell.tsx` `→ A2:401` | SWR-driven 30s poll of `/api/notifications/unread-count` per ADR-038. |
| Inbox client component | `components/notifications/NotificationsPageClient` `→ A2:443` | Renders the inbox page interactively. |
| Preference form | `components/notifications/NotificationPreferencesForm` `→ A2:443` | Drives the preferences PUT. |
| Server actions | `lib/actions/notifications.ts` `→ A2:39, 316` | Wraps the REST calls; only direct backend route exposed at `/api/notifications` `→ A2:316`. |
| Comment section | embedded in project detail `app/(app)/.../projects/[id]/page.tsx` (sibling of tasks/audit per `A2:115`) | Renders comments with INTERNAL vs SHARED badge per ADR-037 §"Consequences". |

A separate `settings/email/page.tsx` ("Async thresholds, email rate limits", `A2:235`) also exists — it sits closer to `integration-ports` (EMAIL domain config) than to notifications and is cross-linked from §6 below.

## 5. Domain events

### 5.1 Published by this module

From `comment/CommentService` `→ A1-backend-map.md:455`:

- `CommentCreatedEvent`
- `CommentUpdatedEvent`
- `CommentDeletedEvent`
- `CommentVisibilityChangedEvent` — explicitly audited and consumed by the portal projection per ADR-037 §"Consequences".

The notification side publishes nothing — it is a pure subscriber.

### 5.2 Subscribed by `NotificationEventHandler`

`NotificationEventHandler.java:52` registers ~24 `@TransactionalEventListener(phase = AFTER_COMMIT)` methods (file lines 69–484). Principal events (per A1-backend-map.md:475 + handler scan):

`CommentCreatedEvent`, `TaskAssignedEvent`, `TaskClaimedEvent`, `TaskRecurrenceCreatedEvent`, `InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoicePaidEvent`, `InvoiceVoidedEvent`, `DocumentUploadedEvent`, `DocumentGeneratedEvent`, `MemberAddedToProjectEvent`, `ProjectCompletedEvent`, `ProjectArchivedEvent`, `BudgetThresholdEvent`, `AcceptanceRequestAcceptedEvent`, schedule events (recurring-project), plus assorted information-request and proposal events.

Two methods at `:436` and `:460` use plain `@EventListener` (not `AFTER_COMMIT`) — those handle internal automation triggers that should fire even if the publishing transaction rolls back; the rest are strictly post-commit. See §6.2.

The dispatch chain inside the handler is: `NotificationService.createNotificationsForRecipients` builds a `Set<UUID>` of recipients (deduplicated, with `event.actorMemberId()` stripped — see `NotificationService.java:251-298` for the comment-fan-out template), persists `Notification` rows, and hands each to `NotificationDispatcher` which iterates registered `NotificationChannel` implementations (`InAppNotificationChannel`, `EmailNotificationChannel` per `notification/channel/`).

## 6. Cross-cutting touchpoints

### 6.1 Fan-out is synchronous and post-commit (ADR-036)

Spring `@TransactionalEventListener(phase = AFTER_COMMIT)` runs in the publishing thread *after* the original transaction commits, in a fresh `REQUIRES_NEW` transaction (ADR-036 §"Consequences"). This is a deliberate trade against `@Async`/SQS — it accepts ~50ms additional latency on a 50-recipient document upload in exchange for zero infrastructure and a debuggable single-thread call stack.

Because handler exceptions don't rollback the user-visible action (it already committed), every method catches and logs at WARN. There is **no retry**. A failed notification create/email is a silent loss.

### 6.2 Plain `@EventListener` exceptions are deliberate

The two `@EventListener` (no `AFTER_COMMIT`) methods at `NotificationEventHandler.java:436, 460` participate in the in-flight transaction, mirroring `automation/AutomationEventListener` (10-bounded-contexts.md:232 — automation needs in-flight access). Nothing in this module should rely on them rolling back; they exist only because their downstream side-effect must observe the same in-flight state the producer sees.

### 6.3 Email is irreversible — that's the whole point of AFTER_COMMIT

Once SMTP has accepted a message, no rollback can recall it. `AFTER_COMMIT` is the contract that prevents the most-feared bug here: a phantom email about a task that was never really assigned because the request ultimately rolled back. ADR-036 §"Decision" calls this out explicitly.

### 6.4 Two-tier email resolution (ADR-095)

`EmailNotificationChannel` resolves the EMAIL adapter via `IntegrationRegistry.resolve(IntegrationDomain.EMAIL, EmailProvider.class)` `→ EmailNotificationChannel.java:83`. Per ADR-095, the registry uses `IntegrationDomain.EMAIL.getDefaultSlug() == "smtp"` (not `"noop"`), and the `"smtp"` slug resolves to `SmtpEmailProvider` in production (`@ConditionalOnProperty(spring.mail.host)`) or `NoOpEmailProvider` in dev (`@ConditionalOnMissingBean(SmtpEmailProvider.class)`). Result: every org gets working email with zero per-tenant configuration; per-tenant overrides (e.g. SendGrid BYOAK) attach by inserting an `OrgIntegration` row.

### 6.5 Comment visibility (ADR-037) — INTERNAL by default; SHARED gated

The visibility model is a binary enum in `Comment.visibility`. Defaults to `INTERNAL` (ADR-037 §"Consequences"). Promotion to `SHARED` is allowed only for project leads, org admins, and owners (enforced via `ProjectAccessService` per ADR-037 §"Decision"). Visibility changes emit `CommentVisibilityChangedEvent` and are audited as `comment.visibility_changed`. Phase 7's `PortalProjectionHandler` filters `visibility = 'SHARED'` to populate the portal read-model — i.e. the same `comments` table is the source of truth for both surfaces.

The glossary (line 80, 285) labels the values `INTERNAL` / `PORTAL` (or PUBLIC) — the codebase uses `INTERNAL` / `SHARED` per ADR-037 and `Comment.java:131`. The two namings are equivalent in intent; `SHARED` is canonical.

### 6.6 Portal-notification deduplication (ADR-258)

ADR-258 establishes the **no-double-send rule**: portal recipients receive at most one email per business event. The Phase 24 `EmailNotificationChannel` (this module) continues to handle invoice / acceptance / proposal / info-request emails for both firm members and portal contacts. The Phase 68 `PortalEmailNotificationChannel` (under `portal/notification/channel/`) subscribes only to three new events (`TrustTransactionApprovedEvent`, `FilingStatusApproachingEvent`, `RetainerPeriodClosedEvent`). The two channels never subscribe to the same event. ADR-258 §"Consequences" specifies a regression test asserting that fact.

Implication for this module: when adding a new event type, decide which channel owns it before wiring a listener — adding a duplicate listener here re-introduces the double-send risk.

### 6.7 Polling, not push (ADR-038)

`NotificationBell` polls `GET /api/notifications/unread-count` every 30 seconds via SWR (`A2-frontend-map.md:401`). No WebSocket, no SSE, no long-polling. The query is an index-only scan over `idx_notifications_unread (recipient_member_id, is_read, created_at DESC)`. ADR-038 documents the migration path to push (drop in `SseEmitter` + `EventSource`; the REST endpoints stay).

### 6.8 TimeReminderScheduler — 15-min tick, per-tenant gating

`schedule/TimeReminderScheduler.java:60` is `@Scheduled(fixedRate = 900_000)` (15 minutes). Each tick uses `tenantScopedRunner.forEachTenant(...)` (ADR-T008) and per tenant:

1. Reads `OrgSettings`. Skips if `!isTimeReminderEnabled()` (`:79`).
2. Skips if outside `OrgSettings.timeReminderTime ± window` (`isWithinTimeWindow`, `:83, 180`).
3. Iterates members; for each, sums minutes logged this week and creates a `TIME_REMINDER` notification if below `OrgSettings.timeReminderMinMinutes` (`:139-152`).

The scheduler lives in `schedule/`, not `notification/`, but is logically part of this module — it is the only producer of `TIME_REMINDER` notifications and it talks directly to `NotificationService`. (10-bounded-contexts.md:144 attributes `TimeReminderScheduler` to the `time-entry` context; that file is wrong on package location but right on conceptual ownership — both pages flag the cross-cutting tie.)

### 6.9 Activity feed is not part of this module

Per ADR-035: `GET /api/dashboard/activity` reads `audit_events` directly with service-layer message formatting. There is no `activity_feed` table, no projection, no aggregate. Anything appearing in the activity feed but not in audit is a bug somewhere else (see `audit.md` for the immutability contract).

## 7. Vertical specifics

Notification copy is rendered in the frontend via the terminology overlay (`frontend/lib/terminology.tsx` per 10-bounded-contexts.md §4) — the same backend-side title (`"Task assigned: …"`) becomes "Action item assigned" in legal-za and "Engagement task assigned" in accounting-za, decided at render time. Backend titles use the canonical word.

Email templates are picked by `EmailNotificationChannel` from `Notification.type` `→ EmailNotificationChannel.java:226` (e.g. `AUTOMATION_EMAIL → notification-automation`). The mapping is hardcoded today — there is no per-vertical template override. Vertical-specific emails would need either a new `Notification.type` value (and a matching template file) or, more idiomatically, a dedicated channel like Phase 68's `PortalEmailNotificationChannel` (ADR-258). **Verify** before shipping any "vertical email customisation" claim — at the time of writing the only customisation lever is the type-string axis. (10-bounded-contexts.md:255 phrases this as "notification copy uses terminology overrides via the frontend at render time"; this page extends that with the email-template-per-type point.)

`TIME_REMINDER` is module-agnostic (every tenant with `time_entry` enabled and `OrgSettings.timeReminderEnabled = true` gets it) — there is no vertical gate.

## 8. Active ADRs

Per `90-adr-index.md:225-285`, all of these carry **Active** status as of 2026-05-10:

- **ADR-034** — flat-comments-with-threading-schema. `parent_id` reserved nullable column; flat queries only.
- **ADR-036** — synchronous-notification-fanout. `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`.
- **ADR-037** — comment-visibility-model. `INTERNAL` default, `SHARED` gated to lead/admin/owner.
- **ADR-038** — polling-for-notification-delivery. 30s SWR poll on `unread-count`.
- **ADR-095** — two-tier-email-resolution. `IntegrationDomain.EMAIL.defaultSlug = "smtp"`.
- **ADR-258** — portal-notification-no-double-send. New channel only for new events.

Adjacent (read-only context): **ADR-035** (activity-feed-direct-audit-query — *not enforced here*; cited to document why this module **does not** own the activity feed), **ADR-135** (reminder-strategy), **ADR-160** (email-rate-limiting-strategy), **ADR-T006** (template comments canonical — referenced in the index header).

## 9. Key flows

- **`50-flows/automation-trigger-to-action.md`** — automation actions frequently emit notifications (`AUTOMATION_EMAIL` type per `EmailNotificationChannel.java:224`); this module is the egress.
- **`50-flows/proposal-to-engagement-to-billing.md`** — `ProposalSentEvent` and `AcceptanceRequestAcceptedEvent` flow through `NotificationEventHandler`.
- **`50-flows/customer-onboarding-and-kyc.md`** — `MemberAddedToProjectEvent` notifications fire when staff are pulled onto a new matter.
- **`50-flows/portal-magic-link-to-task-completion.md`** — portal-side commenting (`POST /portal/comments`) lands in the same `comments` table; firm-side notifications fire for `CommentCreatedEvent` regardless of source.
- **`50-flows/matter-to-cash.md`** — invoice lifecycle events (`INVOICE_APPROVED/SENT/PAID/VOIDED`) all fan out through this module.

A general note: any flow that mutates a domain entity *probably* triggers a notification. The 24-listener handler is the single inflection point.

## 10. Open questions / known fragility

1. **Polling vs push trade-off (ADR-038).** The 30-second delay is acceptable for project-management workflows but visibly stale for operational use (e.g. multi-user trust-account approvals, where a second approver wants to see the request appear instantly). If push becomes a requirement, ADR-038's migration path (`SseEmitter` + `EventSource`) is explicit; the REST API stays unchanged.
2. **Email rate-limiting is documented but unimplemented in this module.** ADR-160 (email-rate-limiting-strategy, Active in the index) is referenced and `frontend/.../settings/email/page.tsx` mentions "email rate limits" (`A2:235`), but the EMAIL channel here makes no per-tenant throttle decision — it delegates wholesale to the resolved `EmailProvider`. Any rate-limiting today lives inside the adapter (e.g. SendGrid's own quotas). **Verify** before claiming rate-limiting coverage in compliance docs.
3. **Threading is wired in the schema but missing in service + UI (ADR-034).** `Comment.parentId` exists on the entity (`Comment.java:41, 138`) and has a setter, but `CommentService` does not accept `parentId` on create per ADR-034 §"Consequences". Any future thread-rendering work must add: (a) `parentId` in the create request DTO, (b) tree-fetch query, (c) frontend nested rendering with depth cap. The schema migration is already paid for. Depth handling is a deferred design call — plausible answer is "max two levels (root + reply), no further nesting" to keep portal rendering simple.
4. **`Notification.type` and `Comment.entityType` are free-form strings, not enums.** A rename (e.g. someone refactoring `INVOICE_PAID → INVOICE_SETTLED`) silently breaks `EmailNotificationChannel.java:224` template selection and `NotificationPreference` rows referencing the old name. There's no compile-time guard. Mitigation: add a registry test that asserts the union of producer-emitted types matches the union of consumer-handled types.
5. **No per-recipient delivery retry.** ADR-036 explicitly accepts that a failed notification is logged-and-lost. For low-stakes notifications (task assigned), this is fine; for `BUDGET_THRESHOLD` or `INVOICE_PAID` it is a known weak point. Owners should consider a cheap outbox (a `notification_dispatch_failures` table + a sweeper) before claiming reliable delivery in any compliance context.
6. **TimeReminderScheduler tick rate is 15 minutes, but the time-window check is wall-clock.** A tenant whose `timeReminderTime` happens to fall in a window the scheduler skips (e.g. if the JVM was paused or the scheduler was disabled for maintenance during the configured minute) silently misses that day's reminder. Consider a "did we send today?" idempotency check on the `notifications` table; right now there is none beyond the in-tick window guard.
7. **Glossary uses `INTERNAL` / `PORTAL` (line 80, 285); code uses `INTERNAL` / `SHARED` (ADR-037, `Comment.java`).** Glossary should be updated to track the code; logged here so it isn't forgotten.
