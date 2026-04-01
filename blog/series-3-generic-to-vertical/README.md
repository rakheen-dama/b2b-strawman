# Series 3: "From Generic to Vertical"

Building one codebase that serves multiple industries. The strategy, architecture, and pack system behind vertical SaaS.

## Posts

| # | Title | Status | Words |
|---|-------|--------|-------|
| 01 | [Why Build Generic Just to Fork It](01-why-build-generic-just-to-fork.md) | Draft | ~2,200 |
| 02 | [Compliance Packs](02-compliance-packs.md) | Draft | ~2,400 |
| 03 | [Document Templates Per Vertical](03-document-templates-per-vertical.md) | Draft | ~2,200 |
| 04 | [The Gap Report](04-the-gap-report.md) | Draft | ~2,300 |
| 05 | [Terminology Overrides](05-terminology-overrides.md) | Draft | ~2,100 |
| 06 | [South African Professional Services](06-south-african-market.md) | Draft | ~2,400 |

**Total: ~13,600 words across 6 posts**

## Series Arc

- **01**: The thesis. Why build the 80% generic foundation before the 20% vertical customization. The fork gap analysis.
- **02**: Technical deep-dive into compliance packs. FICA checklists, conditional items, lifecycle gating, auto-activation.
- **03**: Document templates with vertical pack seeding. Handlebars rendering, context builders, clauses, clone-and-edit.
- **04**: The QA methodology. 27 gaps found, severity categorization, prioritization framework. Reproducible for any vertical.
- **05**: The cheapest vertical customization — terminology overrides. Simple lookup, big impact on product perception.
- **06**: Market selection. Why South Africa, why professional services, why small markets first. Law firm gap analysis.

## Code Examples Source

All code examples are taken from the actual codebase:
- `vertical-profiles/accounting-za.json` — profile definition
- `compliance-packs/fica-kyc-za/pack.json` — FICA checklist
- `field-packs/accounting-za-customer.json` — custom fields (VAT, SARS, entity type)
- `template-packs/accounting-za/pack.json` — document templates
- `clause-packs/accounting-za-clauses/pack.json` — legal clauses
- `automation-templates/automation-accounting-za.json` — automation rules
- `request-packs/year-end-info-request-za.json` — request templates
- `frontend/lib/terminology-map.ts` — terminology overrides
- `AbstractPackSeeder.java` — vertical filtering logic

## Publishing Plan

- Frequency: Weekly for 6 weeks, then as-needed
- Launch after Series 1 and 2 have established audience (~Month 3)
- This series is the sales funnel for the template repo

## Before Publishing Checklist

- [ ] Verify all JSON pack examples match current codebase
- [ ] Add screenshots of the UI with accounting terminology active
- [ ] Verify gap report numbers and severity ratings
- [ ] Add diagrams (pack seeding flow, lifecycle state machine)
- [ ] Review SA regulatory claims for accuracy (FICA penalties, SARS deadlines)
- [ ] Add template repo link once public
