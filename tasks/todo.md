# Phase 89 pre-phase fixes — everything that needs no ADR (2026-11-03)

Source: `requirements/claude-code-prompt-phase89.md` § Prerequisite + the "can go now" list. Founder authorised multi-fix PRs where context fits (2026-11-03). Execution: research agents (read-only) → one builder per PR in its own worktree, **sequential** → two feature-dev:code-reviewer verdicts + CodeRabbit → orchestrator full gates + verify markers → merge → markers committed in bookkeeping (F35).

## PRs (in order)
- [x] **PR-A tooling:** F35 — committed on main `291e1a082` (founder allowed main commits) `/epic_v2` + this loop commit verify markers in the bookkeeping step
- [x] **PR-B clock:** MERGED #1716 `a216f4430` — verify 6304/0/0/26; lifecycle in-window 101/0/3; DB fee transfer SA-dated; G88-F48 (HIGH) + G88-F49 — `Africa/Johannesburg` Clock, 57 `LocalDate.now()` sites, guard test, FE server "today"; red-first test
- [x] **PR-C document keys:** MERGED #1717 — verify 6360/0/0/26; browser+DB+S3: two same-period SoAs → distinct keys/bytes, portal holds the shared one; G88-F33 (HIGH) — identity-based S3 keys for statements + generated docs; collision tests; read-only collided-row report
- [ ] **PR-D OTP parties:** G88-F12 (HIGH) + letter-of-demand debtor binding (founder: fix in PR-D) + scenario 14.4/14.5/14.13 amend (founder-authorised) — `seller_name`/`purchaser_name` field defs, OTP + Voetstoots binding, conveyancing party-binding audit, reconcile existing tenants
- [ ] **PR-E frontend small cluster:** G88-F38 conflict-check null relationship, G-D10b audit key hidden-but-searchable, G88-F46 client Generate Document
- [ ] **PR-F backend small cluster:** G88-F36 @mention (red-first; fix only if red), G88-M10 tariff line sort order
- [ ] **PR-G typecheck:** G88-F47 `typecheck` script in frontend + portal (+ fix surfaced test-type errors; split if large)

## Gates per PR
Targeted tests → PR → reviews A+B bound to head + CodeRabbit line → full `backend/scripts/verify.sh` (backend) / `pnpm lint && build && test && format:check` (FE/portal) → markers → browser-observed PASS for UI changes → merge with `--match-head-commit`.

## Review
- **PR-B #1716** (2026-11-04): 2 review rounds (R1 both REQUEST_CHANGES: reminder copy UTC, retention card UTC, guard holes, DSAR, tests → fixed; R2 both APPROVE; lows + CodeRabbit Minor fixed; CodeRabbit Major (convert stored reminder times) declined with rationale). Lesson: a Docker image built while a fixer edits the worktree copies half-written files — never build the e2e stack during an edit.
- **PR-C #1717** (2026-11-04): R1 review B REQUEST_CHANGES (vacuous DOCX↔PDF test, duplicated key regex, PAIA inline key, duplicate test helper) → fixed; CodeRabbit Minor (orphan object on failed bank import) → fixed. Follow-ups: SoA dialog defaults next period start after period end; BankStatementParseException → 500; Surefire fork shutdown 60 s; mock-login page still says "DocTeams".
