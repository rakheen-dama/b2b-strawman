# ADR-389: Imported clients enter ACTIVE through a dedicated path that fires no lifecycle side effects

**Status**: Accepted

**Context**:

Imported clients must land ACTIVE (requirements decision 3 (RD-3)) without tripping the side effects that creating and activating a client normally has, and without weakening the PROSPECT default the normal UI relies on (requirements § Constraints). At HEAD:

- Every `CustomerService.createCustomer` overload pins PROSPECT (`:388-495`) and publishes `CustomerCreatedEvent` (`:550`), whose listeners create a portal contact (`PortalContactAutoProvisioner.onCustomerCreated`, `:44`) and queue a Xero push (`AccountingSyncEventListener.onCustomerCreated`, `:88`). Its `customer.created` audit logs name and email (`:536-544`).
- Normal activation is PROSPECT → ONBOARDING (auto-instantiates FICA checklists) → ACTIVE (all checklists complete plus `LIFECYCLE_ACTIVATION` prerequisites incl. tax number), each transition auditing `customer.lifecycle.transitioned` and publishing `CustomerStatusChangedEvent`, an automation trigger (`CustomerLifecycleService.java:113-195`).
- PROSPECT blocks matter creation (`CustomerLifecycleGuard`), so imported clients cannot stay PROSPECT if their matters are to follow.
- The 8-argument `Customer` constructor accepts a status and publishes nothing (`Customer.java:152-162`); the Xero import uses it — with PROSPECT, not ACTIVE (`XeroCustomerImportService.java:252-261`).
- The 02:00 `DormancyScheduledJob` moves ACTIVE clients to DORMANT when their latest activity across time entries, tasks, documents, invoices and document comments is older than the threshold **or NULL** (`CustomerLifecycleService.java:44-72`); an imported client has no such rows, and trust postings do not count, so it would go dormant overnight.
- `customers.email` is `NOT NULL` and unique.

The requirements' defaults: portal contacts not auto-invited, provisioning an explicit later action; take-on counts as activity or imported clients are exempt until a set date; no FICA checklists.

**Options Considered**:

1. **Create PROSPECT through `createCustomer`, then drive transitions to ACTIVE programmatically.**
   - Pros: Every rule of the normal path applies.
   - Cons: Instantiates the onboarding checklists the attestation is meant to replace, needs them completed, requires `LIFECYCLE_ACTIVATION` prerequisites (tax number) the old export may lack, fires two lifecycle audits, two `CustomerStatusChangedEvent`s (automations) and the portal and Xero listeners.
2. **Add an `initialStatus` parameter to `createCustomer`.**
   - Pros: One creation method.
   - Cons: Opens ACTIVE-at-birth to every caller of the normal path, the opposite of "do not weaken the PROSPECT default"; still publishes `CustomerCreatedEvent`.
3. **Exempt imported clients from dormancy with a per-client "exempt until" date.**
   - Pros: Explicit.
   - Cons: A new column with its own expiry semantics and UI; an arbitrary date; does not describe what happened (the client was taken on).
4. **A dedicated, documented take-on entry path that builds the client ACTIVE with the same validation, publishes no lifecycle or creation event, and records the take-on time, which the dormancy query counts as activity (chosen).**
   - Pros: The normal path is untouched; the take-on path is one method callable only from take-on (ArchUnit); every side effect is a deliberate yes or no; dormancy uses the ordinary rule from a meaningful start date.
   - Cons: Two creation paths to keep in step (validation, field-group auto-apply); imported clients never get the Xero push, so a firm that wants them in Xero must link them there.

**Decision**: Imported clients are created by `TakenOnCustomerService.createTakenOn`, callable only from the take-on package, which applies `createCustomer`'s email-uniqueness, custom-field and field-group rules, builds the client with `LifecycleStatus.ACTIVE` through the 8-argument constructor, sets `take_on_batch_id` and `taken_on_at`, audits `customer.created` with `{source: "TAKE_ON", take_on_batch_id}` only, publishes no `CustomerCreatedEvent` and performs no lifecycle transition; the dormancy query adds `MAX(c.taken_on_at)` to its activity terms; portal contacts are created only by an explicit per-batch action that sends no email.

**Rationale**:

Each side effect was decided on its own merits rather than inherited. No portal contact: a client who has never heard of Kazi should not appear in a client directory before the firm decides to invite them (FD-10); the later action uses `PortalContactService.createContact`, which itself sends no email, and invitations remain the existing per-client action. No Xero push: the firm's accounting system already holds these clients, and pushing duplicates would be worse than not pushing. No lifecycle transition: the client did not go through onboarding in Kazi; the FICA attestation (ADR-388) records what was done instead, and no `CustomerStatusChangedEvent` means no automation fires for hundreds of clients at once. The audit row omits name and email so the take-on adds no PII to audit storage (the normal path's PII is left as it is and noted). The shared field-group auto-apply is extracted into one method so the two paths cannot drift.

Dormancy is fixed in the query, not with an exemption: `GREATEST` ignores NULLs in PostgreSQL, so adding `MAX(c.taken_on_at)` changes nothing for any client that was not taken on, and for one that was, the take-on is its first activity — it goes dormant only if nothing at all happens for the threshold after take-on, which is the ordinary rule. The same "no activity rows means dormant at once" behaviour plausibly affects a newly activated non-imported client; that is an unreproduced hypothesis and is left to its own reproduce-first PR rather than folded in here. Email stays mandatory because `customers.email` is `NOT NULL UNIQUE` and portal, collections and directory logic key on it; rows without one are errors (FD-22).

**Consequences**:

- Positive:
  - A clients take-on sends nothing, provisions nothing and triggers no automation.
  - Imported clients can receive matters immediately (ACTIVE passes `CustomerLifecycleGuard`).
  - No imported client goes dormant overnight; dormancy still works afterwards.
  - The PROSPECT default and the activation rules of the normal UI are unchanged.
- Negative:
  - A second creation path; ArchUnit and shared helpers keep it honest.
  - Imported clients are not pushed to Xero.
  - Clients without an email cannot be taken on in v1.
  - The general dormancy hazard for newly activated clients remains until separately reproduced and fixed.

**Related**: [ADR-388](ADR-388-fica-attestation-model.md) (the attestation replaces checklists), [ADR-351](ADR-351-portal-contact-directory.md) (portal contact directory), [ADR-357](ADR-357-terminology-guard-extension.md) (lifecycle guard text), [ADR-385](ADR-385-take-on-architecture.md); [architecture §17.3.3, §17.6.4, §17.6.6](../architecture/phase93-client-trust-take-on.md); founder decisions FD-10, FD-22; slice 655A.
