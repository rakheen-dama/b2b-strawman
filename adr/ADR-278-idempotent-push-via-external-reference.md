# ADR-278: Idempotent Push via Kazi `external_reference` + Xero `Reference` Field

**Status**: Accepted

**Date**: 2026-05-03

**Context**: Phase 71's invoice push must be idempotent: re-pushing an already-pushed invoice (e.g. retry from dead-letter, force-resync, replay after a worker crash) must update the existing Xero record rather than create a duplicate. Duplicate invoices in a general ledger are a money-correctness bug, not a UX nuisance.

Idempotency requires a stable, Kazi-controlled identifier that survives retries and lets the adapter recognise its own previously-pushed records. Three candidate vehicles: Xero's human-readable `Reference` field on each invoice (free-text, indexed, visible in the Xero UI); a Kazi-side hash of the invoice payload; or a vendor-supplied idempotency-key header (Stripe-style — Xero does not currently support one).

**Options Considered**:

1. **Kazi `external_reference` (e.g. `KAZI-INV-{invoiceUuid}`) written into Xero's `Reference` field; on push, the adapter first searches Xero for an existing invoice with that `Reference`, then PUT-updates if found, POST-creates if not.** The Kazi-side `accounting_sync_entry` row also stores the resulting Xero `InvoiceID` in `external_id` for fast subsequent updates.
   - Pros: `Reference` is human-readable in the Xero UI — bookkeepers can find Kazi-originated invoices without our help.
   - Pros: Native Xero behaviour — `Reference` is searchable via `where=Reference="KAZI-INV-…"`.
   - Pros: Survives Xero-side renumbering: even if Xero re-issues `InvoiceID` (it doesn't, but defensively), the `Reference` round-trip stays intact.
   - Pros: After the first push, we cache `external_id` on the sync entry, skipping the search on subsequent updates.
   - Cons: Two API calls on the very first push (search + create). One on subsequent updates (PUT by `external_id`).

2. **Hash-based dedup.** Store a `payload_hash` on the sync entry; before push, look up Xero by computing the same hash. Only push if the hash isn't already in Xero.
   - Pros: Pure Kazi-side logic, no Xero search.
   - Cons: Xero has no native "search by arbitrary hash" — we'd have to embed the hash into a Xero field anyway, at which point we may as well embed the human-readable reference.
   - Cons: Hash changes when invoice content changes; dedup keyed on hash means content edits create duplicate Xero records. Wrong semantics.

3. **Xero idempotency-key header.** Stripe-style "send the same key, get the same result." Xero does not currently support this header.
   - Pros: Native protocol-level idempotency if the vendor supports it.
   - Cons: Xero does not support it. Available only if/when Xero ships it.

**Decision**: Option 1 — Kazi `external_reference` (`KAZI-INV-{uuid}` for invoices, `KAZI-CUST-{uuid}` for customers) written into Xero's `Reference` field on the invoice / `AccountNumber` on the contact; cache resulting Xero `InvoiceID` / `ContactID` as `external_id` on the sync-entry row for direct subsequent updates.

**Rationale**: The `Reference` field is the natural Xero-side dedup vehicle: it's searchable, it's human-readable in the bookkeeper's UI, and it survives any Xero-side renumbering. The `KAZI-INV-{uuid}` shape is unambiguously Kazi-originated (no conflict with bookkeeper-set references), and the UUID component is a stable identity that lives forever on the Kazi invoice.

The two-API-call pattern on first push (search + create) is acceptable because the first push happens once per invoice, after which we cache the Xero `InvoiceID` on the sync entry and PUT-by-id thereafter. Re-push from dead-letter or force-resync uses the cached `external_id` directly; no second search.

For customers, the same pattern applies — `KAZI-CUST-{uuid}` in Xero's `AccountNumber` field. The one-shot customer importer creates Kazi customers with `external_reference = "XERO-CONTACT-{xeroContactId}"` so the inverse direction (imported Xero contact → push back to Xero) round-trips cleanly without duplicating.

The decision is contingent on a small spike in slice 71B: confirm against the Xero sandbox that `Reference` is reliably searchable and that POSTing an invoice with a `Reference` already used does *not* silently merge. The fallback (cache `external_id` aggressively, PUT-by-id always after first push) is robust either way.

**Consequences**:

- Positive: Idempotency by construction — re-pushing an invoice cannot create a duplicate.
- Positive: Bookkeeper UI in Xero shows `KAZI-INV-…` references, making provenance obvious.
- Positive: Force-resync and dead-letter retry both safely reuse the same flow.
- Positive: Invoice content edits (description, tax-code, line-item rewrites) update the existing Xero record rather than creating a new one.
- Negative: Two API calls on first push (search + create). Mitigated by caching `external_id` for the second push onward.
- Negative: If a bookkeeper *manually* edits the `Reference` field in the Xero UI to something other than `KAZI-INV-…`, our search will fail to find it and we'll create a duplicate on the next push. Mitigation: the integration UI documents "do not edit `Reference` for Kazi-originated invoices." Realistic-risk-low.
- Neutral: Customers use `AccountNumber` rather than `Reference` because Xero contacts use a different idempotency field. Documented in the contact mapper.

**Related**: [ADR-272](ADR-272-xero-only-accounting-adapter-v1.md), [ADR-274](ADR-274-dedicated-accounting-sync-service-not-rule-engine.md) (sync entry holds `external_reference` and `external_id`), [ADR-273](ADR-273-one-way-accounting-sync-permanent.md) (one-way push direction).
