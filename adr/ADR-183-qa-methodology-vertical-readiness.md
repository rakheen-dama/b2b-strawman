# ADR-183: QA Methodology for Vertical Readiness

**Status**: Accepted
**Date**: 2026-03-15

## Context

DocTeams has 46 phases of functionality, each tested in isolation with unit tests, integration tests, and per-slice verification. There is no end-to-end validation that the platform supports a real firm's daily operations. Features that work individually may fail when combined into a real workflow: fields may be wrong for the target vertical, templates may produce unusable output, automation triggers may not match real patterns, and information gates may block at the wrong moments.

Phase 47 introduces the first vertical QA pass — a structured shakeout for the SA accounting vertical. The question is how to structure this QA process so that it produces actionable findings and is repeatable for future verticals (law firms, consulting firms, etc.).

The constraints are: (1) the QA must exercise the platform through the UI, not through API tests, because UX friction is a primary finding category; (2) the QA must capture both objective failures (broken features, wrong calculations) and subjective judgements (confusing flows, unprofessional output); (3) the output must be structured enough to feed directly into fix phase planning.

## Options Considered

### Option 1: Single automated test suite

Write a Playwright test suite that exercises the 90-day lifecycle. Run it, collect failures, file bugs.

- **Pros**:
  - Repeatable — can re-run after fixes to verify
  - Objective — pass/fail with no interpretation needed
  - Automated — no human time required per run
- **Cons**:
  - Cannot capture UX judgement ("this works but feels wrong")
  - Brittle — lifecycle tests break on any UI change, creating maintenance burden
  - Misses exploration — a test script follows a fixed path, a human notices things along the way
  - Building the test suite is significant effort, and it is only useful for one vertical
  - Cannot assess whether a document template looks professional or an invoice inspires client confidence

### Option 2: Two-pass approach: agent + founder (chosen)

Two passes through a structured lifecycle script. The first pass is agent-driven (Playwright MCP against the E2E stack): systematic, thorough, objective — every step attempted, every checkpoint verified, every gap logged with evidence. The second pass is founder-driven: guided walkthrough focused on UX quality, workflow judgement, and "would I pay for this?" assessment. Both produce structured gap reports that are consolidated into a single prioritised analysis.

- **Pros**:
  - Agent pass catches objective failures systematically — broken features, wrong calculations, missing UI elements
  - Founder pass catches subjective quality issues — UX friction, unprofessional output, workflow confusion
  - Structured gap format enables direct conversion to fix tasks
  - The agent pass is fast (hours) and can be re-run; the founder pass is slow (half a day) but only done once
  - The methodology is documented and repeatable for future verticals
- **Cons**:
  - The agent pass is not fully automated — it requires a capable AI agent with Playwright MCP
  - The founder pass requires dedicated human time
  - Two reports must be consolidated, which adds a reconciliation step
  - Agent cannot assess visual quality (e.g., "does this PDF look professional?") — limited to structural checks

### Option 3: Founder-only walkthrough

Skip the agent pass entirely. The founder walks through the lifecycle script manually, logging gaps as they go.

- **Pros**:
  - Simplest approach — one person, one pass
  - Captures both objective and subjective findings in a single pass
  - No tooling dependency (no Playwright MCP needed)
- **Cons**:
  - Founder time is the scarcest resource — a thorough 90-day walkthrough takes a full day
  - Humans skip tedious steps (bulk data creation, repetitive form filling) — coverage gaps
  - No systematic checkpoint verification — easy to miss failures that are not visually obvious
  - Cannot be repeated cheaply after fixes are applied

## Decision

**Option 2: Two-pass approach (agent + founder).** The agent executes the full lifecycle script systematically, producing an objective gap report. The founder then does a guided walkthrough informed by the agent's findings, producing a UX-focused gap report. Both reports are consolidated.

## Rationale

The two-pass approach divides the work by comparative advantage. The agent is thorough, tireless, and systematic — it will attempt every step, verify every checkpoint, and log every failure with a screenshot. But it cannot judge whether an engagement letter looks professional, whether the billing flow feels right, or whether the dashboard tells a useful story. The founder has limited time but brings domain expertise and quality judgement that no automated tool can replicate.

The agent pass de-risks the founder pass: by the time the founder sits down, the obvious blockers (500 errors, missing pages, broken calculations) are already documented. The founder can skip past known issues and focus their attention on the things only a human can assess — UX quality, workflow coherence, and the overall "would I buy this?" judgement.

Option 1 (automated test suite) was rejected because the primary value of this phase is discovering *unknown* gaps, not verifying *known* requirements. A test suite asserts expectations; this QA phase is exploratory. The agent's Playwright MCP usage is exploratory (it navigates, observes, and judges) rather than assertive (it does not have hardcoded expected values).

Option 3 (founder-only) was rejected because it wastes founder time on mechanical verification that an agent handles better. The founder should not be clicking through 16 custom fields to verify they exist — the agent does that. The founder should be looking at the customer creation form and judging whether the field layout makes sense for an accountant.

The structured gap format (category, severity, description, evidence, suggested fix) ensures that findings are actionable. A gap report of "the billing flow is confusing" is not useful. A gap report of "GAP-042: Retainer close requires navigating to Settings > Billing before the Close Period button appears — expected it on the Retainer detail page — severity major, category ux" can be converted directly into a fix task.

## Consequences

### Positive
- Comprehensive coverage: agent catches objective failures, founder catches subjective quality issues
- Founder time is optimised: spend time on judgement, not mechanical verification
- Structured output feeds directly into fix phase planning
- Methodology is documented and repeatable for future verticals (law, consulting)
- Agent pass can be re-run after fixes to verify resolution

### Negative
- Agent pass depends on Playwright MCP capability — if the agent cannot navigate certain flows (e.g., complex drag-and-drop), those steps are logged as "unable to verify"
- Two reports require a consolidation step (estimated 1-2 hours)
- The agent cannot fast-forward time — automation rules with delays (7-day FICA reminder) cannot be verified and are logged as gaps
- Founder availability is a bottleneck — the founder pass cannot be delegated or parallelised

### Neutral
- The lifecycle script is a one-time artifact — it is not maintained as a living test suite
- The gap report format becomes a standard for future QA phases
- The consolidated gap analysis includes a "fork readiness assessment" that influences strategic decisions beyond this phase
- If the agent pass produces very few gaps, the founder pass may be shortened — the two passes adapt to what is found
