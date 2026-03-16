---
name: init-qa-phase
description: Scaffold all prerequisites for a new QA-fix cycle from a requirements/scenario file. Creates lifecycle script, runs gap analysis, seeds status tracker, and prepares the branch. Usage - /init-qa-phase <requirements-file> [vertical-profile]
---

# Init QA Phase — Scaffold Prerequisites

Generate everything needed to run `/qa-cycle` from a requirements or scenario specification file.

## What This Produces

| Output | Path | Purpose |
|--------|------|---------|
| Lifecycle script | `tasks/phase{N}-lifecycle-script.md` | Day-by-day browser test script for the QA agent |
| Gap report | `tasks/phase{N}-gap-report-agent.md` | Pre-analysis of expected gaps based on codebase inspection |
| Status tracker | `qa_cycle/status.md` | Seeded with gaps from the report, ready for `/qa-cycle` |
| Directory structure | `qa_cycle/fix-specs/`, `qa_cycle/checkpoint-results/` | Empty dirs for agent output |
| Branch | `bugfix_cycle_YYYY-MM-DD` | Working branch for the cycle |

## Arguments

- `<requirements-file>` — path to a requirements doc, scenario spec, or vertical profile description (e.g., `requirements/accounting-za-vertical.md` or `tasks/phase47-lifecycle-script.md`)
- `[vertical-profile]` — optional vertical profile slug (e.g., `accounting-za`, `legal-za`). If omitted, inferred from the requirements file.

## Step 0 — Parse Input

Read the requirements file to determine:
1. **Vertical profile** — which industry/locale pack to test (e.g., `accounting-za`)
2. **Firm persona** — size, location, team roles, client types
3. **Phase number** — for file naming (check `tasks/` for the next available phase number)

If the input IS already a lifecycle script (has "Day 0", "Day 1", checkpoint notation), skip to Step 2.

## Step 1 — Generate Lifecycle Script

Dispatch a **blocking** `general-purpose` subagent to write the lifecycle script:

```
You are writing a **90-day accelerated lifecycle script** for QA testing.

## Input
Read the requirements file at: {REQUIREMENTS_FILE}

## Context
- Platform: DocTeams (multi-tenant B2B SaaS)
- E2E stack: http://localhost:3001 (mock auth — Alice/Bob/Carol)
- Vertical profile: {VERTICAL_PROFILE}
- Playwright MCP for browser automation

## What to Write
Write a lifecycle script to: tasks/phase{N}-lifecycle-script.md

The script simulates the first 90 days of a firm using the platform. Structure:

### Format
- **Day 0**: Firm setup (branding, rates, templates, team)
- **Day 1-3**: First clients onboarded (different entity types)
- **Day 7**: First week of work (time logging, task management, collaboration)
- **Day 14**: Compliance review (checklists, document collection)
- **Day 30**: First billing cycle (invoices, retainer close, payment)
- **Day 45**: Expansion (new engagement types, expenses, bulk billing)
- **Day 60**: Quarterly review (profitability, rate review, reports)
- **Day 75**: Year-end work (annual engagements, regulatory deadlines)
- **Day 90**: Portfolio review (aged debtors, fork readiness assessment)

### Rules
- Each day has numbered checkpoints (e.g., 0.1, 0.2, 1.1)
- Each checkpoint has: Actor (Alice/Bob/Carol), Action, Expected Outcome
- Include prerequisite data blocks where time entries or other data must be created
- Use checkpoint notation: [ ] PASS / [ ] FAIL / [ ] PARTIAL
- Reference specific clients by name (create 3-5 diverse client personas)
- Include both happy path and edge cases
- Test role-based access (Owner vs Admin vs Member perspectives)

### Research
Before writing, search the codebase for:
1. Existing vertical profile packs in `backend/src/main/resources/` (field packs, template packs, compliance packs, automation packs, request template packs)
2. Available features in `frontend/app/(app)/org/[slug]/` (sidebar navigation items)
3. Existing lifecycle script examples in `tasks/` for format reference
4. The vertical profile's specific data model (custom fields, entity types, checklist items)

Incorporate ALL seeded pack content into the script — every field group, template, automation rule, checklist, and request template should be exercised at least once.
```

## Step 2 — Run Gap Analysis

Dispatch a **blocking** `general-purpose` subagent to analyze the codebase against the lifecycle script:

```
You are a **gap analysis agent** for the DocTeams platform.

## Input
Read the lifecycle script at: tasks/phase{N}-lifecycle-script.md

## Your Job
Walk through each day/checkpoint in the script and determine whether the platform
can execute it. Identify gaps — things the script expects but the platform doesn't support.

## Method
For each checkpoint:
1. Search the codebase for the feature it exercises
2. Determine: LIKELY_PASS (feature exists and looks correct), LIKELY_FAIL (feature missing or broken), UNCERTAIN (exists but untested)
3. For LIKELY_FAIL: log a gap with ID, description, severity, suggested fix, effort estimate

## Categories
- **missing-feature**: Feature doesn't exist in the codebase
- **bug**: Feature exists but has a defect
- **ux**: Feature works but the UX flow is broken or confusing
- **vertical-specific**: Feature exists generically but lacks vertical customization
- **content**: Seed data or template content is missing/incorrect

## Severity
- **blocker**: Prevents QA from proceeding to the next checkpoint
- **major**: Significant gap but workaround exists
- **minor**: Friction but functional
- **cosmetic**: Display/label issues only

## Output
Write the gap report to: tasks/phase{N}-gap-report-agent.md

Format:
```markdown
# Phase {N} — Agent Gap Report
## Generated: {date}
## Summary Statistics
| Category | Blocker | Major | Minor | Cosmetic | Total |
## Critical Path Blockers
{Detail each blocker}
## All Gaps (Chronological)
### GAP-001: {title}
**Day**: {N}
**Step**: {description}
**Category**: {category}
**Severity**: {severity}
**Description**: {2-3 sentences}
**Evidence**: {file paths, code references}
**Suggested fix**: {actionable description with effort S/M/L}
## Fork Readiness Assessment
{Overall verdict with percentage estimate}
```

## Research Rules
- Search the ACTUAL codebase, don't guess from architecture docs
- Check backend controllers, services, and entities for feature existence
- Check frontend pages and components for UI availability
- Check seed data files for vertical-specific content
- Include file paths as evidence
```

## Step 3 — Seed Status Tracker

Read the gap report and create `qa_cycle/status.md`:

```markdown
# QA Cycle Status — {DATE}

## Current State

- **QA Position**: Day 0, Checkpoint 0.1 (not started)
- **Cycle**: 0
- **E2E Stack**: Not running
- **Branch**: `bugfix_cycle_{DATE}`
- **Scenario**: `tasks/phase{N}-lifecycle-script.md`

## Tracker

| ID | Summary | Severity | Status | Owner | PR | Day | Notes |
|----|---------|----------|--------|-------|----|-----|-------|
{Extract each gap from the report as a row}
{Blocker gaps → Status: OPEN, Owner: Infra or Dev}
{Major gaps → Status: OPEN, Owner: Dev}
{Minor/cosmetic gaps that are clearly out of scope → Status: WONT_FIX}

## Status Values

- **OPEN** → **SPEC_READY** → **IN_PROGRESS** → **FIXED** → **VERIFIED** → done
- **REOPENED** (fix didn't work) → back to SPEC_READY
- **WONT_FIX** (documented, not blocking e2e flow)
- **BUG** (logged, prioritized, not blocking unless cascading)

## Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| {NOW} | Setup | Initial status seeded from gap report |
```

### Triage Rules for Initial Seeding

When converting gap report entries to status tracker rows:

- **Blockers**: Always `OPEN`, Owner: `Infra` (if seed/stack) or `Dev` (if code)
- **Major bugs**: `OPEN`, Owner: `Dev`
- **Major new features** (requires new entity, new API, new page): `WONT_FIX` with note "New feature. Out of scope for bugfix cycle."
- **Minor new features**: `WONT_FIX`
- **Cosmetic**: `OPEN` if quick fix (S effort), `WONT_FIX` if not
- **Testing limitations** (can't verify delayed triggers, etc.): `WONT_FIX` with note "Testing limitation"

## Step 4 — Create Directory Structure

```bash
mkdir -p qa_cycle/fix-specs qa_cycle/checkpoint-results
```

If `qa_cycle/error-log.md` doesn't exist, create it:
```markdown
# E2E Error Log

| Timestamp | Service | Level | Message |
|-----------|---------|-------|---------|
```

## Step 5 — Create Branch

```bash
BRANCH="bugfix_cycle_$(date +%Y-%m-%d)"
git checkout -b "$BRANCH" 2>/dev/null || git checkout "$BRANCH"
```

## Step 6 — Commit and Report

```bash
git add tasks/phase{N}-lifecycle-script.md tasks/phase{N}-gap-report-agent.md qa_cycle/
git commit -m "feat: scaffold QA cycle for {VERTICAL_PROFILE} vertical (phase {N})"
git push -u origin "$BRANCH"
```

Report to the user:
```
QA cycle scaffolded for {VERTICAL_PROFILE}:

  Lifecycle script:  tasks/phase{N}-lifecycle-script.md ({X} days, {Y} checkpoints)
  Gap report:        tasks/phase{N}-gap-report-agent.md ({Z} gaps: {B} blocker, {M} major, {m} minor)
  Status tracker:    qa_cycle/status.md ({total} items seeded)
  Branch:            bugfix_cycle_{DATE}

To start the cycle:
  /qa-cycle tasks/phase{N}-lifecycle-script.md tasks/phase{N}-gap-report-agent.md --resume
```

## Guardrails

- **Don't start the QA cycle** — this skill only scaffolds. The user runs `/qa-cycle` separately.
- **Don't modify existing code** — only generate documents and seed data files.
- **Research before writing** — the lifecycle script must reference actual features/packs in the codebase, not hypothetical ones.
- **Subagents do the heavy lifting** — the orchestrator validates inputs and assembles outputs.
- **Reuse existing lifecycle scripts as format reference** — check `tasks/phase*-lifecycle-script.md` for structure.
