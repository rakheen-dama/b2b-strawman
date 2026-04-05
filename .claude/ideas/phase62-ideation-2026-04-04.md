# Phase 62 Ideation — Feature Module Gating (Progressive Disclosure)
**Date**: 2026-04-04

## Decision
Gate 3 horizontal power-user features behind opt-in toggles in Settings → Features. All OFF by default. Uses Phase 49's existing module infrastructure with a new `HORIZONTAL` category to distinguish from vertical modules.

## Rationale
61 phases of depth = onboarding risk for small firms. Founder's criterion: **hide features where misconfiguration leads to bad outcomes**, not just complexity. Three features fit:

### Gated (OFF by default)
- **Resource Planning** — allocation grid gives wrong utilization if misconfigured. Irrelevant for 3-5 person firms.
- **Bulk Billing Runs** — batch invoice generation can produce incorrect invoices for many customers in one click.
- **Automation Rule Builder** — seeded packs run silently (keep running!), but custom rules can spam notifications or change statuses incorrectly. Builder UI hidden, execution engine untouched.

### Explicitly NOT gated (founder decisions)
- **Retainer Agreements** — "key sell." Always visible.
- **Profitability & Budgets** — founder initially considered toggling these ON by default, then decided: "data should still be collected... maybe don't add a feature flag here, just keep it in." Passive read-only features with no config risk.
- **Rate Cards** — fundamental to billing. Always visible.
- **Reporting** — read-only, no config risk. Always visible.

### Key Design Decisions
1. **Pure on/off toggles** — no nav restructuring or demotion. Clean absence.
2. **Settings section, not dedicated page** — "Features" section in existing Settings layout with toggle cards.
3. **Only show toggleable modules** — vertical modules (trust_accounting, court_calendar, etc.) NOT shown. Managed by profile selection.
4. **Automation execution isolation** — critical requirement. Disabling `automation_builder` hides the UI but seeded automation packs keep executing for all orgs.
5. **No data loss** — toggling off hides UI, data stays. Toggling back on restores everything.

## Simplification Arc Context
Phase 62 (module gating) + Phase 63 (field graduation) = structural refinement before scaling. Not new features — making the product feel tighter for new users while preserving depth for power users.

## Next Step
`/architecture requirements/claude-code-prompt-phase62.md`
