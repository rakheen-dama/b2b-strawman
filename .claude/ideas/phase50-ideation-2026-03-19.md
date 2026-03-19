# Phase 50 Ideation — Data Protection Compliance (POPIA / Jurisdiction-Aware)
**Date**: 2026-03-19

## Lighthouse Domain
Universal — every SA professional services firm (accounting, legal, consulting) handles personal information and must comply with POPIA. Not vertical-specific. Horizontal compliance infrastructure.

## Decision Rationale
Founder brought POPIA as the topic. Initial exploration went deep (PI discovery engines, consent engines, breach workflows, portal DSAR self-serve) before founder pulled back: "What's the simplest thing that meets legal obligations?"

### Key Insight: Both Layers Is One Layer
"Platform compliance" (DocTeams as operator) and "tenant-facing tools" (firms as responsible parties) turned out to be the same code. The data export that helps a firm respond to a client's access request IS the capability DocTeams uses for its own DSARs. Platform compliance = tenant tools + two legal documents (privacy policy, DPA). No separate module needed.

### What Got Cut
- **Consent engine** — most B2B processing is under contractual necessity or legitimate interest, not consent. Wrong legal basis for professional services.
- **PI discovery engine with annotations** — overkill. Schema is known. Targeted queries per entity type suffice.
- **Portal self-serve DSAR** — firms handle these manually. Portal intake adds complexity for a rare event.
- **Automated breach notification** — organizational process, not software. Simple incident log is enough.
- **Cross-border transfer docs** — af-south-1 hosting eliminates this for SA.

### What Stayed
Data export, anonymization (with financial preservation), retention policies, PAIA manual generation, processing register, DSAR tracking. ~5 epics, ~10-12 slices.

## Founder Preferences (Confirmed)
- Full phase (both DSAR/retention AND documentation)
- Staff-only DSAR intake
- Jurisdiction-aware design (POPIA first, GDPR/LGPD later as config)
- Simplest possible implementation — no over-engineering

## Global Data Protection Context
Discussion revealed nearly every major economy has a GDPR-equivalent (POPIA, LGPD, PDPA, DPA Kenya, NDPR Nigeria, etc.) with remarkably uniform core obligations. This reframed the phase from "POPIA compliance" to "Data Protection Compliance" with jurisdiction packs — same code, different regulatory templates per country. Same pattern as vertical profiles.

## Phase Roadmap (Updated)
- Phase 49: Vertical Architecture (in progress — frontend epics remaining)
- **Phase 50: Data Protection Compliance** (spec written)
- Phase 51 (candidate): Legal Modules — Trust Accounting (first real domain module)
