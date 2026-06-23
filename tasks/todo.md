# Simplification Roadmap — Execution Tracker

Plan: `~/.claude/plans/tranquil-fluttering-patterson.md` (approved 2026-06-10)
Mode: orchestrator + sequential implementation subagents (one PR at a time).
(Previous gap-analysis plan in this file was verified complete — CommentService already on SHARED — and replaced.)

## Wave 0 — Defect fixes (one PR each, reproduce-before-fix)
- [x] 0.1 ReportingController authz — PR #1403 MERGED (squash). 6 endpoints guarded; reproduce-before-fix observed (200→403); full verify 5599/0; CodeRabbit Major (unsynced member) fixed + confirmed.
- [x] 0.2 accounting-za regulatory_deadlines — PR #1407 MERGED. Register premise was wrong (two distinct modules); Option A added the missing slug. Two-direction bug-class test; retainer_agreements divergence documented for triage. Verify 5602/0.
- [x] 0.3 legal-za packs.automation — PREMISE DISPROVEN: pack exists and already auto-installs (installer selects by pack's own verticalProfile field; ProfileDefinition.packs() map is vestigial). Pinning test added; register corrected. PR open, awaiting gate.
- [x] 0.4 `/portal/dev/**` profile gate — PR #1410 open (real gate was CustomerAuthFilter.shouldNotFilter, not SecurityConfig permit; profiles local/dev/keycloak with evidence; 404→401 flip observed). Awaiting CI.

## Wave 0.5 — Unplanned (discovered during 0.2 verify)
- [x] PR #1406 JobWorker test flake — MERGED (CodeRabbit zero comments, Backend CI pass)
- [x] Global scheduling gate — PR #1408 MERGED. Scheduler log lines 145→0 across full suite; zero opt-ins needed; 5602/0. Dead spring.scheduling.enabled config replaced with working kazi.scheduling.enabled.

## Wave 1 — Hygiene
- [x] 1.1 Root cleanup — PR #1411 MERGED (13 media → documentation/screenshots/, proj-snapshot.yml deleted, gitignore hardened, root node_modules deleted)
- [x] 1.2 Docs truth pass — PR #1414 MERGED (docs map, historical banner, observability claims corrected; one CodeRabbit Minor fixed)
- [x] 1.3 MemberFilter Caffeine — ALREADY DONE on main since PR #42; TD-001 was stale. Register fix merged as PR #1412.
- [x] 1.4 Portal in dev compose — PR #1413 MERGED
- [x] 1.5 Thymeleaf exclusion — PREMISE DISPROVEN (InvoiceRenderingService/AcceptanceCertificateService inject TemplateEngine in prod; exclusion would break invoicing). TD-003 invalidated — PR #1415 MERGED. WAVE 1 COMPLETE.

## Wave 2 — pnpm workspace (staged, one app per PR)
- [x] 2.1 Workspace bootstrap — PR #1416 MERGED (+ ci.yml pnpm version-input removal after CodeRabbit catch)
- [x] 2.2 Portal joins — PR #1417 MERGED (zero dep drift via lockfile graft; 191 tests; image run-proven; 4th build site seed-images.yml found; Node 20 alignment)
- [x] 2.3 Frontend joins — PR #1419 MERGED (zero drift graft; 2,339 tests; image run-proven; 7 build sites; removes #1418's interim flag) — NOW BAKING before 2.4
- [x] 2.3a unplanned: PR #1418 MERGED — pre-existing prettier breakage on main + interim --ignore-workspace CI fix (#1416 had silently broken frontend CI for non-members)
- [x] 2.3b unplanned: PR #1420 MERGED — deny-by-default root .dockerignore (CodeRabbit Major on #1419 caught post-merge; process lesson recorded); context proven to exclude compose/.env* secrets
- [x] 2.4 packages/ui — PR #1421 MERGED (@b2mash/ui; 501-file import rewrite; drift table — badge superset; builder died mid-task, continuation agent caught @types/react any-collapse + tsconfig.base Docker miss; Tailwind scan proven)
- [x] 2.5 packages/shared — PR #1422 MERGED (@b2mash/shared; TERMINOLOGY_BASE + explicit PORTAL_TERMINOLOGY_OVERRIDES; cn consolidated; format.ts correctly STOPPED — divergence was test-pinned product behavior)
- [x] 2.6 unified formatting — PR #1423 MERGED (portal byte-identical, staff en-GB/en-ZA; CodeRabbit guards+isOverdue fixes applied). WAVE 2 COMPLETE.
- [x] 2.7 flaky test — PR #1430 MERGED

## Wave 3 — Backend refactors
- [ ] 3.1 TD-009 thin controllers (one per PR): Project → Document → Dashboard → OrgSettings → PortalBranding → repo-injection; delete ArchUnit exemption each time
- [ ] 3.2a org_id predicate guard test for PortalReadModelRepository
- [ ] 3.2b Split PortalReadModelRepository into domain read-repositories (one per PR)
- [x] 3.3 OrgSettings → per-module @Embeddable groups (zero schema change) — COMPLETE 2026-06-12 via 3 wave PRs (#1433, #1434, #1435; merge commits 0590642 / 841dd393e / 60c9ff038). OrgSettings is now entity spine + 10 embedded groups; ~1,060→~600 LOC entity; schema snapshot pin byte-identical throughout.
  - Wave 1 — PR #1433 MERGED 2026-06-12 (0590642): BrandingSettings + PortalSettings + hardened 53-column schema snapshot pin + null-reload test. CodeRabbit Major (updatedAt contract) was REAL — fixed via entity-level @PreUpdate (pinned by test; auto-covers waves 2–3); footer fixture + immutable-list Minors fixed. Verifies observed at all 3 heads (5630/0, 5631/0, 5631/0).
  - Wave 2 — PR #1434 MERGED 2026-06-12 (841dd393e): Tax/Billing/Capacity/Expense embeddables; default_currency kept top-level; snapshot pin unchanged; CodeRabbit Minor (no-op batch-billing PATCH dirtied entity via touchUpdatedAt — verified PR-introduced) fixed + pinned. Verifies observed 5631/0 + 5632/0; both reviews APPROVE. Builder died twice at turn-end monitors (lesson updated); work salvaged by orchestrator.
  - Wave 3 (final) — PR #1435 MERGED 2026-06-12 (60c9ff038): TimeReminderSettings, DataProtectionSettings, DataRequestSettings (split: POPIA panel vs scheduler tunables), PackStatusSettings (10 jsonb). CodeRabbit Minor (locale-sensitive toUpperCase — pre-existing, fixed in passing with Locale.ROOT). Verifies observed 5632/0 at both heads; both reviews APPROVE.

## Phase 2 close-out review (2026-06-12)
- Shipped: PR #1432 (P1 expense-markup write path), PRs #1433/#1434/#1435 (P2 OrgSettings embeddable reorganization, 3 waves). All merged on green CI + clean CodeRabbit + dual review verdicts + observed full verifies (final suite: 5632 tests / 0 failures).
- P3 api-types: DEFERRED by user after premise disproven (no springdoc, REST Docs unused — see Wave 4.1 entry).
- P4 items: untouched per standing instruction (explicit request only).
- Premises disproven this phase (register-claims-are-hypotheses tally): (1) "springdoc already exposes the spec" — false; (2) wave-1 builder's "updatedAt bump was never load-bearing" framing — understated, CodeRabbit Major was real, fixed via @PreUpdate.
- Process incidents: wave-2 builder died twice at end-of-turn monitors despite explicit prohibition (zombie revivals raced the orchestrator's verify in the same worktree; concurrent-verify GreenMail collision reproduced). Mitigations recorded in tasks/lessons.md: outcome-based verify phrasing (worked for wave-3 builder), salvage-then-remove-worktree rule, orphan-JVM sweep after every builder.

## Found-during-review (tracked, not bundled)
- [x] BUG: time-tracking settings form no-op — PR #1431 MERGED (PATCH /time-reminders + hours→minutes; reproduce-before-fix observed).
- [x] BUG (P1): defaultExpenseMarkupPercent write path — PR #1432 MERGED 2026-06-12 (e04f8248b). PATCH /api/settings/expense + UpdateExpenseSettingsRequest + service method + form rewire. Reproduce-before-fix observed (404→200); full verify 5628/0 + CI Backend on final head; frontend gates green. CodeRabbit 2 Majors (ProblemDetail assertions) applied in 577d77998 + confirmed; 3 nitpicks declined. Null semantics: explicit null CLEARS markup (documented divergence from keep-existing siblings, pinned by test). (read in 3 services, exposed in SettingsResponse, setter has zero callers, no endpoint) — the form's markup input is dead UI. Needs scoped work: new endpoint + DTO + service wiring + form rewire. (was: time-tracking settings form is a wire-level no-op — posts timeReminderEnabled/Days/Time/MinHours + defaultExpenseMarkupPercent to PUT /api/settings whose UpdateSettingsRequest has none of those fields (Jackson drops silently). Real endpoint PATCH /api/settings/time-reminders expects timeReminderMinMinutes (different name AND units). Surfaced by CodeRabbit on #1428, verified end-to-end. Needs own reproduce-before-fix PR: wire form to PATCH endpoint with hours→minutes conversion (or extend PUT). Pre-existing, unrelated to DTO move.

## Wave 4 — Decisions to surface
- [ ] 4.1 packages/api-types via openapi-typescript — DEFERRED by user decision 2026-06-12. PREMISE CORRECTION: the plan claimed "springdoc already exposes the spec" — FALSE. Backend has no springdoc dependency; Spring REST Docs is on the classpath but used by zero tests. There is no OpenAPI source today. Reviving P3 requires first adding springdoc-openapi (new dependency, /v3/api-docs endpoint needing profile-gating/security review) or bootstrapping REST Docs coverage, then the types package + CI drift strategy (commit-the-spec vs boot-in-CI was left undecided).
- [ ] 4.2 docs/ deployable: keep vs static-export — user decision
- [x] 4.3 Gateway: no action (judged justified as-is)

## Bake checkpoint (2026-06-11, post-2.3)
- deploy-staging.yml failing on `configure-aws-credentials` ("Could not load credentials from any providers") since 2026-05-30 — predates this session; aligns with the aws-infra extraction. Workspace images never deploy-pipeline-tested; proven via local build+run + green CI. SURFACED TO USER — infra/credentials fix outside this plan.
- Wave 2 remaining: 2.4 packages/ui, 2.5 packages/shared — start after bake. Wave 3 backend refactors and Wave 4 decisions pending.

## Review log
- 2026-06-10 PR #1403 (Wave 0.1): CodeRabbit raised 1 Major — denial tests used an unsynced member, so 403 could've been the unresolved-member path. Fixed by syncing `user_rc_member` in @BeforeAll; CodeRabbit auto-confirmed. CI Backend 27m pass, qodana pass. Merged.

---

# Backend Test Build-Time Reduction — Phase 1 (started 2026-06-22)

Plan: `~/.claude/plans/iterative-gliding-sundae.md`. Sequenced; no test-deletion pass. Goal ~22-24min → ~19-20min via safe PRs. Branch: `test-speed/1a-context-consolidation`.

## Baseline (DONE)
- [x] Full run: **~26min `test` / 22:53 `clean verify`, 5809 tests, ~40 contexts**. Surfaced 1 PRE-EXISTING failure (count-bleed flake) + #1 hot class PackReconciliationRunnerTest 37.7s.

## SHIPPED (stacked on branch test-speed/jobworker-packreconcile; verify running b45yfnw12)
- [x] **Flake fix** `a70807c6e` (branch fix/automation-scheduler-count-bleed-line305) — AutomationScheduler:305 count-bleed (#1490 missed it); id-scoped. **VERIFIED green full suite (5809/0, 22:53).** Gate-unblocker, lands FIRST.
- [x] **1D JobWorker backoff** `2926384d1` — maxRetries 3→1, ~14s→~2s. Targeted green; full verify pending (this run).
- [x] **PackReconciliation scope** `b281484b7` — extract `reconcilePacksForTenant`; test reconciles own tenant not all (#1 hot class 37.7s→~few s; removes cross-tenant DataIntegrityViolation exposure). Targeted green (3/0); full verify pending.

## DECISION (user 2026-06-22): skip low-yield 1A/1B/1C micro-consolidations
- StorageService mocks all used (0 win). maxSize bump contraindicated (CI OOM). Module-guard/trivial conversions ~3-10s each, not worth the verify cycles. → effort redirected to PackReconciliation (done) + Phase 2 spec.

## Phase 2 — DESIGN SPEC (DONE, awaiting sign-off)
- [x] `docs/superpowers/specs/2026-06-23-test-provisioning-design.md` — Lever A golden-schema/clone provisioning (spike-gated), Lever B shard-cluster consolidation, Lever C seeder non-idempotency (DataIntegrityViolation root cause).

## Measured outcome (stacked verify b45yfnw12: 5809/0, 23:02)
- PackReconciliationRunnerTest **37.7s → 2.4s** (~35s). JobWorkerIntegrationTest **34.5s → 27.6s** (~7s). ~42s test-exec removed (below single-run wall noise; per-class numbers are the proof). Recorded in spec baseline.

## PRs (user: 2 PRs, agent drives merge on green gate)
- **PR #1492** flake-fix (base main) — 2 feature-dev reviews APPROVE + CodeRabbit pass; audit trail in body. CI: Backend+qodana pending (watcher bdjpiahqm). Merge FIRST.
- **PR #1493** test-speed JobWorker+PackReconcile (base = #1492 branch, stacked; +comment-fix from review) — 2 reviews APPROVE, CodeRabbit DEFERRED (skipped). Retarget base→main after #1492 merges, then merge.
- Merge-gate hook needs: `.claude/markers/verify-backend.json` (commit = ancestor of PR head, exit 0, <24h) + 2 `## Verdict: APPROVE` + `## CodeRabbit:` line in body. Body trails done; write marker just before each merge.

## MERGED ✅
- [x] **PR #1492** flake-fix MERGED (squash `39214d939`). Backend CI green 35m49s + 2 reviews + CodeRabbit. Suite green again.
- [x] **PR #1493** JobWorker+PackReconcile MERGED (squash `d509f71a8`). Backend CI green 39m49s on rebased head `d23942e48` + 2 reviews + CodeRabbit. main content verified correct (no dup). CI-trigger gotcha hit+handled (retarget didn't fire CI → forced synchronize push).

## Phase 2 — INVESTIGATED, then STOPPED (user, 2026-06-23)
Both cheap levers turned out low-yield once measured; the only real lever is borderline + max-risk. Suite ~23min is largely irreducible without a high-risk provisioning rework.
- **Lever A spike** (`spike/golden-schema-clone-cost`, thrown away): legal-za pipeline = **943 ms/tenant** (115 tables); stripped clone = **245 ms** → **3.8×**, but realistic clone (FK+sequence fidelity) → ~2.5–3× (borderline gate). Provisioning is NOT a multi-second monster; Lever A ceiling ~8–10% with max blast radius. **Not worth building now.**
- **Lever B** (shard): secondary PG is ALREADY a JVM singleton → reclaimable is only context builds ≈ **~4–6s**, not the ~138s the classes total. Real value = OOM headroom/hygiene only. **Skipped.**
- **Lever C** (seeder non-idempotency, `uq_field_group_type_slug` on 2nd reconcile): real correctness bug, deferred to a future own PR. **Not done.**
- Decision: **STOP**. Spec (with spike numbers + corrected estimates) at `docs/superpowers/specs/2026-06-23-test-provisioning-design.md` is the record. Phase 1 (flake fix + ~42s + green suite) stands as the delivered outcome.
