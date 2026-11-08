# Batch F brief: tooling, test infrastructure, docs, scenarios and register

**Branch:** `fix/g89-f-tooling-docs`, cut from a fresh `origin/main` after Batch E merges.
**PR title:** `chore(g89): self-hosted fonts, opt-in visual compares, embedded-PG workdir, e2e date fix, scenario amendments, register + docs (G89 Batch F)`
**Authorisation:**
- Founder D1/D2/D3 (scenario amendments).
- 2026-11-03 ruling: retire visual baselines.
- D22 (F63 to backlog).
- Items recorded in `TRACKER.md`.

**Gates:**
- backend full verify (the embedded-PG change touches test infra);
- frontend gate;
- portal gate;
- 2 bound reviews and CodeRabbit;
- **pre-merge e2e**: all suites on fresh stacks. The fonts change both production builds, and the screenshot compare becomes opt-in.

## 1. G89-F46: self-host the fonts (frontend + portal)

**Problem:** `next/font/google` downloads at build time. The download has failed three times so far: the CI Portal job on #1747, the e2e frontend build at Checkpoint 1, and the portal login-bare build on #1750.

**Fix:**
- Switch both apps to `next/font/local`.
- Commit the `.woff2` files for the weights and subsets actually used, under `frontend/app/fonts/` and `portal/app/fonts/`, or under a shared `packages/` asset folder if the monorepo already shares assets.
- Keep the same CSS variables and font-family names, so styling doesn't change.
- Check the licence: IBM Plex is SIL OFL, so redistribution is fine. Include its `OFL.txt` next to the files.
- Verify that `next build` succeeds with no network access. Run it with `HTTPS_PROXY=http://127.0.0.1:9` or an equivalent, or prove that no font fetch happens in the build log.

## 2. Visual compares opt-in (2026-11-03 founder ruling) and G89-F67

**Fix:**
- In `frontend/e2e/helpers/screenshot.ts` (around `:62-70`), make `toHaveScreenshot` run only when `E2E_VISUAL=1` is set.
- Without the flag, keep taking the curated documentation screenshots (`documentation/screenshots/...`). Only the baseline *comparison* becomes opt-in.
- Apply the same change to any portal e2e screenshot helper.
- Update the e2e README and gate docs: gates no longer need capture-then-score unless `E2E_VISUAL=1`.

**G89-F67** (flaky day-30 fee-note frames) then stops affecting gates. Record that.

## 3. Embedded-Postgres working directory (lesson 2026-11-07)

**Problem:** macOS `dirhelper` deleted the zonky extraction's `share/timezonesets` partway through a verify.

**Fix:**
- In `backend/src/test/java/.../TestcontainersConfiguration.java`, use `EmbeddedPostgres.builder().setOverrideWorkingDirectory(...)` pointing at `backend/target/embedded-pg`, or the zonky equivalent; check the API.
- Make sure `mvn clean` removes it and that CI is unaffected.

## 4. G89-F66: `frontend/e2e/tests/projects/project-time.spec.ts` dates entries with the UTC date

**Problem:** My Work uses the SA date, so the spec fails every night between 00:00 and 02:00 SAST.

**Fix:** use the SA date helper that the legal specs already use (e.g. `isoDate()` / `todayInSA()`; find it). Grep the other e2e specs for `new Date().toISOString().slice(0, 10)` or similar UTC-date patterns, and fix them the same way.

## 5. Tooling and docs

- **G89-F1:** add `frontend/e2e/screenshots` and `portal/e2e/screenshots` to the root `.dockerignore` re-deny block (around `:14-23`).
- **G89-F2:** wherever a doc means a cold build, write `docker builder prune -af`. Locations:
  - `tasks/phase89-go-live-readiness.md:671,763`
  - `architecture/phase89-go-live-readiness.md:1211`
  - `requirements/claude-code-prompt-phase88.md:237`
  - `architecture/phase88-demo-ready-root-cause-closure.md:1548`
  - `tasks/phase88-demo-ready-root-cause-closure.md:537,945`

  Leave the free-disk uses alone.
- **G89-F3:** fix the depth-ii known-skip attribution in `qa_cycle/evidence/phase89-rewalk/634C/e2e-gate.md:14`. The correct attribution is `day-30-45-soa-writeoff` (fixme) plus `null-prefetch-probe` ×3; see `suites/README.md:98`.
- **runner:** in `scripts/run-phase.sh` (around `:242`), change `timeout` to `timeout -k 120`.
- **`backend/db/BASELINE.md`:**
  - set the "next free" line to the actual numbers after E. Tenant is V15 unless E added one. Check with `ls`.
  - global is V6.
- **Dead code:** delete the uncalled, ungated `DocumentTextExtractorService.extractText(Document)`, as noted in the H security review. Grep that it has no callers first.

## 6. §6 scenario amendments (founder D3: amend all four)

Edit the `qa/testplan/demos/*.md` lines. Each edit gets a one-line justification note citing the file:line of the design proof, taken from `qa_cycle/evidence/phase89-rewalk/gaps.md` §4.

| Gap | Change |
|---|---|
| **F4** | L 5.3 "Client comments" → "Comments" (`matter-tab-groups.ts:70`) |
| **F24** | P-75.1 / P-75.2. The nudge email exists for custom-field dates only. Reword to what the product does: court dates and prescriptions appear on `/deadlines` with a "Due soon" flag, and there is no nudge email for them. Cite `PortalEmailNotificationChannel.java:48,185`. |
| **F25** | P-75.6. The deadline panel has no related document; remove that step (`deadline-detail-panel.tsx:115-172`). |
| **F27** | P-3.4. After the client submits, the request reads "In progress" (`RequestStatus.java:11-12`) and the item reads "Submitted". |

Also record the wording-only notes from the defects doc §3:
- 72.1 "withdrawal" is a Payment;
- 6.2's §5a-row-4 annotation is stale since 634E;
- the Section 35 "Financial Year End" input.

## 7. Register: file every new id in `qa_cycle/evidence/phase89-rewalk/gaps.md`

Add a §7 "Post-exit fix initiative (2026-11-06 →)" table. Columns: id · severity · class · one-line observation · file:line · status (fixed PR # / backlog / by design). Ids:
- **F28** — unmattered trust rows (D7, backlog)
- **F29** — `reverseTransaction` publishes no portal event (backlog)
- **F30** — credit reversals are a no-op (backlog)
- **F31** — fixed in #1751
- **F32** — comment edits never reach the portal (backlog)
- **F33** — SHARED task/doc comments appear in the portal matter thread (backlog)
- **F34** — portal optimistic append jumps (backlog)
- **F35** — `GET /api/matters/{id}/members` has no access check (backlog, unverified)
- **F36** — firm reference over 100 characters aborts the portal sync (backlog)
- **F37** — transfers can't be reversed via the API (backlog)
- **F38** — `admin-audit-30day/fixtures.ts` posts to a non-existent endpoint (backlog; fix it here if trivial)
- **F39** — fixed in #1747
- **F40, F41** — fixed in E
- **F42** — `backfillForTenant` trims portal history to 50 rows (backlog)
- **F43** — portal from/to filter uses `occurred_at` (backlog)
- **F44** — fixed in E
- **F45** — multi-matter fee note counted on each matter (LOW, backlog, D14)
- **F46** — fixed here
- **F47–F49** — fixed in E
- **F50** — fixed in E
- **F51** — fixed in #1749
- **F52–F54, F56, F58, F60** — fixed in #1750
- **F55, F59** — fixed in #1750
- **F57** — fixed in E
- **F61** — by design, D21
- **F62** — fixed in E
- **F63** — LOW, backlog, D22
- **F64, F65** — fixed in E
- **F66** — fixed here
- **F67** — fixed here, via opt-in visual compares

Take the details from `tasks/g89-fixes/TRACKER.md`. Update the §1 summary counts.

## Process

Same as `batch-B-brief.md` "Process", with these points:
- targeted backend tests for the embedded-PG change: the full verify proves it, but also run `architecture/**` and a sample integration package;
- run the frontend and portal gates;
- add the offline-build proof for the fonts to the PR body;
- open the PR; do not run the full verify; do not merge;
- report the PR number, head sha, results, changed files by group and judgment calls.
