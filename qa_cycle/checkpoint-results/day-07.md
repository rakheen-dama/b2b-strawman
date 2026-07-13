# Day 7 — Firm drafts + sends proposal (engagement letter) `[FIRM]` — Cycle 2026-07-12

**Actor**: Thandi Mathebula (Owner) — fresh Keycloak login on :3000 (cookies cleared; Sipho's :3002 localStorage session preserved). Clean tenant this cycle — no prior-cycle proposal residue; numbering matches script exactly.

| # | Result | Evidence |
|---|--------|----------|
| 7.1 | PASS | "New Engagement Letter" in matter overflow menu (`overflow-actions-trigger` → menu: New Engagement Letter / Save as Template / Edit / Archive / Delete Matter) on `/projects/66451e87…` |
| 7.2 | PASS | Dialog "New Engagement Letter" — description reads "Create **an** engagement letter for a client engagement." (**LZKC-003 grammar fix holds**); Client combobox pre-filled **Sipho Dlamini**, disabled; Fee Model options Fixed Fee/Hourly/Retainer/Contingency, default **Hourly** |
| 7.3 | PASS | Title "Engagement Letter — Litigation (Dlamini v RAF)" |
| 7.4 | PASS | Fee Model Hourly + full LSSA rate note; renders verbatim in Proposal Details after create |
| 7.5 | PASS | Entered `2026-07-22` → detail renders "Expires 22 Jul 2026" — no +1-day tz drift (**OBS-702 holds**) |
| 7.6 | PASS | Create → redirect `/org/mathebula-partners/proposals/ad3a65ba-e2ed-4e14-ab41-98e2b0f0cb57`, badge **Draft**, **PROP-0001** assigned |
| 7.7 | PASS | Send Proposal dialog → recipient combobox lists exactly "Sipho Dlamini (sipho.portal@example.com)" → selected → Send |
| 7.8 | PASS | Badge **Sent**, "Sent: 12 Jul 2026" in Proposal Details, action button = **Withdraw** |
| 7.9 | PASS | backend.log 21:08:03Z: `Sent proposal ad3a65ba-… to contact fcb3147e-0ee8-4d3a-983b-ec36407ec446` (tenant_5039f2d497cf) + `Portal sync completed for proposal PROP-0001 after commit` |
| 7.10 | PASS | Mailpit `FzProtPkqDSexf4bMTA3vL` "Mathebula & Partners: New engagement letter PROP-0001 for your review" → sipho.portal@example.com; body link `http://localhost:3002/proposals/ad3a65ba-…` (**OBS-703 holds**) |
| 7.11 | PASS | Portal `/proposals` (Sipho, preserved session): "Awaiting Your Response" table shows exactly PROP-0001, status **SENT**, Sent 12 Jul 2026 |
| 7.12 | PASS | Pipeline board: DEAL-0001 dragged Conflict check → **Engagement** (page.mouse drag, `.ring-2` isOver oracle on Engagement column before release; dnd-kit announcement "Draggable item 3aad1c89-… was dropped over droppable area bde102a5-…"). Card: **60% · R 52 500,00**, value R 87 500,00; board "Open weighted value R 52 500,00" |
| 7.13 | PASS | Deal detail `/pipeline/3aad1c89…` → Proposals tab (`deal-proposals-panel`) → "New Proposal" dialog ("Draft a proposal attached to this deal"): Title "Fee Agreement — Dlamini v RAF", fee model native select → **Hourly** (Amount (ZAR) field hidden for hourly) → Create |
| 7.14 | PASS | Deal proposals table: **PROP-0002 / Draft** (`deal-proposal-status-badge`), number links `/org/mathebula-partners/proposals/10117d54-0b50-49b9-a2d1-4d49b689442f`. PROP-0002 **absent** from Sipho's portal `/proposals` (Drafts don't sync) |

## Day 7 day-level checkpoints

- Proposal lifecycle Draft → Sent end-to-end: PASS
- Portal email dispatched (OBS-703), subject + body link verified: PASS
- Portal projection shows PROP-0001: PASS
- Frontend console clean on firm `/proposals` index — 0 errors on fresh load (**LZKC-002 hydration fix holds / OBS-704**): PASS
- Expiry renders consistently with date input (OBS-702): PASS
- Deal advanced to Engagement + deal-linked Draft PROP-0002 not on portal (Phase 80): PASS

## Gaps

- None new. Prior-cycle fixes LZKC-002 (proposals-index hydration) and LZKC-003 ("a engagement" grammar) verified holding.

## IDs for later days

- PROP-0001 (engagement letter, Sent): `ad3a65ba-e2ed-4e14-ab41-98e2b0f0cb57`
- PROP-0002 (deal-linked, Draft): `10117d54-0b50-49b9-a2d1-4d49b689442f`
- Portal contact (Sipho): `fcb3147e-0ee8-4d3a-983b-ec36407ec446`
- Deal DEAL-0001 detail route: `/pipeline/3aad1c89-b8b8-4d27-a94d-687be0682180` (now Engagement 60%)
