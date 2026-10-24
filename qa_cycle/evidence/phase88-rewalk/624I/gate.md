# 624I — Epic 624 gate (task 624.22) + carried 624.11 KC steps

Orchestrator run, 2026-10-25 00:15–01:20 SAST (2026-10-24 22:15–23:20Z, including the post-review KC cleanup), worktree `worktree-epic-624I`, branch `phase88/07I-gate`, on **main `bd32f07ff2729c5f5d611a597c61d568cd022663`** (= origin/main, contains 624A–624H). No product code changes in this slice.

Source trees at this head are byte-identical to the heads earlier slices built from (`git diff --quiet` exit 0):
- `backend/` + `compose/` = 624F head `cdb1dbb0a`
- `frontend/` + `packages/` = 624G head `17834f000`

## Verdicts

| Gate | Result | Baseline | Verdict | Evidence |
|---|---|---|---|---|
| **BE-VERIFY** (`./mvnw clean verify -q`, caffeinate; `verify.sh` preflight skipped, because it would kill the `svc.sh` KC backend) | exit 0, 18 min; 717 surefire reports / **5743 testcases** / 0 failing report files / 26 skipped | 624F 5743 | **PASS** | `logs/gate-be-verify.log`, `.claude/markers/verify-backend.json` |
| **FE-GATE** (`rm -rf .next`, lint, format:check, vitest, build) | lint 0 errors / 92 warnings; format clean; **417 files / 3150 passed / 2 skipped**; build OK | 624G 417/3150/2, 92 warnings | **PASS** | `logs/gate-624I-fe-*.log`, marker |
| **PORTAL-GATE** (lint, format:check, vitest, `rm -rf .next`, build) | lint 0 errors / 4 warnings; format clean; **44 files / 233 passed**; build OK | 624H 44/233, 4 warnings | **PASS** | `logs/gate-624I-po-*.log`, marker |
| **E2E-LEGAL** lifecycle (fresh stack) | **74 passed / 4 failed / 5 skipped / 14 did not run** | 622F 74/4/5/14 | **= baseline** | `logs/e2e-lifecycle.txt` |
| **E2E-LEGAL** depth-ii (same stack, after lifecycle) | **35 passed / 0 failed / 8 skipped** | 622F 35/0/8 | **= baseline** | `logs/e2e-depth.txt` |
| **E2E-PORTAL** `portal-data-isolation.spec.ts` (`KGOSI_EMAIL=contact@acme.example.com`) | **2 passed** | 617C 2 | **= baseline** | `logs/e2e-isolation.txt` |
| **E2E-PORTAL** `portal-client-90day` (`PORTAL_CONTACT_EMAIL=contact@acme.example.com`) | **5 passed / 3 failed / 1 skipped / 3 did not run** | 617C 5/3/1/3 | **= baseline**; same failing titles | `logs/e2e-portal.txt` |
| **E2E-PORTAL** responsive (`--project=chromium`) | **21 passed** | 617C 21 | **= baseline** | `logs/e2e-portal.txt` |
| **E2E-PORTAL** `login-bare` (opt-in, `pnpm test:e2e:login-bare`) | **1 passed** (second run; see run notes) | 624D 1 | **PASS** | `logs/e2e-login-bare.txt`, `logs/e2e-login-bare-run0-cap.txt` |
| **624.11 carried — KC real fee-note email link** | INV-0004 email `8aKFRbFgpzPHombJZLFKQx` → `/invoices/0feb4764-…?orgId=mathebula-partners` → fresh context, branded bounce → one magic link → one exchange → fee note | — | **PASS** | `kc-observed.md` Step A |
| **624.11 carried — KC multi-firm two links** | bare `/login`, body `{email}` only → 2 branded mails (`S5khrDgFvRcHzhezSnGGnt` Khanyile, `Y7Xvz2bMazmZF6T5SRhJRu` Mathebula) → each lands on its own firm's `/matters`, isolated | — | **PASS** | `kc-observed.md` Step B |
| **624.11 carried — exchange double-POST** | fix not landed; per the task row's alternative, the walk used a **production** portal build (one exchange POST per link observed) | — | **N/A — prod build used** | `kc-observed.md` Stack facts |
| **MARKERS** ×3 | backend / frontend / portal written on `bd32f07ff` | — | **DONE** | `.claude/markers/verify-*.json` |

### E2E failing titles (all inherited — set not grown)

legal-lifecycle 4 failed: all four are in the carried set (617C `e2e-runs.md` "Carried locations"):
- `day-00-firm-setup.spec.ts:282` Trust account settings page loads (`locator.fill` on a non-input)
- `day-30-billing.spec.ts:32` Create fee note for Sipho Ndlovu
- `day-45-recon-prescription.spec.ts:41` Start new bank reconciliation
- `day-90-review.spec.ts:179` Profitability dashboard

The failing set did not grow, but it is not title-identical to 617C run 2:
- **`day-90-review.spec.ts:63`** "Fee notes list shows multiple fee notes": its real assertion (`expect.soft(count) >= 1`, spec line 73) **passed in both 624I runs**. In run 0 the test failed only on the missing baseline at line 76. In 617C it failed on line 73, in both runs. That points to a deterministic change between Phase 87 and Phase 88 (fee notes now exist by Day 90), not a flake. Treat it as likely resolved: if 629.9 also passes it, drop it from the carried set. A failure there is a regression to investigate.
- `:179` is now reached, and it fails (a carried title).

portal-client-90day 3 failed: these are the 617C data-prerequisite titles, owned by 628G (the seed):
- `day-00-07:119` Day 3 upload + info request
- `day-45-75:133` Day 60 SoA download
- `day-85-90:91` Day 85 digest cadence

Non-vacuity: I opened `portal-v2/portal-client-90day/day-00-home-landing.png` with Read. It shows the authenticated portal Home with "E2E Test Organization" / "Acme Corp" and the 7-item rail. The PNG is not retained, because visual baselines are untracked by design and were deleted to leave the worktree clean. The `globalSetup` exchange in `logs/e2e-login-bare-run0-cap-backend.txt` (22:56:18Z "Exchanged magic link for portal JWT") independently shows the run was authenticated.

## Orchestrator re-verification (independent of the walker's report)

Reviewer A had no shell, so I record here what the orchestrator re-checked directly:
- **Mailpit API (KC).**
  - `to:multifirm.624i@email.co.za` → exactly `S5khrDgFvRcHzhezSnGGnt` ("…from Khanyile Attorneys 624I", 22:36:43.125Z) and `Y7Xvz2bMazmZF6T5SRhJRu` ("…from Mathebula & Partners", 22:36:43.16Z).
  - The text part of message `8aKFRbFgpzPHombJZLFKQx` contains `http://localhost:3002/invoices/0feb4764-f6f4-425d-9fb1-5ca250ab8c88?orgId=mathebula-partners`.
- **Screenshots.** I opened `kc-A5-feenote-landing.png` (INV-0004 · Sent · R 100,00, header "Sipho Dlamini") and `kc-B4-link1-khanyile-matters.png` (header "Khanyile Attorneys 624I" / "Lerato Multifirm", only the Khanyile matter).
- **Tree identity, counts and cap.** Source-tree identity, the BE surefire tally, and the e2e cap mechanism are in `logs/e2e-stack-provenance.txt`, `logs/gate-be-surefire-tally.txt` and `logs/e2e-login-bare-run0-cap-backend.txt`.
- **The `gate-*` files under `logs/` are orchestrator extracts**: tails, result lines and tallies. The full raw logs were run with `-q` / redirected to `/tmp` and are not retained.

## Run notes (honest record of what did not go right first time)

1. **Run 0 of E2E-LEGAL was not gate-grade, and was discarded.** The first lifecycle / depth-ii run on a fresh stack gave lifecycle 71/15/5/6 and depth-ii 14 passed / 21 failed / 8 skipped. Every extra failure was `A snapshot doesn't exist … writing actual`. The legal visual baselines are not committed (only `.gitkeep` is tracked), so a clean worktree has none. The prior gates' numbers (620E, 622F) were taken with `--update-snapshots`. I wiped the PNGs, ran `e2e-down && e2e-up` again, and re-ran both suites with `--update-snapshots=all`. Those are the numbers above. The real (non-snapshot) failures in run 0 were the same carried titles. Log: `logs/e2e-lifecycle-run0-missing-baselines.txt`.
2. **Run 0 of login-bare failed, because my run order tripped the per-contact magic-link cap. Not a product defect.** Evidence: `logs/e2e-login-bare-run0-cap-backend.txt` (backend log lines, token rows and restart times).
   - **Sequence.** All requests below were for the contact `contact@acme.example.com`.
     - Isolation spec: 2 tokens at 22:56:03Z.
     - `docker restart e2e-backend`: app-ready ("Started BackendApplication") at 22:56:16Z.
     - 90-day `globalSetup`: 1 token at 22:56:18Z.
     - `docker restart e2e-backend` again: app-ready at 22:57:03Z (container `StartedAt` 22:56:52Z; docker records only the last restart).
     - login-bare request: 22:57:06Z.
   - **Which cap fired.** The restarts cleared the in-memory per-email limiter (`PortalLoginRateLimiter`, 3 / 15 min, which would have returned 429). So the 4th request reached `MagicLinkService`'s **per-contact cap**: `MAX_TOKENS_PER_5_MINUTES = 3`, counted from `magic_link_tokens` in the DB (`MagicLinkService.java:29,112-117`). It threw `PortalAuthException("Too many login attempts")`. `PortalAuthService` logs that as `Magic link not issued … (PortalAuthException)` and returns the generic 200, so no mail was sent and the spec timed out waiting for Mailpit.
   - **Re-run.** After the 5-minute window, at 23:01:44Z: token issued 23:01:46Z, exchanged 23:01:49Z, **1 passed**.
   - **Ordering rule for later gates.** Two caps apply to the same address:
     - the per-contact cap: 3 tokens per 5 min, DB-counted, which a restart does not clear;
     - the per-email limiter: 3 request-link calls per 15 min, in memory, cleared only by a restart.

     So run login-bare **first** on a fresh stack. Otherwise, restart the backend (clears the per-email limiter) **and** wait ≥5 min after the last token (per-contact cap), or wait ≥15 min.
3. The portal suites ran against a **production** portal build (`NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`; 4 chunks contain :8081, 0 contain :8080) served on :3002. The KC `svc.sh` portal was stopped for the run and then restored: PID 35523, cwd main checkout `portal/`, `/login` 200.
4. KC backend restart (KC-STACK): `./mvnw -q clean compile -DskipTests` in the main checkout, then a `svc.sh` restart → tenant **V5 applied on KC** to `tenant_5039f2d497cf` and `tenant_56399d06a773` (`logs/kc-backend-restart.log`). KC is therefore now at tenant V5 / global V2.
5. **KC fixture cleanup, R-5 completed after review H-1** (`logs/kc-D-db-cleanup.txt`). Session 0's §R recipe was run scoped to `khanyile-attorneys-624i`:
   - R.0: backend stopped.
   - R.2: deleted 1 `portal_projects` row and 1 `portal_contact_directory` row.
   - R.3: `DROP SCHEMA tenant_db75730dfc5f CASCADE`.
   - R.4: deleted 1 row each from subscriptions, org_schema_mapping, organizations and access_requests.
   - R.6: backend restarted, "Running tenant migrations for 2 schemas", health UP.
   - Read-back: tenant schemas `tenant_5039f2d497cf` and `tenant_56399d06a773` only; mapping `mathebula-partners` and `org_phase86_squash_smoke` only.
   - Every org-keyed `portal.*` table has 0 Khanyile rows (per-table census appended to the log).
   - Residue outside the tables Session 0 guards, recorded and left:
     - 127 COMPLETED `public.job_queue` rows with `org_id='khanyile-attorneys-624i'`;
     - 1 `portal.portal_contact_directory_backfill` marker for `tenant_db75730dfc5f`.

     Session 0 does not clear those for Mathebula either, and neither trips the R.2 guard, R.6 or CP-3.
   - My first attempt executed **no SQL**: zsh did not word-split a `$P` command variable. It caused one extra KC backend restart, with no state change. The script was re-run under bash.
   - Mathebula keeps the walk's `multifirm.624i@email.co.za` client, matter and directory row. Session 0 removes them with the Mathebula tenant, except for the directory row (see follow-ups).

## Orchestrator rulings for the KC walk (R-1..R-7, full text in `kc-observed.md`)

- R-1: KC data is not reserved. Session 0 of every re-walk deletes and re-provisions Mathebula (623A `mid/session-0.md` R.1 / Phase B). 624D's observer was wrong to refuse a new fee note.
- R-4: the multi-firm step uses a dedicated address (`multifirm.624i@email.co.za`) and never Sipho's, so later bare-login walks of Sipho still get one link.
- R-5: the second firm was provisioned through the real flow. Afterwards its Keycloak org and owner were deleted (walk, `logs/kc-C-keycloak-cleanup.txt`) and its DB footprint was removed with the scoped Session 0 recipe (run note 5, `logs/kc-D-db-cleanup.txt`). The walk's ruling to leave the DB schema in place was reversed on review: the leftover rows would have tripped Session 0's R.2 foreign-row guard, R.6 `portal_projects` 0 and CP-3.

## Follow-ups (routed in the plan: 629A and 629C rows, "Carried from 624I")

- **Exchange double-POST under `next dev`** (624D follow-up): still unfixed, and no slice owns it. 629.1 KC-STACK uses `svc.sh start all`, which runs the portal under `pnpm dev`, so every 629 portal sign-in is exposed. The 629 row now carries: serve the portal as a production build for the walk (the R-3 recipe from `kc-observed.md`), or land the fix first.
- **Session 0 recipe gap.** The §R recipe predates global V2, so it does not delete `portal.portal_contact_directory` rows. After 629's reset, Mathebula's directory rows (including `multifirm.624i@email.co.za`) outlive the dropped tenant. Add `DELETE FROM portal.portal_contact_directory WHERE org_id='mathebula-partners'` to R.2.
- **E2E-PORTAL ordering.** login-bare and the other portal runs share `contact@acme.example.com`, so the per-contact cap and the per-email limiter both apply (run note 2). Run login-bare first on a fresh stack. The comments in `login-bare.spec.ts:13-15` / `:96-97` say the cap is "cleared only by a backend restart", which is true only of the per-email 429 limiter. The comment fix is routed with the other 629 items. 628G, which edits the portal suites, may take it first.
- **Visual baselines uncommitted.** A gate on a clean worktree needs `--update-snapshots=all`, otherwise it reports missing-baseline failures. The 629.9 run must use it, or the suites (628E/628F) commit baselines.
- **624D `kc-observed.md` evidence error.** It says the orgId-path success copy matches the bare path. It does not, by design: `portal/app/login/page.tsx:162-166` shows the multi-firm sentence only when there is no orgId. Recorded here and not edited, since it is historical evidence.
