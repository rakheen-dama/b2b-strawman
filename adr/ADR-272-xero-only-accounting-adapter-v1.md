# ADR-272: Xero-only Accounting Adapter for v1

**Status**: Accepted

**Date**: 2026-05-03

**Context**: Phase 71 ships the first real `AccountingProvider` adapter (the production codebase to date has only `NoOpAccountingProvider`). The accounting-za vertical needs an external general-ledger integration to be commercially adoptable, and the legal-za + consulting-za verticals get a quality-of-life win when their bookkeepers already live in an external GL. The Phase 21 BYOAK integration plumbing (`OrgIntegration`, `SecretStore`, `IntegrationRegistry`) is provider-agnostic, so the question is which provider(s) to ship in v1.

The South African SME accounting-software market is dominated by two products: Xero (cloud-native, OAuth2, well-documented REST API) and Sage Pastel / Sage Business Cloud Accounting (older, mixed cloud/desktop, OAuth2 newer-product-only). QuickBooks Online has limited ZA presence. Per the founder's 2026-05-03 ideation: "one platform done well > two stubbed."

**Options Considered**:

1. **Xero only.** Ship a single `XeroAccountingProvider` v1 adapter; defer Sage Pastel and QuickBooks indefinitely.
   - Pros: Tight scope (10-slice budget); single OAuth2 flow to debug; single rate-limit / idempotency surface; can pour effort into the trust-boundary guard, sync observability, and tax-code mapping rather than parallel adapters.
   - Pros: Xero has the strongest SA market share among Kazi's ICP (small accounting firms, small law firms with bookkeepers).
   - Cons: Some prospects who use Sage Pastel cannot adopt Kazi until Phase 72+.

2. **Xero + Sage Pastel together.** Ship two adapters in v1.
   - Pros: Wider commercial reach.
   - Cons: Doubles the slice budget (two OAuth flows, two payload mappers, two rate-limit shapes, two test matrices). Risk: both adapters ship half-baked, neither is stable enough to demo.
   - Cons: Sage Pastel's modern API surface is less mature; we'd be debugging two unfamiliar systems concurrently.

3. **Xero + QuickBooks + Sage all three.** Comprehensive coverage.
   - Pros: Maximal market reach.
   - Cons: Triples slice budget. Cons: QuickBooks ZA market share does not justify the effort right now.
   - Cons: Realistic outcome: nothing ships in this phase; everything ships in Phase 72+ as half-baked stubs.

**Decision**: Option 1 — Xero only for v1.

**Rationale**: The slice budget for this phase is fixed at ~10 slices (`backend/CLAUDE.md` quality-gate rules + the founder's cycle-time discipline). A second adapter would consume slices that are better spent on the trust-boundary guard ([ADR-276](ADR-276-trust-accounting-hard-guard-export.md)), the outbox / retry semantics ([ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md)), and the sync-observability UI — all of which are *cross-provider* infrastructure and pay dividends when the Sage adapter does land.

Xero specifically: cloud-native, OAuth2 + refresh-token mature, REST + JSON, public sandbox, public rate-limit headers, public reference docs. The risk of "we ship the adapter and it works in dev but breaks against real Xero" is the lowest of the three options.

This decision does **not** preclude Sage Pastel; it sequences it. The `AccountingProvider` port is unchanged so a second adapter is a drop-in; the `IntegrationRegistry` already supports multiple slugs per domain. Phase 72+ can add Sage Pastel as a single self-contained slice once Phase 71's infrastructure is proven.

**Consequences**:

- Positive: Tight, deliverable scope. Phase 71 ships in 10 slices.
- Positive: All cross-provider infrastructure (sync engine, trust guard, tax-code mapping, sync log UI) is exercised against a real provider in v1, so Phase 72+ Sage adapter is a much smaller slice.
- Positive: Xero's published Java SDK *or* a hand-rolled `RestClient` adapter — implementer chooses; both fit the port.
- Negative: Sage Pastel prospects cannot adopt accounting-za vertical until Phase 72+. Mitigation: explicit "Sage support coming Phase 72" copy on integration card.
- Negative: The first concrete provider may bleed Xero-isms into the port records (`InvoiceSyncRequest`, etc.) if implementers aren't careful. Mitigation: the port is unchanged in this phase; any new fields go on the Xero-local payload mapper.
- Neutral: The `NoOpAccountingProvider` stays as the fallback for the `noop` slug.
- Neutral: Documentation copy in the integration UI explicitly lists Xero as the v1 provider; no stub cards for Sage / QuickBooks.

**Related**: [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (sync direction), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync engine), [ADR-275](ADR-275-oauth2-augmentation-org-integration.md) (OAuth shape), [ADR-201](ADR-201-integration-guard-service.md) (Phase 21 module-level gating), [ADR-T001](ADR-T001-schema-per-tenant-over-row-level-isolation.md) (tenant isolation).
