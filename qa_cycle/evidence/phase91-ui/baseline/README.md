# Phase 91 — authoritative baseline (task 661.7, slice 661B)

| | |
|---|---|
| HEAD | `main` @ `551778100` (worktree `worktree-epic-661B`, branch `phase91/61B-light-scheme`, **no edits**) |
| Date | 2026-11-25 |
| Node / pnpm | nvm `v20.11.1`; pnpm 10.26.1 called by absolute path `/opt/homebrew/bin/pnpm`, `SHELL=/bin/bash NODE_OPTIONS=""` |
| Recorded by | 661B (observe-first agent). 670A compares against **this** record, not against §18.1's provisional table |

## 1. Metrics (architecture §18.1, commands verbatim)

Run from the worktree root with `B='frontend/app/(app)/org/[slug]'`.

| Metric | Command | §18.1 provisional (`5aae8f4ab`) | **Baseline @ `551778100`** |
|---|---|---|---|
| Pages under `org/[slug]` | `find "$B" -name page.tsx \| wc -l` | 58 | **58** |
| `PageHeader` adoption | `find "$B" -name page.tsx -exec grep -l "PageHeader" {} + \| wc -l` | 7 | **7** (reports, reports/profitability, clients, trust-accounting, matters, trust-accounting/other-creditors, trust-accounting/reconciliation/lists/[listId]; + dashboard via `dashboard-header.tsx`) |
| Files with `<h1` | `grep -rl '<h1' "$B" \| wc -l` | 71 | **71** |
| Display h1 | `git grep -c "font-display text-3xl" -- frontend/app frontend/components \| awk -F: '{s+=$NF} END {print s}'` | 92 | **92** |
| Direct tables by area | `grep -rlE "<Table\b\|<table\b" "$B"` grouped by first path segment | settings 10, trust-accounting 6, invoices 4, legal 1, documents/loading 1 | **22 files: settings 10, trust-accounting 6, invoices 4, legal 1, documents 1** (`documents/loading.tsx`) |
| EmptyState calls without `variant` | interim perl multi-line scan (inventory §4.1) — `perl qa_cycle/evidence/phase91-ui/baseline/scripts/emptystate-scan.pl` | 53 shared (55 all) | **53 shared (55 all)**; 64 tags in 54 files; shared 62 tags / 53 files, 9 with `variant` |
| Raw hex (PCRE, `*.tsx`) | `git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" \| wc -l` | 32 | **33** |
| `dark:` lines, whole frontend | `git grep -c "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" \| awk -F: '{s+=$NF} END {print s}'` | 3647 | **3842** |

Extra inventory §8.1 companions, same HEAD: `page.tsx` containing `<h1` = 37; `frontend/components` non-test files with `<h1` = 6; raw hex ERE = 2; `git grep -o "dark:"` occurrences = 4666.

The drift from §18.1 (hex +1, `dark:` lines +195, EmptyState +1 tag with `variant`) is Phase 93 (take-on) landing between the breakdown and this slice. Page count, `PageHeader`, `<h1` files, display h1 and tables are unchanged.

## 2. FE-GATE and PORTAL-GATE

`pnpm install --frozen-lockfile` at the repo root first (rc 0). Every step was run even when an earlier one failed. None failed.

| Gate | Step | Exit | Result |
|---|---|---|---|
| FE-GATE (`frontend/`, `rm -rf .next` first) | lint | 0 | 0 errors, 76 warnings |
| | typecheck | 0 | — |
| | format:check | 0 | — |
| | test (full vitest) | 0 | **Test Files 508 passed (508); Tests 4980 passed, 2 skipped (4982)**, 0 failed |
| | build | 0 | — |
| PORTAL-GATE (`portal/`) | lint | 0 | 0 errors, 4 warnings |
| | typecheck | 0 | — |
| | format:check | 0 | — |
| | test | 0 | **Test Files 51 passed (51); Tests 293 passed (293)**, 0 failed, 0 skipped |
| | build | 0 | — |

Portal `/home` and `/login` at 1024 light are captured in `../661B/before/portal-1024-light.png`.

## 3. E2E suites

The stack was started with `bash compose/scripts/e2e-down.sh && docker builder prune -f && bash compose/scripts/e2e-up.sh` from the worktree root. Suites ran from `frontend/` with `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm <script>`. No flags were added, so the config's default `list` reporter applied. Retries are 0 locally in every config, so no run can report a test as "flaky". Final summary lines and every failing or skipped test name are in `suite-summaries.txt`.

### Stack A (fresh) — suites in the ordered sequence

| Suite | Passed | Failed | Skipped | Did not run | Flaky | Exit | Wall |
|---|---|---|---|---|---|---|---|
| E2E-LEGAL `test:e2e:legal-lifecycle` | **105** | 0 | 0 | 0 | 0 | 0 | 175 s |
| E2E-LEGAL `test:e2e:legal-depth-ii` | **40** | 0 | 4 | 0 | 0 | 0 | 111 s |
| E2E-EXIT-MONTH `test:e2e:exit-month` | 0 | 1 | 0 | 15 | 0 | 1 | 2 s |
| E2E-DEFAULT `test:e2e` (180 tests, 5 workers) | **166** | 0 | 14 | 0 | 0 | 0 | 53 s |

- **depth-ii skips (4):** these are skipped by design. `day-30-45-soa-writeoff.spec.ts:361` is skipped, and the three tests in `null-prefetch-probe.spec.ts:74` are opt-in probes.
- **default skips (14):**
  - `auth/auth-change-password.spec.ts:35` (Keycloak-only)
  - 8 × `admin-audit-30day/admin-audit-lifecycle.spec.ts` (33, 52, 70, 88, 108, 125, 143, 159)
  - `customers/customer-lifecycle.spec.ts:157`, `:178`
  - `portal/portal-data-isolation.spec.ts:36`, `:48`. These skip unless `KGOSI_EMAIL` is set (memory `env_e2e_portal_gate_gotchas` item 3). A skip here is not a pass.
  - `settings/roles-capabilities.spec.ts:53`
- **exit-month on stack A failed its precondition. This is a test-ordering artefact, not a product failure.** Step 1 (`trust-balanced-month.spec.ts:793`) asserts that the primary account has no transactions: `Mathebula & Partners Trust Account already has trust transactions — trust-balanced-month runs once per fresh stack` (expected 0, received 8). The legal-lifecycle run had already posted to that account. The Conventions table says E2E-EXIT-MONTH runs "on the same fresh stack", but the spec cannot run after legal-lifecycle. **Every later slice (664B, 666B, 667A, 667B, 670A) must give exit-month its own fresh stack.**

### Stack B (fresh) — exit-month alone, no portal

| Suite | Passed | Failed | Did not run | Exit |
|---|---|---|---|---|
| `test:e2e:exit-month` | 13 | 1 | 2 | 1 |

Step 19 failed on purpose (ruling D-3): `PORTAL_BASE_URL is not set — start the portal against :8081 … and run with PORTAL_BASE_URL=http://localhost:3002`. The spec needs a portal running, as `qa_cycle/evidence/phase92-exit/README.md` line 17 documents.

### Stack C (fresh) — exit-month alone, with the documented portal

The worktree portal ran with `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev` (`:3002`). The suite ran with `PORTAL_BASE_URL=http://localhost:3002 EXIT_MONTH_EVIDENCE_DIR=/tmp/661b-exit-month-run`.

| Suite | Passed | Failed | Skipped | Exit | Wall |
|---|---|---|---|---|---|
| **E2E-EXIT-MONTH `test:e2e:exit-month`** | **16** | 0 | 0 | 0 | 74 s |

### Baseline pass counts to meet or beat

**legal-lifecycle 105/105 · legal-depth-ii 40 passed + 4 skipped · exit-month 16/16 (own fresh stack + portal on :3002) · default 166 passed + 14 skipped, 0 failed.**

## 4. Stack build notes

- The first stack build was aborted and redone. While it ran, the worktree HEAD was briefly detached to `5aae8f4ab` by a mistyped `git checkout` in this session. The HEAD was restored to `551778100` within the same minute. Then the build and the gate run were killed, `git status` and `git diff HEAD` were confirmed clean, and both were restarted from scratch. Every number above comes from the restarted runs on `551778100`.
- Stack A: `e2e-up` rc 0, 145 s. Stack B: 105 s. Stack C: 41 s (warm layer cache; `docker builder prune -f` only removes dangling cache).
- After the legal configs ran, the tree was restored with `git clean -fd -- documentation/screenshots frontend/test-results frontend/e2e/screenshots`. This removed 27 untracked curated PNGs under `documentation/screenshots/legal-vertical/`; `frontend/test-results` is no longer tracked. Artefacts were archived to `/tmp/661b-artefacts/`.
- The before captures (`../661B/before/`) were taken on **stack A** after its four suites. The stack left **running** at the end is **stack C**: the e2e stack after exit-month. The portal dev servers on :3012 and :3002 were stopped.
