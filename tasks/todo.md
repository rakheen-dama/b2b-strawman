# Phase 89 pre-phase fixes — everything that needs no ADR (2026-11-03)

Source: `requirements/claude-code-prompt-phase89.md` § Prerequisite + the "can go now" list. Founder authorised multi-fix PRs where context fits (2026-11-03). Execution: research agents (read-only) → one builder per PR in its own worktree, **sequential** → two feature-dev:code-reviewer verdicts + CodeRabbit → orchestrator full gates + verify markers → merge → markers committed in bookkeeping (F35).

## PRs (in order)
- [x] **PR-A tooling:** F35 — committed on main `291e1a082` (founder allowed main commits) `/epic_v2` + this loop commit verify markers in the bookkeeping step
- [x] **PR-B clock:** MERGED #1716 `a216f4430` — verify 6304/0/0/26; lifecycle in-window 101/0/3; DB fee transfer SA-dated; G88-F48 (HIGH) + G88-F49 — `Africa/Johannesburg` Clock, 57 `LocalDate.now()` sites, guard test, FE server "today"; red-first test
- [x] **PR-C document keys:** MERGED #1717 — verify 6360/0/0/26; browser+DB+S3: two same-period SoAs → distinct keys/bytes, portal holds the shared one; G88-F33 (HIGH) — identity-based S3 keys for statements + generated docs; collision tests; read-only collided-row report
- [x] **PR-D OTP parties:** MERGED #1718 — verify 6371/0/0/26; existing-tenant reconcile observed; depth-ii 39/0/4, default 159/0/18, lifecycle (fresh) 101/0/3; wording verified by founder on attorney's behalf; G88-F12 (HIGH) + letter-of-demand debtor binding (founder: fix in PR-D) + scenario 14.4/14.5/14.13 amend (founder-authorised) — `seller_name`/`purchaser_name` field defs, OTP + Voetstoots binding, conveyancing party-binding audit, reconcile existing tenants
- [x] **PR-E frontend small cluster:** MERGED #1719 (+G88-F17 labels, audit #418 timestamp, header wrap) — verify 6374/0/0/26; lifecycle 102/0/2, default 160/0/17; browser 375/1024/1440; G88-F38 conflict-check null relationship, G-D10b audit key hidden-but-searchable, G88-F46 client Generate Document
- [x] **PR-F backend small cluster:** MERGED #1720 — F36 reproduced (5/8 red) + fixed; M10 observed in DB + fee-note preview; verify 6389/0/0/26; lifecycle 102/0/2; G88-F36 @mention (red-first; fix only if red), G88-M10 tariff line sort order
- [x] **PR-G typecheck:** MERGED #1721 — 130+13 test-type errors → 0; `next typegen && tsc --noEmit`; CI step; FE/portal gates green; G88-F47 `typecheck` script in frontend + portal (+ fix surfaced test-type errors; split if large)

## Gates per PR
Targeted tests → PR → reviews A+B bound to head + CodeRabbit line → full `backend/scripts/verify.sh` (backend) / `pnpm lint && build && test && format:check` (FE/portal) → markers → browser-observed PASS for UI changes → merge with `--match-head-commit`.

## Review
- **PR-B #1716** (2026-11-04): 2 review rounds (R1 both REQUEST_CHANGES: reminder copy UTC, retention card UTC, guard holes, DSAR, tests → fixed; R2 both APPROVE; lows + CodeRabbit Minor fixed; CodeRabbit Major (convert stored reminder times) declined with rationale). Lesson: a Docker image built while a fixer edits the worktree copies half-written files — never build the e2e stack during an edit.
- **PR-C #1717** (2026-11-04): R1 review B REQUEST_CHANGES (vacuous DOCX↔PDF test, duplicated key regex, PAIA inline key, duplicate test helper) → fixed; CodeRabbit Minor (orphan object on failed bank import) → fixed. Follow-ups: SoA dialog defaults next period start after period end; BankStatementParseException → 500; Surefire fork shutdown 60 s; mock-login page still says "DocTeams".
- **PR-D #1718** (2026-11-04): R1 review B REQUEST_CHANGES (new `debtor_name` duplicated `opposing_party`) + review A Important (Bond Cancellation named the client as mortgagor) → both fixed. CodeRabbit Major (no generation warning for unset party fields) deferred → Phase 89 E3. Lesson: lifecycle must run on a fresh stack or first in the chain — after depth-ii its conflict check hits depth-ii's same-named clients (11 cascade failures). Follow-ups: respondent-side litigation templates; standalone bond cancellation (no sale) renders a blank mortgagor.
- **PR-E #1719** (2026-11-04): browser observation (not tests) caught two things: hiding audit keys left identical "Fee note" options (→ G88-F17 distinct labels, plus security.* / ai.gate.*) and a React #418 on the audit page (Timestamp cell UTC vs SAST, pre-existing before #1716). CodeRabbit: header actions overflow at 320 px (fixed), facet-less trigger fallback (fixed), screenshot baseline (declined). Follow-ups: audit label polish (REPORT EXPORTED, Paia, Lpff, "Matter closure closed", mixed Title Case); compliance/requests/[id] UTC times; conflict badge ALIAS_MATCH; G-D10a row key.
- **PR-F #1720** (2026-11-04): F36 hypothesis CONFIRMED red-first. Review round: mention after punctuation regressed with `(?<!\S)` → `(?<![\w.+\-])`. Follow-ups: mention recipients not filtered by matter access (Phase 89 candidate); URL handles count as mentions; hyphen/apostrophe names; existing tied draft lines not backfilled.

**Batch complete 2026-11-04:** 7 PRs (A on main `291e1a082`; #1716–#1721), all merged with full gates, two bound reviews per head, CodeRabbit addressed, verify markers committed. Phase 89 requirements updated with the pre-phase status. Next: `/architecture requirements/claude-code-prompt-phase89.md`.

# Phase 89 — /architecture (2026-11-04)

Input: `requirements/claude-code-prompt-phase89.md`. Numbering: ARCHITECTURE.md **Section 14**, ADRs **358+**, tenant migration **V10** (global V3 only if needed). Pre-phase PRs #1716–#1721 wrote no ADRs, so the clock / S3-key / OTP-party ADRs are written here as records of shipped decisions.

- [x] Step 0 — context inventories (`.arch-context.md` 1308 lines, `.arch-context-phase89.md` 498, `.arch-context-prephase.md` 357) + binding rulings in `.arch-rulings-phase89.md` (scope at HEAD: E2 = F43+F44, E3 = F16+F32; 8 follow-up candidates triaged: C1/C2/C3/C5/C6 in, C4/C7/C8 backlog)
- [x] Step 1 — `architecture/phase89-go-live-readiness.md` written (1230 lines, 12 sections, 28 slices 630A–636C, tenant V10 + global V3)
- [x] Step 2 — ADRs 358–365 written; ADR-363/364 aligned with the backfill-marker and receipt-actor rulings
- [x] Step 3 — two reviewers (consistency: 3 BLOCKER/9 MAJOR/12 MINOR; code facts: 0 BLOCKER/5 MAJOR/12 MINOR, 112 citations sampled, 15 wrong) → rulings §6/§7 → all 41 findings applied; re-verified by the orchestrator
- [x] Step 4 — summary presented 2026-11-04; next `/breakdown 89 requirements/claude-code-prompt-phase89.md`

## Review — Phase 89 /architecture (2026-11-04)
- Output: `architecture/phase89-go-live-readiness.md` (Section 14, 1254 lines, 28 slices 630A–636C, tenant V10 §1–§2 + global V3) and `adr/ADR-358…365`. Working files `.arch-context*.md`, `.arch-rulings-phase89.md`, `.arch-review-findings-phase89.md` kept as inputs (untracked).
- Scope corrections vs the requirements text: E2 = F43+F44 (F38 closed #1719); E3 = F16+F32 (M10 #1720, F46 #1719); E6 = docs PR + marker-check script only. Follow-up candidates: C1/C2/C3/C5/C6 in, C4/C7/C8 backlog.
- Decisions that need the founder's eye (§14.12): trust-report capability (VIEW_TRUST alone?), interest-run cancel, receipt actor fallback = invoice creator, co-client deadline visibility, PROJECT-target comment surface.
- Lessons: reviewers caught two design errors a writer cannot see from the inventories (global snapshot re-pin forbidden by BASELINE.md; a `Task.sortOrder` that does not exist) and three real design holes (receipt numbers reused after reversal, POSTPONED rows ghosted by the backfill, `s3Key` breaking the pin) — a code-fact reviewer with Bash is worth its cost on architecture docs too.

# Phase 89 — /breakdown (2026-11-04)

- [x] Step 0 — inputs: architecture §14 (28 slices 630A–636C), ADR-358..365, rulings; TASKS.md last epic 629 → epics 630–636
- [x] Step 1 — `tasks/phase89-go-live-readiness.md` written (664 lines, 7 epics 630–636, 43 slices; mapping table to §14.10 ids)
- [x] Step 2 — verified: 7 epics, 43 slices, all 28 architecture ids mapped, rows 8 pipes = TASKS.md layout
- [x] Step 3 — TASKS.md rows inserted after epic 629 (line 718+), migration note on line 5 updated; breakdown pointer added under architecture §14.10
- [x] Step 4 — summary presented 2026-11-04; next: founder rulings on §14.12 open questions, then `/phase_v2 89`
