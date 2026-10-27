# Slice 625D — AFTER (observed on PR head 93c075ef1)

Stack: e2e mock-auth. `e2e-frontend` rebuilt from the PR worktree (container created 2026-10-27 16:44:23 SAST, healthy;
rebuild log EXIT=0). Backend :8081 unchanged since 625C. Tenant `tenant_7d218705360b`. Observer logged in as Alice (owner)
and only navigated / used filters / tabs. No DB writes, no record edits, no restarts. Screenshots taken by Playwright MCP
into `b2b-strawman/.playwright-mcp/625D-after/` and copied here. Substitutions per ruling R-13: Thandi → Alice Owner;
client tab → Acme Corp `b6dc030f-…` (no e2e client has nine FICA completions).

New code is live: Actor / Entity type are Selects and Event type is a combobox (pre-fix: three free-text inputs).

| # | Check | Verdict | Evidence |
|---|---|---|---|
| 1 | Facet-fed filters | **PASS** | A02, A03, A04, A05 |
| 2 | Entity column labels | **PASS** | A01, A06 |
| 3 | Entity ID Enter commits once | **PASS** | A07 |
| 4 | `?actorId=Alice%20Owner` renders | **PASS** | A08 |
| 5 | Client Activity tab = /activity feed | **PASS** | A12, A13, `api-clients-activity-acme.json` |
| 6 | Overflow 1280 / 1440 | **PASS** | A09, A10, A11 |
| 7 | Console errors | PASS (only pre-existing ones) | see §7 |

## 1. Firm › Audit trail filters (G-D6, E.9)
- **Actor** = Select "All actors" with groups **Members** (Alice Owner 116, Bob Admin 21, Carol Member 6) and **Portal contacts**
  (Portal Contact 4). No "System" option (the facet only lists non-null actor ids; noted pre-fix). → A02
- **Entity type** = Select, 21 options with legal labels and counts: Adverse party 4, Billing rate 2, **Client 17**, Disbursement 14,
  Document 3, Document for acceptance 16, **Fee note 11**, FICA checklist 5, FICA checklist item 10, Firm settings 2,
  Generated document 19, **Matter 19**, Matter closure 3, Matter team member 6, Member 3, Pack 5, Security 4, Tax rate 1,
  Time recording 3, Trust account 1, Trust transaction 12. → A03
- Actor = Alice Owner → URL `…&actorId=2e38845d-7fd5-4348-9b70-280c5d442df6`, 116 total. Then Entity = Matter → URL
  `…&actorId=2e38845d-…&entityType=project` (stored key, not label) → **19 total**: Matter created ×8, Matter created from
  template ×6, Matter closure closed ×4, Matter updated ×1. Same as pre-fix raw-key result (19). Read-only SQL agrees
  (`sql-readonly-crosschecks.txt`: 8/6/4/1); API agrees (`api-audit-events-project-alice.json` total 19). → A04
- **Event type** = searchable combobox (popover with search input + "Suggestions" listbox, labels + raw key + counts).
  Typing "matter" → top option `Starts with “matter”` plus 8 options (Matter created, Matter created from template, Matter closure
  closed, Matter Closure Override Used, Matter team member added/role changed/removed, Matter updated). Typing did not touch the URL
  (history log empty). **Enter** → exactly **one** `history.pushState` to `…&eventType=matter` → **7 total** (4 matter_closure.closed +
  3 matter.closure.override_used). → A05
  - Observation (UX, not a 625D regression): the "Starts with" free-text prefix matches the **stored key**, not the label, so
    "matter" + Enter returns 7 rows although the list showed 8 "Matter …" labels (the `project.*` / `project_member.*` keys are not
    matched). A user who types "matter" and hits Enter will not get "Matter created/updated". Recorded for the orchestrator.

## 2. Entity column (G-D6 last sentence, G-D10 labels)
- Paged through all 4 pages (160 rows): every Entity cell reads `<legal label> <shortid|INV-number>`. Label tally: Generated
  document 19, Matter 19, Client 17, Document for acceptance 16, Disbursement 14, Trust transaction 12, Fee note 11, FICA checklist
  item 10, Matter team member 6, FICA checklist 5, Pack 5, Security 4, Adverse party 4, Matter closure 3, Time recording 3,
  Document 3, Member 3, Billing rate 2, Firm settings 2, Tax rate 1, Trust account 1. **Raw-literal scan (`_`, `:`, `invoice`,
  `project`, `customer`, `checklist…`) → 0 hits.**
- Entity = Fee note (`entityType=invoice`, 11 total): INV-0003 rows read **"Fee note INV-0003"** (partially_paid, sent, approved);
  likewise INV-0002 / INV-0001. **`invoice.created` rows read "Fee note 7b7e7dde"** / "Fee note a93b0aca" / "Fee note b0173b52"
  (no invoice_number on the draft row — expected per the before-README data note). → A06, A01
- Event column unchanged from 625C: `invoice.created/approved/sent` still read just "Fee note" (628D follow-up);
  "Matter Closure Override Used" and "Security Event" remain Title Case catalogue labels.

## 3. Entity ID free-text
- Typed `7b7e7dde-15db-4105-a38c-9da37c3189e5` → no URL change while typing. **Enter** → one pushState to `…&entityId=7b7e7dde-…`
  → 4 total (all INV-0003 rows). A following Tab (blur) did **not** add a second navigation (history log has exactly 1 entry). → A07

## 4. Robustness
- `?tab=audit-trail&actorId=Alice%20Owner` → page renders normally (no "Something went wrong / Unable to load settings"),
  Actor shows "All actors", 160 total (the non-UUID actor id is dropped). Pre-fix this was a Next 500. → A08

## 5. Client Activity tab (G-B2, E.9) — Acme Corp
- Tab click → `?tab=audit`; browser sees a server-action `POST /org/e2e-test-org/clients/b6dc030f-…?tab=audit` (200). Code path on
  the PR head: `audit-tab.tsx` → `fetchClientActivityPage` → `getClientActivity` → `GET /api/clients/{id}/activity`.
  Rendered rows = **15**, identical order/content to the endpoint (`api-clients-activity-acme.json`, totalElements 15); the old
  by-entity endpoint (`/api/audit-events/customer/{id}`) still returns 4, so the 11 checklist rows could only come from /activity.
  15 < page size, so no "Load more" (paging not exercised on e2e data).
- Rows verbatim (newest first): Client lifecycle auto dormancy (System); **Client lifecycle transitioned (ONBOARDING → ACTIVE)**;
  Checklist instance completed; Checklist item skipped — Sanctions Screening; … — FICA Risk Assessment; … — Power of Attorney Signed;
  **Checklist item completed — Conflict Check Performed**; … skipped — Engagement Letter Signed; — Source of Funds Declaration;
  — Beneficial Ownership Declaration; — Proof of Address; — Proof of Identity; Client lifecycle transitioned (PROSPECT → ONBOARDING);
  Checklist instance created; Client created. → A12, A13 (expanded lifecycle row details: oldStatus ONBOARDING, newStatus ACTIVE).
- Checklist-instance rows read **"Checklist instance created" / "Checklist instance completed"** (backend event label; no
  "FICA" wording in the Activity tab). Acme's checklist template (SQL): **"Legal Individual Client Onboarding"**
  (`legal-za-individual-client-onboarding`), instance `ba78be61…`, COMPLETED.

## 6. Overflow (audit trail, unfiltered, 50 rows)
| viewport | doc scrollWidth / innerWidth | table wrapper (`overflow-x-auto`) sw / cw | `<main>` width | pre-fix |
|---|---|---|---|---|
| 1440 | **1440 / 1440** | 806 / 806 | 1200 | 1440/1440 |
| 1280 | **1280 / 1280** (no page overflow) | **798 / 646** — scrolls inside its card; `scrollLeft` reached its max 152 | 1040 (main right edge = 1280) | **1406/1280 (+126)** |
→ A09, A10, A11. The Firm settings tablist at 1280 sits in its own `overflow-x-auto` strip (723/704) — "Subscription" is clipped
visually but scrollable; not page overflow.

## 7. Console
- Audit trail: one React #418 hydration error on every full load (pre-existing, noted in 625C/before). Additional 404s are
  RSC prefetches of Entity-column links (see Defects).
- Client Activity tab: 0 console errors.

## Defects / copy issues found
1. **Dead Entity links (pre-existing, not introduced by 625D; same ENTITY_ROUTES on main b56371a2c)** —
   `frontend/components/audit/entity-cell.tsx`: `trust_transaction → /trust-accounting/transactions/{id}` and
   `matter_closure → /matters/{id}/closure` prefetch **404** (no such routes; `trust-accounting/transactions` has no `[id]`
   segment, `matters/[id]` has no `closure` route). Also `matter_closure` passes the closure-log id (e.g. `013caa57` →
   project `8ca3c73d`) as the matter id, so even a closure route would resolve the wrong matter.
2. **"FICA checklist" label is unconditional** — `frontend/components/audit/entity-type-labels.ts`: `checklist_instance:
   "FICA checklist"`, `checklist_instance_item: "FICA checklist item"`, `checklist_template: "FICA checklist template"`.
   All four e2e instances use template "Legal Individual Client Onboarding", and the tenant also has "Generic Client Onboarding"
   (non-FICA). The audit trail therefore says "FICA checklist e5278c50" for a non-FICA-named checklist. Product call whether
   "Onboarding checklist" / "Checklist" is the right label.
3. **Event-type "Starts with" prefix matches the stored key, not the label** (see §1) — "matter" + Enter → 7 rows, not the
   Matter-labelled project events.
4. Intermittent: after the first Actor selection the document title read "Firm — Roles · Settings | Kazi" while on the Audit trail
   tab; not reproduced on a second attempt (Bob Admin selection kept "Firm — Audit trail"). Not attributed.
5. Event-type search input inside the popover has no accessible name (snapshot shows an unnamed `combobox`; placeholder
   "Search event types…" only). Minor a11y.
