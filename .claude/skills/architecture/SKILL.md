---
name: architecture
description: Produce a self-contained architecture section and ADRs from a requirements prompt file. Usage: /architecture <requirements-file>. Example: /architecture requirements/claude-code-prompt-phase5.md
---

# Architecture Document Generation Workflow

Read a requirements/prompt file and produce a comprehensive architecture section (ready to merge into `architecture/ARCHITECTURE.md`) plus standalone ADR files in `adr/`.

## Arguments

- **Required**: Path to a requirements file (e.g., `/architecture docs/phase6-spec.md`)

## Principles

1. **Existing-architecture-first**: New output must feel like a natural continuation of the existing system.
2. **Delegate research**: Use subagents for exploration. Keep orchestrator context lean.
3. **Self-contained output**: Main document mergeable into `architecture/ARCHITECTURE.md` as-is. ADRs as standalone files.
4. **No code implementation**: Produce only documents and ADRs. Never write Java, TypeScript, or SQL implementation files.
5. **Engineer-ready**: The `/breakdown` skill should be able to derive epics from the output without re-asking requirements.

## Step 0 — Gather Targeted Context

Read the requirements file. Extract 3–5 **domain keywords** from it (e.g., "rate card", "budget", "cost rate" for a billing phase). Then launch a **general-purpose** agent:

```text
Explore the codebase and write a targeted context inventory to:
  /Users/rakheendama/Projects/2026/b2b-strawman/.arch-context.md

The phase being designed involves these domain concepts: {DOMAIN_KEYWORDS}

Collect ONLY:

1. **Numbering** (3 lines total):
   - Next section number: grep "^## " architecture/ARCHITECTURE.md, find the last numbered section, report next number
   - Next ADR number: ls adr/ADR-*.md | sort | tail -1, extract number, report next
   - Next migration number: ls backend/src/main/resources/db/migration/tenant/V*.sql | sort | tail -1, extract number, report next

2. **Relevant entities** (filtered by domain keywords):
   - grep -rl "@Entity" backend/src/main/java/ to list all entity files
   - For each, check if the entity name or package relates to: {DOMAIN_KEYWORDS}
   - Report ONLY matching entities (class name + file path), plus any entities they reference via @ManyToOne/@OneToMany
   - If no matches, report the 5 most recently modified entities instead

3. **Reference entity pattern** (ONE recent entity, up to 80 lines):
   - Pick the most recently created entity (check git log --diff-filter=A --name-only -- '*/entity/*.java' | head)
   - Include its source code as a reference pattern
   - If the entity exceeds 80 lines, include the class declaration, field block, and constructors — omit getters/boilerplate

4. **Reference ADR template** (most recent ADR, up to 50 lines):
   - ls adr/ADR-*.md | sort | tail -1
   - Include its content as a format template
   - If it exceeds 50 lines, include Status through Decision sections, truncate Consequences to first 3 bullets

Write all findings as structured markdown. Target 150–200 lines (numbering ~5, entities ~15, entity pattern ~80, ADR template ~50).
Do NOT read architecture/ARCHITECTURE.md in full — only grep section headers.
Do NOT list all packages or all routes — only what's relevant to {DOMAIN_KEYWORDS}.

Working directory: /Users/rakheendama/Projects/2026/b2b-strawman
```

After the agent finishes, read `.arch-context.md` and present a summary to the user:
- Next section number, next ADR number, next migration number
- Relevant entities found
- Ask for confirmation before proceeding

## Step 1 — Produce the Architecture Document

Launch a **general-purpose** agent to write the architecture doc and ADRs. Pass it:

```text
You are writing an architecture document for Phase {N} of the DocTeams multi-tenant SaaS platform.

## Inputs (read these FIRST)

1. Requirements file: {REQUIREMENTS_PATH}
2. Context inventory: /Users/rakheendama/Projects/2026/b2b-strawman/.arch-context.md
3. Writing prompt (sections, style, size budget): /Users/rakheendama/Projects/2026/b2b-strawman/.claude/skills/architecture/WRITING-PROMPT.md

## Task

1. Read all three input files above.
2. Write the architecture document to: architecture/{KEBAB_TITLE}.md
3. Create ADR files in adr/ following the format from the writing prompt.
4. Ensure the BREAKDOWN-CONTRACT block is present in the Capability Slices section.

Use the context inventory for numbering and entity references.
Follow the writing prompt for section structure, style, size budgets, and ADR format.
The requirements file is your primary input — cover everything it asks for.

Output file: architecture/{KEBAB_TITLE}.md
```

## Step 2 — Quality Review

Launch a **code-reviewer** agent to verify:

```text
Review the architecture document at {MAIN_DOC_PATH} and ADR files at adr/ADR-{NNN}*.md for:

1. Internal consistency — cross-references, entity names, field names, ADR numbers, section numbers
2. Completeness — all requirements covered, BREAKDOWN-CONTRACT block present
3. Compatibility — follows existing entity patterns, correct migration/ADR numbering
4. Size budget — document is 600-1000 lines, core sections have adequate depth
5. Implementability — can an engineer derive epics from the Capability Slices section?

List issues if found. Be concise.
```

Fix any issues found by the reviewer.

## Step 3 — Present Summary

Show the user:
- Output file path (main document)
- ADR files created (with titles)
- Section number for architecture/ARCHITECTURE.md merge
- Migration number(s)
- Capability slice count
- Document line count (verify within 600–1000 budget)
- Any open questions or assumptions made

## Error Handling

- **Requirements file not found**: Ask the user for the correct path.
- **Requirements too vague**: Ask clarifying questions before proceeding. Use AskUserQuestion for structured choices.
- **ADR numbering conflict**: If an ADR number already exists, increment until free.
- **Section numbering conflict**: Same approach — find the next free section number.
- **Review finds critical issues**: Fix and re-review. Do not present output with known issues.
- **Document exceeds 1000 lines**: Ask the writing agent to compress supporting sections (not core sections).
