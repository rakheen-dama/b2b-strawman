# Phase 75 — Calendar Sync + Email-to-Matter Filing

Two cross-vertical integration domains in one phase: bidirectional Google Calendar sync (per-member) and inbound email filing to projects via dedicated forwarding addresses.

## Decisions

- **Provider**: Google Workspace first (Google Calendar API only — no Gmail API access needed). Microsoft 365 adapters deferred to a future phase.
- **Calendar sync direction**: Bidirectional. Kazi events push to Google Calendar; Google Calendar changes pull into Kazi.
- **Calendar connection scope**: Per-member. Each team member connects their own Google account.
- **Calendar entity types**: Configurable per org. Toggles for tasks, court dates, deadlines, project milestones.
- **Email filing method**: Dedicated forwarding addresses per project (e.g. `matter-{token}@inbound.kazi.app`). No Gmail mailbox read access required.
- **Email display**: Threaded conversations on the project timeline using RFC Message-ID/In-Reply-To/References headers.
- **Inbound email processor**: AWS SES Inbound (S3 storage + SNS notification → backend webhook).
- **Calendar sync strategy**: Google push notifications (webhook) for near-real-time inbound sync. Domain events trigger outbound sync.

## Data Model

### Calendar Domain — 4 tables (tenant schema)

**`member_calendar_connection`** — Per-member Google OAuth state.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `member_id` | UUID | FK → member. Unique per provider_slug |
| `provider_slug` | VARCHAR(30) | `google` (extensible to `outlook` later) |
| `google_email` | VARCHAR(255) | Google account email (display only) |
| `sync_enabled` | BOOLEAN | Member-level toggle |
| `sync_token` | TEXT | Google incremental sync cursor |
| `channel_id` | VARCHAR(100) | Push notification channel ID |
| `channel_resource_id` | VARCHAR(100) | Push notification resource ID |
| `channel_expiry` | TIMESTAMP | When the push channel expires |
| `last_synced_at` | TIMESTAMP | Last successful sync |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**`calendar_sync_config`** — Per-org toggles for which entity types sync.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `sync_tasks` | BOOLEAN | Default true |
| `sync_court_dates` | BOOLEAN | Default true |
| `sync_deadlines` | BOOLEAN | Default true |
| `sync_project_milestones` | BOOLEAN | Default false |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**`calendar_event_mapping`** — Maps Kazi entity ↔ Google Calendar event.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `member_calendar_connection_id` | UUID | FK → member_calendar_connection |
| `entity_type` | VARCHAR(30) | TASK, COURT_DATE, DEADLINE, PROJECT, EXTERNAL |
| `entity_id` | UUID | Kazi entity ID (null for EXTERNAL) |
| `google_event_id` | VARCHAR(255) | Google Calendar event ID |
| `google_calendar_id` | VARCHAR(255) | Google Calendar ID (usually "primary") |
| `sync_direction` | VARCHAR(15) | PUSH (Kazi-originated) or PULL (Google-originated) |
| `last_synced_at` | TIMESTAMP | |
| `etag` | VARCHAR(100) | Google event ETag for conflict detection |

**`calendar_sync_entry`** — Work queue. Same SyncState machine as `accounting_sync_entry`.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `member_calendar_connection_id` | UUID | FK |
| `entity_type` | VARCHAR(30) | TASK, COURT_DATE, DEADLINE, PROJECT, EXTERNAL_EVENT |
| `entity_id` | UUID | |
| `direction` | VARCHAR(10) | PUSH or PULL |
| `state` | VARCHAR(30) | PENDING, IN_FLIGHT, COMPLETED, FAILED_RETRYING, DEAD_LETTER |
| `trigger` | VARCHAR(20) | EVENT, MANUAL_RETRY, FORCE_RESYNC, PUSH_NOTIFICATION |
| `attempt_count` | INT | |
| `next_attempt_at` | TIMESTAMP | |
| `last_error_code` | VARCHAR(50) | |
| `last_error_detail` | TEXT | |
| `completed_at` | TIMESTAMP | |
| `created_at` | TIMESTAMP | |

### Email-to-Matter Domain — 4 tables (tenant schema)

**`inbound_address`** — Per-project forwarding address.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `project_id` | UUID | FK → project. One address per project |
| `address_token` | VARCHAR(30) | Cryptographically random, URL-safe. Unique globally |
| `enabled` | BOOLEAN | Default true. Disabled = reject inbound |
| `created_at` | TIMESTAMP | |

Full address: `matter-{address_token}@inbound.kazi.app`

### Shared Schema — Inbound Email Routing (public schema)

**`inbound_email_route`** — Maps address token → tenant before tenant context is established. Written by the tenant-scoped `InboundAddressService` on create/disable; read by the webhook controller before `RequestScopes.runForTenant()`.

| Column | Type | Notes |
|--------|------|-------|
| `address_token` | VARCHAR(30) | PK. Same value as tenant-scoped `inbound_address.address_token` |
| `tenant_schema` | VARCHAR(63) | Target tenant schema |
| `project_id` | UUID | Target project |
| `enabled` | BOOLEAN | Mirrors tenant-scoped enabled flag |
| `created_at` | TIMESTAMP | |

**`email_thread`** — Conversation grouping.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `project_id` | UUID | FK → project |
| `normalized_subject` | VARCHAR(500) | Stripped of Re:/Fwd:/FW:, trimmed, lowercased |
| `participant_addresses` | TEXT[] | Array of all sender/recipient addresses in thread |
| `message_count` | INT | Denormalized count |
| `last_message_at` | TIMESTAMP | For sort order |
| `created_at` | TIMESTAMP | |

**`email_message`** — Individual filed email.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `inbound_address_id` | UUID | FK → inbound_address |
| `thread_id` | UUID | FK → email_thread |
| `from_address` | VARCHAR(255) | Sender email |
| `from_name` | VARCHAR(255) | Sender display name |
| `to_addresses` | TEXT[] | Recipient list |
| `cc_addresses` | TEXT[] | CC list |
| `subject` | VARCHAR(500) | Original subject |
| `body_text` | TEXT | Plain text body |
| `body_html` | TEXT | HTML body (sanitized) |
| `message_id_header` | VARCHAR(255) | RFC Message-ID. Indexed for threading |
| `in_reply_to_header` | VARCHAR(255) | RFC In-Reply-To |
| `references_header` | TEXT | RFC References (space-separated message IDs) |
| `raw_s3_key` | VARCHAR(500) | S3 key for raw .eml file |
| `received_at` | TIMESTAMP | When SES received it |
| `created_at` | TIMESTAMP | |

**`email_attachment`** — File attachments stored in S3.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `email_message_id` | UUID | FK → email_message |
| `filename` | VARCHAR(255) | Original filename |
| `content_type` | VARCHAR(100) | MIME type |
| `size_bytes` | BIGINT | |
| `s3_key` | VARCHAR(500) | StorageService key |
| `created_at` | TIMESTAMP | |

## Calendar Sync — Service Architecture

### OAuth Flow (per-member)

1. Member clicks "Connect Google Calendar" in profile settings.
2. `GoogleCalendarOAuthService.initiateConnect(memberId)` generates authorization URL with PKCE (scopes: `calendar`, `calendar.events`, `profile`, `email`). State token stored in SecretStore.
3. Google consent → redirect callback with auth code.
4. Backend exchanges code for access + refresh token. Refresh token stored in SecretStore keyed `calendar:google:refresh:{memberId}`. Access token is short-lived, fetched on demand.
5. Creates `member_calendar_connection` row.
6. Registers push notification channel with Google Calendar API.
7. Triggers initial full sync (±90 days window).

### Outbound Sync (Kazi → Google)

```
Domain event (TaskCreatedEvent, CourtDateUpdatedEvent, etc.)
  → CalendarSyncEventListener (@TransactionalEventListener AFTER_COMMIT)
    → Check CalendarSyncConfig: is this entity type enabled for the org?
      → Find all member_calendar_connections for assignees of this entity
        → Enqueue CalendarSyncEntry per member (direction=PUSH)
          → CalendarSyncWorker drains queue (30s interval, batch of 25)
            → GoogleCalendarAdapter creates/updates/deletes Google event
              → Store/update mapping in calendar_event_mapping
```

Domain events that trigger outbound sync:
- Task: created, updated (title/due date/assignee), completed, deleted
- Court Date: created, updated, cancelled
- Filing Deadline: created, updated
- Project: due date changed, completed, archived

### Inbound Sync (Google → Kazi)

```
Google Calendar push notification → /api/webhooks/calendar/google
  → Validate X-Goog-Channel-ID + X-Goog-Resource-ID against stored channel
    → Enqueue CalendarSyncEntry (direction=PULL, trigger=PUSH_NOTIFICATION)
      → CalendarSyncWorker drains queue
        → GoogleCalendarAdapter.fetchChanges(syncToken) — incremental sync
          → For each changed event:
            → If mapping exists (PUSH origin): update Kazi entity fields
            → If mapping exists (PULL origin): update local cache
            → If new: create calendar_event_mapping with direction=PULL, entity_type=EXTERNAL
            → If deleted: remove mapping
          → Update syncToken on member_calendar_connection
```

### Conflict Resolution

- Kazi-originated events (direction=PUSH): Kazi is source of truth. Edits in Google Calendar are overwritten on next Kazi update. This prevents drift — the task/court date entity is the canonical record.
- Google-originated events (direction=PULL): Google is source of truth. These are read-only in Kazi's calendar view. No Kazi entity is created — they're displayed from the mapping table.

### Entity-to-Event Mapping

| Kazi Entity | Google Calendar Event |
|---|---|
| Task (with due date) | All-day event on due date. Title = task name. Description includes project name + Kazi deep link |
| Court Date | Timed event (start → end). Title = "[Court] " + description. Location = court name |
| Filing Deadline | All-day event. Title = "[Deadline] " + deadline name |
| Project milestone | All-day event on project due date. Title = "[Project] " + project name |

### Push Notification Channel Renewal

Google push channels expire (max 30 days TTL). `CalendarChannelRenewalJob` runs daily, renews channels expiring within 48 hours. If renewal fails, the member's sync degrades to polling until the channel is re-established (fallback poll interval: 15 minutes).

### Sync Worker

Follows the `AccountingSyncWorker` pattern exactly:
- `@Scheduled(fixedDelay = 30_000)` — drain every 30 seconds
- `TenantScopedRunner.forEachTenant()` — process all tenants
- Batch size: 25 entries per tenant per cycle
- Exponential backoff: 1m → 5m → 15m → 1h → 6h
- Max attempts: 5 → DEAD_LETTER
- Rate limit handling: break batch, retry tenant later

## Email-to-Matter — Inbound Pipeline

### AWS SES Configuration

- MX record for `inbound.kazi.app` → SES inbound endpoint
- SES Receipt Rule: match `*@inbound.kazi.app`
  - Action 1: S3 (store raw .eml in `s3://kazi-inbound-email/{messageId}.eml`)
  - Action 2: SNS → topic `kazi-inbound-email`
  - SpamVerdict = FAIL → reject
  - VirusVerdict = FAIL → reject
- SNS subscription: HTTPS → `https://api.kazi.app/api/webhooks/email/inbound`

### Processing Flow

```
Email arrives at matter-{token}@inbound.kazi.app
  → SES Receipt Rule → S3 + SNS
    → /api/webhooks/email/inbound (InboundEmailWebhookController)
      → Validate SNS signature
      → Fetch raw .eml from S3
      → InboundEmailService.process(rawEmail):
          1. Parse with Jakarta Mail (MimeMessage)
          2. Extract token from recipient → lookup public.inbound_email_route → resolve tenant + project
          3. RequestScopes.runForTenant() (tenant context now established)
          4. Guard: address enabled? project not archived/closed?
          5. Resolve thread (see threading algorithm below)
          6. Create email_message row
          7. Upload attachments to S3 via StorageService, create email_attachment rows
          8. Update email_thread (message_count, last_message_at, participants)
          9. Emit EmailFiledEvent
```

### Threading Algorithm

```
resolveThread(projectId, parsedEmail):
  // Step 1: Header-based (most reliable)
  if inReplyTo != null:
    match = findByMessageIdHeader(projectId, inReplyTo)
    if match: return match.thread

  for ref in references (newest first):
    match = findByMessageIdHeader(projectId, ref)
    if match: return match.thread

  // Step 2: Subject-based fallback (forwarded emails strip headers)
  normalizedSubject = stripPrefixes(subject)  // Re: Fwd: FW: RE:
  recentThread = findByNormalizedSubject(projectId, normalizedSubject, within=30days)
  if recentThread: return recentThread

  // Step 3: New thread
  return createThread(projectId, normalizedSubject)
```

### Security

- Rate limiting: 100 emails/hour per inbound address
- Max email body: 1MB parsed text/HTML
- Max attachment size: 25MB per email (configurable in org settings)
- Blocked attachment types: .exe, .bat, .sh, .cmd, .ps1, .vbs — stripped and logged
- HTML body sanitized (strip scripts, event handlers, external image tracking pixels)
- SES spam/virus filtering as first line of defense
- Address tokens are cryptographically random (22-char URL-safe base64) — not guessable

## Frontend

### Member Profile — Calendar Connection

New section in member profile/settings page:
- "Connect Google Calendar" button → OAuth flow
- Connected state: Google email, last sync time, sync status indicator
- "Disconnect" button (revokes token, deletes mappings, stops sync)
- Error state: "Sync failed — try reconnecting" with retry button

### Org Settings — Integration Cards

**Calendar Sync card** (Settings > Integrations):
- Entity type toggles: Tasks, Court Dates, Deadlines, Project Milestones
- Each toggle updates `calendar_sync_config`
- Requires MANAGE_INTEGRATIONS capability

**Email Filing card** (Settings > Integrations):
- Org-level enable/disable toggle
- Info section explaining forwarding addresses
- Requires MANAGE_INTEGRATIONS capability

### Calendar View Enhancement

Existing `/org/[slug]/calendar` page extended:
- New event source: Google Calendar events (from PULL mappings) rendered with Google icon badge
- New filter chip: "Google Calendar" alongside existing "Tasks" / "Projects"
- External events are read-only (click opens detail popover, no edit)
- Visual distinction: Kazi events use existing colors, Google events use muted/secondary style

### Project Detail — Emails Tab

New tab in the Communication group of the grouped tab bar (Phase 73):
- Filing address bar at top with copy button
- Thread list sorted by `last_message_at` descending
- Each thread: subject, participants, message count, last message date
- Expandable to show individual messages with sender, recipients, timestamp, body, attachments
- Collapsed messages show sender + body preview snippet
- Attachment chips with download links (served via StorageService presigned URLs)
- Thread count badge on the tab

## Integration Points

### Domain Events

| Event | Trigger | Consumers |
|-------|---------|-----------|
| `CalendarConnectedEvent` | Member completes OAuth | Audit, Activity Feed |
| `CalendarDisconnectedEvent` | Member disconnects | Audit, Activity Feed |
| `CalendarSyncFailedEvent` | Entry hits DEAD_LETTER | Notification (member), Audit |
| `EmailFiledEvent` | Inbound email processed | Notification (project members), Audit, Activity Feed |
| `EmailThreadCreatedEvent` | New thread started | Activity Feed |
| `EmailAttachmentStoredEvent` | Attachment saved | Audit |

### Notifications

- `EmailFiledEvent` → project members: "New email filed to {projectName}: {subject}" (in-app + email if enabled)
- `CalendarSyncFailedEvent` → affected member: "Calendar sync error — reconnect may be required"

### Audit

- Calendar connect/disconnect: who, when, which Google account
- Email filed: external sender, project, attachment count
- Uses existing `AuditService.record()` with new `AuditEventType` values

### Activity Feed

- Email filed: "{sender}: {subject}" with thread link on project timeline
- Calendar connect/disconnect: on member activity

### Automation Triggers (Phase 37)

Two new trigger types:
- `EMAIL_RECEIVED` — fires when email filed. Conditions: sender matches, subject contains, has attachments. Actions: assign task, notify, change status.
- `CALENDAR_EVENT_APPROACHING` — fires N days before synced event. Same pattern as `FIELD_DATE_APPROACHING`.

### RBAC

- Calendar connection: any member (personal — no capability check)
- Calendar sync config: MANAGE_INTEGRATIONS
- View filed emails: any member with project access
- Email filing org settings: MANAGE_INTEGRATIONS

## Out of Scope

- Microsoft 365 / Outlook adapters
- Sending/replying to emails from within Kazi
- Gmail label sync or mailbox read access
- Calendar event creation UI (events originate from tasks/court dates, not a "create event" form)
- Meeting scheduling / booking links
- Cross-project email search
- Portal email visibility
- iCal feed export
