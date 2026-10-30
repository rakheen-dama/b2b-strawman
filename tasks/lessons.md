# Lessons Learned

## Surefire JVM Hang — HikariPool Housekeeping Threads (2026-02-20)

**Symptom**: After all tests complete, the Surefire forked JVM hangs indefinitely (2+ hours observed). Test report files stop being written but the JVM process stays alive at 0% CPU.

**Root cause**: HikariCP connection pool housekeeping threads (daemon threads) are kept alive by something holding a non-daemon thread reference. The JVM won't exit because non-daemon threads are still running. The `surefire-forkedjvm-last-ditch-daemon-shutdown-thread-60s` thread sometimes triggers but doesn't always terminate the process.

**Impact**: Blocks the entire `/phase` pipeline — the `run-phase.sh` script waits for the claude process, which waits for the Surefire process.

**Workaround**: `kill <surefire-pid>` — the agent gets a non-zero exit and can re-run or proceed.

**Potential fix**: Add `spring.datasource.hikari.register-mbeans: false` and ensure `@DirtiesContext` or explicit pool shutdown in test teardown. Alternatively, configure Surefire with `<forkedProcessExitTimeoutInSeconds>120</forkedProcessExitTimeoutInSeconds>` to auto-kill hung forks.

**Detection**: If a Surefire process has been running 60+ minutes with 0% CPU and no new TEST-*.xml files in the last 30 minutes, it's hung.

## Detail Section Row Must Be Marked Done (2026-02-20)

**Symptom**: `run-phase.sh` crashes or re-runs a completed slice because it doesn't detect it as Done.

**Root cause**: The `/epic_v2` skill instructed agents to mark Done in "the row starting with `| **{SLICE}** |`" but agents were updating the Implementation Order table (which has format `| 2a | Epic 116 | 116A |`) instead of the Detail Section rows (which have format `| **116A** | 116.1–116.10 |`).

**Fix**: Updated `/epic_v2` SKILL.md to explicitly require updating FOUR locations: (1) Detail Section row (most critical), (2) Implementation Order row, (3) Epic Overview, (4) TASKS.md.

**Detection**: Run `./scripts/run-phase.sh {N} --dry-run` — if "Done" count doesn't match expected, check detail section rows.

## Backend Test Speed — Context-Cache Evictions (2026-04-18)

**Symptom**: `./mvnw test` wall time regressed from ~13m (post-April optimization) to ~10m 48s over a 3-week period, despite no Testcontainers/LocalStack violations.

**Root cause**: Every unique `@TestPropertySource`, `@DynamicPropertySource`, and `@MockitoBean`/`@SpyBean` combination creates a distinct Spring ApplicationContext cache key. Even with the Zonky embedded Postgres singleton shared across contexts, Spring still rebuilds beans + Hibernate + Flyway on each cache miss (~2–3s per rebuild). 27 unique combinations cost ~60–80s of wasted time per run.

**Fix pattern**:
- Move static test overrides into `backend/src/test/resources/application-test.yml` instead of per-class `@TestPropertySource`. Example: `spring.mail.*` for GreenMail was moved there, letting 3 email tests share one context.
- For `@DynamicPropertySource` used to inject runtime-generated values (e.g., ECDSA keypairs), only keep it if the runtime generation is structurally required — prefer a hardcoded static fake when safe.
- For `@MockitoBean` duplicated across 3+ tests with identical behaviour, extract a `@TestConfiguration` class and `@Import` it so tests share one context.

**Anti-patterns to reject in review**:
- Adding `@TestPropertySource` for a value that could live in `application-test.yml`.
- Adding `@DynamicPropertySource` where a static value would work.
- Copy-pasting GreenMail port definitions — use the test default `spring.mail.port=13025`.
- Classifying a `*ControllerTest` and `*IntegrationTest` as "duplicates" based on filename — verify they actually cover the same layer before deleting either.

**Detection**: `grep -rn "@TestPropertySource\|@DynamicPropertySource\|@MockitoBean" backend/src/test/java | wc -l` — if this count grows in a PR, scrutinize.

## Backend Test Speed — JaCoCo Gated Behind `-Pcoverage` Profile (2026-04-18)

**Background**: JaCoCo instrumentation adds ~10–15% runtime overhead on every test run. Default CI `./mvnw test` and local dev loops don't need the coverage report — release/nightly can opt in.

**Change**: `backend/pom.xml` moved `jacoco-maven-plugin` from `<build><plugins>` into a `<profile id="coverage">`. Default `./mvnw test` skips instrumentation. Use `./mvnw -Pcoverage test` when a coverage report is required.

**Migration note**: If CI later adds Codecov/SonarQube integration, enable the `coverage` profile specifically for that job — do NOT re-add JaCoCo to the default build.

## @MockitoBean Audit — 2026-04-18

**Baseline**: 19 `@MockitoBean` declarations across 18 test files. Each creates a distinct Spring-context cache key, so every new mock-bean combination costs ~2–3s for a context rebuild.

**Removed (unused declarations, 2 files)**:
- `PortalPaymentStatusIntegrationTest` had `@MockitoBean StorageService storageService` with no references in the test body — dead code. Removed.
- `PlatformAdminControllerTest` had `@MockitoBean JavaMailSender javaMailSender` with no references. Removed.

**Retained with rationale (17 declarations across 16 files)**:
- **Bespoke stubbing / verification (9 files)**: `DataExportServiceTest`, `AnonymizationControllerTest`, `DataAnonymizationServiceTest`, `DataExportControllerTest`, `InvitationServiceTest`, `OrgCreationControllerTest`, `DemoCleanupServiceTest`, `DemoProvisionServiceTest`, `AccessRequestApprovalServiceTest`. Each test stubs a different method subset with different returns or uses `verify()` + `ArgumentCaptor`. Not safely consolidable without losing mock isolation between test methods.
- **Future migration candidates (deferred)**:
  - `PortalBrandingControllerIntegrationTest`, `PortalInvoiceControllerIntegrationTest`, `PortalCommentPostIntegrationTest` — all three only stub `storageService.generateDownloadUrl()`. They could drop `@MockitoBean` entirely by using `InMemoryStorageService` with keys that pass its regex (update test data + change assertions from `https://s3.example.com/...` to `http://test-storage/test-bucket/...`). ~6s potential saving.
  - `AccessRequestPublicControllerTest`, `AccessRequestVerifyTest` — both stub `JavaMailSender.createMimeMessage()` identically. Now that `GreenMailTestSupport` provides a JVM-singleton SMTP server on port 13025, these could send real mail and assert on `greenMail.getReceivedMessages()` instead. ~4s potential saving.
- **Single-use (legitimate)**: `SubscriptionItnIntegrationTest` mocks `PlatformPayFastService`; `AssistantControllerTest` + `AssistantServiceTest` mock `LlmChatProviderRegistry` (the service mock comment says "Mock the entire registry to avoid the duplicate provider ID issue").

**Why not force a shared `@TestConfiguration` for Keycloak mocks**: attempted mentally — the 4 Keycloak tests stub different method subsets, so a shared mock instance would need manual `Mockito.reset()` between methods. That trades ~6–9s of context rebuild for the risk of stub-leakage flakiness. Not a net win.

## Dependabot Sweep — pnpm 10 Rewrites Specifiers (2026-04-29)

**Symptom**: Plain `pnpm update` (no `--latest` flag) rewrote ~30 package.json specifiers across `frontend/`, `portal/`, etc. — tightening loose ranges like `^4` → `^4.2.4`, and incidentally bumping `recharts ^3.7.0` → `^3.8.1`. The recharts bump introduced a breaking `Formatter` type signature change that broke the Next.js production build.

**Root cause**: In pnpm 10 (vs older versions), `pnpm update` updates the package.json specifier to the current installed version, not just the lockfile. This means it can pull in minor-bump breaking changes even when the user only intended a CVE-fix lockfile refresh.

**Fix pattern**:
- For dependabot CVE work, prefer `pnpm update <specific-pkg>` per affected package over the broader `pnpm update`.
- When a broader sweep is preferred for batch efficiency, run `pnpm test` AND `pnpm run build` per package — the type checker catches breaking transitives that lint and unit tests miss.
- If a single transitive bump breaks compilation, revert *just that one* with `pnpm add <pkg>@~OLD_MAJOR.MINOR.0` (tilde range) rather than abandoning the whole sweep.

**Detection**: After `pnpm update`, `git diff --stat` shows package.json edits much larger than expected if `--latest` wasn't used. That's the cue to inspect for breaking minor bumps.

## Dependabot Sweep — Stacked PRs for Same-File Dependencies (2026-04-29)

**Pattern**: When two dependabot PRs both touch the same lockfile (e.g. PR-A is a broad refresh sweep, PR-B is a single explicit pin bump on `next`), cut PR-B from PR-A's branch tip rather than from `main`.

**Why**: Lockfile diffs from concurrent PRs cannot merge. If both branch from `main`, the second to merge will need a manual rebase + `pnpm install` re-run. Stacking PR-B on PR-A means GitHub renders only the incremental diff in PR-B, and the merge of PR-A leaves PR-B with no conflict. Operationally `gh pr create --base <stacked-branch>` and retarget to `main` once the parent PR merges.

**When NOT to stack**: If the two PRs touch different packages (e.g. backend/pom.xml vs frontend/pnpm-lock.yaml), branch them both from `main` — they're naturally independent and stacking would just add coupling.

## Branch Cutting — Uncommitted Changes Follow You (2026-04-29)

**Symptom**: Edited `backend/pom.xml` for PR-C, kicked off long-running `./mvnw verify` in the background, then `git checkout -b fix/slice2-2A-pr-D-mockidp-express` from main. The pom.xml change carried into the PR-D working tree (because git only refuses checkout when there's an actual conflict, not when the changes apply cleanly to the new branch).

**Risk**: Easy to accidentally commit the wrong scope into the wrong PR.

**Fix pattern**:
1. Always `git status` before `git checkout -b` if you've been editing files.
2. If there are uncommitted changes for the previous branch, commit them first (or `git stash`).
3. If you've already crossed the boundary, switch back, commit, then re-create the new branch — the working tree should be clean.

**Detection**: After cutting a new branch, run `git status --short`. If it shows files unrelated to the new PR's scope, you carried changes you didn't intend to.

## Dormant Directory Audit — frontend-v2 (2026-04-29)

**Pattern**: `frontend-v2/` was a parallel UI-rebuild experiment scaffolded 2026-02-28, last touched 2026-02-28 (Epic 249A), held the only `@clerk/nextjs` dependency in the entire repo (contradicting `frontend/CLAUDE.md`'s anti-pattern rule), and was responsible for 1 critical + ~10 high open Dependabot alerts. It was not referenced by any compose script, `svc.sh`, or root workspace.

**Lesson**: Before triaging dependabot alerts, sweep the repo for orphan top-level directories with their own `package.json`/`pnpm-lock.yaml`. If they're not in any compose/script/svc reference and have no recent commits (>30 days), question whether they should exist at all. Deleting often eliminates more alerts than upgrading.

**Detection one-liner**: `find . -maxdepth 3 -name 'pnpm-lock.yaml' -not -path '*/node_modules/*'` then for each, check `grep -rn "<dirname>" --include="*.sh" --include="*.yml" --include="*.yaml" -l` for active references.



## Auto-merge bypassed reviews — 8 PRs in one phase run (2026-05-03)

**Symptom**: During `/phase_v2 70`, slices 511A and earlier 7 PRs (#1276, 1277, 1278, 1279, 1281, 1282, 1284, 1286) were merged to main with **no real CodeRabbit review** (only the auto-summary comment). 4 of those (#1281, 1282, 1284, 1286) also showed **no evidence the two superpowers reviews ran** — single commit, no `fix: address review findings` commit, sub-4-minute open→merge windows. Quality Gate #2 in CLAUDE.md ("every agent-authored PR gets a review pass before merge") was violated systemically.

**Root cause** (3 lined up):
1. `.claude/hooks/pre-pr-merge-gate.sh` only checked stale `verify-{backend,frontend,portal}.json` markers within 24h. It did NOT check review presence, did NOT verify the marker's `commit` was an ancestor of the PR head (the docstring promised this but the code never did it). One 24h-old marker satisfied the gate for every subsequent PR.
2. `.claude/skills/epic_v2/SKILL.md` Step 4.2 said "Best-effort CodeRabbit collection (one shot, no polling)" and Step 5 said "CodeRabbit's status check state is NOT a blocker. Don't wait for it. Don't poll for it." — explicit policy hole letting agents merge ~90s after PR open.
3. The skill never instructed the agent to write the review verdict block into the PR body, so there was no GitHub-side audit trail to enforce against. Agent narrative in chat was the only "evidence" reviews ran.

**Fix applied** (this session):
1. `pre-pr-merge-gate.sh` — added (a) marker-commit-ancestry check, (b) PR-body audit-trail check requiring two `## Verdict: APPROVE` lines and one `## CodeRabbit: REVIEWED|DEFERRED` line.
2. `epic_v2/SKILL.md` Step 4.2 — replaced "no polling" with bounded 5-min wait (10× 30s iterations) for CodeRabbit; if CR doesn't arrive, agent records `## CodeRabbit: DEFERRED` in PR body and proceeds. Avoids the agent-falls-asleep-waiting failure mode.
3. `epic_v2/SKILL.md` new Step 4.5 — agent MUST `gh pr edit --body-file ...` to append the audit trail (both verdict blocks + CR line) before any merge attempt. Hook will reject merge otherwise.

**Detection**: For any merged PR, `gh pr view <N> --json reviews,body` should show either (a) a non-"Review failed" CodeRabbit review object, or (b) a `## CodeRabbit: DEFERRED` line in the body. Both must be paired with two `## Verdict: APPROVE` markers in the body.

**Damage to audit**: PRs #1281, 1282, 1284, 1286 on main are unreviewed by both gates. Need separate `/review` pass before considering them trustworthy.

---

## Lesson: never run two `./mvnw verify` (or any GreenMail-using tests) concurrently — 2026-05-29

**Context**: During the infra-review PR series I started a second `./mvnw verify` (commit+push+verify for D3 review fixes) while the previous D3 verify was still running. The second run failed with 11 email/notification context errors — all `NoClassDefFoundError: Could not initialize class GreenMailTestSupport` → `IllegalStateException: Could not start mail server smtp:127.0.0.1:13025`.

**Root cause**: GreenMail is a JVM-singleton SMTP server bound to port **13025** (see `application-test.yml` + `GreenMailTestSupport`). Backend tests run sequentially precisely so the port is never contended. Two concurrent Maven test JVMs both try to bind 13025 → the second fails to start the mail server → every test whose static initializer touches `GreenMailTestSupport` errors at ~0ms (context/class-init failure, not test logic).

**How to apply**:
- Run at most ONE `./mvnw verify`/`test` at a time. Before starting a verify, ensure no other is running (`pgrep -fl surefire`, `lsof -iTCP:13025`).
- If you see a burst of email/notification tests erroring at ~0.001s with "Could not start mail server :13025", suspect a concurrent verify (or a leftover JVM holding the port), not a code defect. Re-run clean.
- Don't chain `commit && push && verify` in the background while a prior verify is still in flight.

## Subagents must not end their turn waiting on background work (2026-06-11; RECURRED 2026-06-12)

**Recurrence (2026-06-12, wave-2 embeddables builder)**: the prompt explicitly prohibited `run_in_background` + end-of-turn monitor, and the agent STILL died this way — it armed a `Monitor` on its verify log and ended its turn with "I'll wait for that event rather than continue polling," rationalizing that a Monitor is not `run_in_background`. Its maven/surefire JVMs survived as orphans holding port 13025 with no pipe reader (a later-blocking, unrecordable run). Orchestrator killed PIDs, salvaged the uncommitted worktree, committed, and re-ran the verify itself.
**Prompt fix**: phrase the rule by OUTCOME, not mechanism: "your turn must not end until the verify's exit code is in your transcript — no monitors, no notifications, no waiting of any kind; if you are about to end your turn and the verify has not printed BUILD SUCCESS/FAILURE in YOUR tool output, you have failed the task." Orchestrator must also check `pgrep` for orphan JVMs whenever ANY implementation subagent ends, not just on explicit "waiting on monitor" reports.
**Escalation (same incident, second revival)**: a "completed" subagent whose monitor later fires gets ANOTHER turn and can start ANOTHER verify — it raced the orchestrator's salvage verify in the SAME worktree (two surefire forks, second one lost GreenMail 13025 → "ApplicationContext failure threshold exceeded" cascade in unrelated portal tests, 8 errors; classic concurrent-verify signature, not a code defect). It also kept EDITING the tree after the orchestrator's salvage commit, so the commit was missing two fixes. Mitigation that works: after salvaging a dead builder, immediately commit its tree AND `git worktree remove --force` its worktree — the branch survives in the shared repo, and any later revival finds no working directory and dies harmlessly. Never leave a salvaged zombie worktree mounted.

**What happened**: Two implementation subagents (Wave 0.2 builder, scheduling-gate builder) started `./mvnw verify` in the background, armed a monitor, and ended their turn "waiting for the monitor to fire." A subagent's monitors and child processes die with its session — the second agent's surefire fork was SIGTERMed mid-test (exit 143, fake "VM crash" on whatever test was in flight), and both verifies had to be re-run by the orchestrator.

**How to apply**:
- Subagent prompts for tasks that include a full verify must say: run the verify as a blocking foreground command with a 600000ms timeout (poll a log file from the same turn if needed) — never `run_in_background` + end-of-turn monitor.
- Orchestrator: if a subagent reports "verify is running, waiting on monitor," treat the verify as DEAD. Check `ps` for surviving JVMs, then re-run it in the orchestrator session.
- Surefire exit code 143 + "The forked VM terminated without properly saying goodbye" + a single "crashed test" = the run was killed externally; it is NOT a failure of the named test. Re-run before triaging that test.

## Never bulk force-delete branches you didn't create (2026-06-11)

**What happened**: a worktree-cleanup sweep ran `git branch | grep -E 'fix/|chore/' | xargs git branch -D`, which force-deleted old local branches from PRIOR sessions, not just this session's merged ones. All happened to exist on the remote (verified post-hoc, nothing lost), but `-D` skips the merged check and the blast radius was unintended.

**How to apply**:
- Delete only branches you created this session, by explicit name.
- Use `git branch -d` (lowercase) so unmerged branches refuse deletion; escalate to `-D` only per-branch with a stated reason.
- Before any branch deletion, `git merge-base --is-ancestor <sha> origin/main` or check `git branch -r --contains` — and capture the SHA list in the output for recovery.

## Merge gate: re-fetch review comments in their own step, never alongside the merge (2026-06-11)

**What happened**: PR #1419 was merged by a chained command that fetched the CodeRabbit comment count AND ran `gh pr merge` unconditionally in the same invocation. A Major comment (root .dockerignore secrets-in-context) had landed after the previous gate check; the count was printed but the merge no longer depended on it. The finding was valid and had to be fixed in a follow-up PR (#1420).

**How to apply**:
- The comment check and the merge must be separate tool calls: fetch top-level comment count + read any new ones → only then, in a NEW command, merge.
- Never chain `merge` after a status query with `;` or `&&` where the merge doesn't branch on the query result.
- CodeRabbit's "pass" check-status is not the same as "no comments" — it reports pass while still posting review comments. Gate on the comments API, not the check.

## Test "count-bleed": never assert an exact global count over accumulating shared-tenant state (2026-06-22)

**What happened**: `AutomationSchedulerScheduledTriggerIntegrationTest` failed CI on an unrelated PR (#1489) with `expected: 5L but was: 13L`. Root cause: the class is `@TestInstance(PER_CLASS)` against a tenant provisioned once in `@BeforeAll` and never reset between methods, so sibling methods accumulate ACTIVE projects AND due SCHEDULED rules. The test derived its expected value from a global `projectRepository.findByStatus(ACTIVE).size()` and verified `Mockito.times(activeProjectCount)`. But `processScheduledTenant()` fires *all* due rules, each project-scoped one fanning out per active project, so runner invocations = (accumulated rules) × (active projects) — far more than one rule's project count. Passed in isolation, flaked in the full suite. Fixed in PR #1490 (containment-by-id + rule-scoped delta); a suite-wide sweep confirmed it was the only instance. A reviewer's suggested "tighten back to `containsExactlyInAnyOrder` over the two seeded ids" was declined because the shared mock carries no ruleId, so accumulated rules make those ids recur → that matcher would re-flake.

**How to apply**:
- In any `@SpringBootTest`/`PER_CLASS` test whose tenant is not reset per method, do NOT assert an exact count (`times(query.size())`, `isEqualTo(repo.findAll().size())`, `.hasSize(globalCount)`) over state other methods/classes can add to. Especially scheduler/fan-out/reconciliation/`processScheduled*`/`run(null)` paths that process "all" of something.
- Use instead: (a) containment by id — assert the specific ids you seeded are present/absent; (b) baseline+delta — `count()` before, assert the delta after; or (c) a query scoped by the id created in this test.
- When de-flaking, never "fix" by substituting a different exact count, and beware `containsExactlyInAnyOrder` over shared-mock invocations whose ids can recur across accumulated rows — both reintroduce the flake.
- Documented as a standing convention in `backend/CLAUDE.md` → Test coupling rules.

## macOS sleep absorption fakes monster-slow tests in unattended runs (2026-07-12)

**Symptom**: A detached full `./mvnw clean verify` reported 60 min wall (norm ~23–26 min), and the surefire reports showed two absurd outliers: one testcase at 988s (`InvoiceLifecycleIntegrationTest.shouldRejectApproveWhenNotDraft`, every sibling ≤1.5s) and one class with 1013s of setup time but 0.2s of testcase time (`StatementControllerIntegrationTest`). Neither file had changed in weeks. It looked exactly like a new pathological hot class or a hung process.

**Root cause**: The machine went to system sleep twice mid-run ("Maintenance Sleep" 989s, "Sleep Service Back to Sleep" 977s). Processes are frozen during sleep but wall-clock keeps advancing, so whatever testcase (or `@BeforeAll`) is in flight absorbs the entire sleep window into its measured time. `pmset -g log` matched both stalls to the second. A nohup'd Maven build does not prevent idle sleep — no user activity, no sleep assertion.

**Fix**: Full verifies go through `backend/scripts/verify.sh`, which runs Maven under `caffeinate -is` (falls back to bare `./mvnw` where caffeinate doesn't exist). Shipped in PR #1547.

**Detection / triage rule**: Before diagnosing a suddenly-monster-slow test class from an unattended run, check the shape and the power log:
1. One testcase (or class setup) absorbing ~all the class time while siblings stay normal, in a file with no recent changes → suspect sleep, not code.
2. `pmset -g log | grep -E "Entering Sleep"` — if a sleep window's duration (the trailing `N secs`) ≈ the stall and its timestamp falls inside the run, it's proven environmental — and unlike most "environmental" claims, this one comes with second-level evidence, which is the bar `feedback_test_failures_not_environmental` requires.
3. Correct the run's wall time by subtracting matched sleep windows before comparing against any baseline.

## No self-granted review exemptions on PRs to main (2026-07-09)
- Merged docs-only PR #1530 (one status.md log line) to main without any review pass, self-judging it exempt. User called it out.
- The documentation-only carve-out exists ONLY in the pre-pr-merge-gate hook (verify markers); CLAUDE.md §2's review requirement has NO docs exemption.
- Rule (user-stated policy 2026-07-09): **ALL PRs must be reviewed — no exemptions**, docs-only included, regardless of size or target branch. If a review was skipped, obtain a retrospective review immediately. If a change feels too trivial to review, that's a judgment to surface, not to act on.

## Stale `.next` cache fakes a Turbopack `next/font/google` build failure (2026-10-16)

**Symptom**: `pnpm run build` in a worktree exited 1 with 24 Turbopack errors, all `Module not found: Can't resolve '@vercel/turbopack-next/internal/font/google/font'` / "next/font/google queries have exactly one entry", traced to `app/layout.tsx`'s IBM Plex Sans import — a file the PR (Epic 596, #1565) never touched. Frontend CI on the same commit was green and the builder's earlier build in the same worktree had passed.

**Root cause**: the second build ran on top of the builder's existing `.next` Turbopack cache (plus a concurrent full backend verify loading the machine). `rm -rf .next` and rebuilding passed cleanly with zero font errors — reproduced-and-resolved, not dismissed.

**How to apply**:
- A `next/font/google` resolution error in a file the diff doesn't touch, on a tree whose CI build is green → suspect the local `.next` cache first. Re-run once with `rm -rf .next` before investigating code.
- Marker-grade frontend verifies should start from a clean `.next` (`rm -rf .next` before `pnpm run build`) so the verify can't inherit a builder's cache state.
- Still record the first failure honestly in the marker summary / PR; the clean rerun is the evidence, not the assumption.

## `backend/scripts/verify.sh` appends args AFTER `verify` — `verify.sh clean` runs verify, then wipes the reports (2026-10-16)
- `verify.sh` does `exec caffeinate -is ./mvnw verify "$@"`, so `verify.sh clean -q` executes goals in order `verify clean`: the suite runs, then `clean` deletes `target/surefire-reports` — a failure would leave nothing to triage. Caught during Epic 597 only because the process list showed `verify clean -q`.
- Killing a `nohup bash -c '…; echo EXIT > marker'` wrapper with `pkill -f` on a pattern does NOT reliably kill the exec'd Maven/Surefire JVMs — two verifies ended up sharing one `target/`. After any abort: `ps -p <pids>` to confirm death, then `kill -9` by PID before relaunching.
- **How to apply:** for a clean full verify run `bash scripts/verify-preflight.sh` then `caffeinate -is ./mvnw clean verify -q` (goal order explicit), or call `verify.sh` with no positional goals.

## 2026-10-19 — "No such behaviour exists" needs a whole-codebase grep (PR #1586)
- **What happened**: the QA-scenario spec asserted "there is no automatic ONBOARDING→ACTIVE transition on checklist completion" after grepping only `compliance/` (where the lifecycle service lives). The hook is in `checklist/ChecklistInstanceService.checkLifecycleAdvance`. Five scenario steps were written against the false claim; the reviewer caught it.
- **Rule**: a negative claim about product behaviour ("X never happens") must be backed by a repo-wide search for the transition/target (`grep -rn '"ACTIVE"\|LifecycleStatus.ACTIVE' backend/src/main/java`), not a search in the package the feature is named after. Positive claims can be local; negatives cannot.
- **Also**: when a `test.fixme` asserts a behaviour, treat it as a hypothesis worth checking in code, not as unverified — here it was right.

## 2026-10-19 — Hook hardening lessons (PR #1587)
- **Test hooks under `/bin/bash` (3.2) explicitly.** `bash foo.sh` resolved to homebrew bash 5; `${var,,}` passed every test and would have been a bad substitution → non-zero exit → fail-OPEN under the real interpreter. Run the matrix under both.
- **The harness shell is zsh.** An inline `[[ =~ ]]`/`BASH_REMATCH` unit test run directly in a Bash tool call gave wrong counts (zsh doesn't populate BASH_REMATCH). Put shell unit tests in a file and run them with the target interpreter.
- **`grep -o` consumes separators**: `(^|[[:space:]])--flag([[:space:]]|$)` undercounts adjacent repeats (`--squash --merge` → 1). Count tokens by word-splitting an already-validated string instead.
- **Match the full payload, not a truncated extraction.** `grep -oE '"command"\s*:\s*"[^"]*"'` stopped at the first escaped quote, so `echo "x" && gh pr merge …` was never gated. Use `jq -r .tool_input.command` and cross-check the raw payload.
- **An allow rule changes the threat model of every silent-allow path.** Before `Bash(gh pr merge:*)`, a hook that exited 0 without a decision fell back to the user prompt; after it, that path is an unreviewed merge. Audit every `exit 0` when adding an allow rule.
- **Print expectations next to results.** The two-strategies bypass was visible in my own matrix output (no deny line before the label) and I missed it until a reviewer pointed at it. `[ALLOW]/[DENY] expect X` labels made the next run self-checking.

## Phase 87 closure lessons (617C, 2026-10-22)

Build lessons (607–617B), copied here from the slice rows and memory so they live in the repo:
- **Use a ratchet, not an expected-red contract test (607A).** `WireVocabularyContractTest` started from a committed known-offenders baseline that each batch shrank (`backend/src/test/resources/wire/batch1-known-offenders.txt`, now empty). A test that is "expected to fail until batch N" teaches everyone to ignore red. **Rule:** for a multi-PR migration, pin the current offenders in a file and fail only on *new* ones.
- **Rename slices are bigger than the estimate (607B/607D/609D).** The real blast radius (DTOs, fixtures, e2e helpers, seed scripts) was several times the planned file count. **Rule:** grep the whole repo, including `compose/seed`, `scripts/`, e2e helpers and fixtures, before sizing a rename. Give the slice row a split threshold ("> 40 files → split").
- **`e2e-reseed.sh` does not wipe tenant data (612C, 613D).** A reseeded run showed state-dependent failures that a fresh stack disproved. **Rule:** gate-grade E2E is `e2e-down.sh && e2e-up.sh`, never reseed.
- **Cite carried tests by title, not line (606C → 617C).** Lines drifted between runs: day-00 `:326 → :273 → :282`, day-90 `:61 → :63` and `:177 → :179`. The "seven" carried locations were really six; one had been fixed by #1582 before the phase began. **Rule:** a carried-failure list is title-keyed. Re-derive lines with `grep -n "<title>"` at the run's HEAD and re-confirm each entry with a run before you carry it forward.
- **Know each Playwright config's defaults.** The legal configs default to :3000 and need `PLAYWRIGHT_BASE_URL=http://localhost:3001`; the default config needs `--config` (or `pnpm test:e2e`). A wrong base URL fails as "login page" noise.
- **Deleting hidden UI can unmask spec failures (616E).** Prove a "pre-existing" claim with a same-stack `--no-deps` frontend A/B, not by reasoning.
- **The Docker VM disk fills up (616F, 617C).** Run `docker builder prune -f` (no volumes touched) before `e2e-up.sh`; "No space left on device" mid-build is disk, not code.
- **The portal Playwright helper had never authenticated (617B).** The 90-day project soft-skips everything and exits 0 without `PORTAL_CONTACT_EMAIL`. **Rule:** exit 0 is not evidence. Open one written PNG with Read and confirm it is an authenticated page.
- **The CodeRabbit review limit (>150 files, or an over-quota account) means DEFERRED (610D, 610F, 616D, 617A).** Do not buy on-demand runs without the user. The two code-reviewer passes are the gate, and the PR body says `## CodeRabbit: DEFERRED — <reason>`.
- **The merge-gate hook matches text, anywhere in a Bash command.** Keep the merge command alone on its own line. The hook also refuses any multi-line Bash call that merely *contains* the merge phrase, e.g. a heredoc appending these lessons. Write such text with the Edit/Write tools instead.
- **Only a browser check catches dense-table overflow (615C/615D) and half-width widgets (615E).** Unit tests and builds were green while the page was unreadable. Browser-check UI slices at 1024 and 1440 px.

Capstone lessons (617C):
- **The skeleton-screenshot trap.** A screenshot taken before the data loads captures skeletons or a login page, and the PNG still "looks like" the page in a file listing. **Rule:** before every evidence or deck shot, wait for networkidle, then assert `document.querySelectorAll('[class*=animate-pulse],[class*=animate-spin]').length === 0`. After saving, Read the PNG and confirm the content.
- **`playwright test -- projects` matched every spec.** Playwright file filters are case-insensitive regexes over the absolute path, and the checkout lives under `/Users/…/Projects/…`. One "projects" run executed all 314 default-config tests against a populated tenant. **Rule:** use path-anchored filters (`e2e/tests/projects/`).
- **A portal login needs an `orgId`.** A bare `:3002/login` in a fresh context refuses every sign-in (GAP-L-66). The QA driver uses `/login?orgId=<slug>` (`PORTAL_ORG_SLUG`). When the scenario says "open /login", record the bare path as observed (FAIL, G-D1) *before* using the shim, and say that you used it.
- **The main checkout's `backend/target` can serve pre-rename classes.** At 617C start it dated from 2026-10-18 (no `DiaryController.class`, tenant migrations V1 only). `spring-boot:run` would have mixed stale classes into the walk. **Rule:** before `svc.sh start` after a phase's merges, run `./mvnw -q clean compile -DskipTests`. Prove the new code is served (class file present; new migrations applied on boot) before walking.
- **Keycloak login is two-step once an org exists.** The very first padmin login was single-step (email + password); every later login was `#username` → submit → `#password` → submit. Script both, and snapshot after each submit.
- **Dense-list and Home aggregates need a real-data walk.** `MatterListRowIntegrationTest` was green, but it creates leads only through `ProjectService` / `transferLead` (lowercase `lead`), never through template instantiation, which writes `LEAD`. `ClientListRowIntegrationTest` deliberately excludes SENT fee notes without a due date (the debtor-book rule), and every fee note the walk sent had no due date. So on real data "Fee earner", "Outstanding" and "Debtors" read empty (G-D11). **Rule:** when a projection joins on a free-string role or status, grep every writer of that column (`"LEAD"` vs `Roles.PROJECT_LEAD = "lead"`) and test with rows created through the real UI path.
- **Attribute gaps by code archaeology, and label it.** For "did this phase cause it?", compare the baseline commit with HEAD (`git show <base>:<path>`, `git log -S`, `git diff <base>..HEAD -- <path>`). Write the result as "code archaeology, not observed on the baseline stack". It answered all 5 HIGH gaps (pre-existing) without a second stack. Mind that a "phase baseline" commit can predate commits that are not part of the phase (here 9 Phase 86 tail commits sat between `41cb211fb` and 607A).
- **zsh history modifiers corrupt `$VAR:path` in git commands.** `git show $B:frontend/...` in zsh expands `$B:f…` as a modifier and fails with "ambiguous argument '41cb211fbontend/…'". **Rule:** write `${B}:path` (or quote it) in every `git show`/`git grep` that uses a revision variable.
- **A walker's workaround makes the downstream PASS rows unearned (617C review).** Walker C2 billed a disbursement that the UI could not write off (G-C9), made a Member the matter lead because no member UI exists (G-B3), and signed in to the portal through the `?orgId=` shim (G-D1). Each made the next steps work, and those steps were logged PASS. The review reclassified 10 rows to DEFERRED. **Rule:** when a walker sets up state the scenario did not specify to get past a product gap, every step that depends on that state is `DEFERRED — blocked by G-xx (ran on substituted pre-state: …)`, and the observed text stays in the Evidence cell. Input values the doc leaves open (a made-up email, which partner is matter lead) are not workarounds, unless the value was picked *to avoid* a gap and a later step depends on it.
- **Attribution scope must match the claim.** "None Phase-87-introduced" was written over 9 hand-picked gaps (13 table rows) while Phase 87 had touched 1 033 frontend/portal files. **Rule:** either classify every gap or state "of the M gaps classified (list); the rest unclassified". Mark sampled copy clusters as sampled.

## Phase 88 closure lessons (629D, 2026-10-30)

Build lessons (618–628), copied here from the slice rows, the briefs' rulings and the orchestrator's notes so they live in the repo:
- **Size a slice by its hit list, and keep the PR under 300 files (628A, 628D; walks 623B, 629B).** The terminology sweeps went over the file budget and worked as split builders: the first builder commits the guard red on purpose and writes the remaining-hit list to a file, and later builders split that list by directory and use the guard as the oracle. Walks split into foreground walker runs of at most about 200 browser calls, each pushing its own block, had no stream deaths. The merge hook reads `gh pr diff --name-only`, which returns nothing past about 300 files, so #1703 needed a human merge. **Rule:** size by the measured hit list, not the estimate; split before a PR crosses 300 files, and put evidence in the post-merge docs commit.
- **Migration numbers come from "next free at build time", not from the plan (OQ #9; 620A, 622B, 625C, 627B, 627H).** The plan named tenant V7 as the last file; the phase ended at V9. V4 could take two appended sections only because both merged before V4's first application on a persistent stack (Keycloak dev DB, 2026-10-24 07:01:53Z). An e2e stack that every gate wipes does not count as "applied", but editing a migration it has applied changes the checksum, so recreate that stack. **Rule:** run `ls $M/tenant | sort -V | tail -1` and a `flyway_schema_history` read on the Keycloak tenants before naming a migration and again before merging; record the decision in the slice row (`backend/db/BASELINE.md:70-71`).
- **Name how each reconcile branch will be observed (625E, 626D, 629A).** Platform templates are read-only in the UI (edit means Clone & Customize), so the "firm-edited row" branch of pack reconcile can only be unit-tested, and the first contract ("re-pin the install-time hash") was unimplementable because the hash methods are private to the installers. At the end-of-phase walk the reconcile was seen only in the startup log, because Session 0 then dropped and re-provisioned the walked tenant. **Rule:** for every reconcile branch, write down how it will be observed (log, UI on an existing tenant, or test only) and say "test only" in the closure instead of implying it was seen.
- **Strict assertions expose false positives in both directions (621D, 625D, 628A, 628E, 628F).** Un-fixme'ing an upstream step broke a downstream assertion that had passed only because the step never ran. A soft-skip fallback locator matched a different element that still carried the old string. The strict sweep turned vacuous passes into real failures: 83 of 187 default-suite guards targeted UI that no longer existed. **Rule:** before converting or un-skipping a spec, classify each guard by whether its target exists; run every spec you convert; after a shared-helper change, re-run every directory that imports it.
- **Task-row claims are hypotheses (620D, 622D, 625A, 626E, 626F).** Rows named wrong files, wrong "known" behaviour, impossible acceptance strings ("deposit approval", when deposits have no approval step) and wrong arithmetic; one row was wrong in eight places. A reproducer running in parallel with the scout, plus orchestrator rulings written into the brief before the build, gave builds with no fix cycles. **Rule:** measure and reproduce before building; rulings go into the brief, not into chat.
- **Only a real-browser observer catches some bug classes (622A, 622B, 624G, 626E, 626F, 628B).** Both reviewers and the unit tests passed while an HTML-escaped `&middot;` broke PDF rendering, a new enum value had no badge mapping (a 500 on fee-note pages), a table's min-content overflowed the page at 1280 px, a `contentRect` loop never settled, and draft-only content leaked. **Rule:** any slice whose output reaches a PDF, a badge map or a layout gets a real-browser observer; measure `scrollWidth` at 1024 and 1440 px (reinforces the Phase 87 lesson on browser-only overflow).
- **A new enum value or a newly reachable row is a cross-layer change (622B, 624A, 625A).** `PARTIALLY_PAID` broke frontend badge maps and the portal handler in a "backend" slice; stamping `project_id` on trust rows exposed one client's events to co-clients on a multi-client matter; projecting `amount_paid` missed a reversal path that never published the sync event. **Rule:** for a new state, census every consumer map in the same slice; for a newly reachable row, check many-to-many joins for confidentiality; for a new read-model field, census every writer.
- **Rulings can cause defects (626G, 627H, 627J).** A ruling to re-apply a sharing rule to every document whenever the set changes re-shared a deliberately unshared attachment (a HIGH); a ruling to copy the portal's bare `window.open` convention missed `noopener`; a ruling to "match X" leaked into a shared base class and changed two other pack types. **Rule:** a ruling distinguishes new items from kept ones, is scoped to the slice's own class, and is checked against security lint when it copies a convention.
- **First-pass reviewers skim (619C, 624F, 624H, 625A, 627C, 627F, 627H).** Even when told to read in full, first passes read diff hunks, skipped CLAUDE.md and ADRs, or read 4 of 22 files; one HIGH was found only by a strict re-dispatch. `feature-dev:code-reviewer` has no Bash, so it cannot re-run evidence checks. **Rule:** give reviewers an explicit file list, require "Files read in full: path — N lines" in the output, re-dispatch on any partial read, and use a read-only general-purpose reviewer that can run git and SQL for evidence or gate PRs.
- **A bound verdict needs a head that does not move (620E, 626A, 627A, 627F).** Committing observer evidence or a doc nit to the PR branch after the reviews voids both SHA-bound verdicts. **Rule:** post-review evidence, markers and doc nits go in the post-merge docs commit; the reviewed head never moves.
- **CodeRabbit rate limits are waits, not DEFERRED (619–628).** "Review limit reached — wait N minutes" followed by `@coderabbitai review` after N minutes produced real reviews, which found money-path Majors (622C) and a `timestamptz`/`timestamp` COALESCE bug in a migration (627B) that both reviewers missed. "No actionable comments" lands as an edit to the summary comment, not as a review. **Rule:** DEFERRED only when the review is paid-only or over the file budget (reinforces the Phase 87 CodeRabbit lesson).

Re-walk and closure lessons (623, 629):
- **Time of day is a test variable (629C → G88-F48 HIGH, G88-F49 LOW).** Suites run between 22:00Z and 24:00Z (00:00–02:00 SAST) date browser input in SAST and server-side postings in UTC; a fee transfer landed a day before its deposit and the interest run was misallocated. A diagnostic `TZ=UTC` re-run went green and stayed unscored. **Rule:** any server-side "today" uses the firm's zone; a date-sensitive failure inside that window is a product signal (read the data first, then make one controlled zone change), never a flake; never pin Playwright `timezoneId` to make a run green.
- **A diagnostic run never closes a scored item (629C ruling R-6).** An early ruling closed two carried tests on the unscored diagnostic run; review reversed it. **Rule:** "if run X passes it" means the scored run X; a DEFERRED item gets an owner who can actually run it (one of the 17 DEFERRED tests sat behind G88-F49, not G88-F48).
- **Verify markers must be committed in the bookkeeping step (G88-F35).** Five Epic-624 PRs passed the live hook, with the marker written in the main checkout, but their docs commits were made from another checkout and never `git add`-ed the marker, so the audit could not recover it. **Rule:** the docs commit adds `.claude/markers/verify-*.json` explicitly, and the epic gate checks that `git log -- .claude/markers` has one entry per code PR.
- **Walk evidence discipline (623A, 623B, 629A).** Split a mixed row into a/b rows (14.8a PASS / 14.8b FAIL), never "PASS with gap". "Scenario mismatch" needs file:line proving the design is deliberate; absence of a feature proves only absence ("design intent unproven"). Owner cells never name a closed slice. Never leave `$ SELECT …` placeholder lines; append a delimited read-only re-run. Redact `/accept/<token>`, `token=` and presigned query strings before committing logs. **Rule:** `.gitignore`'s `logs` rule drops `**/logs/*`, so evidence logs need `git add -f`, and every committed log is grepped for tokens first.
- **Markdown tables break on literal pipes and newlines (626E, 628G, 628H, 629B).** Page titles ("· Settings | Kazi"), regexes and multi-line notes broke task-file and evidence tables, and a scripted edit on one row merged two rows. **Rule:** escape `|` as `\|`, keep every row on one line, edit rows with exact line handling, and compare each row's unescaped pipe count with its header before opening a docs PR.
