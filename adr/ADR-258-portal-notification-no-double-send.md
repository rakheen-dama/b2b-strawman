# ADR-258: Portal Notification — No Double-Send Rule

**Status**: Accepted

**Context**:

Several prior phases already send emails to portal contacts for specific events:

- **Phase 24** (Outbound Email Delivery) — magic-link login emails; invoice events (approved, sent, paid, voided) via the firm-side `EmailNotificationChannel` that recognises portal recipients.
- **Phase 28** (Document Acceptance) — `AcceptanceRequestSentEvent` fires a portal email to the designated contact.
- **Phase 32** (Proposal Engagement Pipeline) — `ProposalSentEvent` emails the portal contact with a proposal link.
- **Phase 34** (Client Information Requests) — `InformationRequestSentEvent` emails the portal contact.

Phase 68 introduces a new `PortalEmailNotificationChannel` — a dedicated portal-email path targeting `PortalContact` recipients (as opposed to firm members) with portal-specific templates (`portal-trust-activity`, `portal-deadline-approaching`, `portal-retainer-period-closed`, etc.). The natural question: should the new channel also take over the existing portal-email events (invoices, acceptance, proposals, info-requests), or should it only handle new events?

Two concrete risks if this is done carelessly:

1. **Double-send** — If both the old path and the new channel fire on the same event, every client gets two emails per transaction. Hard to undo once seen.
2. **Migration churn** — Retrofitting the new channel onto working Phase 24/28/32/34 wiring means touching stable code, rewriting templates, re-running E2E suites, and risking regressions.

**Options Considered**:

1. **Reuse existing email paths for already-wired events; add new channels only for new events** — Phase 68's new `PortalEmailNotificationChannel` subscribes to only the three new portal events (`TrustTransactionApprovedEvent`, `FilingStatusApproachingEvent` (the Phase 51-era name — confirm existence at implementation; if absent, `DeadlinePortalSyncService` emits the approaching event synthetically), `RetainerPeriodClosedEvent`). Existing events keep their existing email paths.
   - Pros:
     - Zero regression risk on stable flows.
     - No double-send possible — existing events are not subscribed by the new channel.
     - Small PR scope for Phase 68 notification work.
     - Matches the principle "only change what's changing".
   - Cons:
     - Two email paths for portal contacts live in parallel (old in `notification/channel/EmailNotificationChannel`, new in `portal/notification/channel/PortalEmailNotificationChannel`).
     - Preferences handling is slightly split — new prefs (`trust_activity_enabled` etc.) are on the new table; old prefs (invoice unsubscribe, etc.) live in Phase 24's preference model.

2. **Re-route everything through `PortalEmailNotificationChannel`** — Retire the old portal-email wiring in Phase 24/28/32/34; unify under the new channel.
   - Pros:
     - One code path for all portal emails.
     - Unified preference model.
   - Cons:
     - Massive migration. Four phases' worth of stable email behaviour to rewrite and re-test.
     - During rollout, risk of double-send (both old and new channel firing) or no-send (old removed before new ready).
     - Preference migration requires a careful data backfill.
     - Phase 68 scope balloons — the Phase 68 ideation was explicit that notifications are an addition, not a re-platform.

3. **Per-event deduplication layer** — Keep both paths; introduce a deduplication service that checks a `sent_events` log and suppresses duplicates.
   - Pros:
     - Belt-and-braces safety against double-sends.
   - Cons:
     - Complex infra for a problem that doesn't exist if we simply don't subscribe to the same events twice.
     - Deduplication logic is notoriously hard to get right (idempotency keys, clock skew, retries).
     - Adds moving parts without solving the underlying question — which path "owns" each event.

**Decision**: Option 1 — reuse existing email paths for already-wired events; add the new `PortalEmailNotificationChannel` only for new events (trust / deadline / retainer).

**Rationale**:

**Minimise change, minimise risk.** Phase 24/28/32/34 email wiring is stable and tested. Rewriting it for aesthetic unification has no product value and real regression risk.

**The no-double-send rule is trivially satisfied if we never subscribe twice.** By scoping the new channel's event list strictly to events not previously wired, the double-send risk is zero — no need for a dedup layer.

**Preference coexistence is acceptable.** The new `portal_notification_preference` table governs the three new event categories + digest + an `action_required_enabled` umbrella flag. Existing unsubscribe mechanisms (Phase 24 172) govern invoice/proposal/acceptance/info-request emails. A client who unsubscribes all via the portal's unsubscribe-all page hits both mechanisms (the new `unsubscribed_all_at` timestamp + the Phase 24 preference record) — unified by the `POST /portal/notifications/unsubscribe-all` endpoint.

**Future unification is still possible.** Nothing in this ADR precludes a later phase from migrating all portal-email traffic to the new channel. That's a Phase 70+ concern; Phase 68 doesn't block or predetermine it.

**Consequences**:

- `PortalEmailNotificationChannel` subscribes to three events only: `TrustTransactionApprovedEvent`, `FilingStatusApproachingEvent` (confirm Phase 51 event name at implementation — emit synthetically from `DeadlinePortalSyncService` if no such event exists), `RetainerPeriodClosedEvent`.
- `EmailNotificationChannel` (firm-side, from Phase 24) continues to handle invoice / acceptance / proposal / info-request events for both firm members and portal contacts.
- The portal preferences page shows toggles for "Trust activity", "Retainer updates", "Deadline reminders" (governed by the new table) plus an "Action-required notifications" umbrella that covers the existing-path events and hooks into Phase 24's preference mechanism.
- Unsubscribe-all sets both `unsubscribed_all_at` on `portal_notification_preference` AND the Phase 24 unsubscribe flag — one endpoint, two side effects.
- Tests: `PortalEmailNotificationChannelTest` explicitly asserts that the channel does NOT react to `InvoiceApprovedEvent` / `ProposalSentEvent` / etc. — the no-double-send contract is a test.
- Documentation for firms explicitly notes that invoice / acceptance / proposal emails continue via the existing path — prevents confusion about why those don't appear in the new preferences page.

**Related**:

- [ADR-170](ADR-170-email-byoak-provider-port.md) — Phase 24 email provider port (the existing path).
- [ADR-172](ADR-172-unsubscribe-and-preferences.md) — Phase 24 unsubscribe mechanism (reused).
- [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) — New surfaces require new events, hence new channel for those specifically.
