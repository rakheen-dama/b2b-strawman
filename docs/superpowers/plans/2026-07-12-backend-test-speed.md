# Backend Test-Speed Campaign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce backend `./mvnw clean verify` wall time and eliminate the leftover-process flakiness (stale Surefire/Maven JVMs holding GreenMail port 13025), by (a) converting heavyweight `@SpringBootTest` tests that exercise pure logic into plain unit tests — each conversion approved by 2 independent consensus agents — and (b) hardening the Surefire/JVM lifecycle.

**Architecture:** One shared worktree (`perf/backend-test-speed-2026-07`). Phase 0 measures a fresh baseline (full clean verify with surefire reports). Phase 1 analyses: hot-class ranking from the reports × a sweep of `@SpringBootTest` classes (bias to the ~70 files added since 2026-06-23, which post-date the last audit). Phase 2 is the consensus gate: every integration→unit conversion needs 2 independent reviewer-agent approvals before a builder may touch it. Phase 3 implements sequentially (one builder at a time — house rule), Phase 4 runs the full verify in the worktree and compares timings, Phase 5 is PR + review + merge through the merge gate.

**Tech Stack:** Java 25, Spring Boot 4, Maven Surefire, JUnit 5, embedded zonky Postgres, GreenMail singleton (:13025).

## Global Constraints

- Full clean `./mvnw verify` is the merge bar (CLAUDE.md Quality Gate 1). Targeted runs are inner-loop only.
- Never two concurrent `./mvnw verify` runs (GreenMail fixed port 13025). Baseline must finish before the worktree verify starts.
- Surefire heap stays pinned at `-Xmx3g` via `@{argLine}` (PR #1487) — do not touch.
- JaCoCo stays gated behind `-Pcoverage`.
- `*ControllerTest` vs `*IntegrationTest` filename pairing ≠ duplicate coverage — read both files before claiming overlap.
- Preserve the six invariants in memory `feedback_test_speed_conventions` (GreenMail singleton, no new `@TestPropertySource`/`@DynamicPropertySource`/`@MockitoBean` context keys, ArchUnit rules stay strict).
- A conversion must preserve the behaviour under test. If the test's value IS the integration (DB constraint, security chain, tenant isolation, migration shape), it is NOT convertible — consensus agents must reject.
- Do NOT re-litigate measured dead ends: context-cache maxSize (no-op), golden-schema clone provisioning (rejected 2026-06-23), parallel Surefire forks (contraindicated), shard-Postgres consolidation (~4–6s only).
- One PR for the campaign unless scope forces a split; every PR reviewed (no exemptions).

---

### Task 0: Baseline measurement (STARTED — running detached)

**Files:** none (measurement only)

- [x] **Step 1:** Confirm no stale JVMs: `pgrep -fl "ForkedBooter|surefire"` empty, `lsof -i :13025` empty.
- [x] **Step 2:** Run detached: `./mvnw clean verify -Dlogging.level.org.springframework.test.context.cache=DEBUG` → log + `baseline-verify.done` marker in scratchpad.
- [ ] **Step 3:** On completion: record wall time, test count, failures. Extract per-class times from `backend/target/surefire-reports/TEST-*.xml` (sum `<testcase time>` per class, rank descending, top 40). Save ranking to `tasks/test-speed-2026-07/hot-classes.md`.
- [ ] **Step 4:** If baseline is NOT green: stop, root-cause per systematic-debugging, fix on main first (separate PR). Flake ≠ environmental — root-cause every failure.

### Task 1: Candidate analysis (2 read-only scout agents, parallel — allowed since they build nothing)

**Files:** produce `tasks/test-speed-2026-07/candidates.md` and `tasks/test-speed-2026-07/flakiness-fixes.md`

- [ ] **Step 1 (Scout A — conversion candidates):** Sweep `backend/src/test/java` for `@SpringBootTest` classes whose test methods exercise pure/near-pure logic (no MockMvc round-trip needed, no DB assertions, no provisioning dependency). Prioritise files added/changed since 2026-06-23 (`git log --since=2026-06-23 --name-only -- backend/src/test`). For each candidate: class name, current runtime (from Task 0 ranking), what it actually asserts, proposed conversion shape (plain JUnit / Mockito / `ApplicationContextRunner` slice), and the risk of coverage loss. Also flag classes that share the baseline context (cheap — conversion saves little) vs classes owning a unique context (expensive — conversion kills a whole context build).
- [ ] **Step 2 (Scout B — flakiness/leftover processes):** Read `backend/pom.xml` Surefire config, `GreenMailTestSupport`, lessons.md entries (HikariPool hang 2026-02-20, zombie JVMs 2026-06-10, concurrent-verify 2026-05-29). Evaluate exactly these levers and recommend go/no-go each: (1) `<forkedProcessExitTimeoutInSeconds>` so hung forks self-kill; (2) `<shutdown>kill</shutdown>` on Surefire; (3) a `backend/scripts/verify-preflight.sh` that detects+kills stale Maven/Surefire JVMs (etime threshold) and checks :13025 free, wired into docs/skills that run verifies; (4) Hikari `register-mbeans`/teardown if evidence supports it. No speculative fixes — each must map to an observed failure mode.

### Task 2: Consensus gate (2 independent reviewer agents per conversion batch)

**Files:** `tasks/test-speed-2026-07/consensus.md`

- [ ] **Step 1:** For each Scout-A candidate, dispatch 2 independent reviewer agents (separate contexts, neither sees the other's verdict) with the candidate file's full source and the question: "Does converting this to the proposed unit-test shape lose integration coverage that matters? APPROVE/REJECT + reasoning."
- [ ] **Step 2:** Only candidates with 2/2 APPROVE proceed. 1/2 = rejected (no tie-breaks, conservative by design). Record all verdicts + reasoning in consensus.md.

### Task 3: Implement approved conversions + flakiness fixes (sequential builders, shared worktree)

**Files:** per approved candidate (filled in from consensus.md); `backend/pom.xml`; possibly `backend/scripts/verify-preflight.sh`, `backend/CLAUDE.md`.

Per conversion, the builder follows TDD-preserving discipline:
- [ ] **Step 1:** Run the existing test class alone; confirm green + record its time.
- [ ] **Step 2:** Write the replacement unit test in the same package (keep the class name unless the suffix taxonomy demands `*Test`); assertions must cover every behaviour the old test asserted (map 1:1, note any intentionally dropped assertion with justification).
- [ ] **Step 3:** Run the new test: green. Delete/strip the old `@SpringBootTest` scaffolding.
- [ ] **Step 4:** Run the whole package + any package importing the changed classes (test-scoping rule).
- [ ] **Step 5:** Commit (one commit per class or per same-shape cluster).

Flakiness fixes (from Task 1 Scout B go/no-go): apply only the "go" items, each its own commit, each with the observed-failure-mode justification in the commit message.

### Task 4: Verify + compare

- [ ] **Step 1:** Preflight: no stale JVMs, :13025 free, baseline run fully finished.
- [ ] **Step 2:** Full `./mvnw clean verify` in the worktree (detached + marker, same method as Task 0). Must be GREEN.
- [ ] **Step 3:** Compare wall time + per-class times vs baseline. Expect honest numbers; ±1 min is machine noise — report per-class deltas as the real proof.
- [ ] **Step 4:** Confirm test count delta = conversions only (no accidental coverage loss; count `<testcase>` elements, not `<testsuite tests=N>`).

### Task 5: PR, review, merge

- [ ] **Step 1:** Push branch, open PR with: baseline vs after table, consensus.md summary, per-conversion coverage map, flakiness fixes with root-cause references.
- [ ] **Step 2:** CodeRabbit poll (bounded) + 2 feature-dev:code-reviewer agents. Address all findings.
- [ ] **Step 3:** Merge via the gate (verify marker + review audit trail in PR body). Update tasks/todo.md review section + lessons.md if corrections occurred.
