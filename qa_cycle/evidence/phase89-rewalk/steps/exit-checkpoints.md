# Exit checkpoints — legal E.1–E.11 and portal P-E.1–P-E.11 (computed by 636B.3 after the register was final)

Inputs: `steps/*.md` (106 scored rows, S2-13 scored per ruling 0.R4), `gaps.md` (26 open ids: 3 HIGH, 6 MEDIUM, 17 LOW after ruling 0.R5), `markers.md` (26/26 exit 0). Scoring rules per brief §4.2 / D-5: E.1 is scored over the SCORED rows only; SKIP rows are "not walked — targeted re-walk (decisions 2–3)".

## Legal (L `:578-593`)

| Row | Checkpoint | Result | Evidence (how scored) | Gap |
|---|---|---|---|---|
| E.1 | Every step above is checked | FAIL | 106 scored rows: 98 PASS · 6 FAIL (S1-29, S2-12, S2-25, S2-45, S2-55, S2-59) · 2 PARTIAL (S1-02, S1-76). SKIP rows listed in README "Not walked" | G89-F4, F12, F18, F22, F24, F25 |
| E.2 | All 7 wow moments captured without visual regression | PARTIAL | All 7 captured: `wow-1-home.png` (S1-06), `wow-2-accepted.png` (S1-27), `wow-3-conflict.png` (S1-20), `wow-4-matter.png` (S1-30), `wow-5-profitability.png` (S1-70), `wow-6-fee-note-pdf.png` (S1-76), `wow-7-activity.png` (S2-22). Reviewed: wow-6 is the HTML preview, not the rendered PDF, and has no banking block (S1-76 PARTIAL); wow-7 shows "status to DONE / IN_PROGRESS" (G88-F24, open LOW, cited); wow-4 was retaken after reload (G89-F5); 1–3 and 5 no defect | G89-F5; G88-F24 (cited) |
| E.3 | Zero BLOCKER or HIGH in `gaps.md` | FAIL | 3 HIGH open (G89-F11, G89-F12, G89-F22 — all confirmed, 0.R3/0.R5) | G89-F11, G89-F12, G89-F22 |
| E.4 | Tier removal on 3+ screens | PASS | S1-18 Subscription flat + "Managed Account"; S1-07/S1-08 team invite inline form, no "Upgrade to Pro"; S2-24 "3 members · 0 pending invitations", Subscription "Billing · Trial · Manual · Managed Account" | — |
| E.5 | Field promotion on Client / Matter / Task create dialogs | N/A | Not targeted (0.49–0.52 SKIP, decisions 2–3) | — |
| E.6 | Progressive disclosure — 4 legal modules, no cross-vertical leak | PASS | S1-06 six zones (Today · Matters · Clients · Fees · Trust · Firm); S1-16 Trust zone "Trust reports", 7 cards; trust, court calendar, disbursements, conflict check all reached on the walk; no non-legal module seen | — |
| E.7 | Keycloak flow end-to-end | PASS | S1-01…S1-10: `/request-access` → OTP → padmin approve → Thandi registers via invite → dashboard; Bob / Carol invited and registered; no mock IDP (S1-02 PARTIAL only for the uncaptured "Submitting…" transient) | — |
| E.8 | Terminology sweep | N/A | 90.1 sweep SKIP (not targeted); still-open LOWs G88-F27 / G-D10a / G-D10c cited | — |
| E.9 | 90 days of audit events recorded and filterable | PASS | S1-79 ("Disbursement written off"), S2-02 ("Fee note paid"), S2-23 (72 event types, label-only, `invoice.sent` → "Fee note sent 2"); 252 events at 00:54 SAST | — |
| E.10 | One clean pass, no dev dispatch mid-loop | PASS | No product code changed during 636B (`markers.md` tree identity: backend / frontend / portal diffs empty against the verified heads). Orchestrator rulings (0.R2–0.R4) and one corrective trust entry through the UI (CORR, ruling 0.R4) are recorded; neither is a dev dispatch | — |
| E.11 | Test suite gate | PASS | E.11a backend byte-identical to #1735's verified head (`git diff a8d5b86ae..HEAD -- backend` empty; #1735 marker OK); E.11b/d frontend identical to #1739 (marker OK); E.11c `pnpm typecheck` frontend + portal exit 0 (S2-29); every Phase 89 PR's markers committed (26/26 exit 0) | — |

Legal: PASS 6 · FAIL 2 · PARTIAL 1 · N/A 2.

## Portal (P `:243-256`; there is no P-E.7)

| Row | Checkpoint | Result | Evidence | Gap |
|---|---|---|---|---|
| P-E.1 | Every step checked (or N/A with rationale) | FAIL | Portal rows: 28 PASS · 3 FAIL (S2-45; S2-55, S2-59 as written) · 2 N/A rows (`Removed —` P-75.4/75.5, P-90.1/90.2). Only the legal-za profile was exercised (targeted walk) | G89-F22, F24, F25 |
| P-E.2 | 4 wow frames captured (wow 5 dropped), each reviewed (P-E.2b) | PARTIAL | All 4 captured and reviewed, but only 2 are clean: `p-wow-1-home.png` (S2-36) four cards, no defect · `p-wow-3-paid.png` (S2-48) clean content (captured at 1698 px — see P-E.10). **Defects in the frames:** `p-wow-2-trust.png` (S2-44) layout clean, but its **content** shows the G89-F12 artefacts (running balance -R 1,34, the CORR description) — not a deck candidate as-is; `p-wow-4-document.png` (S2-52) prints the raw enum "Matter type: LITIGATION" (G89-F26) and the Documents row sits at the bottom edge (composition — 636C should scroll) | G89-F12 (p-wow-2 content), G89-F26 (p-wow-4 copy) |
| P-E.3 | Zero BLOCKER or HIGH in `gaps.md` | FAIL | 3 HIGH open (G89-F11, G89-F12, G89-F22 — F22 is the portal trust balance) | G89-F11, G89-F12, G89-F22 |
| P-E.4 | Profile gating (`/trust`, `/deadlines` legal-za only) | N/A | Only the legal-za tenant was walked (targeted re-walk); `/trust` and `/deadlines` render for legal-za without error | — |
| P-E.5 | Magic link → JWT in localStorage, no auth cookie, zero KC form | PASS | Every portal sign-in (Sipho ×2, Moroka, Lerato; sessions 2 + 3) was a magic link → one `POST /portal/auth/exchange` → localStorage `portal_jwt, portal_customer, portal_last_org_id`, cookies **0**, no Keycloak form; firm sessions ran in a separate context | — |
| P-E.6 | Terminology sweep | N/A | Not swept (targeted walk). Observed portal labels consistent: "Matters", "Fee notes", "Trust statement", "Diary dates", "Documents & requests"; one raw enum in seeded description text → G89-F26 | G89-F26 |
| P-E.8 | Email follow-through without re-login (Day 0 / 7 / 21 / 75) | PARTIAL | Day 0: "Your portal access link" → Access Portal → `/matters` (S2-35, S2-43 sign-in) PASS · Day 21: "Trust account activity" → View trust ledger → `/trust/<matter>` without re-login (S2-43) PASS · Day 75: no deadline nudge email exists (G89-F24) → not observable · **Day 7 weekly digest → DEFERRED (sub-item; not a product failure, no G89 id):** the only digest producer is `PortalDigestScheduler.scheduledRun` with `@Scheduled(cron = "0 0 8 ? * MON", zone = KaziTime.ZONE_ID)` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestScheduler.java:105-106`; `KaziTime.ZONE_ID = "Africa/Johannesburg"`, `config/KaziTime.java:17`) — Monday 08:00 SAST only. The compressed walk ran Thu 2026-11-05 21:34 → Fri 2026-11-06 01:25 SAST, so no tick could fire (next tick Mon 2026-11-09 08:00 SAST); the portal Day-7 rows were SKIP (not targeted); the manual trigger `POST /internal/portal/digest/run-weekly` (`PortalDigestInternalController.java:23,32`) is an internal API, outside a browser-only walk. Mailpit (19 messages) holds no "Your weekly update" email, as expected. Owner: a post-Phase-89 observation on a run that crosses a Monday 08:00 SAST tick (digest → **Open portal** → `/home` without re-login) | G89-F24 |
| P-E.9 | Payment flow end-to-end + receipt | PASS | S2-46 mock checkout → "Payment confirmed · Paid on 6 Nov 2026"; S2-48 receipt INV-0002/R1 downloads, bytes ≠ fee note | — |
| P-E.10 | Visual review at `lg` | PARTIAL | 3 of 4 frames reviewed at the 1440 `lg` viewport with no layout defect (p-wow-1, p-wow-2, p-wow-4; p-wow-4's bottom-edge Documents row is composition, not layout). **`p-wow-3-paid.png` was captured at the isolated context's default 1698 px viewport, not at 1440** — that frame was not reviewed at `lg`. 1024 checks on S2-44, S2-48, S2-53, S2-56, S2-57 had no horizontal overflow | — (capture deviation; 636C re-shoots at 1440) |
| P-E.11 | Test suite gate | PASS | Portal prod build exit 0 (S2-00, `logs/portal-build.log`); `pnpm typecheck` portal exit 0 (S2-29); portal tree identical to #1732's verified head with its marker OK (`markers.md`); 636A portal vitest 273 + suites green. The "500B" bullet is stale (PR #1738 out-of-scope list) → N/A | — |

Portal: PASS 3 · FAIL 2 · PARTIAL 3 · N/A 2 (P-E.7 does not exist). Row-level DEFERRED 0; one DEFERRED sub-item inside P-E.8 (Day-7 weekly digest).

All 21 computed rows: PASS 9 · FAIL 4 · PARTIAL 4 · DEFERRED 0 · N/A 4.
