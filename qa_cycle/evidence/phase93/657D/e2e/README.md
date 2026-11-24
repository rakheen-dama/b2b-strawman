# 657D E2E-LEGAL + exit-month (PR #1855 @ `4a8bdbbe8`)

All runs from `worktree-epic-657D` (branch `epic-657D/take-on-report-files-and-first-month`, head `4a8bdbbe8537e28518756d49dbb9bd87b188e618`, see `stack1-head.txt`) on nvm node **v20.11.1** (first line of each log). Each stack was brought up fresh from the worktree (`e2e-down.sh && e2e-up.sh`; logs `stack{1,2,3}-{up,down}.log`). 2026-11-24.

| Stack | Suite | Command (frontend package script) | Result | Baseline (657C) | Log |
|---|---|---|---|---|---|
| 1 (containers 13:31:30Z) | legal-lifecycle (start 13:32:19Z) | `test:e2e:legal-lifecycle` | **105 passed** (3.2 m) | 105 passed | `lifecycle-4a8bdbbe8.log` |
| 1 (same stack, after lifecycle) | legal-depth-ii (start 13:35:32Z) | `test:e2e:legal-depth-ii` | **40 passed, 4 skipped** (2.0 m) | 40 passed, 4 skipped | `depth-4a8bdbbe8.log` |
| 2 (containers 13:38:06Z) | exit-month (start 13:38:48Z) | `test:e2e:exit-month`, with the portal dev server on :3002 (`portal-dev.log`, `next dev --port 3002`) | **16 passed** (1.4 m) | 16 passed | `exitmonth-4a8bdbbe8.log` |

0 failed and 0 flaky on every suite (no `failed`/`flaky`/`✘` line in any log). The 4 depth-ii skips are the same 4 as 657C's run (`depth-f22e316.log`):

- 1 × `test.fixme` "Alice: Statements-tab Download fetches the PDF" (`day-30-45-soa-writeoff.spec.ts:361`, a presigned LocalStack URL is unreachable from the frontend container);
- 3 × the opt-in `null-prefetch-probe.spec.ts` probes.

657C's README described all four as probes, which was inaccurate.

Provenance
- Containers: `stack{1,2,3}-containers.txt`. `stack1-657d-provenance.txt` holds only the number `13`, and the command that produced it is not recorded in this folder, so it is not relied on here. The OBS run independently confirmed that stack 3 serves the 657D code: the Results step and the Trust Reports card offer the take-on report downloads, the files are rendered into `take_on_report_files`, and the trend returns `takenOnAt` (`../obs/README.md`).
- Tenant `tenant_7d218705360b` at Flyway **V36** on stack 3 (checked by read-only psql during OBS; V36 includes `take_on_report_files` / `take_on_report_renders`).

Stacks
- Stack 3 (fresh, containers 13:41:03Z) was used only for OBS (`../obs/README.md`). Nothing ran on it before OBS. **Stack 3 is left running.**
