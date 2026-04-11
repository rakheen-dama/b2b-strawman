# Fix Spec: GAP-TERMINOLOGY-ROLLUP — Legal terminology leaks on secondary UI

## Priority
LOW — covers GAP-S2-02 (partial), GAP-S2-04, GAP-S3-02, GAP-S3-06, GAP-S6-01, GAP-S6-04. Single
sweep-style fix. Do LAST after all HIGH/MEDIUM blockers.

## Problem
Multiple hardcoded legacy-domain strings leak into the UI despite the legal-za terminology being
otherwise applied on primary pages:
- "New Proposal" button + dialog title (should be "New Engagement Letter")
- "Total Proposals" KPI (should be "Total Engagement Letters")
- "New Invoice" button + "No invoices yet" empty state (should be "New Fee Note" / "No fee notes
  yet")
- "Back to Customers" back-link on customer detail page (should be "Back to Clients")
- Matter detail "Customers" tab (should be "Clients")
- Sidebar logo text "DocTeams" (should be "Kazi")
- Dashboard helper card "Getting started with DocTeams" (should be "Getting started with Kazi")
- Settings sidebar legacy labels ("Project Templates", "Project Naming", Custom Fields subtitle
  mentions "projects, tasks, customers, invoices")
- "Getting started with Kazi" in messages JSON

## Root Cause (confirmed via grep)
Hardcoded strings in component files:
- `frontend/app/(app)/org/[slug]/proposals/page.tsx:90` — `New Proposal` button label
- `frontend/components/proposals/create-proposal-dialog.tsx:151` — `<DialogTitle>New Proposal</DialogTitle>`
- `frontend/components/proposals/proposal-summary-cards.tsx:23` — "Total Proposals" KPI
- `frontend/components/invoices/invoice-generation-dialog.tsx:58` — "New Invoice"
- `frontend/components/invoices/create-invoice-button.tsx:50` — "New Invoice"
- `frontend/components/customers/customer-invoices-tab.tsx:62` — "No invoices yet"
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx:433` — "Back to Customers"
- `frontend/components/desktop-sidebar.tsx:29` — hardcoded "DocTeams" logo text
- `frontend/lib/messages/en/getting-started.json` — "Getting started with DocTeams"
- `frontend/app/layout.tsx` — global metadata title `Kazi — Practice management, built for Africa`
  never updates per-page (this is the browser tab title issue from GAP-S6-01 — secondary, keep
  as-is for now)

## Fix Steps
These are pure find-and-replace edits. Group them by concern:

### A. DocTeams → Kazi brand fixes
1. `frontend/components/desktop-sidebar.tsx:29` — replace the hardcoded "DocTeams" span with
   `Kazi`. Also audit `mobile-sidebar.tsx` for the same string.
2. `frontend/lib/messages/en/getting-started.json` — replace "Getting started with DocTeams"
   with "Getting started with Kazi". Also sweep any remaining "DocTeams" in
   `frontend/lib/messages/` tree.
3. Grep the rest of `frontend/components/` for `DocTeams` — replace with `Kazi` everywhere in
   non-test files. (Tests and e2e files can stay — they're not user-facing.)

### B. Proposal → Engagement Letter (legal-za only)
The right fix is to use the existing terminology helper (vertical profile-driven). Grep for
`terminology` or `verticalTerminology` under `frontend/lib/`:
- If a helper exists, call `terminology.proposalNew` / `terminology.proposalListHeading` etc.
- If no helper exists: hardcode "Engagement Letter" where the legal-za vertical is the only
  tenant (acceptable for now — the codebase is single-vertical in practice, and the non-legal
  vertical is out of QA scope).

Edits:
1. `frontend/app/(app)/org/[slug]/proposals/page.tsx:90` — button label
2. `frontend/components/proposals/create-proposal-dialog.tsx:151` — dialog title + subtitle
3. `frontend/components/proposals/proposal-summary-cards.tsx:23` — "Total Engagement Letters"
4. Grep `proposals` directory for any remaining "Proposal" / "proposal" strings in visible UI.

### C. Invoice → Fee Note
Same approach as B. Edits:
1. `frontend/components/invoices/invoice-generation-dialog.tsx:58` — "New Fee Note"
2. `frontend/components/invoices/create-invoice-button.tsx:50` — "New Fee Note"
3. `frontend/components/customers/customer-invoices-tab.tsx:62` — "No fee notes yet"
4. Sweep the invoices empty-state body copy on `invoices/page.tsx` — "Generate fee notes from
   tracked time...".

### D. Customer → Client (legal-za)
1. `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx:433` — "Back to Clients"
2. `frontend/components/customers/` — grep for hardcoded "Customer Readiness", "customers
   represent the organisations", "Customer Comments" → legal-za replacement. Note: the
   "Customer Comments" tab on the matter page refers to portal-visible customer comments, so
   it's intentional — leave it but the label should be "Client Comments" for consistency.
3. Matter detail tabs — the "Customers" tab should read "Clients" (find in
   `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` or the tab component).

### E. Settings page terminology (GAP-S2-02)
1. Settings sidebar items: "Project Templates" → "Matter Templates", "Project Naming" → "Matter
   Naming" for the legal-za profile.
2. Custom Fields subtitle: "projects, tasks, customers, invoices" → "matters, tasks, clients,
   fee notes".

### F. Test updates
Run `pnpm test` after the sweep — several test files will fail because they assert on the old
strings:
- `frontend/__tests__/proposals-dashboard.test.tsx:130` — `getByText("Total Proposals")`
- `frontend/__tests__/components/invoices/*.test.tsx` — multiple `getByText("New Invoice")`
- `frontend/__tests__/components/customers/customer-invoices-tab.test.tsx` — `getByText("No
  invoices yet")`

Update these test assertions to match the new copy.

## Scope
- Frontend only
- Many small files touched; single sweep
- Migration needed: no

## Verification
Grep after the sweep:
```
rg -n "New Proposal|Total Proposals|New Invoice|No invoices yet|Back to Customers|DocTeams" frontend/ --glob '!*test*' --glob '!*e2e*'
```
Expected: 0 hits in production source.

Browser smoke check:
1. Proposals list → button reads "New Engagement Letter"
2. Proposals KPI → "Total Engagement Letters"
3. Invoices list → button reads "New Fee Note"; empty state "No fee notes yet"
4. Customer detail → back link reads "Back to Clients"
5. Sidebar logo reads "Kazi"
6. Dashboard helper card reads "Getting started with Kazi"

## Estimated Effort
M (~1.5 hr — many files, test updates, but mechanical)

## Notes
- Browser tab title (GAP-S6-01) requires adding per-page `export const metadata` in each
  page.tsx or using `generateMetadata`. Defer unless in scope.
- "Customer Readiness" widget rename is a small component change; include in D.
- Do NOT attempt to build a full terminology helper in this cycle — too large for < 2 hr budget.
