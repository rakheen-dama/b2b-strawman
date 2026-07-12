# Day 8 — Sipho reviews + accepts proposal `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini on :3002. Context: fresh portal tab; magic link re-requested at `/login` (Day-4 session not carried into this browser session), exchange → authenticated. No Keycloak form at any point.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 8.1 | PASS | Proposal-email link `http://localhost:3002/proposals/a7e87eac-…` lands on portal engagement-letter detail without re-authentication (magic-link session valid) |
| 8.2 | PASS (OBS-701 cascade) | Detail renders: title + SENT badge, PROP-0001, Sent 6 Jul 2026, Expires 17 Jul 2026, **Fee Details** (Fee Model: Hourly Rate), **Engagement Letter Details** (seeded letter body: "Dear Sipho Dlamini", Fee Arrangement, full LSSA rate note, expiry, T&C line), **Accept Engagement Letter** / **Decline** buttons. No tariff line-item fee-estimate table — does not exist in product (OBS-701 WONT_FIX; hourly model has no computed line items) |
| 8.3 | PARTIAL (OBS-701 cascade, no new gap) | ZAR currency present ("R 2,500/hr", "R 87,500.00" in rate note). No VAT 15% line — hourly fee model carries no computed totals, per OBS-701 product shape |
| 8.4 | PASS | 📸 `day-08-proposal-review.png` (full page) |
| 8.5 | PASS | Click **Accept Engagement Letter** → accepted immediately with inline confirmation (product uses inline confirm, no dialog — scenario allows "or inline confirm") |
| 8.6 | N/A | Tenant does not route acceptance through `/accept/[token]` — acceptance is in-session on `/proposals/{id}` |
| 8.7 | PASS | Status badge → **ACCEPTED**; banner "Thank you for accepting this engagement letter. Your matter has been set up." (Firm-side actor/timestamp verification is Day 10.1) |
| 8.8 | PASS | 📸 `day-08-proposal-accepted.png` |
| 8.9 | PASS | `/home`: no pending-proposal surface shows the proposal (cards: Pending info requests 0, Upcoming deadlines 0, Recent fee notes none, Last trust movement none) |
| 8.10 | PASS | `/proposals`: row shows **ACCEPTED** badge; "Awaiting Your Response" grouping gone |

## Day 8 day-level checkpoints

- Accessible via email link without re-auth: **PASS**
- Acceptance recorded: **PASS** (portal-side; firm-side confirmation deferred to Day 10.1 per script)
- No double-accept: **PASS** — after accept (and on reload) the "Your Response" section with Accept/Decline is gone; banner reads "This engagement letter has been accepted."
- Terminology consistent: **PARTIAL — gap LZKC-004 (Low).** Portal chrome consistently uses legal-za "Engagement Letter(s)" (nav, headings, buttons, table) — but the seeded letter body says "This **proposal** expires on 2026-07-17" / "This **proposal** is subject to…" and the email subject is "New **proposal** PROP-0001 for your review". Mixed vocabulary for the same object on the client surface.

## Gaps

- **LZKC-004** (Low): mixed "proposal"/"engagement letter" terminology on portal-facing copy (email subject + ProposalContentSeeder default body use "proposal"; portal chrome uses "Engagement Letter").
