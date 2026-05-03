# Handover — 2026-05-02 night (post PR #1266 merge)

Continuation of `HANDOFF-2026-05-02-evening.md`. The 2026-05-02 evening session shipped PR #2 of the ADR-T008 series; this handover queues the Phase-69 unblockers and the Class-3 (Radix asChild) prevention items.

**Do not trust this document blindly — verify before acting.**

---

## What just shipped (verified on `main` at `528469d2f`)

- **PR #1266 merged** — `feat(multitenancy): tenant scope binding consolidation (backlog #8 / PR #2 / ADR-T008 Surface 2)`. Squash SHA on main: `528469d2f`.
- 17 commits — ~36 site migrations + 2 new APIs + 1 new helper bean + ArchUnit rule + ADR-T008 amendment + architectural-review fixes (H1 + H2 + M1 + M3) + CodeRabbit fixes (FQCN exemptions, log nits, hard-coded path).
- **Test count baseline on main:** 5055 testcases / 0F / 0E / 26 skip — counted via `<testcase>` element aggregation (Surefire `<testsuite tests=N>` is unreliable with `@Nested`; see `feedback_surefire_nested_count` memory).

| Artifact | Path |
|---|---|
| New 3-binding API | `RequestScopes.runForTenantWithMember(tenantId, orgId, memberId, action)` |
| New per-tenant fan-out bean | `TenantScopedRunner.forEachTenant(BiConsumer<String,String>)` |
| New cross-tenant discovery helper | `TenantDiscoveryHelper.findInTenants(Supplier<Optional<T>>)` |
| ArchUnit rule | `TenantScopeBindingTest.no_direct_scopedvalue_binding_outside_multitenancy_and_exempt` (8-entry FQCN-scoped exemption catalogue) |
| ADR | `adr/ADR-T008-tenant-scoped-runner.md` (amended PR #2) |
| Spec | `docs/superpowers/specs/2026-05-02-tenant-scope-binding-consolidation.md` |
| Plan | `docs/superpowers/plans/2026-05-02-tenant-scope-binding-consolidation.md` |
| Tracking issue | #1267 (TrustLedgerPortalSyncService cross-tenant guard — gated on next exposure) |

**Regression run post-merge:** 4 pre-existing Playwright failures + 6 API 403s. Pre-existing per evidence (test-result dirs predated PR #2). Mandate says don't auto-fix — deferred to user-gated regression-pack audit. Not your problem this session.

---

## Mandate (verbatim, NON-NEGOTIABLE — same as previous handovers)

> No workarounds, fix actual flows and bugs as they are found. Quality is king. This is not a race.

> All builds must be green. Reviews must be considered. Merge gates and agent contract are tightened. Agents should not look for loopholes or ways to be lazy and careless. Pride should be taken.

> Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired in. No workarounds besides Mailpit API and dev-only Keycloak issues.

> **Regression pack provenance is suspect** — the pack was built when bugs were live, so test failures may encode buggy old behaviour rather than real regressions. **Don't auto-fix regression failures** until the pack itself has been audited.

> **UI questions go to Vercel/Next.js expert skills or agents.** Memory `feedback_ui_questions_use_vercel_experts.md` lists which skill maps to which question. Don't reason from first principles when an expert-mode skill exists.

> **User has granted autonomy through merge prep, with merge requiring explicit user approval.** Specifically: commit, complete tasks, create PRs, address review comments. Stop at merge gate unless user says "merge".

If a rule blocks you, raise it; don't bypass.

---

## What's pending — Phase-69 unblockers + Class-3 prevention

**Priority order. PR each item separately (Quality Gate #7).**

### 1. #4 — `#1228` brand walker extension (~10 min, frontend) — Phase 69 GATE

**Goal:** extend `portal/lib/__tests__/brand.test.ts` walker to cover `hooks/`, `middleware.ts`, `e2e/`, root-level files. Current walker misses these directories so hardcoded brand strings (e.g. raw `"Kazi"` / `"DocTeams"` instead of `process.env.NEXT_PUBLIC_BRAND_NAME` or whatever convention is used) can leak in.

**⚠️ Reference verification required first:** the previous handover cited `qa_cycle/audits/slop-hunt-PR-1228.md` finding 1 — **this file does NOT exist**. Existing slop-hunt audits are for PRs 1230, 1234, 1240, 1241, 1245. Either the citation was wrong or the file was renamed. Before touching code:
1. `find qa_cycle -iname '*1228*' -o -iname '*brand*'` to locate the actual reference.
2. Read the current `portal/lib/__tests__/brand.test.ts` to understand the walker shape.
3. If you can't find a definitive audit doc, infer the gap from the `MEMORY.md` entry `feedback_product_name_kazi.md` ("Product is Kazi, company is b2mash — never DocTeams") + a quick grep of the missing directories for any hardcoded brand strings.

**Do:**
- Add the missing path patterns to the walker.
- Plant a known-bad brand string in one of the newly-walked dirs, run the test, confirm it fails. Revert the planted violation. Re-run; confirm it passes.
- `cd portal && pnpm test brand.test.ts` (frontend toolchain — `SHELL=/bin/bash` prefix may be needed per env quirks memory).

### 2. #7 — Codify "dialog owns button" pattern in `frontend/CLAUDE.md` (~30 min) — Phase 69 GATE

**Reference:** `qa_cycle/fix-specs/audit-03-aschild-sweep.md` recommendation #3.

**Context:** Class 3 (Radix `asChild` adjacency) is the bug class where `<DialogTrigger asChild>` wraps a Button defined elsewhere in the file. The `asChild` prop merges props onto the child — easy to compose wrong (extra wrapper, conditional rendering, etc.) and ship a broken/no-op trigger. Recent fixes (audit-03 sweep, then PR #1263 propagated to 4 sites; PR #1262 dropped a dead mount-gate workaround in `CreateProposalDialog`) cleaned the existing instances. The pattern recommendation: dialog components should *own* the trigger button (define + colocate inside the same component), rather than receiving it as a child via composition.

**Do:**
- Read `qa_cycle/fix-specs/audit-03-aschild-sweep.md` recommendation #3 for the precise pattern wording.
- Add a new section in `frontend/CLAUDE.md` (under "Anti-Patterns" or a new "Dialog patterns" section) documenting:
  - The pattern: dialog components own their trigger button (good); dialog as a generic shell that wraps an externally-defined button (bad).
  - Rationale: prevents Class-3 asChild adjacency bugs.
  - One ✅ good example + one ❌ bad example.
  - Cross-reference to PR #1262 / PR #1263 for live precedent.
- Bonus if you also list the dialog components that currently follow / don't follow the pattern (sets up #9 ESLint rule's exemption list).

### 3. #9 — ESLint custom rule for `<*Trigger asChild>` adjacency (~2 hr)

**Reference:** `qa_cycle/fix-specs/audit-03-aschild-sweep.md` recommendation #2.

**Context:** mechanical Class-3 prevention. Complements #7 (which documents the pattern) by making it lintable. The rule catches the bad-shape composition before it ships.

**Do:**
- Read `qa_cycle/fix-specs/audit-03-aschild-sweep.md` recommendation #2 for the precise lint condition. Likely shape: when a `*Trigger asChild` wraps a Button, the wrapped element must be the immediate child (no intermediate fragments, no conditional rendering, no extra wrapper divs).
- Locate the existing ESLint config: `frontend/eslint.config.*` (flat) or `frontend/.eslintrc.*` (legacy) — check both. Same in `portal/`.
- Add a new local rule (`frontend/eslint-rules/no-aschild-adjacency.js` or similar). Register it in the ESLint config.
- Test the rule against:
  - Known-good fixture (the dialog-owns-button pattern) — should pass.
  - Known-bad fixture (DialogTrigger asChild wrapping a fragment / conditional / extra div) — should fail.
- Run the rule across the existing frontend + portal source. Should pass empty after #7's documented exemptions are honoured (or the rule should already accept the existing-dialog-component shapes).
- If the rule surfaces violations beyond the documented pattern, **stop and re-spec** — they may be legitimate variations or bugs we haven't discovered yet. Don't auto-fix.

### 4. #10 — SSR snapshot harness for the dialog component family

**Reference:** `qa_cycle/fix-specs/audit-03-aschild-sweep.md` recommendation #4.

**Context:** PR #1262 added one SSR snapshot for `CreateProposalDialog` (the OBS-704 v3 fix) — it caught the dead mount-gate workaround by snapshot diff. Generalising into a reusable harness covers the full dialog family.

**Do:**
- `gh pr view 1262 --json files --jq '.files[].path'` — find the snapshot setup PR #1262 added.
- Identify the dialog component family: `find frontend/components portal/components -name "*Dialog*.tsx" -o -name "*Dialog*.jsx"` — list them.
- Build a parameterised SSR snapshot test that walks the family and renders each dialog in SSR mode (Next.js `renderToStaticMarkup` or `react-dom/server`). Strip auto-generated IDs (e.g. Radix's `useId` outputs) before snapshot comparison — they're inherently unstable.
- Output: a single test file (`frontend/components/__tests__/dialog-family.snapshot.spec.tsx` or similar) that snapshots N dialogs.
- Plant one mount-gate-style regression in a dialog, confirm the snapshot diff catches it, revert.

---

## Anti-cheat reminders (specific to these items)

- **`Audit-03 = qa_cycle/fix-specs/audit-03-aschild-sweep.md`** — the whole reference is in `fix-specs/`, not `audits/`. The previous handover may have ambiguous wording. Verify each `audit-03 recommendation #N` reference matches what the file actually says before implementing.
- **#4 cited `slop-hunt-PR-1228.md` does not exist.** Find the right reference or infer from MEMORY's brand convention.
- **Plant-and-revert verification** is mandatory for #4, #7-as-cross-ref-checks, #9, #10. Same pattern as PR #2's ArchUnit rule verification.
- **Use `pnpm` not `npm`.** `SHELL=/bin/bash` prefix may be needed (env quirks memory). `cd` into `frontend/` or `portal/` first — don't run from repo root.
- **Frontend tests use vitest** (per CLAUDE.md). Full vitest run, not narrowed by file path.
- **No `NODE_OPTIONS` fiddling needed for these** — but if you hit "openssl-legacy-provider" errors, clear with `NODE_OPTIONS=""`.
- **UI/React/Next.js judgment calls go to expert skills** (`vercel:nextjs`, `vercel:react-best-practices`, `vercel:shadcn`) — don't reason from first principles. Memory note: `feedback_ui_questions_use_vercel_experts.md`.
- **Don't trust `gh pr merge`'s apparent success.** Always `git fetch origin main && git log origin/main -1` to verify the merge SHA actually landed.
- **Pre-merge gate hook** blocks PRs against main unless verify markers are fresh. Don't bypass with `--admin` / `--no-verify`.
- **Worktree workflow**: each PR gets its own worktree under `.worktrees/<branch-name>` (gitignored). After merge, `git worktree remove .worktrees/<branch-name>` then `git branch -d <branch-name>`. The `--delete-branch` flag on `gh pr merge` will fail in a worktree because main is checked out elsewhere — clean up manually.
- **CodeRabbit takes ~5 min per PR.** Don't skip the review pass; address all actionable findings before requesting merge.

---

## Out of scope for this session

- Regression pack audit (gated to user)
- TrustLedger cross-tenant guard (issue #1267 — gated on next time someone wants to expose `TrustLedgerPortalSyncService.backfillForTenant` via a controller)
- PR #2 polish nits (M2 chained-`Carrier.where` ArchUnit hole; M4 TenantScopedRunner log-emission test gap + MDC inconsistency; L1/L2/L3 minor doc polish — bundle into a tidy-up PR if/when convenient, but not this session)
- TD-009 controller refactor (`InternalAuditController`, `PortalBrandingController`, `MockPaymentController` — opportunistic, on next touch only)
- #5 CI parity workflow — separate session per user direction; would also fix `scripts/run-regression-test.sh` exit-code masking
- #1238 NULL currency — architectural decision deferred to user

---

## How to start (next-agent checklist)

1. Read `CLAUDE.md` (top "Quality Gates" section) and this file in full.
2. `pwd && git log --oneline -2` — should show `528469d2f` as main HEAD (or `git fetch origin main && git checkout main && git pull --ff-only` first).
3. `find qa_cycle -iname '*1228*' -o -iname '*brand*'` — locate the right reference for #4. If nothing definitive, see the MEMORY.md `feedback_product_name_kazi.md` entry as the canonical brand convention.
4. `cat qa_cycle/fix-specs/audit-03-aschild-sweep.md` — read in full to confirm recommendations #2, #3, #4 say what this handover claims.
5. Pick #4 first (smallest, fastest, builds Phase-69 momentum).
6. PR each item separately — don't bundle. CodeRabbit review per PR. User-merge only.

Quality is king. Not a race. Doing #4 + #7 unpauses Phase 69 — which has been waiting.
