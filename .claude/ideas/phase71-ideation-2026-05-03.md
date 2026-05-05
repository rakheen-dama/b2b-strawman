# Phase 71 Ideation — Xero Accounting Integration (One-Way Sync)
**Date**: 2026-05-03

## Lighthouse domain
Accounting-za vertical (commercial unlock — small SA accounting practices won't adopt without Xero push). Also a quality-of-life win for legal-za + consulting-za firms whose bookkeeper already lives in Xero.

## Decision
**Pivoted mid-conversation** from "deepen AI (Drafting + Compliance specialists)" to "ship Xero integration" — founder call. Phase 71 ships:
1. **Xero-only adapter** on the existing Phase 21 `AccountingProvider` port (`NoOpAccountingProvider` already in place; `XeroAccountingProvider` is the only real adapter).
2. **One-way push** for invoices + customers (Kazi → Xero). Permanent product decision.
3. **One-way pull** for payments (Xero → Kazi) via new sibling port `AccountingPaymentSource` — interface segregation, not overload.
4. **Dedicated `AccountingSyncService`** with own queue/retry/idempotency/rate-limit handling — explicitly NOT routed through Phase 37 rule engine.
5. **OAuth2 augmentation** of `OrgIntegration` + `SecretStore` with `accounting_xero_connection` table (Xero tenant id, token expiry, status); refresh tokens in `SecretStore`.
6. **Trust-accounting hard guard** (§86 regulatory boundary — fails closed, audit event, no bypass).
7. **One-time customer import** from Xero on first connect (read-only migration; Kazi is source of truth thereafter).
8. **Tax code mapping** table with ZA defaults (STANDARD_15→OUTPUT2 etc).
9. **Reconciliation UI** — sync log, drift handling, manual retry from dead-letter, Xero-status chip on invoice detail.

## Key design decisions
1. **Xero only.** Sage Pastel deferred to Phase 72+. Modern OAuth2 REST API + dominant SA SME share > stubbing two adapters.
2. **One-way sync, permanently.** No bidirectional. No conflict resolution. No "who wins" rules. Captured as ADR-273.
3. **Dedicated sync service, not Phase 37 rule action.** Retry/back-off/idempotency/rate-limit semantics don't fit `ActionExecution`. ADR-274.
4. **Polling, not webhooks, for inbound payments.** 15-min default poll. Webhooks deferred (delivery reliability + public endpoint config burden).  ADR-277.
5. **Sibling `AccountingPaymentSource` port** rather than fattening `AccountingProvider`. ADR-279.
6. **Trust accounting hard-blocked** from any sync; refusal is audited, never bypassable. ADR-276.
7. **Time entries do NOT sync.** Permanent — Kazi is system of record.
8. **Idempotency via Kazi-side `external_reference` + Xero `Reference` field.** ADR-278.
9. **No `PlanTier`** (repeat of Phase 70's strategic decision). Capability gates only: `INTEGRATION_MANAGE`, `INTEGRATION_VIEW_SYNC_STATUS` (new), `FINANCIAL_RECONCILE` (new).
10. **No Phase 70 `SCHEDULED` trigger reuse for payment poll** — explicitly use plain Spring `@Scheduled` to avoid coupling sync to the rule engine.
11. **Multi-currency push refused** with clear error in v1 (multi-currency invoicing is a separate future epic).

## Scope snapshot
- ~10 epics, ~14–18 slices
- 3 new tenant tables (V121): `accounting_xero_connection`, `accounting_sync_entry`, `accounting_tax_code_mapping`
- 1 new sibling port (`AccountingPaymentSource`)
- 1 real adapter (`XeroAccountingProvider`) + thin HTTP client + OAuth service + payload mappers
- 1 dedicated sync service + 2 scheduled workers (push drain @30s, payment poll @15m)
- 3 OAuth endpoints, 1 import endpoint, sync-log endpoints, force-resync endpoint
- New routes: `/settings/integrations/xero` + `/settings/integrations/xero/sync-log`
- Xero status chip on invoice detail; Xero contact line on customer detail
- Reuses Phase 21 ports, Phase 10 invoice events, Phase 25 `PaymentEvent`, Phase 6 audit, Phase 6.5 notifications, Phase 41/46 capabilities

## Explicitly parked
- Sage Pastel adapter (Phase 72+).
- QuickBooks / Zoho / Wave (indefinite).
- Bidirectional sync of any kind (permanent).
- Time-entry → Xero billable expense (permanent).
- Multi-currency invoice push.
- Automatic split/merge on Xero-side restructuring (drift surfaced for manual handling).
- Xero outbound webhooks.
- Phase 37 rule action `INVOKE_ACCOUNTING_SYNC`.
- AI-assisted reconciliation (Phase 72+ Compliance Assistant territory).
- Bulk re-sync tool / per-tenant cost dashboards.
- Xero file attachment push (rendered invoice PDFs).
- VAT201 reporting from Kazi (accountant's job).

## Phase roadmap after 71
- **Phase 72 candidates**: (a) **Drafting + Compliance specialists** (the AI work that got pivoted away from this phase — still a strong candidate). (b) **Sage Pastel adapter** + maybe Xero file-attachment push as a cleanup pass. (c) **Compliance Assistant** — would be the natural place to land AI-assisted payment reconciliation against the drift queue.
- **Founder pivot pattern observed** — AI deepening got bumped twice now (parked in Phase 70 backlog, pivoted away mid-Phase 71 ideation). Worth flagging that Drafting/Compliance might keep slipping; if it matters, schedule it explicitly rather than carrying it as "next phase candidate."
- **Integrations layer broader push** (calendar, Slack) still pending — could follow Sage Pastel naturally.

## Domain notes (SA accounting + integration)
- **Xero rate limits**: 60 calls/minute, 5000 calls/day per tenant connection. Worker honours `X-Rate-Limit-Remaining` and `Retry-After`.
- **Xero tax codes (ZA)**: `OUTPUT2` (15% standard), `ZERORATEDOUTPUT`, `EXEMPTOUTPUT`, `NONE`. Tenant can override the defaults via `TaxRates` API population.
- **§86 trust accounting**: trust ledgers must NEVER flow to operating-account general ledger. Hard guard, audit-only refusal, no bypass UI. The Compliance Assistant (deferred) will eventually watchdog this — Phase 71 ships it as deterministic Java.
- **Disbursements VAT split** (legal-za): zero-rated pass-throughs (sheriff/deeds/court) vs standard-rated (counsel/search/travel) already separate `InvoiceLine` rows in Phase 10 — they map to separate Xero line items with their respective tax codes. No new logic needed.
- **OAuth2 refresh-token rotation**: Xero rotates refresh tokens on each access-token refresh — `XeroOAuthService` must persist the new refresh token immediately or the connection breaks on next refresh.
- **`Xero-tenant-id` header** is per-connection, not per-call configurable — pinned to the tenant chosen at OAuth consent time.

## Next step
`/architecture requirements/claude-code-prompt-phase71.md`
