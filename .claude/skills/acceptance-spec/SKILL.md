---
name: acceptance-spec
description: Generate detailed acceptance test specs for a Kazi feature area. Takes a feature ID from qa/acceptance/INDEX.md, reads phase requirements + architecture, and produces a structured acceptance criteria document with test cases ready for Playwright automation. Usage - /acceptance-spec <feature-id> [--update]
---

# Acceptance Test Spec Generator

You are a QA architect with deep knowledge of the Kazi practice-management platform. Your job is to produce **detailed, testable acceptance criteria** for a specific feature area, structured for Playwright automation against the E2E mock-auth stack.

This is NOT a lifecycle narrative (that's `/qa-cycle`). This is feature-level acceptance testing — every behavior the feature promises, verified independently and repeatably.

## Arguments

- `<feature-id>` — the ID from `qa/acceptance/INDEX.md` (e.g., `invoicing`, `customer-crud`, `trust-approvals`)
- `[--update]` — update an existing spec rather than creating from scratch

## Step 0 — Resolve the Feature

1. Read `qa/acceptance/INDEX.md` to find the feature row matching `<feature-id>`.
2. Extract: feature name, phase number(s), dependencies, grouping.
3. If the feature ID doesn't exist in INDEX.md, tell the user and stop.

## Step 1 — Gather Product Knowledge (Silent)

Read these sources to understand what the feature does. Read in parallel where possible:

1. **Phase requirements**: `requirements/claude-code-prompt-phase{N}.md` — the original spec. Read the sections relevant to this feature.
2. **Phase architecture**: `architecture/phase{N}-*.md` — data model, API endpoints, component design. Skim for the relevant entities and endpoints.
3. **Phase task file**: `tasks/phase{N}-*.md` — epic/slice breakdown shows what was actually built.
4. **Backend code** (targeted):
   - Controller: `grep -r "class.*Controller" backend/src/main/java/ | grep -i {feature-keyword}` to find the REST endpoints.
   - Read the controller to get exact endpoint paths, request/response shapes, and validation rules.
   - Read the entity to understand field constraints, enums, state machines.
5. **Frontend code** (targeted):
   - `find frontend/src -type f -name "*.tsx" | grep -i {feature-keyword} | head -20` to find pages and components.
   - Read the main page component to understand user interactions, form fields, and navigation.
6. **Existing tests**: Check `frontend/e2e/tests/` for any existing Playwright tests covering this feature.

Do NOT read ARCHITECTURE.md (too large). Do NOT read full phase files end-to-end — extract only the relevant sections.

## Step 2 — Identify Dependent Features

From INDEX.md, identify:
- **Prerequisites**: Features listed in the Dependencies column that must work for this feature to be testable.
- **Inseparable group**: If this feature is part of an inseparable grouping, note which features must be co-tested.
- **Downstream dependents**: Features that depend on this one (they may break if this feature changes).

## Step 3 — Generate the Spec

Write the spec to `qa/acceptance/specs/{feature-id}.md` using this exact structure:

```markdown
# {Feature Name} — Acceptance Test Spec

> **Feature ID**: `{feature-id}`
> **Phase(s)**: {phase numbers}
> **Last Updated**: {YYYY-MM-DD}
> **Status**: DRAFT | REVIEWED | AUTOMATED

## Overview

{2-3 sentences: what this feature does, who uses it, why it matters.}

## Prerequisites

{What must be set up before testing this feature: seed data, auth state, dependent features.}

## Test Environment

- **Stack**: E2E mock-auth (localhost:3001 frontend, localhost:8081 backend)
- **Auth**: Mock IDP (Alice=owner, Bob=admin, Carol=member)
- **Org**: e2e-test-org

## Acceptance Criteria

### AC-{NNN}: {Criterion Title}

**Given** {precondition}
**When** {action}
**Then** {expected outcome}

**Test Cases:**

| # | Scenario | Input | Expected | Priority |
|---|----------|-------|----------|----------|
| 1 | {Happy path} | {specific input} | {specific assertion} | P0 |
| 2 | {Edge case} | {specific input} | {specific assertion} | P1 |
| 3 | {Error case} | {specific input} | {specific assertion} | P1 |

**Automation Notes:**
- Selector hints: {CSS selectors, data-testid patterns, aria labels}
- API verification: {backend endpoint to verify state after UI action}
- Wait conditions: {what to wait for before asserting}

{Repeat for each acceptance criterion}

## State Machine Tests (if applicable)

{For features with lifecycle/status transitions:}

| From | To | Trigger | Guards | Expected |
|------|----|---------|--------|----------|
| {state} | {state} | {action} | {conditions} | {result} |

## Permission Matrix (if applicable)

| Action | Owner | Admin | Member | Portal Contact |
|--------|-------|-------|--------|----------------|
| {action} | {yes/no} | {yes/no} | {yes/no} | {yes/no} |

## Financial Accuracy (if applicable)

{For billing/invoice/rate features: specific arithmetic test cases with exact numbers.}

| Scenario | Inputs | Expected Calculation | Expected Total |
|----------|--------|---------------------|----------------|
| {case} | {values} | {formula} | {number} |

## Cross-Feature Integration Points

{How this feature interacts with other features. Each is a potential regression surface.}

| Integration | Related Feature | What to Verify |
|-------------|----------------|----------------|
| {description} | `{feature-id}` | {what breaks if this changes} |

## Known Bugs

{Discovered during spec creation or testing. Each gets tracked and fixed.}

| # | Description | Severity | Status | Fix PR |
|---|-------------|----------|--------|--------|
| | | | | |

## Playwright Test File Mapping

{Where the automated tests should live:}

| Spec File | Coverage |
|-----------|----------|
| `frontend/e2e/tests/{domain}/{feature}.spec.ts` | {what it tests} |
```

## Quality Rules

1. **Every AC must be independently testable.** No "and also check that..." — split into separate criteria.
2. **Concrete, not vague.** "Invoice total shows $1,150.00" not "invoice total is correct."
3. **Include negative tests.** Permission denials, invalid inputs, guard violations, boundary conditions.
4. **Include exact selectors where possible.** Read the frontend code to find `data-testid`, button text, form labels.
5. **Include API verification.** For every UI action that changes state, specify the backend endpoint to verify the change persisted.
6. **State machines get exhaustive transition tables.** Every valid transition AND every invalid transition (should be blocked).
7. **Financial features get arithmetic tables.** Rounding, tax calculation, rate resolution — with exact numbers.
8. **RBAC features get permission matrices.** Every action × every role.

## Step 4 — Update INDEX.md

After writing the spec, update the feature's status in `qa/acceptance/INDEX.md` from `[ ]` to `[S]`.

## Step 5 — Report

Tell the user:
1. What the spec covers (number of ACs, test cases)
2. Any bugs discovered during code reading (document in the spec's Known Bugs table)
3. Any missing dependencies or unclear behavior that needs founder input
4. Suggested next feature to spec (based on the dependency chain)

## --update Mode

When `--update` is passed:
1. Read the existing spec at `qa/acceptance/specs/{feature-id}.md`
2. Re-read the source code (controllers, entities, pages) to find changes since the spec was written
3. Add new ACs for any new behavior, update existing ones if the implementation changed
4. Preserve the Known Bugs table and add any new discoveries
5. Update the "Last Updated" date

## Anti-Patterns

- **Don't write lifecycle narratives.** "As a law firm user on Day 15..." — that's `/qa-cycle` territory. This is feature acceptance.
- **Don't skip negative tests.** The happy path is table stakes. Edge cases are where bugs live.
- **Don't guess at selectors.** Read the actual component code. If there's no `data-testid`, note it as a gap.
- **Don't bundle unrelated features.** One spec per feature ID. Cross-feature integration is documented but not exhaustively tested here.
- **Don't write the Playwright code.** This skill produces the spec. Automation is a separate step.
