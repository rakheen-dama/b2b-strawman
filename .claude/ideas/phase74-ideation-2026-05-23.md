# Phase 74 Ideation — AI Intelligence Suite: Contract Review, Drafting & Compliance
**Date**: 2026-05-23

## Catalyst
Phase 72 AI infrastructure is fully operational (provider, firm profile, execution gates, cost metering, FICA + matter intake skills). Phase 73 matter detail redesign done. Founder wants to deepen AI — the skill wave that's been parked twice (Phases 70, 71). Also wants separate QA test plans for skill accuracy and full AI lifecycle.

## Decision
**Three skills: Contract Review, Template-Guided Drafting, On-Demand Compliance Audit.** Founder explicitly chose these over trust watchdog + fee note generator (my recommendation). The pick signals the founder values *intelligence and decision-support* over *operational automation* — review/draft/audit are about making better decisions, not saving keystrokes.

## Key design choices (founder-selected)
1. **Contract review → document-as-report** over inline annotations. Fits existing document system, no Tiptap annotation extension. Migration path to annotations in future.
2. **Drafting → template-guided** over freeform. Template as skeleton, AI fills variables + generates narrative. Safer, more controllable, leverages existing template system.
3. **Compliance audit → on-demand only** over scheduled sweeps. "Run audit" button on compliance dashboard. No scheduler complexity in v1.
4. **Two separate QA test plans**: (a) skill accuracy + execution flow (golden inputs → expected outputs, infrastructure checks), (b) full AI lifecycle scenario (browser-driven 30-day script).

## What was explicitly rejected
- Trust accounting watchdog (separate future skill)
- Fee note narrative generator (separate future skill)
- Regulatory monitor / Gazette integration (needs external data source)
- Inline document annotations (Tiptap extension scope too large)
- Freeform drafting (needs more firm profile maturity)
- Scheduled compliance sweeps (on-demand first)

## claude-for-legal-sa influence
Same session also decided to use claude-for-legal-sa as knowledge source (see separate ideation file). Contract review system prompt will incorporate SA legal framework from the fork's topic files (commercial, employment, corporate) and statute YAMLs.

## Scope snapshot
- 3 new AiSkill implementations on existing Phase 72 `AiSkill` interface
- 2 new tables (V127): `compliance_audit_report`, `compliance_audit_finding`
- 3 new system prompt files + output schemas
- ~3 new backend services (DocumentTextExtractor, ComplianceDataCollector, AiReviewReportGenerator)
- Frontend: review trigger on documents, drafting dialog, compliance dashboard extension
- Reuses: AiSkillExecutionService, AiExecution, AiExecutionGate, AiFirmProfile, AiCostService
- Estimated: 8-10 epics, ~15-18 slices

## Phase roadmap after 74
- **Phase 75 candidates**: (a) Trust accounting watchdog + fee note generator (the operational AI skills that got bumped). (b) Scheduled compliance sweeps + bulk contract review (natural extensions of Phase 74). (c) Calendar/Slack integration (cross-vertical, different domain entirely).
- **AI skill pivot pattern**: founder consistently chooses intelligence/decision-support skills over operational/automation skills. Trust watchdog has been parked 3x now. May not be as high-value as assumed — or may need a concrete LSSA inspection scenario to motivate.

## Next step
`/architecture requirements/claude-code-prompt-phase74.md`
