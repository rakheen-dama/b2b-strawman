# Phase 63 Ideation — Custom Field Graduation (All Entities)
**Date**: 2026-04-04

## Decision
Promote ~21 custom fields across Customer, Project, Task, and Invoice to proper entity columns. Remove from field pack JSONs. No data backfill — new writes go to columns, old JSONB data stays as-is. Custom field system remains for genuinely custom fields.

## Rationale
Started as Customer-only graduation (8 fields). Founder expanded scope: "Not just customer fields — look at all custom fields. Which core entity fields can be moved into the domain as first-class values?" Full audit across all 4 entity types and all pack files revealed 21 fields that are structural — queried in services, required for invoicing, rendered in every template, present in all verticals.

**Founder's philosophy**: "Customer should be a strong domain — anything we make decisions on should be structural." Extended to all entities.

### Key Decisions
1. **`financial_year_end` promoted** — accounting-originated but DeadlineCalculationService reads it from JSONB on every run. Promoting gives proper LocalDate typing and query performance.
2. **`engagement_type` / `matter_type` unified as `work_type`** — "important field on which decisions are made." VARCHAR, not enum, because values differ per vertical.
3. **Structured address** (6 columns, not text blob) — "Text fields should only be used if really needed." Enables filtering by city/country, per-field validation.
4. **`tax_number` consolidates** common pack `tax_number` + accounting-za `vat_number` — single column.
5. **No data backfill** — "Porting old data is not important." Clean break.

### Counts by entity
- Customer: 13 new columns (address 6, contact 3, tax_number, registration_number, entity_type, financial_year_end)
- Project: 3 new columns (reference_number, priority, work_type)
- Task: 1 new column (estimated_hours — priority already structural)
- Invoice: 4 new columns (po_number, tax_type, billing_period_start, billing_period_end — payment_reference already structural)

### Pack cleanup
- common-invoice.json becomes empty → deleted
- common-customer "Contact & Address" group → removed entirely
- 6+ fields removed from accounting-za-customer, legal-za-customer
- engagement_type/matter_type removed from vertical project packs
- priority/estimated_hours removed from common-task

## Simplification Arc Context
Phase 62 (module gating) + Phase 63 (field graduation) = structural refinement. Not new features — making the product feel tighter, strengthening core domains, reducing cognitive load before scaling.

## Next Step
`/architecture requirements/claude-code-prompt-phase63.md`
