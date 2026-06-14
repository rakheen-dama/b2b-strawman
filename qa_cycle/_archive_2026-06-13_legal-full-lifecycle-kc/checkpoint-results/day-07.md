# Day 7 — Firm drafts + sends proposal (engagement letter) — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Thandi Mathebula (Owner — signs proposals). Context swap from Day 5 Bob session: signed Bob out, fresh Keycloak login on :3000 (`thandi@mathebula-test.local` / `SecureP@ss1`, realm `docteams`), landed on dashboard as "Thandi Mathebula". Portal projection check (7.11) reused Sipho's live portal session on :3002 (magic-link cookie still valid, no re-auth).
**Driver**: QA agent via Playwright MCP — browser UI only; Mailpit API used only to read the proposal notification email; backend log read for 7.9 sync confirmation.
**Pre-checks**: svc.sh status — backend (PID 45933→46138 after Day-5 retest restart) / gateway / frontend / portal all RUNNING+HEALTHY.
**Result**: **11/11 checkpoints PASS + 5/5 summary checkpoints PASS. Zero new gaps.**

## Created Day 7
- Proposal / Engagement Letter **PROP-0001** — ID `6a1b35fc-b342-4101-abd7-f2ab8ffad26e`
  (`/org/mathebula-partners/proposals/6a1b35fc-b342-4101-abd7-f2ab8ffad26e`)
- Title: "Engagement Letter — Litigation (Dlamini v RAF)"; Fee Model Hourly; Hourly Rate Note = LSSA-tariff estimate text; Created 13 Jun 2026; Expires 20 Jun 2026; Sent 13 Jun 2026
- Sent to portal contact **Sipho Dlamini** (sipho.portal@example.com), portal contact ID `793df2fa-6350-46af-b0c0-8b3ac0d7d855`
- Proposal email in Mailpit: `nso7TsKUKSxPWXvKkDhXwR` — subject "Mathebula & Partners: New proposal PROP-0001 for your review"; portal link `http://localhost:3002/proposals/6a1b35fc-b342-4101-abd7-f2ab8ffad26e`

## Checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 7.1 | Matter RAF-2026-001 → **+ New Engagement Letter** (legal-za term for "+ New Proposal") | PASS | Navigated to `/org/mathebula-partners/projects/08ad56c4-…`. "New Engagement Letter" lives under the header **More actions** overflow menu (alongside Save as Template / Edit / Archive / Delete). Clicking it opens the engagement-letter dialog. |
| 7.2 | Dialog opens with Client pre-filled = Sipho Dlamini (disabled, from matter context) | PASS | Dialog "New Engagement Letter" / subtitle "Create a engagement letter for a client engagement". Client combobox shows "Sipho Dlamini" with `disabled` attribute (DOM-verified `disabled:true`, not editable). |
| 7.3 | Title = "Engagement Letter — Litigation (Dlamini v RAF)" | PASS | Title textbox filled (DOM value verified). |
| 7.4 | Fee Model = Hourly (default); Hourly Rate Note set | PASS | Fee Model select pre-selected `HOURLY` (legal-za default, DOM `value=HOURLY`). Hourly Rate Note filled with "R 2,500/hr (LSSA tariff High Court Party-and-Party 2024/2025) — 30h Bob Ndlovu (attorney) + 5h Thandi Mathebula (senior partner) ≈ R 87,500.00 estimate." |
| 7.5 | Expiry Date set (7-day acceptance window) | PASS | Expiry Date input is `type="date"`; filled `2026-06-20` (DOM value verified; sensible future date for the acceptance window). |
| 7.6 | Create → redirect to `/proposals/{id}`; status = Draft, PROP-0001 assigned | PASS | Clicked "Create Engagement Letter" → redirected to `/org/mathebula-partners/proposals/6a1b35fc-b342-4101-abd7-f2ab8ffad26e`. Status badge **Draft**; reference **PROP-0001**; Details: Fee Model Hourly, Hourly Rate full text, Created 13 Jun 2026, Expires 20 Jun 2026. Breadcrumb "Engagement Letters" (legal terminology). Backend log: `Created proposal 6a1b35fc-… (PROP-0001) for customer 2211a80a-…`. Screenshot `day-07-proposal-draft.png`. |
| 7.7 | Send Proposal → recipient combobox lists portal contacts → select Sipho → Send | PASS | Clicked "Send Proposal". Dialog "Send Proposal" → Recipient combobox listbox showed single option "Sipho Dlamini (sipho.portal@example.com)". Selected it; Send button enabled (DOM `disabled:false`); clicked Send. |
| 7.8 | Status → Sent; "Sent: {date}" field appears; action button → Withdraw | PASS | Status badge Draft → **Sent**; new Details row **Sent: 13 Jun 2026**; action button changed "Send Proposal" → **Withdraw**. Screenshot `day-07-proposal-sent.png`. |
| 7.9 | Backend log: `Sent proposal {id} to contact {portalContactId}` + `Portal sync completed for proposal PROP-0001 after commit` | PASS | `.svc/logs/backend.log`: (1) `Sent proposal 6a1b35fc-… to contact 793df2fa-6350-46af-b0c0-8b3ac0d7d855`; (2) `Portal sync completed for proposal PROP-0001 after commit`; (3) `Portal notification sent template=portal-new-proposal contact=793df2fa-… to=sipho.portal@example.com`. All in tenant `tenant_5039f2d497cf`, user Thandi `3efe16db-…`. |
| 7.10 | Mailpit: proposal email to sipho.portal@example.com with portal click-through (OBS-703 fix) | PASS | Mailpit msg `nso7TsKUKSxPWXvKkDhXwR`, to sipho.portal@example.com. Subject "Mathebula & Partners: New proposal PROP-0001 for your review". Body: "New Proposal for Your Review", "Hi Sipho Dlamini", "Proposal PROP-0001", "Engagement Letter — Litigation (Dlamini v RAF)", **View Proposal** CTA → `http://localhost:3002/proposals/6a1b35fc-b342-4101-abd7-f2ab8ffad26e`. OBS-703 fix holds (portal click-through present). |
| 7.11 | Portal `/proposals` shows PROP-0001 in "Awaiting Your Response" with status SENT (firm→portal projection) | PASS | Portal :3002 `/proposals` (Sipho live session, sidebar "Engagement Letters"): section "Awaiting Your Response" → row PROP-0001 / "Engagement Letter — Litigation (Dlamini v RAF)" / **SENT** / 13 Jun 2026 / Fee **-** / View → `/proposals/6a1b35fc-…`. Firm→portal sync confirmed. Fee column "-" = OBS-701 carry-over (fee-estimate/VAT line absent on portal proposal — expected, not filed). Screenshot `day-07-portal-proposal-sent.png`. |

## Day 7 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Proposal lifecycle Draft → Sent succeeds end-to-end | PASS | More actions → New Engagement Letter → fill form → Create (Draft, PROP-0001) → Send to Sipho → Sent. All transitions clean, no errors. |
| Portal email dispatched (OBS-703) — subject + body + link verified | PASS | Mailpit `nso7TsKUKSxPWXvKkDhXwR`; subject + body + `http://localhost:3002/proposals/6a1b35fc-…` CTA all present. |
| Portal `/proposals` projection shows PROP-0001 (firm→portal sync) | PASS | Portal index shows PROP-0001 SENT under "Awaiting Your Response"; backend `Portal sync completed for proposal PROP-0001 after commit`. |
| Frontend console clean (no hydration mismatch on `/proposals` index — OBS-704) | PASS | Firm side: only the exempt OBS-201 `/api/assistant/invocations` 404 (×4, carried from matter-page context) + 1 Next.js dev advisory warning (`scroll-behavior: smooth`, cosmetic dev-only). Portal `/proposals`: **0 console errors, 0 warnings**. No hydration mismatch either side. |
| Expiry date renders consistently (no +1-day tz drift — OBS-702) | PASS | Input `2026-06-20` → firm detail "20 Jun 2026". No tz drift. |

## Console notes
- **Firm side**: 4× `/api/assistant/invocations?...PENDING_APPROVAL` 404 — exempt OBS-201 (AI assistant proxy unwired in KC mode), expected. 1 Next.js dev warning about `scroll-behavior: smooth` — cosmetic dev-only advisory, not a JS/hydration error.
- **Portal side**: clean — 0 errors, 0 warnings on `/proposals`.
- No real JavaScript/hydration/render errors anywhere during Day 7.

## Carry-over exemptions observed (not re-filed)
- **OBS-701**: portal proposal view shows no fee-estimate/VAT line — confirmed by Fee column "-" on portal `/proposals`. Expected, WONT_FIX carry-over.
- **OBS-201**: `/api/assistant/*` 404 in KC mode — present on firm matter page context. Exempt.

## Gaps filed
None. Day 7 passed cleanly with zero new gaps.
