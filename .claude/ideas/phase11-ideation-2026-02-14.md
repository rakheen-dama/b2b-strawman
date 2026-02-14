# Phase 11 Ideation — Tags, Custom Fields & Views
**Date**: 2026-02-14

## Lighthouse Domain
- **Target vertical**: South African / African small-to-medium law firms (2-20 fee earners)
- SA legal market underserved by modern SaaS (Ghostpractice, LegalSuite, spreadsheets)
- Founder wants overlap with other industries — foundation, not vertical-specific code

## Decision Rationale
Options considered: Reporting & Export, Customer Portal Frontend, Tags/Custom Fields/Views, Document Templates, Recurring Work & Retainers

**Chosen**: Tags, Custom Fields & Views (Phase 11), Document Templates (Phase 12)

**Why this order**:
- Custom fields are a force multiplier — every future phase (reporting, portal, templates) benefits from extensibility
- Document templates consume custom field values (e.g., `{{matter.custom.case_number}}`), so fields must exist first
- Customer Portal gets better with each phase shipped (invoicing, fields, templates all add portal features) — waiting is correct
- Reporting is premature without the extensibility layer that makes reports per-org useful

## Key Design Preferences (from founder)
1. **Field packs = Option A (platform-defined) + config-driven replacement** — not just org-defined-from-scratch. Ship common packs (address, tax number), let forks add domain packs. Orgs shouldn't start from zero.
2. **Saved views in scope** — if it doesn't add too much complication
3. **Tags as separate lightweight concept** alongside structured fields (agreed with recommendation)
4. **No blow-your-mind demo features** — foundation infrastructure > flashy features
5. **Fork-friendliness is paramount** — every domain should work across verticals with zero customization

## Phase Roadmap (emerging)
- Phase 10: Invoicing (in-flight)
- Phase 11: Tags, Custom Fields & Views
- Phase 12: Document Templates & Generation (consumes custom fields)
- Phase 13+: Candidates — Recurring Work/Retainers, Customer Portal Frontend, Reporting & Export

## SA Legal Domain Notes (for future vertical fork)
- Matter types: Litigation, Conveyancing, Family Law, Commercial, Collections
- Key fields: Case Number, Court, Filing Date, Opposing Counsel, Property Address, Transfer Duty, Bond Amount
- Trust accounting compliance (Legal Practice Act) — vertical-specific, not foundation
- LSSA tariff rates — vertical-specific rate card data
- Communication: heavily email/WhatsApp-based, email integration high value
