# Phase 28 Ideation — Document Acceptance (Lightweight E-Signing)
**Date**: 2026-02-27

## Lighthouse Domain
- Accounting/bookkeeping firms (unchanged)
- Engagement letter acceptance is a universal need — every firm does this via manual email today
- Competitive references: Ignition (click-to-accept proposals), PandaDoc (e-signatures), Practice Ignition (lightweight accept)

## Decision Rationale
Two options considered: (1) lightweight click-to-accept, (2) full e-sign provider integration (DocuSign/SignRequest).

Lightweight accept chosen because:
- Covers 100% of accounting firm engagement letter needs (IRBA requires evidence of agreement, not cryptographic signature)
- Covers POPIA consent agreements (proof of consent, not signature)
- FICA doesn't require signing — it's document collection (already handled by checklists)
- For legal: covers ~40% (engagement letters, general correspondence) but not property transfers or notarial acts requiring ECTA AES
- Full e-sign becomes Phase 32+ or fork-specific (legal vertical)
- Lightweight system doubles as fallback when no e-sign provider is configured

## Key Design Preferences
1. **Portal contacts only** — leverages existing magic-link auth, no parallel auth path
2. **Click + typed name** — "I, [name], accept this document." Stronger evidence of intent than click-only
3. **Auto-generate Certificate of Acceptance** — timestamped PDF with IP, user agent, name, document hash. This is what firms file as proof
4. **Manual remind only** — no auto-reminder scheduling (can add later)
5. **Configurable expiry** — org-level default (30 days) with per-request override
6. **Single signer** — no multi-signer, no counter-signature for v1

## Updated Phase Roadmap
- Phase 26: Invoice Tax Handling (in progress)
- Phase 27: Document Clauses (spec'd)
- **Phase 28: Document Acceptance** (requirements written)
- Phase 29: Accounting Sync (Xero + Sage)
- Phase 30: Verification Port + Checklist Auto-Verify
- Phase 31: Platform Self-Billing

## Scope Assessment
~6 epics, ~11 slices. Moderate phase. Builds heavily on existing infrastructure (portal, magic links, email delivery, PDF rendering, audit). The net-new surface area is the AcceptanceRequest entity, portal acceptance page, certificate generation, and status tracking UI.
