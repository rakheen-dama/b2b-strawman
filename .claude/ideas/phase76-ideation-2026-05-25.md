# Phase 76 Ideation — Calendar Sync + Email-to-Matter Filing
**Date**: 2026-05-25

## Catalyst
Founder asked for competitive gap analysis vs Clio and best-in-class practice management tools. Two critical daily-workflow gaps identified: no external calendar sync (practitioners live in Google Calendar/Outlook, not Kazi's calendar view) and no email-to-matter filing (the matter file is incomplete without client correspondence). Both are cross-vertical — every service business needs them.

## Decision
**Two integration domains in one phase: bidirectional Google Calendar sync (per-member) + inbound email filing via dedicated forwarding addresses (AWS SES).** Both horizontal, not vertical-specific.

## Key design choices (founder-selected)
1. **Google Workspace first** — single OAuth for Calendar API. Microsoft 365 deferred. Gmail API not needed (forwarding addresses decouple email filing from provider).
2. **Bidirectional calendar sync** over push-only — Google events appear in Kazi, Kazi events appear in Google Calendar.
3. **Per-member connections** over per-org — calendar is personal. Each member does their own OAuth.
4. **Configurable entity types per org** — admins toggle which types sync (tasks, court dates, deadlines, milestones).
5. **Dedicated forwarding address** over Gmail API sync or manual upload — `matter-{token}@inbound.kazi.app` works with any email client.
6. **Threaded conversations** over flat email list — RFC Message-ID/In-Reply-To/References threading with subject fallback.
7. **AWS SES inbound** over SendGrid Inbound Parse — already on AWS, no new vendor.
8. **Google push notifications** over polling — near-real-time sync, lower API quota usage.

## What was explicitly rejected
- Microsoft 365 in v1 (future phase — same port interface)
- Gmail mailbox read access (forwarding addresses eliminate the need)
- Per-org shared calendar (too noisy, not how practitioners work)
- Polling-only sync (5-15 min latency unacceptable for calendar)
- Manual email paste/upload (too high-friction for adoption)
- Sending/replying from within Kazi (out of scope for v1)

## Architecture precedent
Follows the Xero sync pattern exactly: IntegrationDomain enum + @IntegrationAdapter + SyncEntry work queue + SyncWorker (30s drain, exponential backoff) + event-driven enqueue. New pattern: member-scoped OAuth (vs org-scoped for Xero). Shared-schema routing table for inbound email webhook resolution.

## Phase roadmap after 76
- **Phase 77 candidates**: (a) Microsoft 365 adapters (Outlook Calendar + Mail via Graph API — same port interfaces, add adapters). (b) Email compose/reply from within Kazi (completes the email loop). (c) Cross-project email search. (d) iCal feed export for read-only consumers.

## Next step
`/architecture requirements/claude-code-prompt-phase76.md`
