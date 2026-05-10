# ADR-272: Xero-Only Accounting Adapter for V1

**Status**: Accepted

**Context**:

Phase 71 delivers the first commercial accounting integration for Kazi. The platform currently ships a `NoOpAccountingProvider` as the default adapter behind the Phase 21 `AccountingProvider` port ([ADR-088](ADR-088-integration-port-package-structure.md)). The goal is to replace the no-op with a real adapter that pushes invoices and customers to an external accounting system and pulls payment status back.

South Africa's SME accounting software market is dominated by two players: Xero and Sage Pastel. Both offer APIs, but they differ substantially in API maturity, authentication model, developer documentation quality, and market trajectory. The Kazi platform targets small professional-services firms (5–30 people) in the accounting-za, legal-za, and consulting-za verticals. These firms overwhelmingly use cloud-based accounting software — Sage Pastel's legacy desktop product is declining in this segment while Xero's cloud product is growing. The founder has explicitly decided (2026-05-03 ideation session) that Phase 71 ships one adapter done well, not two adapters stubbed.

The decision here is which accounting platform to target first, and whether to ship one or multiple adapters in this phase. This complements [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md) (SecretStore reuse for integration credentials) and the Phase 21 BYOAK integration infrastructure ([ADR-088](ADR-088-integration-port-package-structure.md), `IntegrationRegistry`, `IntegrationGuardService`).

**Options Considered**:

1. **Xero-only adapter** — Implement `XeroAccountingProvider` as the sole real adapter in Phase 71. Sage Pastel, QuickBooks, and other providers are deferred to future phases. The `NoOpAccountingProvider` remains as the fallback for tenants with no configured accounting integration.
   - Pros:
     - Xero's REST API (`api.xro/2.0/`) is mature, well-documented, and stable. Endpoint shapes have been consistent since Xero API 2.0 launched. Error responses are structured JSON with actionable error codes.
     - Xero uses standard OAuth2 with PKCE for authentication — the same flow the broader industry uses. Token refresh is straightforward (POST to `/connect/token` with refresh token). No proprietary auth ceremony.
     - Xero dominates the cloud-accounting segment for SA SME firms in the target market (accounting practices, law firms, consulting firms). The firms that reject Kazi during demo because "it doesn't talk to my ledger" are predominantly Xero users.
     - Xero's rate limits (60 calls/minute, 5000/day per connection) are generous enough for the sync volumes Phase 71 targets (tens of invoices per day per tenant, not thousands).
     - Xero's contact and invoice models map closely to Kazi's `Customer` and `Invoice` entities. Tax codes, line items, currency, and due dates all have direct Xero equivalents. The payload mapping layer is thin.
     - Shipping one adapter means one set of integration tests, one OAuth flow, one set of error handling, one set of rate-limit logic. Quality is higher because the team's attention is not split.
   - Cons:
     - Tenants using Sage Pastel (still a meaningful segment, particularly older firms and firms with legacy desktop Pastel installs) cannot use the integration in Phase 71. They must wait for a future phase.
     - Platform perception risk: a Xero-only integration might signal to Sage Pastel users that Kazi is "a Xero shop" and not for them. Mitigated by messaging ("Sage Pastel support coming soon") and by the fact that the `AccountingProvider` port is provider-agnostic — adding Pastel later is additive, not a rewrite.
     - No fallback if Xero's API has an extended outage or breaking change. Mitigated by the dead-letter / retry infrastructure in `AccountingSyncService` and by the fact that a Xero outage affects all Xero integrations industry-wide, not just Kazi's.

2. **Xero + Sage Pastel dual adapter** — Ship both `XeroAccountingProvider` and `SagePastelAccountingProvider` in Phase 71. Each implements the same `AccountingProvider` port. Tenants choose their provider during integration setup.
   - Pros:
     - Covers the two dominant SA accounting platforms from day one. No tenant is left out based on their accounting software choice.
     - Forces the `AccountingProvider` port to be genuinely provider-agnostic from the start — any Xero-specific assumptions in the sync service would be caught immediately.
     - Stronger commercial pitch: "Kazi integrates with your accounting system" vs "Kazi integrates with Xero."
   - Cons:
     - Sage Pastel's API is less mature than Xero's. The Sage Business Cloud Accounting API has undergone multiple breaking changes, documentation gaps are common, and the OAuth2 implementation has provider-specific quirks (non-standard token lifetimes, inconsistent error shapes).
     - Two adapters double the integration testing surface. Each adapter needs its own payload mapper, its own error classifier, its own rate-limit handler. The test matrix grows combinatorially (2 providers x N sync scenarios x M error scenarios).
     - Two OAuth2 flows with different scopes, different consent screens, different token refresh behaviours. The frontend must handle both providers' brand guidelines and connection UX.
     - Development time roughly doubles for the adapter layer. Phase 71's slice budget (6–10 files, ~800 LOC per slice, ~14–18 slices) is already tight for one adapter. Two adapters would push the phase to 20+ slices or force quality compromises.
     - The Sage Pastel adapter would ship at lower quality than the Xero adapter because development attention is split. A mediocre Sage Pastel integration is worse than no Sage Pastel integration — it creates support burden and damages trust.

3. **QuickBooks adapter instead of Xero** — Target Intuit QuickBooks Online as the first adapter, given its global market share and well-documented API.
   - Pros:
     - QuickBooks Online has the largest global market share for cloud accounting. If Kazi ever expands beyond SA, QuickBooks is the natural first choice internationally.
     - QuickBooks API documentation is extensive, with SDKs in multiple languages (including Java). The OAuth2 flow is standard.
     - Strong developer ecosystem with sandbox environments for testing.
   - Cons:
     - QuickBooks' SA market share among the target segment (small professional-services firms) is negligible. SA firms use Xero or Sage Pastel. Building for QuickBooks first would serve almost no current or near-term Kazi tenants.
     - QuickBooks' tax model is US-centric (sales tax, not VAT). Mapping SA VAT codes (STANDARD_15, ZERO_RATED, EXEMPT) to QuickBooks tax codes requires additional configuration that Xero handles natively for SA.
     - The founder's decision is explicit: Xero first, targeting SA market. QuickBooks is deferred indefinitely.
     - QuickBooks' invoice model uses different terminology and structure (Items, SalesReceipt vs Invoice) that would require a thicker mapping layer.

**Decision**: Option 1 — Xero-only adapter for Phase 71. Sage Pastel is deferred to Phase 72+. QuickBooks is deferred indefinitely.

**Rationale**:

The decision is driven by three converging factors: market fit, API quality, and founder mandate.

Market fit is decisive. Kazi targets small SA professional-services firms. In this segment, Xero is the dominant cloud accounting platform. The firms that reject Kazi during demo because invoices cannot flow to their ledger are overwhelmingly Xero users. Building a Sage Pastel adapter first (or alongside) would not address the primary commercial blocker. The one-adapter decision is not about technical difficulty — it is about focusing engineering effort where it unlocks the most revenue.

API quality compounds the market argument. Xero's API is the most mature of the three options: standard OAuth2, stable REST endpoints, structured errors, predictable rate limits, and a contact/invoice model that maps closely to Kazi's domain entities. The `XeroAccountingProvider` adapter will be thinner and more reliable than a Sage Pastel equivalent because the API surface is better designed. This matters for a first integration — the sync service, retry logic, dead-letter handling, and trust-boundary guard are all new infrastructure. Building that infrastructure against a well-behaved API reduces the risk of conflating adapter bugs with sync-engine bugs.

The `AccountingProvider` port from Phase 21 ([ADR-088](ADR-088-integration-port-package-structure.md)) is provider-agnostic by design. Adding a Sage Pastel adapter in a future phase is additive: implement `SagePastelAccountingProvider`, register it with `@IntegrationAdapter(domain = ACCOUNTING, slug = "sage-pastel")`, and the `IntegrationRegistry` resolves it per tenant. No sync-service changes, no schema changes, no frontend changes beyond a new integration card. The Xero-only decision in Phase 71 does not create technical debt — it creates a proven integration path that future adapters can follow.

**Consequences**:

- Positive:
  - Engineering effort is concentrated on one adapter, yielding higher quality: thorough error handling, comprehensive rate-limit observance, well-tested payload mapping, and a polished OAuth2 connection UX.
  - The `AccountingSyncService`, `TrustBoundaryGuard`, and sync-entry infrastructure are validated against a well-behaved API, reducing the risk of architectural mistakes that would be expensive to fix later.
  - The adapter serves as a reference implementation for future providers. When Sage Pastel is built in Phase 72+, the developer can follow the `XeroAccountingProvider` as a template for structure, error handling, and test coverage.
  - Faster time-to-market for the primary commercial blocker (accounting-za firms rejecting Kazi because invoices stay in Kazi).

- Negative:
  - Sage Pastel users cannot use the accounting integration in Phase 71. These tenants see a "Coming soon" state on the integration card. Depending on market mix, this could affect 20–30% of the target segment.
  - Risk of Xero-specific assumptions leaking into the sync service despite the port abstraction. The `AccountingSyncService` must remain provider-agnostic — code reviews must watch for Xero-specific error codes, Xero-specific retry logic, or Xero-specific payload assumptions in the service layer.

- Neutral:
  - The `NoOpAccountingProvider` remains as the default adapter for tenants without a configured accounting integration, unchanged from Phase 21.
  - The `IntegrationRegistry` continues to resolve adapters by `(domain, slug)` per tenant. A Xero-connected tenant resolves `slug=xero`; all others resolve `slug=noop`. No registry changes needed.
  - Per [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md), the new Xero connection and sync tables live in tenant schemas. The adapter choice is tenant-scoped — one tenant on Xero does not affect another tenant's integration state.

- Related: [ADR-088](ADR-088-integration-port-package-structure.md) (integration port package structure), [ADR-098](ADR-098-payment-gateway-interface-design.md) (payment gateway interface — same port pattern), [ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md) (SecretStore reuse for credentials), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way sync model), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (dedicated sync service), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (schema-per-tenant isolation).
