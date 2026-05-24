# claude-for-legal-sa Integration Decision — 2026-05-23

## Context

User forked `anthropics/claude-for-legal` to `claude-for-legal-sa` (at `../claude-for-legal-sa`).
The fork adds a South African jurisdiction overlay: 47 statute YAML files, 23 topic overlay
markdown files, practice profile templates, and evaluation test cases across employment, commercial,
corporate, and product law. It's a Claude Code plugin system, not a standalone product.

## Decision: Path 1 — Knowledge Source, Not Integration

Three paths were evaluated:
1. **Knowledge source** (chosen) — consult the statute/topic data when building Kazi features.
   No runtime dependency, no YAML import pipeline, no MCP server bridging the two.
2. **Statute YAML as data source** — import the 47 YAML files as a reference dataset with a thin
   lookup service. Rejected for now: maintenance burden of Gazette-driven updates not worth it
   until Kazi has enough legal-vertical users.
3. **Keep separate** — no connection at all. Rejected: misses the opportunity to inform Kazi's
   AI prompts with specific legal knowledge.

## First Action: Prompt Refinement (done in this session)

Sharpened the two Phase 72 AI skill system prompts using claude-for-legal-sa knowledge:

### FICA Verification (`ai/skills/fica-verification/system.txt`)
- Added three CDD tiers (standard/enhanced/ongoing) with specific section references
- Added SA-specific document acceptance criteria (Smart ID, CIPC, trust deeds, CK forms)
- Added PEP and beneficial ownership triggers (s21B, 25% threshold, 2022 amendment)
- Added R25k cash threshold reporting (s28), R50m sanctions (s45C)
- Added 5-year record retention requirement (s22)
- Added RMCP obligation (s42)

### Matter Intake (`ai/skills/matter-intake/system.txt`)
- Added matter-type to statute mapping (8 practice areas with governing acts + thresholds)
- Added court jurisdiction guidance (Magistrate vs High Court routing)
- Added fee structure explanation (party-and-party vs attorney-and-client, contingency fee cap)
- Added conflict classification (absolute vs consentable, former client rules, screening guidance)
- Added risk flags (CCMA 30-day window, POPIA 72hr, prescription periods, Competition Commission)
- Added SA contract law fundamentals (no consideration, specific performance, Shifren principle)

## Future Opportunities

When building these future features, consult claude-for-legal-sa:
- **Contract review skill** → `commercial-legal/topics/` (6 files)
- **Employment compliance skill** → `employment-legal/topics/` (6 files, especially dismissal.md)
- **Fee note generator** → LSSA tariff knowledge + practice profile billing sections
- **Regulatory deadline tracker** → statute YAML `effective_from`/`effective_until` patterns
- **Trust accounting watchdog** → Attorneys Act s78, rule 54

## Key Source Files in claude-for-legal-sa
- Statutes: `jurisdictions/za/statutes/*.yaml` (47 files)
- Employment topics: `jurisdictions/za/employment-legal/topics/` (6 files)
- Commercial topics: `jurisdictions/za/commercial-legal/topics/` (6 files)
- Corporate topics: `jurisdictions/za/corporate-legal/topics/` (5 files)
- Practice profiles: `jurisdictions/za/*/practice-profile-template.md`
