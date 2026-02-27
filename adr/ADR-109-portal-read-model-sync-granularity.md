# ADR-109: Portal Read-Model Sync Granularity

**Status**: Accepted

**Context**:

The portal needs acceptance data for two distinct purposes: (1) list views showing pending acceptances on the portal dashboard, where the contact sees which documents are awaiting their action, and (2) the transactional acceptance page where the contact views the PDF, types their name, and submits acceptance.

The existing portal read-model pattern (`customerbackend/`) stores denormalized copies of tenant-schema data in a portal-specific schema. `PortalDocument`, `PortalProject`, and `PortalInvoice` entities are synced via domain events published by the main application and handled by `PortalEventHandler`. This keeps portal reads fast and decoupled from the main tenant schema. The read-model is eventually consistent — there is a small sync delay between when a change occurs in the main schema and when it appears in the portal read-model.

The acceptance page is different from other portal pages because it has a write action: the contact clicks "I Accept" and the system records the acceptance. This is a critical transactional flow — the system must check the real-time status of the acceptance request (is it still valid? has it been revoked? has it expired?) before allowing the acceptance to proceed. Reading stale data from the read-model could allow a contact to accept a document that was revoked seconds earlier.

**Options Considered**:

1. **Full sync** -- Mirror all `AcceptanceRequest` fields to a `PortalAcceptanceRequest` read-model entity. The portal reads exclusively from the read-model for all views, including the acceptance page.
   - Pros:
     - Complete separation between portal reads and main schema writes. Consistent with the existing portal read-model pattern.
     - Fast reads from the dedicated portal schema. No cross-schema queries.
     - All portal data access follows the same pattern — developers do not need to remember which pages read from the read-model and which read live.
   - Cons:
     - Acceptance is a transactional flow. Stale status in the read-model creates a real risk: a contact could view a revoked request as still pending, or attempt to accept a request that expired between the sync and their click. The `accept()` call would need to validate against the main schema anyway, making the read-model data partially untrusted.
     - Sync latency, even if small (100ms–2s), means the acceptance page could show "Pending" when the request was just revoked. The UX of "I clicked accept and got an error saying it was revoked" is confusing.
     - All acceptance request fields must be synced: `acceptorName`, `acceptorIpAddress`, `certificateS3Key`, etc. This is a large sync surface for data that is mostly needed only at acceptance time, not for list views.
     - Domain events must capture every field change, including the acceptance metadata that is recorded during the `accept()` call. This creates a circular dependency: the `accept()` handler writes to the main schema and then publishes an event to update the read-model with the same data.

2. **Minimal sync** -- Only store `requestToken`, `status`, and a document reference in the read-model. The portal acceptance controller fetches all display data live from the main schema.
   - Pros:
     - Minimal sync surface: only 3-4 fields to keep in sync. Events are simple status updates.
     - The acceptance page always has fresh data from the main schema. No staleness risk for the transactional flow.
     - Simpler event handlers — no need to sync document titles, org branding, or acceptance metadata.
   - Cons:
     - Portal list views (dashboard "Pending Acceptances") need to display document title, sent date, and expiry — this data is not in the read-model, so the list view must also hit the main schema. This defeats the purpose of having a read-model for list views.
     - Every portal page that shows acceptance data requires a live query to the main schema, increasing cross-schema coupling.
     - Inconsistent with the existing portal read-model pattern: other portal entities (documents, projects, invoices) are fully synced for list views.

3. **Hybrid (chosen)** -- The read-model stores enough data for list views (token, status, document title, org branding, sent/expiry dates). The acceptance page controller reads live from the main schema via token lookup. Domain events keep the list view data in sync; the acceptance page always has fresh data.
   - Pros:
     - List views are fast: the portal dashboard's "Pending Acceptances" reads entirely from the read-model. No cross-schema queries for browsing.
     - The transactional acceptance page is always consistent: it reads the real-time status, expiry, and request validity from the main schema. No risk of accepting a revoked or expired request due to sync latency.
     - Sync events are limited to status transitions and list-view metadata. The read-model does not need to store acceptance evidence fields (`acceptorName`, `acceptorIpAddress`, `acceptorUserAgent`, `certificateS3Key`).
     - Matches the criticality split: list views can tolerate 1-2 seconds of sync latency (showing a request as "Sent" when it was just accepted is harmless), but the acceptance action cannot tolerate any staleness.
   - Cons:
     - Two data sources for different views: list views read from the read-model, the acceptance page reads from the main schema. Developers must know which source applies to which page.
     - The portal acceptance controller needs access to main-schema entities (`AcceptanceRequest`, `GeneratedDocument`). This crosses the read-model boundary for one specific flow.
     - Slightly more complex architecture than either pure approach.

**Decision**: Option 3 -- Hybrid approach.

**Rationale**:

The acceptance page is a critical transactional flow where stale data has real consequences. A contact could accept a document that was revoked seconds ago if the read-model has not yet synced the revocation event. Reading live from the main schema for this specific page eliminates the consistency risk entirely. The `accept()` call checks the real-time status, expiry, and request validity — there is no window where a stale read-model could cause an incorrect acceptance.

Meanwhile, the portal dashboard's "Pending Acceptances" list is a convenience view where 1-2 seconds of sync latency is completely acceptable. Showing a request as "Sent" when it was just revoked is a minor cosmetic issue — the contact sees an updated status on their next page load. This matches the existing portal read-model pattern: `PortalDocument` and `PortalProject` are eventually consistent list views, and this has never been a problem because those pages are read-only.

Full sync (Option 1) would technically work, but the `accept()` call must validate against the main schema regardless — you cannot allow a write operation based on read-model data. This means the acceptance page would read from the read-model for display, then validate against the main schema for the write. Two queries to two schemas for one page, with the read-model query being redundant.

Minimal sync (Option 2) goes too far in the other direction — it makes list views hit the main schema, which defeats the purpose of having a read-model. The portal dashboard should not be making cross-schema queries to display a list of pending acceptances.

**Consequences**:

- Positive:
  - `PortalAcceptanceRequest` entity in the portal read-model stores list-view fields: `id`, `portalContactId`, `generatedDocumentId`, `documentTitle`, `documentFileName`, `status`, `requestToken`, `sentAt`, `expiresAt`, `orgName`, `orgLogo`, `createdAt`. This is sufficient for rendering the "Pending Acceptances" list on the portal dashboard.
  - Domain events (`AcceptanceRequestSent`, `AcceptanceRequestAccepted`, `AcceptanceRequestRevoked`, `AcceptanceRequestExpired`) trigger read-model sync via `PortalEventHandler`. Only status and timestamp fields are updated — no acceptance evidence fields synced.
  - The acceptance page always shows real-time data. No risk of stale status, expired-but-shown-as-valid, or revoked-but-shown-as-pending scenarios.

- Negative:
  - The portal acceptance page is a special case in the portal architecture — it is the only portal page that reads from the main tenant schema rather than from the read-model. This exception must be documented for developers.
  - `PortalAcceptanceController` (at `/api/portal/acceptance/{token}`) has a dependency on `AcceptanceRequest` and related main-schema entities. Other portal controllers depend only on read-model entities.

- Neutral:
  - The `PortalAcceptanceRequest` read-model entity stores the `requestToken` for list view links — the portal dashboard's "Pending Acceptances" list renders "Review & Accept" links that route to `/portal/accept/{requestToken}`. The token is non-secret in this context (it is embedded in every acceptance email sent to the contact).
  - If the portal adds more write actions in the future (e.g., portal contact can reject a document, request changes, or upload a counter-signed version), this hybrid pattern scales well: list views from the read-model, transactional pages from the main schema.
  - The global portal migration (in the portal schema) creates the `portal_acceptance_requests` table. This is separate from the tenant-schema migration that creates `acceptance_requests`.

- Related: [ADR-107](ADR-107-acceptance-token-strategy.md) (acceptance token strategy), [ADR-108](ADR-108-certificate-storage-and-integrity.md) (certificate storage), [ADR-031](ADR-031-portal-read-model.md) (portal read-model architecture), [ADR-035](ADR-035-activity-feed-direct-query.md) (direct query vs. read-model trade-offs).
