# Phase 76 — Calendar Sync + Email-to-Matter Filing

## System Context

Kazi is a multi-tenant B2B practice-management platform with three live verticals (legal-za, accounting-za, consulting-za). Phase 76 adds two cross-vertical integration domains that close the biggest daily workflow gaps vs competitors like Clio: bidirectional Google Calendar sync and inbound email filing to projects via dedicated forwarding addresses.

**Strategic context**: Kazi has deep functionality (trust accounting, AI skills, proposals, compliance, resource planning) but lacks the **daily operational connective tissue** that embeds it in the practitioner's existing workflow. Calendar sync and email-to-matter are the two integrations practitioners use dozens of times per day. Without them, Kazi asks the user to change their workflow to fit the tool.

### Predecessor systems this phase builds on

- **Phase 71 — Xero Accounting Integration** (PRs #1325–#1338) — `AccountingProvider` port, `AccountingSyncEntry` work queue, `AccountingSyncWorker` (scheduled drain, exponential backoff, SyncState machine), `XeroOAuthService` (PKCE), `XeroApiClient`, event-driven sync enqueue, `TrustBoundaryGuard`. This is the primary architectural precedent — calendar sync follows the same pattern.
- **Phase 24 — Outbound Email Delivery** (PRs #348–#361) — `EmailProvider` port, `SmtpEmailProvider`, `SendGridEmailProvider` (BYOAK), `EmailDeliveryLog`, `EmailWebhookService` (signature validation, tenant routing), `EmailNotificationChannel`. The email domain already has outbound; this phase adds inbound.
- **Phase 21 — Integration Ports & BYOAK** (PRs #302–#314) — `IntegrationDomain` enum, `@IntegrationAdapter` annotation, `IntegrationRegistry` (tenant-scoped adapter resolution, Caffeine cache), `OrgIntegration` entity, `SecretStore`, `IntegrationGuardService`. All integration infrastructure is built.
- **Phase 56 — Production Infrastructure** (PRs #854–#871) — AWS infrastructure (ECR, ECS, ALB, RDS, S3, SES). SES is already provisioned for outbound; this phase configures SES inbound receiving.
- **Phase 37 — Workflow Automations** (PRs #555–#568) — `AutomationRule`, trigger types, condition evaluation, action executors. This phase adds two new trigger types (`EMAIL_RECEIVED`, `CALENDAR_EVENT_APPROACHING`).
- **Phase 73 — Matter Detail Redesign** (PRs #1339–#1346) — Sidebar + grouped tab bar layout. The Emails tab lives in the Communication group alongside Comments and Activity.
- **Phase 6.5 — Notifications, Comments & Activity** (PRs #107–#122) — Notification entity, domain event handlers, activity feed formatter. Email filed / calendar connected events use the same infrastructure.
- **Calendar infrastructure** — `CalendarService` (aggregates tasks + projects by date range, role-based visibility), `CalendarController`, frontend calendar page with month/list views. Calendar sync extends this with external Google Calendar events.
- **Legal calendar infrastructure** — `CourtCalendarService`, `CourtDate`, `PrescriptionTracker`, `CourtDateReminderJob` (Phase 55). Court dates are a key entity type that syncs to Google Calendar.

### What is missing today

- **No external calendar sync.** Kazi has a calendar view (tasks + projects + court dates) but it's an island. Practitioners live in Google Calendar / Outlook. Court dates, task deadlines, and filing due dates are invisible in the calendar they actually check. A missed court date = malpractice liability; a missed tax deadline = penalties. Every competitor (Clio, PracticePanther, Smokeball) offers calendar sync.
- **No email-to-matter filing.** Email is the primary communication channel for every professional services firm. Client emails, opposing counsel correspondence, vendor communications — all must be part of the matter record. Today they live in Gmail/Outlook inboxes with no connection to Kazi. The matter file is incomplete without them.
- **No inbound email processing.** Outbound email (invoices, notifications, portal links) works well (SMTP + SendGrid). But there's no mechanism to receive emails into the platform — no inbound address, no email parsing, no attachment extraction.

### Founder decisions that constrain this phase (2026-05-25 ideation)

- **Google Workspace first.** Single OAuth consent covers Google Calendar API. Microsoft 365 adapters deferred to a future phase.
- **Bidirectional calendar sync.** Kazi events push to Google Calendar; Google Calendar changes pull into Kazi.
- **Per-member calendar connections.** Each team member connects their own Google account. Calendar is personal, not org-level.
- **Configurable entity types per org.** Org admins toggle which entity types sync (tasks, court dates, deadlines, project milestones).
- **Dedicated forwarding addresses for email filing.** Each project gets a unique address (e.g. `matter-{token}@inbound.kazi.app`). No Gmail mailbox read access. Works with any email client.
- **Threaded conversation view.** Emails grouped by RFC Message-ID/In-Reply-To/References headers with subject-based fallback.
- **AWS SES for inbound email processing.** SES receives at `inbound.kazi.app`, stores raw .eml in S3, SNS notifies backend webhook. No new vendor dependency.
- **Google push notifications for calendar sync.** Near-real-time inbound sync via webhook registration. Fallback to polling if push channel fails.
- **No Gmail API access.** The forwarding address approach means we don't need Gmail read/send scopes. OAuth scopes are calendar-only.

## Objective

Ship two cross-vertical integration domains in one phase:

1. **Bidirectional Google Calendar Sync** — per-member OAuth, push Kazi events (tasks, court dates, deadlines, milestones) to Google Calendar, pull Google Calendar events into Kazi's calendar view. Configurable entity types per org. Push notification webhook for near-real-time inbound sync.

2. **Inbound Email Filing** — per-project forwarding addresses, AWS SES inbound pipeline, email parsing + threading, attachment storage in S3, threaded conversation view on the project detail page.

Both domains are horizontal (not vertical-specific) and follow the established integration port architecture (`IntegrationDomain`, `@IntegrationAdapter`, `IntegrationRegistry`, `SyncEntry` work queue pattern).

## Constraints & Assumptions

- Google Calendar API v3 and Google OAuth2 with PKCE.
- SES Inbound is only available in us-east-1, us-west-2, eu-west-1. The production SES region must be one of these.
- MX records for `inbound.kazi.app` must point to the SES inbound endpoint.
- Google push notification webhooks require a public HTTPS endpoint with valid certificate.
- Per-member OAuth tokens stored in `SecretStore` (encrypted at rest), same as Xero.
- Calendar sync worker follows `AccountingSyncWorker` exactly: 30s drain interval, batch of 25, exponential backoff (1m → 5m → 15m → 1h → 6h), max 5 attempts → DEAD_LETTER.
- Email HTML bodies must be sanitized (strip scripts, event handlers, tracking pixels) before storage and display.
- No new RBAC capabilities needed. Calendar connection is personal (no capability check). Org-level settings use existing `MANAGE_INTEGRATIONS`. Email viewing uses existing project access.

## Calendar Sync Domain

### Data Model

**`member_calendar_connection`** (tenant schema) — per-member Google OAuth state and push channel info. Columns: id, member_id (FK, unique per provider_slug), provider_slug, google_email, sync_enabled, sync_token (incremental sync cursor), channel_id, channel_resource_id, channel_expiry, last_synced_at, created_at, updated_at.

**`calendar_sync_config`** (tenant schema) — per-org entity type toggles. Columns: id, sync_tasks (default true), sync_court_dates (default true), sync_deadlines (default true), sync_project_milestones (default false), created_at, updated_at.

**`calendar_event_mapping`** (tenant schema) — bidirectional entity ↔ Google event mapping. Columns: id, member_calendar_connection_id (FK), entity_type (TASK/COURT_DATE/DEADLINE/PROJECT/EXTERNAL), entity_id (null for EXTERNAL), google_event_id, google_calendar_id, sync_direction (PUSH/PULL), last_synced_at, etag.

**`calendar_sync_entry`** (tenant schema) — work queue, same SyncState machine as accounting_sync_entry. Columns: id, member_calendar_connection_id (FK), entity_type, entity_id, direction (PUSH/PULL), state (PENDING/IN_FLIGHT/COMPLETED/FAILED_RETRYING/DEAD_LETTER), trigger (EVENT/MANUAL_RETRY/FORCE_RESYNC/PUSH_NOTIFICATION), attempt_count, next_attempt_at, last_error_code, last_error_detail, completed_at, created_at.

### OAuth Flow

GoogleCalendarOAuthService — same PKCE pattern as XeroOAuthService. Scopes: calendar, calendar.events, profile, email. State token in SecretStore. Refresh token in SecretStore keyed `calendar:google:refresh:{memberId}`. On successful connect: create member_calendar_connection, register push notification channel, trigger initial full sync (±90 day window).

### Outbound Sync (Kazi → Google)

CalendarSyncEventListener listens to domain events (TaskCreatedEvent, TaskUpdatedEvent, CourtDateCreatedEvent, etc.) via @TransactionalEventListener AFTER_COMMIT. Checks CalendarSyncConfig for entity type enablement. Finds all member_calendar_connections for entity assignees. Enqueues CalendarSyncEntry per member (direction=PUSH). CalendarSyncWorker drains queue, GoogleCalendarAdapter creates/updates/deletes events, stores mapping.

Entity-to-event mapping:
- Task (with due date) → all-day event. Title = task name. Description = project name + Kazi deep link.
- Court Date → timed event (start/end). Title = "[Court] " + description. Location = court name.
- Filing Deadline → all-day event. Title = "[Deadline] " + name.
- Project milestone → all-day event. Title = "[Project] " + project name.

### Inbound Sync (Google → Kazi)

Google push notification → /api/webhooks/calendar/google. Validate X-Goog-Channel-ID + X-Goog-Resource-ID. Enqueue CalendarSyncEntry (direction=PULL). Worker drains, calls GoogleCalendarAdapter.fetchChanges(syncToken) for incremental delta. New events create EXTERNAL mappings (read-only in Kazi). Deleted events remove mappings. Update syncToken.

### Conflict Resolution

Kazi-originated (PUSH): Kazi is source of truth. Google edits overwritten on next Kazi update. Google-originated (PULL): Google is source of truth. Read-only in Kazi.

### Push Channel Renewal

CalendarChannelRenewalJob runs daily, renews channels expiring within 48 hours. Fallback to 15-minute polling if renewal fails.

### Frontend

Member profile settings: "Connect Google Calendar" OAuth button, connected state (email, last sync, status), disconnect, error/retry.

Org Settings > Integrations: Calendar Sync card with entity type toggles (MANAGE_INTEGRATIONS).

Calendar page: new "Google Calendar" filter chip, external events with Google badge, read-only detail popover.

## Email-to-Matter Domain

### Data Model

**`inbound_email_route`** (public/shared schema) — token → tenant routing for webhook processing before tenant context is established. Columns: address_token (PK), tenant_schema, project_id, enabled, created_at. Written by tenant-scoped InboundAddressService; read by webhook controller.

**`inbound_address`** (tenant schema) — per-project forwarding address. Columns: id, project_id (FK, unique), address_token (cryptographically random 22-char URL-safe base64), enabled (default true), created_at. Full address: `matter-{token}@inbound.kazi.app`.

**`email_thread`** (tenant schema) — conversation grouping. Columns: id, project_id (FK), normalized_subject, participant_addresses (TEXT[]), message_count, last_message_at, created_at.

**`email_message`** (tenant schema) — individual filed email. Columns: id, inbound_address_id (FK), thread_id (FK), from_address, from_name, to_addresses (TEXT[]), cc_addresses (TEXT[]), subject, body_text, body_html (sanitized), message_id_header (indexed), in_reply_to_header, references_header, raw_s3_key, received_at, created_at.

**`email_attachment`** (tenant schema) — S3-stored attachments. Columns: id, email_message_id (FK), filename, content_type, size_bytes, s3_key, created_at.

### AWS SES Inbound Pipeline

MX record for inbound.kazi.app → SES endpoint. Receipt rule: match `*@inbound.kazi.app`, S3 action (store raw .eml), SNS action (notify backend). SpamVerdict/VirusVerdict FAIL → reject.

Webhook: /api/webhooks/email/inbound. Validate SNS signature. Fetch .eml from S3. InboundEmailService.process(): parse with Jakarta Mail, extract token → lookup public.inbound_email_route → establish tenant context, guard checks (enabled, project not archived), resolve thread, store message + attachments, emit EmailFiledEvent.

### Threading Algorithm

1. Header-based: match In-Reply-To against existing message_id_header. Fallback to References (newest first).
2. Subject-based: normalize subject (strip Re:/Fwd:/FW:), match against recent threads (within 30 days) in same project.
3. New thread if no match.

### Security

Rate limiting: 100 emails/hour per address. Max body: 1MB. Max attachment: 25MB (configurable). Blocked types: .exe, .bat, .sh, .cmd, .ps1, .vbs. HTML sanitized. SES spam/virus filtering as first line. Address tokens cryptographically random (not guessable).

### Frontend

Project detail — Emails tab (Communication group): filing address bar with copy button, thread list (sorted by last_message_at), expandable messages (sender, recipients, timestamp, body, attachments), attachment download via presigned URLs, thread count badge on tab.

Org Settings > Integrations: Email Filing card with org-level enable/disable toggle (MANAGE_INTEGRATIONS).

## Integration Points

### Domain Events

CalendarConnectedEvent, CalendarDisconnectedEvent, CalendarSyncFailedEvent, EmailFiledEvent, EmailThreadCreatedEvent, EmailAttachmentStoredEvent.

### Notifications

EmailFiledEvent → project members: "New email filed to {projectName}: {subject}". CalendarSyncFailedEvent → affected member: "Calendar sync error".

### Audit

Calendar connect/disconnect, email filed (sender, project, attachment count). Existing AuditService.record() with new AuditEventType values.

### Activity Feed

Email filed: "{sender}: {subject}" on project timeline. Calendar connect/disconnect on member activity.

### Automation Triggers

EMAIL_RECEIVED — fires on email filed. Conditions: sender matches, subject contains, has attachments. CALENDAR_EVENT_APPROACHING — fires N days before synced event (same pattern as FIELD_DATE_APPROACHING).

## Out of Scope

- Microsoft 365 / Outlook adapters (future phase — add adapters to same ports)
- Sending/replying to emails from within Kazi
- Gmail label sync or mailbox read access
- Calendar event creation UI (events originate from tasks/court dates)
- Meeting scheduling / booking links
- Cross-project email search
- Portal email visibility
- iCal feed export

## ADR Topics

- **ADR: Per-member vs per-org integration connections** — member_calendar_connection introduces a new pattern (member-scoped OAuth) alongside existing org-scoped OrgIntegration. Document the boundary: calendar is personal, accounting is org-level.
- **ADR: Shared-schema routing for inbound email** — public.inbound_email_route breaks the tenant-isolation pattern for webhook routing. Document why (webhook arrives before tenant context) and the security boundary (token is random, route table is read-only from webhook path).
- **ADR: Calendar conflict resolution strategy** — last-write-wins with origin-based source of truth. Document trade-offs vs merge-based resolution.
- **ADR: SES Inbound vs SendGrid Inbound Parse** — document the decision and region constraint.

## Style & Boundaries

- Follow the Xero sync architecture exactly for the calendar sync worker, SyncState machine, and event-driven enqueue pattern.
- Follow the @IntegrationAdapter discovery pattern. Add CALENDAR to IntegrationDomain enum.
- GoogleCalendarOAuthService follows XeroOAuthService (PKCE, SecretStore, state validation).
- Email parsing uses Jakarta Mail (already on the classpath via Spring Boot).
- No Lombok. Java records for DTOs. Constructor injection only.
- Frontend follows Phase 73 grouped tab bar pattern for the Emails tab.
- Calendar view extension follows existing filter chip + CalendarService patterns.
