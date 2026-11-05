# 636B.1 candidate findings (running list — NOT the final `gaps.md` register)

> Superseded by `gaps.md` (authoritative IDs and severities after rulings 0.R2/0.R3/0.R5); retained as the walker's candidate list.

Walker 636B.1. Severities are **proposed** (rating precedent Phase 88 O-4: new product failure → MEDIUM; trust money path / client-data confidentiality → HIGH; copy, layout, scenario-wording drift → LOW). The orchestrator confirms every HIGH/MEDIUM.

Numbering note: ids run from G89-F1 as the 636B.1 brief instructs. The brief §5.3 also seeds the final register with the three 636A tooling LOWs (636A-c1/c2/c3); if those take G89-F1…F3 in `gaps.md`, renumber these candidates by +3 when the register is assembled.

| ID | Proposed sev | Step(s) | Class | Observed | Expected | file:line | Evidence |
|---|---|---|---|---|---|---|---|
| G89-F1 | LOW | S1-29 (L 5.3) | Scenario mismatch needing authorisation — design proven | Matter Client group menu reads `Clients, Information requests, Comments, Adverse parties`; the sub-tab is **"Comments"** (`tab-item-comments`, `?tab=comments`), which loads cleanly | 5.3 says Client › **"Client comments"** sub-tab | `frontend/lib/constants/matter-tab-groups.ts:70` (`{ id: "comments", label: "Comments" }`); PR #1738 out-of-scope item 1 (pre-known §8); scenario `qa/testplan/demos/legal-za-90day-keycloak.md:273` | `steps/days-01-07.md` S1-29, `screenshots/5.3-1440-client-comments.png` |
| G89-F2 | LOW | S1-30 side observation (L 4.4 → 5.4) | Product bug (staleness) | After Bob logged 1 h 30 m from Work › Tasks, switching to the Overview tab client-side showed **"0.0h HOURS LOGGED"**; a full reload showed "1.5h". Same family as G88-M9 (panels stale until reload) | Overview KPI reflects the logged time without a reload | not traced (matter Overview KPI data fetched at page load; tab switch does not refetch) — hypothesis, cited only | `steps/days-01-07.md` S1-30 evidence; first-pass frame was overwritten by the post-reload `wow-4-matter.png` (the 0.0h state is recorded in the batch output quoted in the step file) |
| G89-F3 | LOW | S1-67 side observation (F16-c/d) | Product copy (regulatory wording) | Client Trust Statement and Trust Receipt previews print "Trust Account: Mathebula & Partners — **Section 78(1) Trust Account**" — a hard-coded suffix citing the repealed Attorneys Act 53 of 1979 s78(1), not the firm's configured account name "Mathebula & Partners Trust Account" (the LPA 28 of 2014 trust account is s86) | Trust documents name the firm's trust account (or cite LPA s86) | `backend/src/main/resources/template-packs/legal-za/trust-receipt.json:122`, `backend/src/main/resources/template-packs/legal-za/client-trust-statement.json:122` (`" — Section 78(1) Trust Account"`) | `steps/days-22-45.md` S1-67; `screenshots/F16-c-1440-client-trust-statement.png`, `screenshots/F16-d-1440-trust-receipt.png` |

## Pre-known candidates (§8) seen on the walked path — cited, not re-filed
- 632A (a): matter Generate Document menu lists "Standard Engagement Letter" / "Matter Summary Report" (S1-26).
- 632A (b): OTP warnings show raw paths `project.customFields.seller_name` / `…purchaser_name` (S1-50).
- 632A (c): Trust Receipt "[Amount to be inserted]", "[Reference to be inserted]", "[Purpose to be inserted]"; Client Trust Statement "[Transaction details to be inserted]" (S1-67).
- 634A: 212 px group cards at 1024 (S1-13) — no overflow observed.
- 634C: no `@` autocomplete when typing a mention (S1-35).
- G88-M9 (open LOW): fee-note Audit trail panel showed only "Fee note created" after Approve + Send until reload (S1-73).

## Not reproduced / not on the walked path (session 1)
- 634B "4th KPI tile wraps alone" — Overview tiles at 1024 rendered 4 tiles, no overflow (S1-30); wrap not judged from the frame.
- 634E "first 50 comments" / "portal-authored comments show Unknown" — not on the walked path.
