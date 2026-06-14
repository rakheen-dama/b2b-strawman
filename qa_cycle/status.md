# QA Cycle Status — AI Core Live-Claude Verification (Keycloak) — Cycle 2026-06-14

- **Branch**: `bugfix_cycle_2026-06-14`
- **Scenario**: `qa/testplan/demos/ai-core-verification-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-06-14
- **Mode**: Clean slate. Session 0 = dev-up + keycloak-bootstrap + svc start all, then drop any `tenant_verifain*` schema and delete `@verifain-test.local` KC users. Orgs/users created through real onboarding flow.
- **Context**: Phases 72 & 74 shipped the AI core code-complete but never exercised against live Claude (only the `NoOpAiProvider` stub + stubbed backend tests). This cycle verifies the whole AI substrate end-to-end against **real Claude**. It is the blocker to clear before any MCP-server work (`.claude/ideas/mcp-plugin-strategy-2026-06-14.md`).

## 🔴 Human-in-the-loop dependency (RESOLVED for this run)

- A funded `sk-ant-…` Anthropic key is required. **Decision**: the human enters it themselves.
- **Flow**: QA runs V0 onboarding → orchestrator PAUSES → human logs in as Nomsa (`nomsa@verifain-test.local` / `SecureP@ss1`) in their OWN browser at http://localhost:3000 → Integrations UI → enters funded key (V1 steps 1.1–1.7), enables, runs connection test green → tells orchestrator "done" → QA resumes verifying V1 then V2–V12.
- Estimated live spend for a clean run: **< R30**. Stub-vs-live proof obligation: every skill PASS needs non-empty `output` + `costCents > 0` + non-zero token usage in backend log. `costCents == 0` / empty output = stub answered = **FAIL**.

## Per-Stage Workflow (NON-NEGOTIABLE)

For each verification stage V0–V12:

1. **QA agent walks the stage end-to-end** via Playwright MCP. Records the **Observed PASS** criterion result with evidence (browser state + backend log excerpt + gate id / audit id / costCents).
2. **Triage every gap.** Product agent decides: SPEC_READY, WONT_FIX-EXEMPT, or scenario-amendment (requires authorization).
3. **Fix every spec.** Dev agent: reproduce → full `./mvnw verify` → marker → commit → PR → review → merge.
4. **PR each bugfix into `main`** (its own PR). Pre-merge gate hook blocks unless verify markers green.
5. **Retest each fix on main** with QA agent. Mark VERIFIED only after observed end-to-end PASS.
6. **Only then advance.** Next stage starts when the current stage's blocking gaps are VERIFIED.

## Mandate
- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired (per CLAUDE.md §6 WONT_FIX exemptions). Everything else is own/fix.
- No production data; all data disposable. Backward compatibility not a priority.
- No SQL shortcuts — all operations via REST API or browser UI. Only legitimate REST use is the Mailpit email API.

## QA Position

- **Stage**: V0 (firm onboarding) — **NOT STARTED**. Cycle just initialized; awaiting Infra clean-slate startup.

## Dev Stack

- **Status**: Not running (clean-slate startup pending — Infra agent next).

## Flags

- (none)

## Tracker

| Gap ID | Stage | Summary | Severity | Owner | Status |
|--------|-------|---------|----------|-------|--------|
| _(none yet)_ | | | | | |

## Stage Checklist

| Stage | Description | Result |
|-------|-------------|--------|
| V0 | Onboard firm (Nomsa owner, Pieter member) | PENDING |
| V1 | BYOAK key configuration (human enters key) | PENDING |
| V2 | Firm AI profile cold-start | PENDING |
| V3 | Matter-intake skill (live Claude) | PENDING |
| V4 | FICA verification skill (live Claude) | PENDING |
| V5 | Contract review skill (live Claude) | PENDING |
| V6 | Drafting skill (live Claude) | PENDING |
| V7 | Compliance audit skill (live Claude) | PENDING |
| V8 | Execution-gate approval flow | PENDING |
| V9 | Cost metering & budget enforcement | PENDING |
| V10 | Capability / RBAC gating (Pieter) | PENDING |
| V11 | Error & graceful-degradation paths | PENDING |
| V12 | Audit trail | PENDING |

## Log

- **2026-06-14** — Cycle initialized. Archived completed Legal-ZA cycle (ALL_DAYS_COMPLETE, 0 open gaps) to `qa_cycle/_archive_2026-06-13_legal-full-lifecycle-kc/`. Branch `bugfix_cycle_2026-06-14` created. Fresh tracker seeded. Human-in-the-loop key decision: human enters key at V1. Stack decision: clean-slate startup. Next action: Infra agent (Session 0 clean-slate startup).
