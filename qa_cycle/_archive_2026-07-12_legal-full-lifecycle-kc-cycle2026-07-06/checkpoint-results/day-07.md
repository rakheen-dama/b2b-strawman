# Day 7 — Firm drafts + sends proposal (engagement letter) `[FIRM]` — 2026-07-06

**Actor**: Thandi Mathebula (Owner) via Keycloak login on :3000.

## IMPORTANT — prior partial execution discovered

On arrival at Day 7, the tenant already contained 3 proposals created earlier today (2026-07-05T23:46Z):
- **PROP-0001** "Engagement Letter — Litigation (Dlamini v RAF)" — **Sent** (email in Mailpit 23:46:47, backend log send + portal-sync entries present)
- **PROP-0002** same title — stray Draft (duplicate)
- **PROP-0003** "Fee Agreement — Dlamini v RAF" — Draft (matches checkpoint 7.13's deal-linked proposal)

Evidence (Mailpit timestamps 23:46–23:47, exact script strings, backend log) shows a **prior QA session executed Day 7 through ~7.13 and died before writing results/status** (status.md still said "Next: Day 7"). No day-07.md existed; branch log confirms last commit was Day 5.

**Handling**: re-executed 7.1–7.6 live (which created a 4th duplicate, PROP-0004, before the pre-existing data was discovered), then verified the remaining checkpoints against the observed state of PROP-0001/PROP-0003. **Draft proposals have no Delete action in the UI** (detail page offers only "Send Proposal"; list rows have no actions), so PROP-0002 and PROP-0004 remain as documented residue. They are Draft-only, never sync to the portal, and were confirmed absent from the portal index. Reference numbering therefore deviates from script: engagement letter = PROP-0001 (script: PROP-0001 ✓), deal-linked proposal = **PROP-0003** (script expected PROP-0002).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 7.1 | PASS | "New Engagement Letter" lives in matter overflow menu (`overflow-actions-menu`) on `/org/mathebula-partners/projects/272be4f8-…`; clicked, dialog opened |
| 7.2 | PASS | Dialog "New Engagement Letter"; Client combobox pre-filled **Sipho Dlamini** and disabled; Fee Model defaults **Hourly**. NOTE: dialog description reads "Create **a engagement** letter" — grammar bug (LZKC-003) |
| 7.3 | PASS | Title set "Engagement Letter — Litigation (Dlamini v RAF)" (re-executed on PROP-0004; identical on PROP-0001) |
| 7.4 | PASS | Fee Model Hourly + full LSSA rate note saved; renders verbatim in Proposal Details on both PROP-0001 and PROP-0004 |
| 7.5 | PASS | Entered `2026-07-16` → detail renders "16 Jul 2026" — **no +1-day tz drift (OBS-702 verified)**. PROP-0001 shows Expires 17 Jul 2026 consistently on firm detail, portal detail, and email ("expire on 17 July 2026") |
| 7.6 | PASS (numbering deviation) | Create → redirect to `/org/mathebula-partners/proposals/{id}`, status badge **Draft**. Canonical engagement letter is PROP-0001 (prior partial run); re-execution minted PROP-0004 |
| 7.7 | PASS (outcome-verified) | Send was performed by the prior partial run — recipient combobox interaction not re-observed this session. Outcome observed: backend log `Sent proposal a7e87eac-… to contact 0e1fbd3d-…` (tenant_5039f2d497cf) + email to sipho.portal@example.com |
| 7.8 | PASS | PROP-0001 detail: badge **Sent**, "Sent: 6 Jul 2026" in Proposal Details, action button = **Withdraw** |
| 7.9 | PASS | `.svc/logs/backend.log` 23:46:47Z: `Sent proposal a7e87eac-f5b2-455f-8663-95f15e5da6e5 to contact 0e1fbd3d-0eaa-4070-a236-c3b489c27ce0` + `Portal sync completed for proposal PROP-0001 after commit` |
| 7.10 | PASS | Mailpit: subject "Mathebula & Partners: New proposal PROP-0001 for your review" to sipho.portal@example.com; body link `http://localhost:3002/proposals/a7e87eac-…` (OBS-703 holding) |
| 7.11 | PASS | Portal `/proposals` (as Sipho, fresh magic-link): "Awaiting Your Response" table shows exactly PROP-0001, status **SENT**, Sent 6 Jul 2026 |
| 7.12 | PASS | `/pipeline` board: DEAL-0001 card in **Engagement** column, **60% · R 52 500,00** weighted, value R 87 500,00 (advance performed by prior run; state observed) |
| 7.13 | PASS (outcome-verified) | Deal-linked proposal "Fee Agreement — Dlamini v RAF" exists as **PROP-0003** Draft (dialog interaction performed by prior run; numbering deviates from script's PROP-0002 due to stray duplicate) |
| 7.14 | PASS | Deal detail `/pipeline/64c2e57c-…` → Proposals tab: 1 row, PROP-0003, **Draft** badge, number links to `/org/mathebula-partners/proposals/c6f77a1d-…`. PROP-0003 (and all drafts) absent from Sipho's portal `/proposals` index |

## Day 7 day-level checkpoints

- Proposal lifecycle Draft → Sent end-to-end: **PASS**
- Portal email dispatched (OBS-703): **PASS**
- Portal projection shows PROP-0001: **PASS**
- Frontend console clean on `/proposals` index (OBS-704): **FAIL — gap LZKC-002.** Reproducible React hydration error on firm `/org/mathebula-partners/proposals`: `Hydration failed…` in `CreateProposalDialog > DialogTrigger` (`aria-controls="radix-_R_4clritrqiqbn5rknelb_"` mismatch server vs client). Confirmed on two independent fresh loads. Cosmetic (tree regenerated client-side), page functional.
- Expiry renders consistently (OBS-702): **PASS**
- Deal advanced + deal-linked Draft proposal not on portal: **PASS**

## Gaps

- **LZKC-002** (Medium): hydration mismatch on firm `/proposals` index — regression-class of OBS-704 fix-verification checkpoint. Evidence: `.playwright-mcp/console-2026-07-06T08-58-30-443Z.log`.
- **LZKC-003** (Low): engagement-letter dialog description reads "Create a engagement letter…" — article not adjusted by legal-za terminology substitution.
- Observation (no gap): Draft proposals cannot be deleted via UI — leaves PROP-0002/PROP-0004 residue on this tenant. Firm-side only; drafts never sync to portal.
