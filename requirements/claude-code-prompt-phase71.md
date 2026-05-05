# Phase 71 — Xero Accounting Integration (One-Way Sync)

## System Context

Kazi is a multi-tenant B2B practice-management platform with three live verticals (legal-za, accounting-za, consulting-za). Phase 71 is the first **commercial unlock** for the accounting-za vertical: small SA accounting practices cannot adopt a system that does not push invoices into the accountant's general ledger. It is also a quality-of-life unlock for legal-za and consulting-za firms whose bookkeeper already lives in Xero.

Three predecessor systems make Phase 71 cheap rather than expensive:

- **Phase 21 — Integration Ports + BYOAK** (PRs #302–#314) — `IntegrationRegistry`, `OrgIntegration` entity, `SecretStore` (encrypted credentials), `IntegrationGuardService`, `IntegrationController`, integrations settings UI. The `AccountingProvider` port is already defined under `backend/.../integration/accounting/` with `NoOpAccountingProvider`, `InvoiceSyncRequest`, `CustomerSyncRequest`, `LineItem`, `AccountingSyncResult`, and `ConnectionTestResult` shapes. **No new port abstraction is needed for invoice + customer push.**
- **Phase 10 — Invoicing & Billing from Time** (PRs #167–#178) — `Invoice` entity with `DRAFT → APPROVED → SENT → PAID` lifecycle, `InvoiceLine` with billable-time linkage, currency on org settings. Invoice domain events already exist (`InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoicePaidEvent`).
- **Phase 25 — Online Payment Collection** (PRs #362+) — `PaymentEvent` entity with status, gateway-id, amount; `PaymentGateway` port. Phase 71 reuses this entity for Xero-originated payments — a payment is a payment whether it came from a card gateway or from a Xero match.

Three other systems are deliberately **out of scope** for the sync path:

- **Phase 37 — Workflow Automations.** Per founder decision (2026-05-03 ideation), sync is **not** routed through the rule engine. Accounting sync needs retry/back-off/idempotency/rate-limit semantics that don't fit the rule executor cleanly, and debugging Xero `429`s through the `ActionExecution` log would be miserable. The dedicated `AccountingSyncService` is the only sync path. Rules **may** in a future phase fire a "sync this invoice now" action, but that is explicitly Phase 72+.
- **Phase 60/61 — Trust Accounting (Legal Practice Act §86).** Trust ledgers are a **regulatory boundary** — they must not flow to a tenant's operating-account general ledger. Phase 71 hard-codes a guard: any invoice tagged trust-related, any line item drawn from a trust account, or any customer with active trust balances must never be pushed to Xero. The guard fails closed, with an audit event explaining why.
- **Phase 70 — Specialist AI Assistants.** No AI in this phase. The Compliance Assistant (deferred to Phase 72+) will eventually watchdog the trust-vs-operating boundary, but Phase 71 ships the boundary as deterministic Java code.

**What is missing for the accounting-za commercial pitch today:**

- **Push to Xero.** Today an invoice approved in Kazi sits in Kazi. The firm's bookkeeper has to re-key it into Xero. This is the #1 reason a small accounting practice rejects Kazi during demo.
- **Payment reconciliation.** Today a payment received in the firm's bank account → matched in Xero → never reaches Kazi's AR aging. The "unpaid invoices" report in Kazi is permanently stale within 24 hours.
- **OAuth2 in `OrgIntegration`.** Today `OrgIntegration` + `SecretStore` model API-key credentials only. Xero is OAuth2 with a refresh-token flow; the credential model needs a refresh-token + token-expiry shape, plus a re-auth UX when refresh fails.
- **Sync observability.** Today there is no "did this invoice make it to Xero" UI. A failed push is silent. Bookkeepers need a sync-status surface or they will not trust the integration.

**Founder decisions that constrain this phase** (2026-05-03 ideation, this conversation):

- **Xero only for v1.** Sage Pastel is deferred to a later phase. One platform done well > two stubbed.
- **One-way invoice + customer push** (Kazi → Xero). No bidirectional sync. No conflict resolution. No "who wins" rules.
- **One-way payment pull** (Xero → Kazi). Payments seen in Xero update Kazi's `PaymentEvent` ledger so AR aging is current.
- **Time entries do NOT sync.** They stay in Kazi as the system of record.
- **Dedicated `AccountingSyncService`.** Not routed through Phase 37 rule engine.
- **BYOAK-style.** Each tenant connects their own Xero org via OAuth2; no platform-mediated multi-tenant Xero app account aggregation.
- **Trust accounting stays out.** Section 86 trust ledgers must not flow to Xero — hard guard, fails closed, audit event on every refusal.
- **One-time customer import from Xero on first connect.** Read-only migration tool, not ongoing bidirectional sync. After import, the customer record lives in Kazi and is push-only thereafter.
- **No PlanTier.** Per the strategic "no plan-tier subscriptions" decision (2026-04-11), there is no Starter/Pro gating. Connect-Xero is gated **only** on a new capability `INTEGRATION_MANAGE` (or the existing equivalent — Phase 41/46). `PlanTier`, `@RequiresPlan`, `<PlanGate>` must **not** be reintroduced.
- **Capability-gated.** Connect / disconnect / view-sync-status / manually-reconcile each map to capabilities, not plans.

## Objective

Ship a tenant-connectable, OAuth2-based **Xero integration** that pushes Kazi invoices + customers to Xero on approval, pulls payment status from Xero on a schedule, surfaces sync state in the UI, and refuses (with an auditable explanation) to leak trust-accounting data. Implement everything against the existing Phase 21 `AccountingProvider` port plus a new sibling **payment-pull port**, with all sync orchestration owned by a new dedicated `AccountingSyncService` — *not* the Phase 37 rule engine.

## Constraints & Assumptions

- **No new accounting provider abstraction beyond what Phase 21 ships.** Reuse `AccountingProvider`, `InvoiceSyncRequest`, `CustomerSyncRequest`, `LineItem`, `AccountingSyncResult`. Add **one** new method or one new sibling port (`AccountingPaymentSource`) for payment pull — see Section 3 — but no broader redesign.
- **OAuth2 is additive to `OrgIntegration` + `SecretStore`.** Add fields for refresh-token, access-token-expiry, and Xero-tenant-id (Xero's connection ID, not Kazi's tenant). API-key based providers continue to work unchanged.
- **The Xero adapter is the only real adapter shipped.** `NoOpAccountingProvider` stays as the no-op fallback. There is no Sage Pastel / QuickBooks adapter in this phase. Do not stub them — dead code only.
- **Sync is push, never poll, for outbound.** Domain event (`InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoiceVoidedEvent`, `CustomerCreatedEvent`, `CustomerUpdatedEvent`) → `AccountingSyncService.enqueue(...)` → background sync worker drains the queue. No cron-triggered "scan all invoices" jobs.
- **Sync is poll, never push, for inbound payments.** A `SCHEDULED`-style worker (every 15 minutes by default, tenant-configurable down to 5 minutes) calls Xero's "invoices modified since X" endpoint, finds invoices that moved to `PAID` in Xero, writes a `PaymentEvent` of source=`XERO_RECONCILE` against the matching Kazi invoice, transitions the Kazi invoice to `PAID`. **Do NOT reuse Phase 70's `SCHEDULED` automation trigger** — it is owned by a different subsystem and conflating them couples sync to the rule engine. Use a plain Spring `@Scheduled` worker on the `AccountingSyncService`.
- **Idempotency is mandatory.** Every push to Xero carries a Kazi-side `external_reference` (e.g. `KAZI-INV-{uuid}`); every Xero invoice the adapter creates is annotated with this reference. Re-pushing an already-pushed invoice updates the Xero record (PUT/POST-with-reference), it never creates a duplicate. Pull-side: `PaymentEvent`s are deduplicated by `(invoice_id, xero_payment_id)`.
- **Retry with exponential back-off + dead-letter.** Transient failures (5xx, 429, network) retry with back-off (1m, 5m, 15m, 1h, 6h). After max retries, the sync entry moves to a `DEAD_LETTER` state visible in the reconciliation UI, with a manual "retry" action. **Never retry on 400-class errors** (validation failures); these go straight to `DEAD_LETTER` so the user sees them.
- **Rate limits respected.** Xero rate-limits at 60 calls/minute and 5000 calls/day per tenant connection. The sync worker honours `X-Rate-Limit-Remaining` and `Retry-After` headers and back-pressures the queue when limits are tight.
- **Trust-accounting guard is non-negotiable.** Any invoice with `is_trust_invoice=true`, any invoice with line items linked to trust accounts, any customer with active trust balances → push is refused, audit event emitted, sync entry tagged `BLOCKED_TRUST_BOUNDARY`. The guard runs before the adapter is called. The guard cannot be bypassed via UI or API.
- **No Xero webhook receiver in v1.** Xero supports outbound webhooks for "invoice updated" events, but webhook delivery is unreliable and requires public HTTPS endpoint configuration per tenant. Polling every 15 minutes is sufficient for v1; webhooks are a Phase 72+ optimisation.
- **No invoice line-level reconciliation.** If the Xero side splits a Kazi invoice into multiple Xero invoices (or merges multiple), Phase 71 marks the Kazi invoice `RECONCILE_DRIFT` and surfaces the issue in the UI for manual handling. Auto-merge / auto-split logic is Phase 72+.
- **Currency is org-default only.** The Kazi `OrgSettings` currency (typically ZAR for SA tenants) is what gets pushed. Multi-currency invoices (Phase 19 reporting touched this; full multi-currency invoicing is a separate epic) are out of scope — push is refused with a clear error.
- **Tax codes are mapped, not invented.** A new `accounting_tax_code_mapping` table maps Kazi tax modes (`STANDARD_15`, `ZERO_RATED`, `EXEMPT`, `OUT_OF_SCOPE`) to the tenant's chosen Xero tax codes (`OUTPUT2`, `ZERORATEDOUTPUT`, `EXEMPTOUTPUT`, `NONE`). Defaults are pre-seeded for ZA. The mapping is editable in the integration settings UI.
- **Disbursements VAT split survives the push.** SA legal disbursements (zero-rated pass-throughs vs standard-rated) are already separate `InvoiceLine` records in Phase 10. They map to separate Xero line items with their respective tax codes — this is data, not new logic.
- **Schema-per-tenant only** (ADR-T001). All new tables (`accounting_sync_entry`, `accounting_tax_code_mapping`, `accounting_xero_connection`) live under `tenant/`. No global tables. One Flyway migration `V121` (Phase 70 lands V120).
- **Capability-gated, not plan-gated.** Capabilities introduced or reused: `INTEGRATION_MANAGE` (existing — connect/disconnect, settings), `INTEGRATION_VIEW_SYNC_STATUS` (new — view sync entries, retry from dead-letter), `FINANCIAL_RECONCILE` (new — manually mark a Kazi invoice paid based on a Xero record where automatic match failed). Capability seeding follows Phase 41/46 patterns.

---

## Section 1 — `XeroAccountingProvider` Adapter

### 1.1 Adapter shape

New package `backend/.../integration/accounting/xero/`:

- `XeroAccountingProvider implements AccountingProvider` — `providerId() = "xero"`. Implements `syncInvoice`, `syncCustomer`, `testConnection`. Each method translates Kazi DTOs → Xero API payloads, calls `XeroApiClient`, translates response → `AccountingSyncResult`.
- `XeroApiClient` — thin HTTP client (`RestClient`) wrapping the Xero `api.xro/2.0/` REST surface. Owns: bearer-token attachment, refresh-on-401, `Xero-tenant-id` header, rate-limit observance (read response headers, surface as exception with `retryAfter` Duration), JSON (de)serialisation. Methods: `createOrUpdateInvoice`, `createOrUpdateContact`, `getInvoicesModifiedSince`, `getInvoice`, `validateConnection`.
- `XeroOAuthService` — OAuth2 flow: authorisation URL builder, code-exchange, refresh-token rotation. Stores tokens in `SecretStore` keyed by `OrgIntegration.id` + a fixed key prefix (`xero:access`, `xero:refresh`).
- `XeroInvoicePayloadMapper`, `XeroContactPayloadMapper` — pure functions: Kazi `InvoiceSyncRequest` → Xero invoice JSON; `CustomerSyncRequest` → Xero contact JSON. Tax-code lookup goes through `AccountingTaxCodeMappingService`.

### 1.2 Connection metadata

New tenant-scoped table `accounting_xero_connection`:

- `id` (uuid PK)
- `org_integration_id` (FK to `OrgIntegration.id`, unique)
- `xero_tenant_id` (Xero's UUID for the connected Xero org — distinct from Kazi tenant)
- `xero_org_name` (display)
- `connected_by_member_id`
- `connected_at`
- `last_token_refresh_at`
- `access_token_expires_at`
- `scope` (granted scopes list, persisted JSON or comma-string)
- `status` (`CONNECTED`, `REFRESH_FAILED`, `REVOKED`)
- `disconnected_at` (null until disconnected)

Refresh tokens themselves live in `SecretStore`, never in this table.

### 1.3 OAuth2 lifecycle

Three new endpoints on `IntegrationController` (or a new `XeroIntegrationController` if the cardinality stays Xero-specific):

- `GET /api/integrations/xero/connect` — returns `{ authUrl, state }` for the front-end to open Xero's consent screen.
- `GET /api/integrations/xero/callback?code=...&state=...` — completes the OAuth handshake, persists `accounting_xero_connection`, writes tokens to `SecretStore`, emits audit event `integration.xero.connected`.
- `DELETE /api/integrations/xero/connection` — revokes Xero-side connection, marks row `REVOKED`, audit event `integration.xero.disconnected`. Existing sync entries are left as historical record.

Token refresh is automatic and silent in `XeroApiClient` on `401`. After three consecutive refresh failures the connection moves to `REFRESH_FAILED`, the integration card surfaces a "Reconnect required" banner, and outbound sync pauses (entries queue but do not push).

---

## Section 2 — `AccountingSyncService` (Dedicated Sync Engine)

### 2.1 Sync entry entity

New tenant-scoped table `accounting_sync_entry`:

- `id` (uuid PK)
- `entity_type` (`INVOICE`, `CUSTOMER`, `PAYMENT_PULL`)
- `entity_id` (uuid — references the Kazi invoice / customer / invoice-being-reconciled)
- `provider_id` (string, "xero")
- `direction` (`PUSH`, `PULL`)
- `state` (`PENDING`, `IN_FLIGHT`, `COMPLETED`, `FAILED_RETRYING`, `DEAD_LETTER`, `BLOCKED_TRUST_BOUNDARY`, `RECONCILE_DRIFT`)
- `attempt_count` (int)
- `next_attempt_at` (timestamp)
- `last_error_code` (string nullable — "RATE_LIMITED", "VALIDATION_FAILED", "AUTH_EXPIRED", "TRUST_BOUNDARY", "MULTI_CURRENCY", "DRIFT_DETECTED", etc.)
- `last_error_detail` (text nullable)
- `external_reference` (Kazi-side dedup key, e.g. `KAZI-INV-{uuid}`)
- `external_id` (Xero-side ID once known)
- `created_at`, `updated_at`, `completed_at`

Indexed on `(state, next_attempt_at)` for the worker drain query, and on `(entity_type, entity_id)` for "what's the sync status of this invoice" lookups.

### 2.2 Service surface

`AccountingSyncService` (Spring `@Service`):

- `enqueueInvoicePush(UUID invoiceId, SyncTrigger trigger)` — called by event listener on `InvoiceApprovedEvent` / `InvoiceSentEvent` / `InvoiceVoidedEvent`. Idempotent: re-enqueue of an `IN_FLIGHT` or `PENDING` entry is a no-op (replaces with newer payload). Trust-boundary check happens here.
- `enqueueCustomerPush(UUID customerId, SyncTrigger trigger)` — called on `CustomerCreatedEvent` / `CustomerUpdatedEvent` (only when fields Xero cares about change — name, email, address, tax-id).
- `pollPaymentsForConnection(UUID orgIntegrationId)` — called by the scheduled worker. Calls `getInvoicesModifiedSince(lastPollAt)`; for each invoice that moved to `PAID` in Xero and matches a Kazi invoice via `external_reference`, writes a `PaymentEvent` and transitions the Kazi invoice. Returns sync summary.
- `retryFromDeadLetter(UUID syncEntryId)` — manual retry from UI (capability-gated `INTEGRATION_VIEW_SYNC_STATUS`). Resets attempt_count, sets next_attempt_at = now.
- `getSyncSummary()` — for the integration card: counts by state, oldest pending, most-recent completed.

### 2.3 Worker

`AccountingSyncWorker` — Spring `@Scheduled(fixedDelay = 30_000)`:

- Drains up to N entries (default 25) where `state IN ('PENDING', 'FAILED_RETRYING') AND next_attempt_at <= now`.
- For each: marks `IN_FLIGHT`, looks up the right `AccountingProvider` for the entity's `OrgIntegration`, calls `syncInvoice` / `syncCustomer`, classifies the result, updates state.
- Retry policy: 5 attempts at 1m, 5m, 15m, 1h, 6h offsets. After attempt 5 → `DEAD_LETTER`.
- Tenant-aware: the worker iterates active tenants, sets `RequestScopes.TENANT_ID`, drains per-tenant. Per-tenant rate limits are honoured.
- A separate `@Scheduled(fixedDelay = 900_000)` (15-minute) worker invokes `pollPaymentsForConnection` for every `CONNECTED` Xero connection.

### 2.4 Event integration

New event listener `AccountingSyncEventListener` subscribes to the relevant invoice + customer domain events and calls into `AccountingSyncService.enqueue*`. The listener is the **only** automated push trigger; nothing else (no controller, no rule engine action) calls `enqueue*`.

Manual push for one-off "force resync this invoice" lives behind capability `INTEGRATION_VIEW_SYNC_STATUS` on a `POST /api/integrations/sync/{entryId}/retry` endpoint (or a "force resync" action on the invoice detail page that creates a new sync entry — pick one shape, document the choice).

---

## Section 3 — Payment Pull (Xero → Kazi)

The existing `AccountingProvider` port has no payment-pull contract. Add **one** sibling port rather than overloading the existing one:

```java
public interface AccountingPaymentSource {
  String providerId();
  List<ExternalPaymentEvent> getPaymentsModifiedSince(Instant since);
}

public record ExternalPaymentEvent(
    String externalInvoiceReference,  // matches Kazi-side `external_reference`
    String externalPaymentId,         // Xero payment ID
    BigDecimal amount,
    String currency,
    Instant paidAt,
    PaymentStatus status              // PAID, PARTIALLY_PAID, VOIDED
) {}
```

`XeroAccountingProvider implements AccountingProvider, AccountingPaymentSource`.
`NoOpAccountingProvider` returns an empty list.

When `pollPaymentsForConnection` runs:

1. Calls `provider.getPaymentsModifiedSince(connection.lastPollAt)`.
2. For each result: looks up Kazi invoice via `external_reference` mapping. If not found → log, skip (it's a Xero-native invoice, not ours).
3. If found and the Kazi invoice is not yet `PAID` → writes a `PaymentEvent` with `source=XERO_RECONCILE`, transitions invoice to `PAID`, emits `InvoicePaidEvent` (which Phase 6 audit + Phase 6.5 notifications already consume).
4. If amounts don't match within 1 cent → invoice goes `RECONCILE_DRIFT`, surfaces in UI, no auto-transition. Manual reconcile (capability `FINANCIAL_RECONCILE`) closes it.
5. Updates `connection.lastPollAt`.

---

## Section 4 — Trust Accounting Guard

`TrustBoundaryGuard` — pure Java service (no LLM, no AI):

- Inputs: an `Invoice` plus its lines and customer.
- Outputs: `TrustBoundaryDecision { allowed, reason }`.
- Refuses if **any** of:
  - `Invoice.isTrustInvoice == true` (legal-za field from Phase 60)
  - Any `InvoiceLine.sourceAccount` references a trust account (`TrustAccount` entity)
  - The customer has any active trust balance (`TrustLedger` non-zero open balance)
- The `AccountingSyncService.enqueueInvoicePush(...)` calls the guard **before** persisting the sync entry. A refused invoice gets an entry written with `state=BLOCKED_TRUST_BOUNDARY` and `last_error_code=TRUST_BOUNDARY` so it appears in the UI, and an audit event `integration.xero.push_blocked_trust` is emitted with the full reason. The entry is never auto-retried — it is a permanent record of the refusal.
- Frontend integration: invoice detail page shows a passive notice "Not pushed to Xero — trust-related invoice" with the audit-event link. No UI action exists to bypass the guard.

---

## Section 5 — One-Time Customer Import

When a Xero connection is first established:

- The integration UI offers a "Import existing customers from Xero" flow (capability-gated, owner-initiated).
- Backend endpoint `POST /api/integrations/xero/import-customers` paginates Xero contacts, dedups against existing Kazi customers (by email, then by name+tax-id), creates new `Customer` records in `PROSPECT` state with `external_reference` set to the Xero contact ID and tagged `IMPORTED_FROM_XERO`.
- Returns a summary `{ created, skipped_duplicate, skipped_no_email, total }`.
- This endpoint is run **once** per connection — subsequent runs are blocked. Re-running requires disconnecting and reconnecting.
- Imported customers are immediately push-eligible from Kazi → Xero (no merge logic needed; the `external_reference` lets the adapter recognise its own contact and PUT-update rather than POST-create).

There is **no** ongoing customer pull. Once imported, Kazi is the source of truth.

---

## Section 6 — Tax Code Mapping

New tenant-scoped table `accounting_tax_code_mapping`:

- `id`, `provider_id` (`"xero"`), `kazi_tax_mode` (`STANDARD_15`, `ZERO_RATED`, `EXEMPT`, `OUT_OF_SCOPE`, `STANDARD_OTHER`), `external_tax_code` (string — Xero tax code), `display_label` (string), `is_default` (bool).

Pre-seeded defaults for ZA on first connect:

| Kazi mode | Xero code (default) |
|---|---|
| STANDARD_15 | OUTPUT2 |
| ZERO_RATED | ZERORATEDOUTPUT |
| EXEMPT | EXEMPTOUTPUT |
| OUT_OF_SCOPE | NONE |

UI on the Xero settings page lets the user override per row from a dropdown populated by Xero's `TaxRates` API.

---

## Section 7 — Frontend

### 7.1 Integration card (extends Phase 21 settings UI)

`/settings/integrations/xero` — replaces the no-op state for Xero. Sections:

- **Connection status** — `Connected to {xero_org_name}` / `Reconnect required` / `Not connected`. Connect button (Xero brand-compliant), Disconnect button (capability-gated, confirmation dialog).
- **Sync summary** — counts by state (`PENDING`, `IN_FLIGHT`, `COMPLETED last 24h`, `DEAD_LETTER`, `BLOCKED_TRUST_BOUNDARY`, `RECONCILE_DRIFT`). Click a count → navigates to the filtered sync log.
- **Tax code mapping** — table editor; one row per Kazi tax mode.
- **One-time import** — button (visible until consumed), summary after run.
- **Settings** — payment poll interval (5 / 15 / 30 / 60 minutes), default invoice "send to Xero on" trigger (`APPROVED` vs `SENT`), connection metadata (Xero org name, scopes, last refresh).

### 7.2 Sync log page

`/settings/integrations/xero/sync-log` — paginated list of `accounting_sync_entry` rows, filterable by state and entity type. Each row: entity link, state badge, attempts, last error, action menu (`Retry from dead letter`, `View audit trail`, `Open in Xero` for completed entries). Capability-gated `INTEGRATION_VIEW_SYNC_STATUS`.

### 7.3 Invoice detail integration

The invoice detail page (Phase 10 frontend) gains a small **"Xero status"** chip:

- Not synced (no sync entry yet).
- Pending (entry exists, not yet pushed).
- Synced — shows Xero invoice ID + "Open in Xero" link.
- Failed — shows last error + "Retry" action (capability-gated).
- Blocked (trust boundary) — passive label with audit link.
- Reconcile drift — passive label with manual-reconcile action (capability `FINANCIAL_RECONCILE`).

### 7.4 Customer detail integration

A subtler "Xero contact: {id}" line appears on the customer detail page if a successful customer push has occurred. No retry UI on customer level — re-push happens automatically on next change.

---

## Section 8 — Audit, Notifications, Events

- **Audit events (Phase 6)**: `integration.xero.connected`, `integration.xero.disconnected`, `integration.xero.refresh_failed`, `integration.xero.invoice_pushed`, `integration.xero.invoice_push_failed`, `integration.xero.customer_pushed`, `integration.xero.payment_reconciled`, `integration.xero.push_blocked_trust`, `integration.xero.reconcile_drift`, `integration.xero.dead_letter`, `integration.xero.manual_retry`, `integration.xero.customers_imported`.
- **Notifications (Phase 6.5)**: opt-in per member. Default-on for `OWNER` role: `xero.refresh_failed`, `xero.dead_letter` (digest, daily). Default-off for everyone: `xero.invoice_pushed`, `xero.payment_reconciled` (these would be too noisy).
- **Domain events (consumed)**: `InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoiceVoidedEvent`, `CustomerCreatedEvent`, `CustomerUpdatedEvent`.
- **Domain events (emitted)**: `XeroConnectionEstablishedEvent`, `XeroConnectionRevokedEvent`, `XeroSyncEntryCompletedEvent`, `XeroSyncEntryDeadLetteredEvent`, `XeroPaymentReconciledEvent`. These are emitted *after* state changes commit so other subsystems (notifications, dashboards) can subscribe without coupling to the sync internals.

---

## Section 9 — Out of Scope (Explicit)

To keep the phase tractable:

- **Sage Pastel adapter.** Phase 72+.
- **QuickBooks / Zoho Books / Wave adapters.** Indefinite.
- **Bidirectional customer sync.** Permanent decision per founder. One-way push is the model.
- **Bidirectional invoice sync** (i.e. invoices created in Xero appearing in Kazi). Permanent decision.
- **Time-entry → Xero billable expense** sync. Permanent — time stays in Kazi.
- **Multi-currency invoice push.** Refused with clear error in v1; multi-currency invoicing is a separate epic.
- **Automatic split/merge when Xero side restructures the invoice.** Drift is surfaced for manual handling.
- **Xero outbound webhooks.** Polling is sufficient for v1.
- **Phase 37 rule action `INVOKE_ACCOUNTING_SYNC`.** Not in v1; the dedicated service is the only sync entry point.
- **AI-assisted reconciliation** (Compliance Assistant matching ambiguous payments). Phase 72+ once the Phase 70 specialist framework has more mileage.
- **Per-tenant rate-limit reporting / cost dashboards.** Out of scope; rate-limit observance is internal back-pressure only.
- **Xero file attachments** (pushing the rendered PDF invoice as an attachment to the Xero record). Nice-to-have, deferred.
- **VAT201-style reporting from Kazi.** Out — that's the accountant's job in Xero; Kazi feeds the data, doesn't replace the accountant.
- **Bulk re-sync tool** (e.g. "push every invoice from the last 6 months"). Deferred; the per-invoice "force resync" action is sufficient until a real need surfaces.
- **`PlanTier` reintroduction** in any form.

---

## Section 10 — ADR Topics to Address

- **ADR-272 — Xero-only adapter for the accounting-integration phase.** Decision rationale: API maturity, SA SME market share, OAuth2 standardisation. Sage Pastel deferred to a follow-up phase. Supersedes nothing; complements ADR-201 (`IntegrationGuardService`) and the Phase 21 BYOAK ADRs.
- **ADR-273 — One-way sync model (push invoices/customers, pull payments).** Permanent product decision. Records the trade-off (merge complexity vs. data freshness) and the founder's call.
- **ADR-274 — Dedicated `AccountingSyncService` instead of Phase 37 rule action.** Captures the retry/idempotency/rate-limit reasoning.
- **ADR-275 — OAuth2 augmentation of `OrgIntegration` + `SecretStore`.** New `accounting_xero_connection` table; refresh tokens in `SecretStore`; refresh-on-401 strategy.
- **ADR-276 — Trust-accounting hard guard against accounting export.** Records the §86 regulatory boundary; no bypass; audit-only refusal.
- **ADR-277 — Polling over webhooks for inbound payment reconciliation in v1.** Reasoning: webhook reliability, public-endpoint config burden, 15-minute SLO acceptable.
- **ADR-278 — Idempotent push via Kazi-side `external_reference` + Xero `Reference` field.** Records the dedup contract.
- **ADR-279 — Sibling `AccountingPaymentSource` port instead of overloading `AccountingProvider`.** Records the interface-segregation rationale.

(Final ADR numbers depend on what Phase 70 lands; sequence reserved at draft time.)

---

## Section 11 — Style and Boundaries

- **Backend conventions**: `backend/CLAUDE.md` rules apply. Constructor injection, no Lombok, `ProblemDetail` via semantic exceptions, controllers thin (one service call, no orchestration, no helpers, no business logic). All new code under `integration/accounting/xero/` and `integration/accounting/sync/`.
- **No Testcontainers.** Embedded Postgres only. Mock the Xero API at the `XeroApiClient` boundary using `@MockitoBean`; do not start a fake Xero HTTP server in tests. WireMock is acceptable if a `@TestConfiguration` keeps it isolated.
- **Frontend conventions**: `frontend/CLAUDE.md` rules apply. New routes under `frontend/app/(authenticated)/settings/integrations/xero/`. Reuse Phase 21 integration card components.
- **Tenant isolation**: every new entity is dedicated-schema-only (ADR-T001). The sync worker iterates tenants explicitly and binds `RequestScopes.TENANT_ID` per tenant — it never operates without a tenant context.
- **Schema-per-tenant migration**: V121 (one migration) for `accounting_xero_connection`, `accounting_sync_entry`, `accounting_tax_code_mapping`. No global migration.
- **Capability gates**: every new endpoint annotated with `@RequiresCapability`. Capabilities seeded in Phase 41/46 fashion.
- **Slice budget**: stay within 6–10 files / ~800 LOC per slice. Migration + entities + repositories in one slice; service + worker in another; OAuth + adapter in another; etc. Expected ~10 epics or fewer, ~14–18 slices total.
- **PR shape**: one slice = one PR; CodeRabbit review + reviewer subagent on every PR; full `./mvnw verify` clean before merge per CLAUDE.md quality gates.
- **No PlanTier.** Repeat: do not reintroduce `PlanTier`, `@RequiresPlan`, `<PlanGate>`. Use capabilities only.

---

## Next Step

Run `/architecture requirements/claude-code-prompt-phase71.md` to generate the phase-71 architecture doc and ADRs.
