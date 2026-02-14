# AI Features Ideation — Category A (Drudgery Removal)
**Date**: 2026-02-14

## Direction
- **Category A only** — "AI that removes drudgery." Founder explicitly dislikes Category B (chatbots, AI insights cards, predictive dashboards). Cringe-worthy.
- **Not a near-term phase** — comes after Document Templates (Phase 12) and possibly Customer Portal Frontend
- **Two features agreed as the first AI scope:**

### 1. Time Narrative Polish
- "Clean up for invoice" button on invoice draft screen
- Transforms cryptic time entry notes into professional client-facing descriptions
- Single LLM call per line item, or batch on invoice generation
- Example: `"call w/ J re: transfer"` → `"Telephone consultation with client regarding property transfer documentation"`
- Tiny scope, biggest perceived value

### 2. Smart Line Item Grouping
- When generating invoice from many time entries, LLM groups related entries into 4-5 summarized line items
- 47 entries → 5 professional descriptions with aggregated hours
- Needs review/edit UI for suggested groupings
- Medium scope

### AI Infrastructure
- Not decided yet — options: Anthropic (Claude), OpenAI, or provider-agnostic abstraction
- Server-side calls from Spring Boot backend
- To be decided when the phase is planned

## Parked Ideas
### Personalized Demo Generator (marketing tool, not product feature)
- Prospect describes their company → AI seeds a demo tenant with realistic data
- Playwright takes screenshots or records video of the app with personalized data
- Generates a custom demo walkthrough (PDF/HTML/video)
- Confidence: high (8/10) — all APIs exist, Playwright is straightforward
- Sequence: post-launch growth tool, not a phase — build when there's a product to demo
- Key risk: data realism (LLM prompt engineering) and Playwright selector maintenance

## Roadmap Context
- Phase 10: Invoicing (in-flight, ~30% done)
- Phase 11: Tags, Custom Fields & Views (planned)
- Phase 12: Document Templates & Generation
- Phase 13+: Customer Portal Frontend, AI Invoice Features, Reporting — order TBD
