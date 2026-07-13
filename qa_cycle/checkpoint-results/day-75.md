# Day 75 — Weekly digest + late-cycle isolation spot-check `[PORTAL]` — cycle 2026-07-12 (run 2026-07-13)

**Actor**: Sipho Dlamini (portal :3002 — standing session still valid, no re-login needed).

**Digest mechanism**: PortalDigestScheduler cron is Monday 08:00; this session ran Monday 2026-07-13 at ~03:30 SAST, i.e. the cron tick was still ~4.5 h in the future — no auto-digest in Mailpit. Used the product's manual trigger per GAP-L-99 precedent (standing REST exception): `POST /internal/portal/digest/run-weekly?orgId=mathebula-partners&targetEmail=sipho.portal@example.com` with `X-API-KEY`. Dry-run first → `{tenantsProcessed:1, digestsSent:1(would-have-sent), skipped:0, errors:[]}`, then real send → `{tenantsProcessed:1, digestsSent:1, skipped:0}`. Backend log: "Portal digest sweep complete … 1 digest emails sent, 0 errors".

**`portal_digest_cadence` unset — handling note (handoff question)**: org_settings row has `portal_digest_cadence = NULL` and `digest_last_sent_at = NULL` pre-run. Scheduler code (PortalDigestScheduler ~L201–204) defaults a null/absent cadence to **WEEKLY**, so the digest sent normally — no enabling step required. Post-run, `digest_last_sent_at` stamped `2026-07-13 01:30:01+00` (cadence column itself stays NULL; default-WEEKLY is the designed shape).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 75.1 | PASS | Mailpit `3GqhueqL8RYKTdir8DCNis` → sipho.portal@example.com, subject **"Mathebula & Partners: Your weekly update"** (01:30:01Z) |
| 75.2 | PASS (designed copy-shape per prior-cycle precedent) | Body: "Hi Sipho Dlamini, Here is your activity summary for the past 7 days." → **Recent fee notes: INV-0001 — PAID ZAR 1250.00**; **Trust account activity: 3 transaction(s) recorded in your trust account.** Client-safe copy throughout. The digest's designed content model is fee notes + trust + requests + deadlines; it does not render literal "SoA downloaded"/"matter closed" lines (same accepted shape as prior cycle — the underlying paid/trust events are represented) |
| 75.3 | PASS (BLOCKER-severity isolation) | Full digest HTML scan: **zero** matches for moroka / est-2026 / peter / deceased / liquidation / estate / 25 000 / 25,000 / 25000 |
| 75.4 | PASS | Digest CTA "Open portal →" href = `http://localhost:3002/home` (unsubscribe → `/settings/notifications?unsubscribe=1`). Navigated the CTA URL → `/home` renders Sipho-only: Pending info requests 0, Upcoming deadlines 0, Recent fee notes INV-0001 R 1 250,00, Last trust movement R 70 000,00 · 13 Jul 2026 |
| 75.5 | PASS (+ LZKC-020 improvement) | `/activity` "Your actions" timeline, all actor "You": document downloads ×2 (Day 61 SoA + closure letter), REQ-0003 item submits ×2 + uploads ×2 (Day 46), fee-note payment (Day 30), **"Engagement letter accepted — You" (Day 8 — NEW vs prior cycle: acceptance now appears in the client's own trail, LZKC-020 fix visible portal-side)**, FICA submits ×3 + uploads ×3 (Day 4). Zero Moroka references. Day 11/15 "trust balance view" entries N/A — page views aren't audited (prior-cycle content-model precedent) |
| 75.6 | PASS (passive isolation, Day-14 Moroka onboarding + 61 script-days) | `/home` clean (above); `/trust` auto-forwards to Sipho's single ledger — **balance R 0,00** "As of 13 Jul 2026" (PAYMENT R 70 000 → 0; deposits 20k/50k below with correct running balances), NOT the R 25 000 Moroka figure; `/projects`: All = RAF matter + Engagement Letter project (both Sipho's, known auto-created 2nd project observation), **Past tab = Dlamini v Road Accident Fund only (CLOSED matter, renders cleanly, no error/grey-out)** |
| 75.7 | PASS | 📸 `day-75-portal-digest-plus-activity.png` |

## Day 75 day-level checkpoints

- Digest contents match activity trail (per digest content model, no missing-event discrepancy): **PASS**
- Closed matter correctly rendered as closed (Past tab): **PASS**
- Isolation holds at Day 75: **PASS**

## Console

0 portal JS errors across home/activity/trust/projects (one Next.js `scroll-behavior: smooth` framework WARNING — advisory, not an error, not product code).

## Gaps

None new. Known observations re-seen and not re-filed: trust card label "Matter 66451e87" UUID prefix (cosmetic, carried).
