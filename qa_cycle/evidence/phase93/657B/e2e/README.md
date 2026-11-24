# 657B E2E-LEGAL + exit-month (PR #1853 @ `01ae60e12`)

All runs from `worktree-epic-657B` (branch `phase93/57B-trust-position-validation`) on nvm node **v20.11.1** (first line of each log), `PLAYWRIGHT_BASE_URL=http://localhost:3001`, `NODE_OPTIONS=""`, pnpm by absolute path, flags without `--`. Each stack was fresh (`e2e-down.sh && e2e-up.sh`, built from the worktree at `01ae60e122f41387512608ed149910c3abba28e1` — `stack{1,2}-head.txt`, `-down.log`, `-up.log`). No Maven verify ran at any point (`pgrep -fl surefire` empty before each suite); one stack at a time. 2026-11-24.

| Stack | Head | Suite | Result | Baseline | Log |
|---|---|---|---|---|---|
| 1 (containers created 06:59:44Z) | `01ae60e12` | `test:e2e:legal-lifecycle` (07:00:46Z) | **105 passed** (3.0 m) | 105 passed | `lifecycle-01ae60e.log` (force-added) |
| 1 (same stack, after lifecycle) | `01ae60e12` | `test:e2e:legal-depth-ii` (07:04:19Z) | **40 passed, 4 skipped** (2.0 m) | 40 passed, 4 skipped | `depth-01ae60e.log` (force-added) |
| 2 (containers created 07:07:29Z) | `01ae60e12` | `test:e2e:exit-month`, portal on :3002 against :8081, `PORTAL_BASE_URL=http://localhost:3002` (07:08:47Z) | **16 passed** (1.4 m) | 16 passed | `exitmonth-01ae60e.log` (force-added) |

Provenance
- Both stacks: the built frontend bundle contains the 657B testid `take-on-equation` (and stack 1 also "Control totals") in `/app/frontend/.next/server/chunks/ssr/frontend_0imcgad._.js` and `/app/frontend/.next/static/chunks/12wgn6qraax1a.js` (`stack{1,2}-frontend-657b-string.txt`). Stack 1's `/app/app.jar` holds `takeon/dataset/TrustPositionEquation$TrustPositionMessage.class`, built 06:59 (`stack1-backend-657b-classes.txt`). Container create times and image ids: `stack{1,2}-containers.txt`.
- Tenant `tenant_7d218705360b` at Flyway **V35** `phase93 go live checklist` on both stacks (`stack{1,2}-flyway.txt`); 657B adds no migration.
- Portal (stack 2 only, for exit-month step 19): `cd portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev` (the command in the header of `trust-balanced-month.spec.ts`), log `portal-dev.log`; stopped after the suite.

Notes
- All three suites equal their baselines on the first run: 0 failed, 0 flaky. Nothing failed, so no re-run was needed.
- The 4 depth-ii skips are the opt-in `null-prefetch-probe.spec.ts` probes, as in the baseline.
- The lifecycle run wrote 25 untracked `documentation/screenshots/legal-vertical/*.png`; deleted with `git clean -f documentation/screenshots/legal-vertical/`. No tracked Playwright config was dirtied (`git status` showed only this evidence folder).
- Stack 2 was then used for OBS (`../obs/`), as 657A did.
