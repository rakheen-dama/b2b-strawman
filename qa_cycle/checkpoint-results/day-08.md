# Day 8 — Sipho reviews + accepts proposal `[PORTAL]` — Cycle 2026-07-12

**Actor**: Sipho Dlamini on :3002 — Day-4 magic-link session still valid (localStorage JWT survived the firm-side days). No Keycloak form at any point.

| # | Result | Evidence |
|---|--------|----------|
| 8.1 | PASS | Proposal-email link `http://localhost:3002/proposals/ad3a65ba-…` (Mailpit `FzProtPkqDSexf4bMTA3vL`) lands on portal engagement-letter detail without re-authentication |
| 8.2 | PASS (OBS-701 cascade) | Detail renders: title + SENT badge, PROP-0001, Sent 12 Jul 2026, Expires 22 Jul 2026, Fee Details (Fee Model: Hourly Rate), Engagement Letter Details (seeded body: "Dear Sipho Dlamini", Fee Arrangement, full LSSA rate note, expiry, T&C line), **Accept Engagement Letter** / **Decline**. No tariff line-item fee-estimate table — OBS-701 product shape (WONT_FIX) |
| 8.3 | PARTIAL (OBS-701 cascade, no new gap) | ZAR present ("R 2,500/hr", "R 87,500.00" in rate note). No VAT 15% line — hourly fee model carries no computed totals (same disposition as prior cycle) |
| 8.4 | PASS | `day-08-proposal-review.png` (full page) |
| 8.5 | PASS | Click **Accept Engagement Letter** → accepted immediately, inline confirmation (scenario allows inline confirm) |
| 8.6 | N/A | Acceptance is in-session on `/proposals/{id}` — no `/accept/[token]` routing on this tenant |
| 8.7 | PASS | Badge → **ACCEPTED**; banner "Thank you for accepting this engagement letter. Your matter has been set up." (firm-side actor/timestamp check is Day 10.1) |
| 8.8 | PASS | `day-08-proposal-accepted.png` |
| 8.9 | PASS | `/home`: no pending-proposal surface (Pending info requests 0, Upcoming deadlines 0, no fee notes, no trust movement) |
| 8.10 | PASS | `/proposals`: PROP-0001 row **ACCEPTED**; "Awaiting Your Response" grouping gone |

## Day 8 day-level checkpoints

- Accessible via email link without re-auth: PASS
- Acceptance recorded (portal-side; firm confirms Day 10.1): PASS
- No double-accept: PASS — on reload, Accept/Decline gone, banner "This engagement letter has been accepted."
- Terminology consistent: **PASS — LZKC-004 fix holds.** Seeded body now reads "This **engagement letter** expires on 2026-07-22" / "This **engagement letter** is subject to…", and the email subject is "New **engagement letter** PROP-0001 for your review" — no "proposal" leakage on client-facing copy this cycle

Console: 0 errors across Day 8 navigations.

## Gaps

- None new. Prior-cycle LZKC-004 (mixed proposal/engagement-letter vocabulary) verified fixed.
