# Day 75 — Weekly digest + late-cycle isolation spot-check `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini (portal :3002, existing session).

**Digest mechanism**: PortalDigestScheduler cron is Monday 08:00; backend was not yet running at 08:00 today, so no auto-digest in Mailpit. Used the product's manual trigger (GAP-L-99 precedent, prior-cycle day-75): `POST /internal/portal/digest/run-weekly?orgId=mathebula-partners&targetEmail=sipho.portal@example.com` with `X-API-KEY` — dry-run `{tenantsProcessed:1, digestsSent:1, skipped:0}` then real send. Legitimate product path, not an SQL/cron shortcut.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 75.1 | PASS | Mailpit `Se4qdVQNpS4N7KXguVoLqd` → sipho.portal@example.com, subject **"Mathebula & Partners: Your weekly update"** (15:28:47Z) |
| 75.2 | PASS (copy-shape note per prior cycle) | Body: "Hi Sipho Dlamini, Here is your activity summary for the past 7 days." → **Recent fee notes: INV-0001 — PAID ZAR 1250.00**; **Trust account activity: 3 transaction(s) recorded**. Client-safe copy. The digest's designed content model is fee notes + trust + requests + deadlines; it does not render literal "SoA downloaded"/"matter closed" lines (same designed shape accepted in prior cycle — underlying events represented) |
| 75.3 | PASS (BLOCKER-severity isolation) | Full digest HTML scan: zero matches for moroka / est-2026 / peter / deceased / liquidation / 25 000 / 25,000 / 25000 |
| 75.4 | PASS | Digest CTA "Open portal →" → `http://localhost:3002/home` (+ unsubscribe → `/settings/notifications?unsubscribe=1`). `/home` renders Sipho-only: Pending 0, Deadlines 0, INV-0001 R 1 250,00, Last trust movement R 70 000,00 · 6 Jul 2026 |
| 75.5 | PASS | `/activity` "Your actions" timeline: 2× document downloads (Day 61 SoA + closure letter), fee-note payment (Day 30), info-request item submissions + uploads (Day 46 ×2, Day 4 ×3). All actor "You"; zero Moroka references |
| 75.6 | PASS (passive isolation, 61 days post-Moroka) | `/home` clean; `/trust` auto-forwards to Sipho's ledger — **balance R 0,00** (PAYMENT R 70 000 → bal 0; deposits 20k/50k below), NOT the R 25 000 Moroka figure; `/projects`: **Past tab = RAF-2026-001 only (CLOSED)**, **Active tab = Engagement Letter project only** — filter verified with real-coordinate clicks (OBS-6002 precedent), closed matter renders cleanly in Past, no error/grey-out |
| 75.7 | PASS | 📸 `day-75-portal-digest-plus-activity.png` |

## Day 75 day-level checkpoints

- Digest contents match activity trail (no missing-event discrepancy per digest content model): **PASS**
- Closed matter correctly rendered as closed (Past tab): **PASS**
- Isolation holds at Day 75: **PASS**

## Console

0 portal JS errors across home/activity/trust/projects.

## Gaps

None new.
