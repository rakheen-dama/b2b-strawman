Read `qa_cycle/HANDOFF-2026-05-02-night.md` in full before doing anything else. That file is your brief — it captures what just shipped (PR #1266 / `528469d2f` merged on main) and the 4 queued items: Phase-69 unblockers (#4 brand walker, #7 dialog-owns-button doc) + mid-priority Class-3 prevention (#9 ESLint asChild rule, #10 SSR snapshot harness).

Also read `qa_cycle/fix-specs/audit-03-aschild-sweep.md` — recommendations #2, #3, #4 are the source of truth for #9, #7, #10 respectively. Do NOT proceed with #7/#9/#10 if the file's recommendations don't match what the handover claims; raise the discrepancy.

After you've read those, run these in this order:

1. `pwd && git log --oneline -2` — should show `528469d2f` as main HEAD. If not, `git fetch origin main && git checkout main && git pull --ff-only` first.
2. `find qa_cycle -iname '*1228*' -o -iname '*brand*'` — find the actual reference for #4. The handover-cited `qa_cycle/audits/slop-hunt-PR-1228.md` does NOT exist; either it was renamed or the citation is wrong. If you can't find a definitive reference, infer from MEMORY's `feedback_product_name_kazi.md` brand convention.
3. `cat qa_cycle/fix-specs/audit-03-aschild-sweep.md` — read in full to confirm the recommendations.
4. `cd portal && pnpm test brand.test.ts 2>&1 | head -20` — confirm the existing brand test passes on main before extending it.

Then start with #4. Items in priority order:

1. **#4 — `#1228` brand walker extension** (~10 min, frontend) — extend `portal/lib/__tests__/brand.test.ts` walker to include `hooks/`, `middleware.ts`, `e2e/`, root-level files. **Phase 69 GATE.**
2. **#7 — Codify "dialog owns button" pattern** (~30 min) — add a section in `frontend/CLAUDE.md` per `audit-03-aschild-sweep.md` rec #3. **Phase 69 GATE.**
3. **#9 — ESLint custom rule for `<*Trigger asChild>` adjacency** (~2 hr) — mechanical Class-3 prevention per rec #2.
4. **#10 — SSR snapshot harness for dialog family** — generalise PR #1262's pattern per rec #4.

PR each one separately. Quality Gate #7 (one fix per PR) — do not bundle. CodeRabbit review per PR. User-merge only.

Mandate reminders (full text in handover):

- Quality is king. Not a race.
- No workarounds. Fix actual flows and bugs as found.
- Verify references before acting — `audit-03-aschild-sweep.md` and the brand-walker citation both need confirmation. If a referenced file/finding doesn't exist or doesn't say what we think, raise it; don't fix-and-pray.
- Plant-and-revert verification is mandatory for #4, #9, #10 (and a cross-ref check for #7) — same pattern PR #2's ArchUnit rule used (inject a violation, confirm the rule/test/lint catches it, revert).
- Frontend tests use vitest (full run, not narrowed by file path).
- UI/React/Next.js judgment calls go to `vercel:nextjs` / `vercel:react-best-practices` / `vercel:shadcn` skills (or spawn a frontend-specialist agent) — don't reason from first principles.
- Use `pnpm` not `npm`; `cd` into `frontend/` or `portal/` first; `SHELL=/bin/bash` prefix may be needed (env quirks memory).
- Pre-merge gate hook will block you if you try to skip — don't bypass with `--admin` / `--no-verify`.
- Don't trust `gh pr merge`'s apparent success. Always `git fetch origin main && git log origin/main -1` to verify the merge SHA actually landed.
- Worktree cleanup: `gh pr merge --delete-branch` will fail in a worktree (main is checked out elsewhere). After merge, `git worktree remove .worktrees/<branch-name>` then `git branch -d <branch-name>` manually.
- **Autonomy granted through merge prep** (commit, complete tasks, create PRs, address review comments). **Merge requires my explicit "merge" approval.**

Out of scope (do NOT pick up): regression pack audit (gated to me), TrustLedger guard issue #1267 (gated on next exposure), PR #2 polish nits M2/M4/L1/L2 (bundle separately if convenient), TD-009 controller cleanup (opportunistic only), #5 CI parity workflow (separate session — also fixes `scripts/run-regression-test.sh` exit-code masking), #1238 NULL currency (decision pending).

Do not pick autonomously beyond the 4 items in the handover. Do not start work without my sign-off after you've finished the verification checks above. Stop and re-spec if scope expands during a fix.
