# ADR-330: Archive Tag + Mirrored Read-Only Repository Instead of a Maintained Fork

**Status**: Accepted

**Context**:

Phase 86 deletes roughly 300 backend files, 27 frontend routes, four portal pages, two vertical profiles, the docs site and 158 Flyway migrations ([`architecture/phase86-subtraction-legal-only.md`](../architecture/phase86-subtraction-legal-only.md) §11.1). The founder's instruction was *"drop accounting and agency, push them out into a different fork so we don't lose it all"*, with the constraint recorded in `requirements/claude-code-prompt-phase86.md` ("Founder decisions that constrain this phase"): **archive, don't fork** — tag today's `main` as `horizontal-platform-final`, mirror to an archived read-only repository, and never maintain it. A maintained second codebase is explicitly rejected; if accounting or agency ever becomes real it restarts from the tag with hindsight.

The platform was built to be forked per vertical (ADR-181..185/189, Phase 49; ADR-244, Phase 65), so "fork it" is the natural reading of the original strategy. This ADR records why that strategy is *not* followed for the subtraction, what "never maintained" means operationally, and how the tag becomes a precondition that every deletion PR must satisfy (§11.8.5).

**Options Considered**:

### A. How the horizontal platform is preserved

1. **Annotated tag on `main` + mirror to a read-only archive repository; never maintained (CHOSEN)** — `git tag -a horizontal-platform-final` at the last pre-deletion commit, pushed to origin; the whole repository mirrored (`git clone --mirror` / `git push --mirror`) to `kazi-horizontal-archive` (or similar) and set read-only. Resurrection = `git checkout -b <vertical> horizontal-platform-final` in the live repo, re-applied with the knowledge Phase 86's seam tables give.
   - Pros: zero ongoing cost — nothing to merge, test, upgrade or deploy; history is byte-identical and discoverable by one command (`git show horizontal-platform-final:<path>`); the archive repo survives even a force-push or repository move; every removed ADR, migration and QA scenario stays readable at its original path.
   - Cons: the archive rots relative to `main` — Spring Boot, Next.js and the Phase 87 wire renames will all diverge, so resurrecting a vertical is a re-port against a changed core, not a merge; nothing stops a future contributor from forgetting the tag exists (mitigated: `TASKS.md` rows, `BASELINE.md` and the superseded ADRs all name it).
2. **Maintained fork repository** (`kazi-horizontal`) receiving periodic merges from `main`.
   - Pros: accounting/agency could be resumed with current dependencies and a green build; preserves the original "foundation forked per vertical" story.
   - Cons: two codebases for a solo operator in the middle of a design-partner GTM motion (`.claude/ideas/gtm-design-partner-plan-2026-07-11.md`) — every merge from `main` lands in packages the fork keeps and `main` deleted, so each merge is a conflict-resolution session; the fork's full verify (~26 min today) runs for a product with no customer; the requirements reject this outright.
3. **Long-lived branch in the same repository** (`horizontal-platform`), no separate repo.
   - Pros: no second repository to administer; still one command to inspect.
   - Cons: branches invite "just rebase it" drift and accidental merges; branch protection does not make a branch immutable the way a read-only mirror is; `git branch -D` is one keystroke, a mirror is not.
4. **Keep the code in-tree behind build profiles / module flags** (delete nothing).
   - Pros: no archive needed; a flag flip restores everything.
   - Cons: this is exactly what the requirements forbid ("Delete, don't disable … the point is less code"); every horizontal package would still compile, migrate, seed and run in `./mvnw verify`; the legal UI would keep its generic vocabulary; the zero-tenant squash window (ADR-331) would be wasted because flagged tables still need DDL.

### B. How the archive precondition is enforced

1. **Slice 1 verification plus a merge-gate hook check scoped to `phase86/*` branches (CHOSEN)** — slice 1 runs `git ls-remote --tags origin refs/tags/horizontal-platform-final` and resolves the archive URL; `.claude/hooks/pre-pr-merge-gate.sh` denies `gh pr merge` on a Phase 86 branch when the tag is absent on origin (§11.8.5).
   - Pros: the hook is the one place every merge passes through (CLAUDE.md §10); the check is branch-scoped so it costs nothing on unrelated PRs; the tag being an ancestor of the slice-2 base is verified once, in writing, in `tasks/phase86-subtraction.md`.
   - Cons: a branch-name convention (`phase86/<nn>-<slug>`) becomes load-bearing; a mis-named branch bypasses the check (mitigated: `/breakdown` fixes the convention).
2. **PR-template checklist only.**
   - Pros: no hook code.
   - Cons: checklists are narrated around (CLAUDE.md §9); nothing prevents a merge.
3. **CI job on every PR.**
   - Pros: visible in the PR checks tab.
   - Cons: runs on every PR in the repository for a one-phase precondition; CI cannot see the local archive URL record.

**Decision**: Tag `main` as `horizontal-platform-final`, mirror the repository to a read-only archive, and never maintain it (A1). The tag is a merge-gate precondition for every Phase 86 deletion PR (B1). "Never maintained" means: no dependency bumps, no bug fixes, no CI, no deploy target; the archive is a historical record, not a product.

**Rationale**:

1. **Zero production tenants, one product.** Nothing in the archived code has a customer or data behind it. Maintaining a fork would be spending the design-partner runway on a hypothetical second vertical when ADR-244 already established that a future vertical should ship pack-only unless it needs new primitives — with hindsight, accounting-za would be rebuilt smaller, not resumed.
2. **Solo-operator continuity.** The GTM plan commits one person to Phase 86 → 87 → 84 and pilot onboarding. A second repository that must stay green is a standing tax with no revenue line; the founder decision rejects it explicitly.
3. **The tag is what makes "delete, don't gate" safe.** Every removal in §11.3 is a true deletion; the only reversibility is the tag. That is why it is enforced in the merge gate rather than left as a checklist item.
4. **Full-verify cost.** The backend suite already needs `-Xmx3g` and ~26 minutes; the removal packages contribute contexts, seeders and count-pinned tests. Keeping them anywhere that runs CI keeps that cost.
5. **A mirror is the strongest immutability the toolchain offers.** A read-only mirror repository cannot be rebased, force-pushed or branch-deleted by a slip; it also survives a rename or migration of the live repository. A tag alone is one `git push --delete` from gone; the pair is cheap insurance.

**Resurrection procedure (recorded so it is not re-derived):** `git fetch --tags origin && git checkout -b resurrect/<vertical> horizontal-platform-final`, then read §11.3's seam tables for that package in reverse — they enumerate every consumer the package had, which is exactly the list that must be re-wired against the post-Phase-87 wire. The archive repo is the fallback if the tag is ever lost from origin.

**Consequences**:

- Positive: `main` loses ~31k backend lines and every horizontal surface with no loss of history; `git show horizontal-platform-final:backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql` is the documented way to read a pre-squash migration (ADR-331, `backend/db/BASELINE.md`).
- Positive: no second CI, deploy target or dependency-update stream; the review and verify bars apply to one codebase.
- Negative: resurrecting accounting or agency is a re-port, not a merge — the archived packages target a `Project`/`Customer` wire that Phase 87 renames and a migration series that no longer exists.
- Negative: ADR-070/071, 072..075, 124..129, 150..153, 173..176, 197..199, 200..204, 219..226 (PayFast parts), 227..229, 265..270, 313..318 and the non-legal parts of ADR-181..185/189 are marked **Superseded by ADR-330** in slice 12; their files stay so the reasoning remains readable, but nothing in `main` implements them.
- Negative: `TASKS.md` keeps the rows for Phases 16/17/32/38/45/47/51/52/57/59/66/70/80 annotated "Removed in Phase 86 — archived at `horizontal-platform-final`"; readers must learn that a listed phase can be archived rather than live.
- Negative: ADR numbering continues from 330 in `main` while the archive stops at 329; a future reader of the archive sees decisions that `main` later reversed with no forward pointer — the superseded markers exist only in `main`.
- Negative: the archive repository is one more thing with an owner and a URL; slice 1 records the URL in `tasks/phase86-subtraction.md` and `.claude/ideas/phase86-87-ideation-2026-10-15.md`, and `BASELINE.md` repeats it, because nothing else in the codebase will.
- Related: [ADR-331](ADR-331-migration-squash-at-zero-tenants.md) (the squash whose history lives only at this tag), [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md) (what survives of the Phase 49 vertical architecture), [ADR-244](ADR-244-pack-only-vertical-profiles.md) (why a resurrected vertical would be pack-only), [ADR-181](ADR-181-vertical-profile-structure.md) / [ADR-189](ADR-189-vertical-profile-storage.md) (the forked-per-vertical premise this ADR retires), [ADR-240](ADR-240-unified-pack-catalog-install-pipeline.md) (packs catalogue, retained).
