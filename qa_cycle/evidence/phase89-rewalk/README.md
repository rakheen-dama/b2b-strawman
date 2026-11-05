# Phase 89 exit gate — 636B targeted Keycloak re-walk — evidence index

> **DRAFT SKELETON** (written by the 636B scout, 2026-11-05). Session 636B.1 fills every `<…>` placeholder; session 636B.2 completes its sections. Delete this note when final.

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
| Build sha (`$MAIN` HEAD = `origin/main` at KC-STACK) | `<sha>` (scout saw `86e0fd5de` on 2026-11-05) |
| KC-STACK time | `<ISO>` — clean compile, `docker builder prune -f`, `svc.sh start all` |
| Services (svc PID / port-holder PID / etime / cwd) | backend `<…>` · gateway `<…>` · frontend `<…>` · portal `<…>` |
| Health | 8080 `<…>` · 8443 `<…>` · 3000 `<…>` · 3002/login `<…>` · KC :8180 realm `docteams` `<…>` · Mailpit `<…>` |
| Backend ERROR lines at boot | `<n>` |
| Fresh tenant | org **`<Mathebula & Partners>`**, slug `<mathebula-partners>`, schema `<tenant_…>`, provisioned `<ISO>` (D-1 option `<A/B>`); V1…V11 `installed_on` = `<…>` |
| Fresh-branch trust pack (630A) | `<Trust report pack seeded for tenant …: 7 definitions — log line + time>` |
| Reconcile-branch trust pack (630.5, existing tenant) | `<report_definitions count on tenant_56399d06a773 = …>`; `pack_install trust-reports` on both KC tenants 2026-11-04 20:56Z (`632A/db.txt`) |
| Power | `<AC / battery %>`, caffeinate PID `<…>` |

### Flyway `flyway_schema_history` max version (Keycloak dev DB `docteams`)

| Schema | Before KC-STACK (scout 2026-11-05 21:1x) | After KC-STACK | After Day 0 provisioning | Expect |
|---|---|---|---|---|
| `tenant_5039f2d497cf` (Phase 88 Mathebula; dropped by D-1 A and re-created) | 10 (02:19:20 SAST) | `<…>` | `<…>` | V11 |
| `tenant_56399d06a773` (`org_phase86_squash_smoke`) | 10 (02:19:20 SAST) | `<…>` | `<…>` | V11 |
| `public` (global) | 3 (`portal backfill markers`, 04:32) | `<…>` | `<…>` | V3 |

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
- Firm: **55.1–55.2 (G88-F32 firm)**, **58.1 (G88-F17)**, **INT-60 (G88-F39, §14.12 #2)**, **61.1a (+M2)**, **68.2 (G88-F19, G88-F20)**, **72.1a**, **74.1**, **PRE-75-refund + APPROVE-75 (G88-F23)**, **75.9 (G88-F16, G88-F33)**, **C5**, **80.4a (G-D5)**, **80.5 (G-D5)**, **85.6 side (G88-M13)**, **85.18**, **90.1 (G-D10b)**, **TR-90 (G88-F40)**, **POPIA-1 (G88-F43)**, **POPIA-2 (G88-F44)**, **C3**, **E.11c typecheck (G88-F47)**.
  NOTE: portal swap-in, 61.1, 68.1, 70.1–72.1, 75.1, 75.2–75.8, PRESC (prescription tracker on RAF), 85.10–85.15, 88.x (wow 7), 90.4.
- Portal (Sipho unless noted): **P-0.A**, **P-0.C**, **P-0.D**, **P-0.1**, **P-0.2**, **P-0.3/0.4**, **P-0.5**, **P-0.7**, **P-0.8 (G88-F30)**, **P-0.9 (G88-F29)**, **P-3.2**, **P-3.4 (G88-F29)**, **P-21.1**, **P-21.2**, **P-21.3 (G88-F31)**, **P-CP21.2 (G88-F23)**, **P-45.2 (G88-M10, Moroka)**, **P-45.6/CP45.3 (G88-F32, Moroka)**, **P-45.8**, **P-60.2 (G88-F20)**, **P-60.5/CP60.1 (G88-F33, G88-F16)**, **P-60.6**, **SIPHO-RCPT (G88-F32)**, **P-75.1 (Lerato)**, **P-75.2 (§5b row 19)**, **P-75.3**, **P-75.6**, **P-75.7**, **P-85.1/85.2 + CP85-1**, **P-90.4**, **P-90.5**.
  N/A: P-75.4, P-75.5, P-90.1, P-90.2 (`Removed —` lines).
  NOTE: P-45.1–45.5, Sipho `/deadlines`, portal restore.
- Exit: legal **E.1–E.11** and portal **P-E.1–P-E.11**, computed in `steps/exit-checkpoints.md`.

**Not walked (SKIP — not targeted, not a prerequisite):** `<copy the SKIP list from the brief §4.3 and add anything the call budget dropped, with the reason>`.

**Suite-only gaps (no walk step in `full/gaps.md`; D-4):**
- G88-F48 / G88-F49: observable only 00:00–02:00 SAST; covered by #1716's tests and 636A lifecycle 105/0/0.
- G88-F38: optional S1-58; suite `day-07-14-work-trust.spec.ts:292` un-fixme'd.
- G88-F46: control exercised at F16-c/d.

## Sections (fill per step file — recount with `for f in steps/*.md; do for s in PASS FAIL PARTIAL DEFERRED N/A NOTE; do printf "%s %s %s\n" $f $s $(grep -cE "^\| [^|]+ \| [^|]+ \| $s \|" $f); done; done`)

| Session | File | Rows (scored) | PASS / FAIL / PARTIAL / DEFERRED / N/A | NOTE |
|---|---|---|---|---|
| — | `steps/session-0.md` | 0 | — | `<n>` |
| 636B.1 | `steps/day-00.md` | `<n>` | `<…>` | `<n>` |
| 636B.1 | `steps/days-01-07.md` | `<n>` | `<…>` | `<n>` |
| 636B.1 | `steps/days-08-21.md` | `<n>` | `<…>` | `<n>` |
| 636B.1 | `steps/days-22-45.md` | `<n>` | `<…>` | `<n>` |
| 636B.2 | `steps/days-46-90.md` | `<n>` | `<…>` | `<n>` |
| 636B.2 | `steps/portal.md` | `<n>` | `<…>` | `<n>` |
| 636B.2 | `steps/exit-checkpoints.md` | `<n>` | `<…>` | — |
| | **Total** | `<n>` | `<…>` | `<n>` |

Browser calls: 636B.1 `<n>` / 200 · 636B.2 `<n>`.

## Wow frames (E.2 / P-E.2 inputs; 636C re-shoots the deck on a production build)

| Frame | Step | File | Reviewed (defects) |
|---|---|---|---|
| Legal wow 1 — Home | 0.25 | `screenshots/wow-1-home.png` | `<…>` |
| Legal wow 2 — engagement letter accepted | 4.3c | `screenshots/wow-2-accepted.png` | `<…>` |
| Legal wow 3 — conflict check No Conflict | 1.4 | `screenshots/wow-3-conflict.png` | `<…>` |
| Legal wow 4 — matter detail | 5.4 | `screenshots/wow-4-matter.png` | `<…>` |
| Legal wow 5 — profitability | 33.5 | `screenshots/wow-5-profitability.png` | `<…>` |
| Legal wow 6 — fee note PDF | 47.3 | `screenshots/wow-6-fee-note-pdf.png` | `<…>` |
| Legal wow 7 — activity feed | 88.3 | `screenshots/wow-7-activity.png` | `<…>` |
| Portal wow 1 — home | P-0.6 | `screenshots/p-wow-1-home.png` | `<…>` |
| Portal wow 2 — trust balance | P-21.6 | `screenshots/p-wow-2-trust.png` | `<…>` |
| Portal wow 3 — fee note paid / receipt | P-45.7 | `screenshots/p-wow-3-paid.png` | `<…>` |
| Portal wow 4 — document downloaded | P-60.6 | `screenshots/p-wow-4-document.png` | `<…>` |
| Portal wow 5 | — | dropped (Phase 89 decision 7 / G88-F34) | N/A |

## Results

- Register: `gaps.md` — exit rule **0 HIGH + 0 MEDIUM**. Verdict: `<…>`.
- Marker audit: `markers.md`. Scout pre-check: 26/26 PRs #1716–#1741 exit 0 with `--committed-only`; 11 backend markers on #1722+.

## Stack state left by 636B.1 / for 636B.2

`<see HANDOFF.md; summarise services, tenant, last row, next row>`

## Stack state left by 636B.2 / for 636C

`<services, portal restored, tenant data summary for the deck re-shoot, Mailpit count, $MAIN status>`
