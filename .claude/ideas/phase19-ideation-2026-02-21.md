# Phase 19 Ideation — Reporting & Data Export
**Date**: 2026-02-21

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- Reporting is vertical-agnostic — every professional services vertical exports timesheets and invoices

## Decision Rationale
Founder explored leads management but agreed to shelve it after discussion:
1. Leads management is too vertical-specific (conflict checks for legal, AML for accounting, simple pipeline for agencies) — poor fork reuse
2. Existing Customer lifecycle (PROSPECT + checklists) partially covers intake vetting
3. Add-on subscription angle is weak — leads management doesn't monetize well as an upsell

**Chosen**: Reporting & Data Export. Key reasoning:
1. All data already exists (time entries, invoices, profitability, customers) — pure aggregation layer
2. Read-only phase = zero risk to existing functionality
3. Natural premium tier candidate ("3 reports on Starter, unlimited on Pro")
4. Foundation for future report types without code deployment (template-driven)
5. Founder wanted a smaller-than-normal phase to keep momentum

## Key Design Preferences (from founder)
1. Template engine (Thymeleaf) for report rendering — report layouts as data, not code
2. Acknowledged Thymeleaf is dev-friendly but not customer-friendly — visual builder is a future phase
3. Three standard reports: Timesheet, Invoice Aging, Project Profitability
4. CSV + PDF export formats, HTML preview in-page

## Shelved Ideas
- **Leads management** — too vertical-specific, weak add-on value, existing PROSPECT lifecycle partially covers it
- **Customer Portal Frontend** — founder not ready yet, planned for a future phase
- **Integrations (BYOAK)** — testing subscriptions are a logistics headache right now

## Phase Roadmap (updated)
- Phase 18: Task Detail Experience (complete)
- Phase 19: Reporting & Data Export (requirements written)
- Phase 20+: Candidates — Customer Portal Frontend, Integrations (BYOAK), Resource Planning

## Architecture Notes
- **New entity**: `ReportDefinition` (slug, parameter_schema JSONB, column_definitions JSONB, template_body)
- **Reuses**: ThymeleafRenderingService + PdfRenderingService from Phase 12
- **Strategy pattern**: `ReportQuery` interface with slug-based dispatch
- **Seed pack**: "standard reports" pack (3 reports + templates), idempotent via slug
- **Export**: CSV direct from data (no template), PDF via Thymeleaf → OpenHTMLToPDF
- **Frontend**: Reports nav item, list page (cards by category), detail page (dynamic param form + results table + export buttons)
