# Phase 89 exit gate — 636B targeted Keycloak re-walk — evidence index

> Final (636B.3, 2026-11-06 01:2x SAST). Sessions: 636B.1 (2026-11-05 21:34–22:53 SAST), 636B.2 (22:54–23:26 SAST), 636B.3 (2026-11-06 00:42–01:25 SAST, **inside 00:00–02:00 SAST under the founder's amendment** — every date the product printed in that window was the SA date). Orchestrator rulings 0.R, 0.R2, 0.R3, 0.R4 and 0.R5 are summarised (0.R3/0.R4 quoted) in the **Rulings index** at the top of `HANDOFF.md`, so they can be read without the uncommitted walker brief.

Scenarios:
- `qa/testplan/demos/legal-za-90day-keycloak.md` (as amended by 635A / PR #1738);
- `qa/testplan/demos/portal-client-90day-keycloak.md` (as amended by 635A).

Targeted walk (Phase 89 founder decisions 2–3, rulings §8; task file `tasks/phase89-go-live-readiness.md` 636.2–636.5):
- the step ids of every gap closed by #1716–#1721 and Epics 630–634 (from `qa_cycle/evidence/phase88-rewalk/full/gaps.md`);
- every step 635A amended;
- G-D5, re-observed with a court date inside 14 days.

Prerequisite steps are recorded as NOTE and are not scored. Steps that were neither targeted nor a prerequisite are SKIP (listed below). The walk was browser-only through Playwright MCP, with read-only SELECTs for evidence and the Mailpit API for mail. Status vocabulary: PASS / FAIL / PARTIAL / DEFERRED / N/A (+ NOTE); VERIFIED is used for closed gaps in `gaps.md`.

Other files in this directory:
- per-slice evidence `630B/` … `634E/`;
- 636A suites `suites/`;
- this walk's `steps/`, `screenshots/`, `logs/`, `HANDOFF.md`, `gaps.md`, `markers.md`.

## Build, stack and tenant (636.2)

| Item | Value |
|---|---|
| Build sha (`$MAIN` HEAD = `origin/main` at KC-STACK) | `86e0fd5deca903225f959723b1a43e99c4b32e3b` (= `origin/main`, fetched 21:34:55 SAST; `logs/kc-stack-identity.txt`) |
| KC-STACK time | 2026-11-05T21:35:36+02:00 stop → `docker builder prune -f` → `./mvnw -q clean compile -DskipTests` (exit 0, tenant migrations end `V11__phase89_comment_notification_id.sql`) → `pnpm install --frozen-lockfile` → `svc.sh start all` 21:36:41–21:37:08; backend restarted once more for the D-1 reset at 21:38:43 (`logs/kc-stack-restart.txt`, `logs/session0-reset.txt`) |
| Services (svc PID / port-holder PID / etime / cwd) | backend :8080 holder **76758** (started 21:38:43) · gateway svc 75499 / :8443 **75625** (21:36:58) · frontend svc 75638 / :3000 **75682** (21:37:03, `next dev` 16.3.8) · portal svc 75689 / :3002 **75733** (21:37:08, `next dev`) — all cwd under `$MAIN`; old holders 86551 / 334 / 83754 / 51281 stopped first |
| Health | 8080 `UP` · 8443 `UP` · 3000 `200` · 3002/login `200` · KC :8180 realm `docteams` `200` · Mailpit cleared → 200 (`DELETE /api/v1/messages`) |
| Backend ERROR lines at boot | `0` (both boots) |
| Fresh tenant | org **Mathebula & Partners**, slug `mathebula-partners`, schema `tenant_5039f2d497cf` (deterministic name, re-created), provisioned 2026-11-05T19:45:00Z (D-1 option **A**; KC org id `2cc9bcfd-fe7a-4f45-a343-0adddbd5ee28`); V1…V11 `installed_on` = 21:45:00.846 → 21:45:02.224 SAST (11 rows); `vertical_profile = legal-za`; "Successfully applied 11 migrations … now at version v11" 19:45:02.238Z |
| Fresh-branch trust pack (630A) | `2026-11-05T19:45:02.912985Z [TrustReportPackSeeder] Trust report pack seeded for tenant tenant_5039f2d497cf: 7 definitions`; 7 `report_definitions` rows |
| Reconcile-branch trust pack (630.5, existing tenant) | `report_definitions` count on `tenant_56399d06a773` (legal-za) = **7** (`logs/flyway-after-kc-stack.txt`); `pack_install trust-reports` on both KC tenants 2026-11-04 20:56Z (`632A/db.txt`) |
| Power | **battery** (no AC available): 64 % at 21:34 → 39 % at 22:49 SAST; `caffeinate -dimsu -t 14400` PID 74574; lid open |

### Flyway `flyway_schema_history` max version (Keycloak dev DB `docteams`)

| Schema | Before KC-STACK (scout 2026-11-05 21:1x) | After KC-STACK | After Day 0 provisioning | Expect |
|---|---|---|---|---|
| `tenant_5039f2d497cf` (Phase 88 Mathebula; dropped by D-1 A and re-created) | 10 (02:19:20 SAST) | 11 (21:36:53) — then dropped (backup `/tmp/p88-tenant-backup-202611052137.sql`) | **11** (fresh, 21:45:02) | V11 |
| `tenant_56399d06a773` (`org_phase86_squash_smoke`) | 10 (02:19:20 SAST) | **11** (21:36:53) | 11 | V11 |
| `public` (global) | 3 (`portal backfill markers`, 04:32) | **3** | **3** | V3 |

Boot lines: `logs/kc-stack-backend-startup.txt`. Provisioning lines: `logs/day00-provisioning.txt`.

## Walk list (day order)

Full expectations are in the walker brief (§4, uncommitted). Each step file repeats the expected text per row.

**Session 636B.1 — Days 0–45 + amended firm steps** (scored rows in bold):
- Day 0: **0.5**, **0.15**, **0.28**, **0.29**, **0.41**, **0.47 (G88-F3)**, **0.48 (G88-F3)**, **0.59 + TR-0 (G88-F40)**.
  NOTE: Session 0 / KC-STACK, 0.1–0.4, 0.6–0.14, 0.16–0.27, 0.30–0.40, 0.42–0.45, 0.61–0.67.
- Days 1–7: **1.3**, **3.6a (+G88-M2)**, **3.8**, **4.1**, **4.6 (G-B8)**, **5.3 (§14.12 #6/B7)**, **5.4 (G88-M13)**, **5.12 (G88-M4)**, **5.14 (G88-M4)**, **6.1 (+M13 member)**, **6.2 (G88-F36)**, **6.3 (G88-M6)**, **6.5**, **MC-1 (B7 + PROJECT deep link)**, **7.1**.
  NOTE: 1.1–1.2, 1.5–2.8, 3.1–3.6, 3.7, 4.2–4.4, 5.5–5.11.
- Days 8–21: **8.1**, **9.4a (+M2)**, **12.1**, **12.2**, **12.3**, **14.4 (G88-F12)**, **C1-a (C1)**, **14.5 (G88-F12)**, **14.7 (+C1 negative)**, **14.8 (G88-F12)**, **15.3**.
  NOTE: 8.2–9.4, 9.5, 10.1, 11.1, 13.1, 14.2–14.3, 15.1–15.2.
- Days 22–45: **22.1**, F38-opt (G88-F38, optional), **23.3**, **28.1a (+M2)**, **C6-a (C6)**, **30.3-soa**, **30.4-soa (G88-F16)**, **30.5-soa**, **30.6-soa (G88-F16, F33 baseline)**, **C6-b (C6)**, **F16-c/d (G88-F16, F46 control)**, **33.1-comment (G88-F36)**, **C2-neg (C2)**, **35.1**, **38.2 (G88-F17)**, **45.3 (G88-M10)**, **45.4 + 47.1–47.2 (G88-M10)**, **45.5 (G88-M4)**, **45.9 (G88-F17)**, **45.10 (+G88-M13)**.
  NOTE: 22.2–23.2, 30.1–30.2-soa, 33.x profitability (wow 5), 36.1–38.1, 45.1–45.2, 45.6–45.8.

**Session 636B.2 — Days 46–90 + portal + E-checkpoints:**
- Firm: **55.1–55.2 (G88-F32 firm)**, **58.1 (G88-F17)**, **INT-60 (G88-F39, §14.12 #2)**, **61.1a (+M2)**, **68.2 (G88-F19, G88-F20)**, **72.1a**, **74.1**, **PRE-75-refund + APPROVE-75 (G88-F23)**, **75.2–75.8 (S2-13 — scored per ruling 0.R4, after the CORR NOTE)**, **75.9 (G88-F16, G88-F33)**, **C5**, **80.4a (G-D5)**, **80.5 (G-D5)**, **85.6 side (G88-M13)**, **85.18**, **90.1 (G-D10b)**, **TR-90 (G88-F40)**, **POPIA-1 (G88-F43)**, **POPIA-2 (G88-F44)**, **C3**, **E.11c typecheck (G88-F47)**.
  NOTE: portal swap-in, 61.1, 68.1, 70.1–72.1, 75.1, CORR (S2-13 prerequisite, ruling 0.R4), PRESC (prescription tracker on RAF), 85.10–85.15, 88.x (wow 7), 90.4.
- Portal (Sipho unless noted): **P-0.A**, **P-0.C**, **P-0.D**, **P-0.1**, **P-0.2**, **P-0.3/0.4**, **P-0.5**, **P-0.7**, **P-0.8 (G88-F30)**, **P-0.9 (G88-F29)**, **P-3.2**, **P-3.4 (G88-F29)**, **P-21.1**, **P-21.2**, **P-21.3 (G88-F31)**, **P-CP21.2 (G88-F23)**, **P-45.2 (G88-M10, Moroka)**, **P-45.6/CP45.3 (G88-F32, Moroka)**, **P-45.8**, **P-60.2 (G88-F20)**, **P-60.5/CP60.1 (G88-F33, G88-F16)**, **P-60.6**, **SIPHO-RCPT (G88-F32)**, **P-75.1 (Lerato)**, **P-75.2 (§5b row 19)**, **P-75.3**, **P-75.6**, **P-75.7**, **P-85.1/85.2 + CP85-1**, **P-90.4**, **P-90.5**.
  N/A: P-75.4, P-75.5, P-90.1, P-90.2 (`Removed —` lines).
  NOTE: P-45.1–45.5, Sipho `/deadlines`, portal restore.
- Exit: legal **E.1–E.11** and portal **P-E.1–P-E.11**, computed in `steps/exit-checkpoints.md`.

**Not walked (SKIP — not targeted, not a prerequisite):** session 636B.1: 0.49–0.58, 1.4, 2.9, 3.9–3.10, 6.4, 6.6, 10.2, 14.1, 14.6, 14.9–14.13, 15.4, 16.1–21.1, 23.4–23.6, 24.1, 26.1, 28.1, 30.1 (Carol), 30.7, 32.1, 36.x detail, 38.3–38.4, 45.11 — reason: not targeted. **Carried to 636B.2 by the session-1 stop (not SKIP):** S1-78 (45.6–45.8), S1-79 (45.9), S1-80 (45.10) — blocked by a brief-internal conflict (S1-72 billed every Sipho disbursement; see `HANDOFF.md`). Sessions 636B.2 / 636B.3 (S1-78…S1-80 were walked by 636B.2): 50.1, 62.1, 65.1, 70.1 (Carol time), 75.10–75.11, 80.1–80.4, 85.1–85.5 (Bob pass), 85.7–85.9, 85.16–85.17, 88.x detail, 90.2–90.6 sweeps, portal Day 7, P-0.6, P-0.10, P-3.1, P-3.3, P-3.5–3.6, P-21.4–21.6, P-60.1/60.3/60.4 (driven inside S2-51), P-85.3–85.6, P-90.3, P-90.6 — reason: not targeted. **Ruled deviations:** D-2 (portal /deadlines narrated as Lerato), 0.R2 (D-3(h) extra disbursement for 45.6), 0.R4 (S2-13 corrective R 1,34 entry `CORR/G89-F12` as a NOTE prerequisite; S2-12 re-scored FAIL; S2-13 scored on 75.2–75.8), D-6 fallback (Day 30 on 5 Nov, Day 75 on 6 Nov SAST). Order deviations are recorded in the step files (portal Sipho rows in the Day-68 sign-in; C3 before POPIA-2).

**Suite-only gaps (no walk step in `full/gaps.md`; D-4):**
- G88-F48 / G88-F49: observable only 00:00–02:00 SAST; covered by #1716's tests and 636A lifecycle 105/0/0.
- G88-F38: optional S1-58; suite `day-07-14-work-trust.spec.ts:292` un-fixme'd.
- G88-F46: control exercised at F16-c/d.

## Sections (fill per step file — recount with `for f in steps/*.md; do for s in PASS FAIL PARTIAL DEFERRED N/A NOTE; do printf "%s %s %s\n" $f $s $(grep -cE "^\| [^|]+ \| [^|]+ \| $s \|" $f); done; done`)

| Session | File | Rows (scored) | PASS / FAIL / PARTIAL / DEFERRED / N/A | NOTE |
|---|---|---|---|---|
| — | `steps/session-0.md` | 0 | — | 8 |
| 636B.1 | `steps/day-00.md` | 8 | 7 / 0 / 1 / 0 / 0 | 9 |
| 636B.1 | `steps/days-01-07.md` | 15 | 14 / 1 / 0 / 0 / 0 | 6 |
| 636B.1 | `steps/days-08-21.md` | 11 | 11 / 0 / 0 / 0 / 0 | 5 |
| 636B.1 + 636B.2 | `steps/days-22-45.md` | 20 (18 by 636B.1; S1-79, S1-80 by 636B.2) | 18 / 0 / 2 / 0 / 0 | 6 (4 by 636B.1; D-3(h), S1-78 by 636B.2) |
| 636B.2 + 636B.3 | `steps/days-46-90.md` | 21 (S2-13 scored per ruling 0.R4) | 19 / 2 / 0 / 0 / 0 | 11 (+1 superseded BLOCKED row) |
| 636B.2 + 636B.3 | `steps/portal.md` | 31 (+2 N/A rows) | 26 / 5 / 0 / 0 / 2 | 3 |
| | **Total (walk rows = sum of the rows above)** | **106** (+2 N/A rows) | **95 / 8 / 3 / 0 / 2** | 48 |
| 636B.3 | `steps/exit-checkpoints.md` (not walk rows; not in the total) | 21 computed (E.1–E.11, P-E.1–P-E.11 without P-E.7) | 9 / 4 / 4 / 0 / 4 — row-level DEFERRED 0; **1 DEFERRED sub-item**: P-E.8 Day-7 weekly digest (cron Monday 08:00 SAST never elapsed in the Thu 21:34 → Fri 01:25 walk window, `PortalDigestScheduler.java:106`) | — |

Browser calls: 636B.1 **177** / 200 (Playwright MCP tool calls, incl. 1 `browser_close`, 3 snapshots/evaluates and 1 click; most UI actions batched in `browser_run_code_unsafe`) · 636B.2 ≈109 / 250 · 636B.3 **88** / 250 (fresh MCP browser; isolated contexts sipho3 / moroka3 / lerato3).

## Wow frames (E.2 / P-E.2 inputs; 636C re-shoots the deck on a production build)

| Frame | Step | File | Reviewed (defects) |
|---|---|---|---|
| Legal wow 1 — Home | 0.25 | `screenshots/wow-1-home.png` | 636B.1: captured (fresh tenant, empty-state Home, 6 zones); no defect noted |
| Legal wow 2 — engagement letter accepted | 4.3c | `screenshots/wow-2-accepted.png` | 636B.1: captured (Generated documents row "Accepted"); no defect noted |
| Legal wow 3 — conflict check No Conflict | 1.4 | `screenshots/wow-3-conflict.png` | 636B.1: captured ("No Conflict"); no defect noted |
| Legal wow 4 — matter detail | 5.4 | `screenshots/wow-4-matter.png` | 636B.1: captured after reload (first pass showed stale "0.0h", G89-F5 candidate) |
| Legal wow 5 — profitability | 33.5 | `screenshots/wow-5-profitability.png` | 636B.1: captured; no defect noted |
| Legal wow 6 — fee note PDF | 47.3 | `screenshots/wow-6-fee-note-pdf.png` | 636B.1: captured from the firm HTML **Preview** (`/api/invoices/<id>/preview`), not a rendered PDF — S1-76 PARTIAL; no banking block (never configured) |
| Legal wow 7 — activity feed | 88.3 | `screenshots/wow-7-activity.png` | 636B.3: captured (estate Activity after the override close); shows raw "status to DONE / IN_PROGRESS" (G88-F24, open LOW, cited) |
| Portal wow 1 — home | P-0.6 | `screenshots/p-wow-1-home.png` | 636B.3 review: four cards, no layout defect |
| Portal wow 2 — trust balance | P-21.6 | `screenshots/p-wow-2-trust.png` | 636B.3: layout clean; content shows the G89-F12 artefacts (running balance -R 1,34, the CORR entry) — not a deck candidate as-is |
| Portal wow 3 — fee note paid / receipt | P-45.7 | `screenshots/p-wow-3-paid.png` | 636B.3: clean (Paid badge, Payments row, Download receipt); taken at the popup default 1698 px |
| Portal wow 4 — document downloaded | P-60.6 | `screenshots/p-wow-4-document.png` | 636B.3: Documents row at the bottom edge (re-shoot should scroll); description shows "Matter type: LITIGATION" (G89-F26) |
| Portal wow 5 | — | dropped (Phase 89 decision 7 / G88-F34) | N/A |

## Results

- Register: `gaps.md` — exit rule **0 HIGH + 0 MEDIUM**. Verdict: **FAIL** — 3 HIGH (G89-F11, G89-F12, G89-F22; F11 is recorded HIGH, the founder may downgrade it to MEDIUM in 636C if ruled an input-validation edge — ruling 0.R4 item 2) + 6 MEDIUM (G89-F13, F14, F16, F17, F18, F23) + 18 LOW open (27 ids), all HIGH/MEDIUM confirmed by the orchestrator (0.R3, 0.R5 in `gaps.md`); 4 scenario mismatches (F4, F24, F25, F27) for founder §6. Closed-gap re-verification: 29 VERIFIED · 5 PARTIAL · 2 FAIL (G88-F40 → F18; G88-F23 → F12/F22) · 2 suite-covered (F48, F49).
- Walk rows: **106 scored → PASS 95 · FAIL 8 · PARTIAL 3 · DEFERRED 0** (+2 N/A rows) — after the CodeRabbit re-score of S1-65 (→ PARTIAL), S2-41 and S2-56 (→ FAIL as written). Exit checkpoints: legal E PASS 6 · FAIL 2 · PARTIAL 1 · N/A 2; portal P-E PASS 3 · FAIL 2 · PARTIAL 3 · N/A 2 (one DEFERRED sub-item inside P-E.8).
- Marker audit: `markers.md`. Scout pre-check: 26/26 PRs #1716–#1741 exit 0 with `--committed-only`; 11 backend markers on #1722+.

## Stack state left by 636B.1 / for 636B.2

See `HANDOFF.md`. Services unchanged since KC-STACK (backend 76758, gateway 75625, frontend 75682, portal 75733 — portal still `next dev`, prod swap is S2-00). Tenant `tenant_5039f2d497cf` with 4 ACTIVE clients, 4 ACTIVE matters, INV-0001 / INV-0002 SENT, REQ-0001 Sent, SoA shared to portal. Last row done **S1-77**; next row **S1-78** (needs an orchestrator ruling — see HANDOFF "Blocking conflict"). Clock: Day 30 rows ran 2026-11-05 SAST.

## Stack state left by 636B.2 / for 636C

Services (01:11 SAST): backend :8080 76758, gateway :8443 75625, frontend :3000 75682 (`next dev`), **portal restored to svc `next dev`** (svc 19714 / next-server 19757) — prod build 99129/99092 killed by exact PID (`logs/portal-restore.txt`). `$MAIN` HEAD `86e0fd5de`, status = pre-walk (`portal/next-env.d.ts` back to its pre-walk content, `tasks/insights/latest.stats.json`). Mailpit total 19.

Tenant `tenant_5039f2d497cf` for the 636C deck re-shoot: clients Sipho (matter **CLOSED** 6 Nov, client trust R 1,34 = interest), Moroka (estate **CLOSED** under override, trust R 38 017,81, INV-0002 **PAID** by card), Khumalo (OTP matter ACTIVE), Lerato (RAF ACTIVE: Hearing 13 Nov, Case Management 20 Dec, prescription 26 Nov), Nomvula (Debt Recovery ACTIVE), "POPIA Scratch p89rw" (anonymised). INV-0001 PAID (EFT). Trust account R 38 019,15. Caveat for the deck: Sipho's trust frames carry the G89-F12 artefacts (-R 1,34 row, CORR entry).
