# Phase 64 Ideation — Legal Vertical QA: Terminology, Templates & 90-Day Lifecycle
**Date**: 2026-04-06

## Decision
Phase 64 closes two UX gaps (terminology pack + matter templates), builds screenshot infrastructure, then validates the entire legal vertical via a 90-day lifecycle QA test plan. Runs after Phase 61; Phases 62/63 run after 64.

## Rationale
Phases 55 (legal foundations), 60 (trust accounting), and 61 (legal compliance) deliver the legal vertical's core functionality. But two gaps make the product feel generic rather than purpose-built:

1. **No terminology pack** — UI says "Project" not "Matter", "Invoice" not "Fee Note". The `legal-za` profile already references `terminologyOverrides: "en-ZA-legal"` but the file doesn't exist.
2. **No matter-type templates** — Creating a new matter starts blank. Law firms expect pre-populated task lists for litigation, estates, collections, commercial.

Without fixing these before QA, every screenshot looks like "generic SaaS with trust accounting bolted on" instead of "built for law firms."

## Key Design Decisions
1. **Conveyancing template omitted** — too many conditional paths (bond types, transfer scenarios, sectional title vs full title). Better as a fork-day feature.
2. **4 matter templates**: Litigation, Estates (deceased), Collections (debt recovery), Commercial (corporate/contract) — exercises widest surface area.
3. **4 client archetypes for QA**: Individual (litigation), Company (commercial), Trust (estates), Company (collections) — each exercises different legal modules.
4. **Dual screenshot strategy**: Playwright `toHaveScreenshot()` regression baselines (~50) + curated hero shots (~15) for blog/deck.
5. **Test firm**: "Mathebula & Partners" — 4-attorney general practice, Johannesburg. Users: Alice (Senior Partner), Bob (Associate), Carol (Candidate Attorney).

## Vertical Gap Assessment (Pre-QA)
- **Fully built**: Court calendar, conflict check, LSSA tariff, trust accounting (full ledger, reconciliation, interest, investments, Section 35), FICA compliance, legal custom field packs
- **Phase 61 (pending)**: §86(3)/(4) investment distinction (hard legal requirement), KYC verification (nice-to-have)
- **Not planned**: Matter closure workflow, dedicated disbursements module, smart deadline-to-calendar scheduling

## Next Step
`/architecture requirements/claude-code-prompt-phase64.md`
