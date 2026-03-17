# Post-Architect Verification Results

**Date:** 2026-03-17
**Tester:** QA Agent (Claude)
**Stack:** E2E mock-auth on localhost:3001, backend 8081, Mailpit 8026
**Scope:** PRs #728-#742 — all Phase 49 fixes after architect review

---

## Check 1: Custom field population (Day 0 prerequisite)

**PASS**

Set custom fields on Kgosi Construction (Pty) Ltd:
- `Company Registration Number` = `2019/123456/07`
- `VAT Number` = `4520012345`
- `SARS Tax Reference` = `9012345678`

Fields saved successfully (confirmed by page reload showing persisted values).

---

## Check 2: Template variable fidelity (PR #728 — variable key fix)

**PASS**

Generated "Engagement Letter -- Monthly Bookkeeping" for project Monthly Bookkeeping -- Kgosi.

- "Registration Number: 2019/123456/07" -- present and correct (not blank, not `________`)
- "Client: Kgosi Construction (Pty) Ltd" -- present and correct
- "Engagement Type: Monthly Bookkeeping" -- present and correct
- No `{{` mustache tokens visible anywhere in the rendered document

Evidence: `check2-3-4-engagement-letter-preview.png`

---

## Check 3: Clause rendering (PR #730 — slug-based fallback + PR #740 — O(1) slug index)

**PASS**

Clause selection dialog (Step 1 of 2):
- Required clauses (disabled checkboxes, locked): Limitation of Liability (Accounting), Termination (Accounting), Confidentiality (Accounting), Document Retention (Accounting)
- Optional clauses (toggleable checkboxes): Fee Escalation, Third-Party Reliance, Electronic Communication Consent
- All clauses render with category badges (Legal, Commercial, Compliance) and descriptions

Generated document body includes clause text for all selected clauses:
- Limitation of Liability clause: R1,000,000 cap, SAICA terms
- Termination clause: 30-day notice, handover provisions, SARS obligations
- Confidentiality clause: POPIA reference, SAICA/IRBA requirements
- Document Retention clause: 5-year retention, Tax Administration Act compliance
- Third-Party Reliance clause: limits third-party reliance
- Electronic Communication Consent clause: ECTA reference

**Deselect test:** Deselected "Fee Escalation" optional clause, regenerated. Fee Escalation clause was absent from the output. Confirmed selective clause rendering works.

Evidence: `check2-3-4-engagement-letter-preview.png`

---

## Check 4: Dropdown label resolution (PR #732 + PR #741 — cached field defs)

**PASS**

On the Monthly Bookkeeping -- Kgosi project, set `engagement_type` to "Monthly Bookkeeping" via the dropdown.

In the generated engagement letter:
- "Engagement Type: Monthly Bookkeeping" -- shows human-readable display label
- Does NOT show "MONTHLY_BOOKKEEPING" (raw enum value)

Evidence: `check2-3-4-engagement-letter-preview.png`

---

## Check 5: Table formatting (PR #733 + PR #742 — locale-aware formatter)

**PASS**

Generated "SA Tax Invoice" for Kgosi Construction INV-0002.

Line items table:
- Unit Price: `R5 500,00` -- R prefix, space as thousands separator, comma for decimals
- VAT: `R825,00` -- correct formatting
- Total: `R6 325,00` -- correct formatting

Summary amounts:
- Subtotal: `R5 500,00`
- VAT (15%): `R825,00`
- Total Due: `R6 325,00`

Dates:
- Issue Date: `17 March 2026` -- human-readable format, NOT raw ISO

Evidence: `check5-6-invoice-za-preview.png`

---

## Check 6: Invoice VAT number (PR #738 — resolved custom fields)

**PASS**

In the SA Tax Invoice output:
- "Client VAT Number: 4520012345" -- matches the custom field value we set in Check 1
- Customer name: "To: Kgosi Construction (Pty) Ltd" -- correct

Note: Org's own "VAT Registration No:" is blank (expected -- org VAT not configured in seed data).

Evidence: `check5-6-invoice-za-preview.png`

---

## Check 7: Portal acceptance page (PR #737 — new page)

**PARTIAL**

The portal acceptance page exists at `/accept/[token]` and renders correctly:
- With an invalid/unknown token: Shows "Link Not Valid" state with AlertCircle icon and helpful message
- Page does NOT crash or show a 500 error
- Code review confirms the page handles all states: loading, not_found, expired, revoked, ready (with PDF viewer + acceptance form), accepted (with confirmation)

**Cannot fully test end-to-end acceptance flow** because:
1. The "Save to Documents" action in the generation dialog did not persist the document to the Generated Docs tab (both project and customer tabs show "No documents generated yet")
2. No "Send for Acceptance" button was visible in the generation dialog (only "Back", "Download PDF", "Save to Documents")
3. No acceptance tokens exist in the seed data

The acceptance page component itself is well-implemented with proper state handling, PDF preview iframe, name input form, and brand color support. The backend API integration (`/api/portal/acceptance/{token}`) is wired correctly.

Evidence: `check7-portal-acceptance-page.png`

**Issues noted:**
- "Save to Documents" button click does not appear to persist the generated document (Generated Docs tab remains empty on both project and customer pages)
- No "Send for Acceptance" action exposed in the current generation UI flow

---

## Check 8: Mailpit email delivery (PR #734 — SMTP auth fix)

**PASS**

Mailpit at localhost:8026 shows 2 emails delivered:

1. **Subject:** "Information request REQ-0001 from E2E Test Organization"
   - From: noreply@docteams.app
   - To: thabo@kgosiconstruction.co.za
   - Size: 5.2 kB

2. **Subject:** "Information request REQ-0002 from E2E Test Organization"
   - From: noreply@docteams.app
   - To: thabo@kgosiconstruction.co.za
   - Size: 5.2 kB

Both emails from the lifecycle seed's information request sends. SMTP delivery is working correctly.

Evidence: `check8-mailpit-emails.png`

---

## Check 9: Project-customer link (PR #731 + PR #736 — create + update)

**PASS**

Navigated to BEE Certificate Review -- Vukani project:
- Project header shows: "Customer: Vukani Tech Solutions (Pty) Ltd" with working link
- Generated "Engagement Letter -- Advisory" from this project

In the generated advisory letter:
- "Dear Vukani Tech Solutions (Pty) Ltd" -- correct customer name (NOT blank)
- Clauses reference "Vukani Tech Solutions (Pty) Ltd" throughout
- Project name "BEE Certificate Review -- Vukani" referenced in scope section
- No Kgosi data appears in the Vukani document

Evidence: `check9-vukani-advisory-letter.png`

---

## Check 10: Cross-customer isolation

**PASS**

Verified by comparing generated documents across two different customers:

1. **Kgosi Construction** engagement letter: References Kgosi data only (registration number 2019/123456/07, Kgosi Construction (Pty) Ltd throughout)
2. **Vukani Tech Solutions** advisory letter: References Vukani data only (Vukani Tech Solutions (Pty) Ltd throughout, BEE Certificate Review project)

No cross-contamination observed. Each customer's generated document contains only that customer's data.

Additionally, the SA Tax Invoice for Kgosi (INV-0002) shows only Kgosi's VAT number (4520012345), not any other customer's data.

---

## Summary

| Check | Description | Result |
|-------|-------------|--------|
| 1 | Custom field population | PASS |
| 2 | Template variable fidelity (PR #728) | PASS |
| 3 | Clause rendering (PR #730, #740) | PASS |
| 4 | Dropdown label resolution (PR #732, #741) | PASS |
| 5 | Table formatting (PR #733, #742) | PASS |
| 6 | Invoice VAT number (PR #738) | PASS |
| 7 | Portal acceptance page (PR #737) | PARTIAL |
| 8 | Mailpit email delivery (PR #734) | PASS |
| 9 | Project-customer link (PR #731, #736) | PASS |
| 10 | Cross-customer isolation | PASS |

**Overall: 9 PASS, 1 PARTIAL (Check 7)**

Check 7 is PARTIAL because while the portal acceptance page renders correctly and handles all states properly, the "Save to Documents" action did not persist documents, and no "Send for Acceptance" button is exposed in the generation UI. The acceptance page itself at `/accept/[token]` is functional and properly handles invalid tokens.

### New Issues Found

1. **Save to Documents not persisting:** Clicking "Save to Documents" in the generation dialog does not appear to save the document. Both the project's and customer's "Generated Docs" tabs show "No documents generated yet" after saving. This may be a backend issue or a silent API error.

2. **No Send for Acceptance action in UI:** The generation dialog shows "Back", "Download PDF", and "Save to Documents" but no "Send for Acceptance" option. This may be intentional (acceptance flow triggered elsewhere) or a missing UI integration.
