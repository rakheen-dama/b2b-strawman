# Phase 23 Ideation — Custom Field Maturity & Data Integrity
**Date**: 2026-02-25

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- This phase is vertical-agnostic — every fork benefits from robust custom fields and data validation

## Decision Rationale
Founder started with "integrations" but surfaced a deeper concern: **the system is too permissive**. Documents can be generated with missing fields, billable time logged without rates, and custom fields are opt-in per instance rather than enforced. Agreed that hardening the custom fields system is prerequisite work before accounting integrations (which would sync bad data outward).

Key motivations (from founder):
1. Too easy to produce misconfigured documents with missing fields
2. No enforcement — free-text heavy, no guardrails on what's required before generation
3. Custom fields are a foundation piece that every vertical fork depends on
4. Invoice custom fields are missing entirely (no `EntityType.INVOICE`)
5. Accounting integrations should come after data integrity is solid

## Key Design Preferences
1. **Auto-apply field groups**: founder wants retroactive apply assessed by architect (not ruled out, not committed — depends on complexity)
2. **Generation validation**: warnings on drafts, blocks on final/SENT — admin override for finals only
3. **Billable time warnings**: non-blocking, shown before submission in LogTimeDialog
4. **Conditional visibility**: single-field dependencies only (dropdown Y = value Z → show field X)
5. **Group dependencies**: one level, convenience not constraint (can remove auto-applied dependency group)
6. **Invoice custom fields**: no default pack — different forks will have different invoice fields

## Shelved Ideas
- **Accounting integrations (Xero/Sage)** — still planned, but after data integrity is solid. Likely Phase 24+
- **Email delivery (SendGrid)** — small effort, could be a mini-phase or bundled with accounting
- **Visual form builder** — future phase, explicitly out of scope

## Phase Roadmap (updated)
- Phase 22: Customer Portal Frontend (in progress — backend done, portal app remaining)
- Phase 23: Custom Field Maturity & Data Integrity (requirements written)
- Phase 24+: Candidates — Accounting sync (Xero), Email delivery, AI integration, E-signatures

## Architecture Notes
- Auto-apply flag on `FieldGroup` entity, retroactive apply TBD by architect
- `requiredContextFields` JSONB on `DocumentTemplate` for template-declared field requirements
- `INVOICE` added to `EntityType` enum + JSONB columns on invoices table
- `visibilityCondition` on `FieldDefinition` — simple `{dependsOnSlug, operator, value}` object
- `dependsOn` list on `FieldGroup` for one-level group dependencies
- Bug fixes: DATE validation, CURRENCY blankness, field type immutability
