# 624I — Epic 624 gate (task 624.22) + carried 624.11 KC steps

Orchestrator run, 2026-10-25 00:15–01:05 SAST (2026-10-24 22:15–23:05Z), worktree `worktree-epic-624I`, branch `phase88/07I-gate`, on **main `bd32f07ff2729c5f5d611a597c61d568cd022663`** (= origin/main, contains 624A–624H). No product code changes in this slice.

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

portal-client-90day 3 failed: these are the 617C data-prerequisite titles, owned by 628G (the seed):
- `day-00-07:119` Day 3 upload + info request
- `day-45-75:133` Day 60 SoA download
- `day-85-90:91` Day 85 digest cadence

Non-vacuity: I opened `portal-v2/portal-client-90day/day-00-home-landing.png` with Read before deleting it. It shows the authenticated portal Home with "E2E Test Organization" / "Acme Corp" and the 7-item rail.

## Run notes (honest record of what did not go right first time)

1. **Run 0 of E2E-LEGAL was not gate-grade, and was discarded.** The first lifecycle / depth-ii run on a fresh stack gave lifecycle 71/15/5/6 and depth-ii 14 passed / 21 failed / 8 skipped. Every extra failure was `A snapshot doesn't exist … writing actual`. The legal visual baselines are not committed (only `.gitkeep` is tracked), so a clean worktree has none. The prior gates' numbers (620E, 622F) were taken with `--update-snapshots`. I wiped the PNGs, ran `e2e-down && e2e-up` again, and re-ran both suites with `--update-snapshots=all`. Those are the numbers above. The real (non-snapshot) failures in run 0 were the same carried titles. Log: `logs/e2e-lifecycle-run0-missing-baselines.txt`.
2. **Run 0 of login-bare failed, because my run order tripped the per-contact magic-link cap. Not a product defect.**
   - The isolation spec (2 links) and the 90-day globalSetup (1 link) issued 3 tokens for `contact@acme.example.com` at 22:56:03–22:56:18Z.
   - The cap is 3 per contact per 5 min and is DB-counted, so a backend restart does not clear it. The 4th request at 22:57:06Z logged `Magic link not issued for org e2e-test-org (PortalAuthException)` and sent no mail, so the spec timed out waiting for Mailpit.
   - I re-ran it after the window, at 23:01:44Z: 1 passed.
   - For later gates, run login-bare first, or space it ≥5 min after the other portal runs.
3. The portal suites ran against a **production** portal build (`NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`; 4 chunks contain :8081, 0 contain :8080) served on :3002. The KC `svc.sh` portal was stopped for the run and then restored: PID 35523, cwd main checkout `portal/`, `/login` 200.
4. KC backend restart (KC-STACK): `./mvnw -q clean compile -DskipTests` in the main checkout, then a `svc.sh` restart → tenant **V5 applied on KC** to `tenant_5039f2d497cf` and `tenant_56399d06a773` (`logs/kc-backend-restart.log`). KC is therefore now at tenant V5 / global V2.

## Orchestrator rulings for the KC walk (R-1..R-7, full text in `kc-observed.md`)

- R-1: KC data is not reserved. Session 0 of every re-walk deletes and re-provisions Mathebula (623A `mid/session-0.md` R.1 / Phase B). 624D's observer was wrong to refuse a new fee note.
- R-4: the multi-firm step uses a dedicated address (`multifirm.624i@email.co.za`) and never Sipho's, so later bare-login walks of Sipho still get one link.
- R-5: the second firm was provisioned through the real flow, and its Keycloak org and owner were deleted afterwards. The DB tenant schema stays.

## Follow-ups (not fixed here)

- **629 Session 0 / CP-3:** the leftover legal-za tenant schema `tenant_db75730dfc5f` ("Khanyile Attorneys 624I") and its directory row for `multifirm.624i@email.co.za` stay in the KC DB. Session 0's "only `tenant_56399d06a773` remains" read-back must accept or drop it.
- **Exchange double-POST under `next dev`** (624D follow-up): still unfixed. Every dev-mode KC walk is exposed to it until it lands.
- **Visual baselines uncommitted:** a gate on a clean worktree needs `--update-snapshots=all`, otherwise it reports missing-baseline failures. Either commit the baselines or put the flag into the gate definition (628/629 own the suites).
- 624D `kc-observed.md` evidence error: it says the orgId-path success copy matches the bare path. It does not, by design: `portal/app/login/page.tsx:162-166` shows the multi-firm sentence only when there is no orgId.
