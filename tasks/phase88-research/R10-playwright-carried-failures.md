# R10 — Playwright carried failures and stale specs: read-only diagnosis

Repo: `/Users/rakheendama/Projects/2026/b2b-strawman` @ `50b88ed5c` (main). Baseline for "did anything change" questions: `41cb211fb` (pre-Phase-87).
Sources read: `tasks/phase87-capstone-findings.md` §7, `qa_cycle/evidence/phase87-capstone/e2e-runs.md`, the tracked log excerpts under `qa_cycle/evidence/phase87-capstone/logs/`, and the still-present ephemeral full logs `/tmp/617c-e2e-*.log` (they hold the full call logs quoted below). No stack was run.

## 0. Two cross-cutting mechanisms behind most "carried" failures

**M1 — `getByText(...).isVisible().catch(() => false)` hides strict-mode violations.**
Every soft assertion in the carried legal specs is written as
`expect.soft(await page.getByText(/re/i).isVisible({ timeout }).catch(() => false)).toBeTruthy()`.
`locator.isVisible()` is strict: when the text regex matches two or more elements it throws
`strict mode violation`, the `.catch` turns that into `false`, and the assertion fails *even though the text is on screen*. It also ignores `timeout` (documented: isVisible does not wait), so it is evaluated immediately after a navigation/click. Any fix of A2/A3/A4/A6 must replace this with `expect(locator.first()).toBeVisible()` or role-scoped locators.

**M2 — the lifecycle specs model flows the product never had.**
The six carried tests pre-date the product's current flows (none of the targeted components changed since `41cb211fb` except wire/route renames — see per-item git notes). They are not Phase 87 regressions and no Phase 87 slice touched the failing lines' targets.

---

## A. legal-lifecycle carried six

### A1 · day-00 · "Alice: Trust account settings page loads"
- **Spec:** `frontend/e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts:282-315`. Goes to `/settings/fees?tab=trust-accounting` (already amended for Phase 87 S9), optionally clicks `/create.*trust|new.*trust|add.*account/i`, then
  `page.locator('input[name*="lpff"], input[name*="interest"], [data-testid*="lpff"]').first()` → `.fill("6.5")`.
- **Failure (run 1 + run 2):** `locator.fill: Element is not an <input>…` — the locator resolved to `<div data-slot="card" data-testid="lpff-rates-section">`.
- **Targeted UI:** `frontend/app/(app)/org/[slug]/settings/fees/_tabs/trust-accounting.tsx:163-230` — the LPFF card is a **read-only table** (Effective From / Rate / LPFF Share / Notes) with no input. LPFF rates are entered on `/trust-accounting/interest` via `InterestPageClient` (`variant="rate"`, button **"Add Rate"**, `app/(app)/org/[slug]/trust-accounting/interest/InterestPageClient.tsx:46-55`) → `components/trust/LpffRateDialog.tsx` (`data-testid="lpff-rate-dialog"`, labels "Effective From", "Rate %", "LPFF Share %", "Notes (optional)").
- **Git since 41cb211fb:** the old page `settings/trust-accounting/page.tsx` at `41cb211fb` had the identical read-only `lpff-rates-section` card (lines 192-245); 616C only moved it into the Fees & Trust tab. The spec's locator was identical at `41cb211fb:343-349`. Nothing regressed.
- **Verdict: LOCATOR/FLOW DRIFT** (spec-only fix, allowed per 611B lesson B-7; no scenario doc change needed).
- **Proposed fix:** on the settings tab assert `getByTestId("trust-settings-page")`, `getByTestId("lpff-rates-section")` visible; to set the 6.5 % rate navigate to `${BASE}/trust-accounting/interest`, click `getByRole("button", { name: "Add Rate" })`, fill `getByLabel("Rate %")` = `6.5`, `getByLabel("LPFF Share %")`, `getByLabel("Effective From")`, submit; then re-open the settings tab and assert the table row shows `6.50%`. Also note the "Add Trust Account" click (`data-testid="add-trust-account-button"`) opens a dialog the spec never fills — nothing in the lifecycle suite creates a trust account via API either (grep of `e2e/tests/legal-lifecycle`, `e2e/fixtures`, `e2e/helpers` finds none). Confirm on repro whether the lifecycle tenant has a trust account at all; it affects A4.

### A2 · day-30 · "Alice: Create fee note for Sipho Ndlovu (hourly + tariff + disbursement)"
- **Spec:** `day-30-billing.spec.ts:32-156`. Flow modelled: `/invoices` → button `/New (Invoice|Fee Note)/i` → combobox `/customer|client/` → option Sipho → combobox `/project|matter/` → button `/Create|Save|Generate/i` → button `/add.*line|new.*line|add.*item/i` → textbox "description" / "amount" → `/save|add|create/i` → soft asserts `getByText(/tariff|lssa/i)` (:141) and `getByText(/15%|vat/i)` (:151).
- **Failure:** both soft asserts `Received: false` at :141 and :151 (run 1 and run 2).
- **Targeted UI (current, unchanged in substance since baseline):**
  - `/invoices` list `app/(app)/org/[slug]/invoices/page.tsx` → `components/invoices/create-invoice-button.tsx`: "New fee note" opens a cmdk Popover (CommandInput placeholder "Search customers…" = the combobox the spec finds; CommandItems are `role=option`, filtered to record `status === "ACTIVE"` — record status, not lifecycle, so PROSPECT Sipho is listed). Selecting an option does `router.push('/org/{slug}/clients/{id}?tab=invoices')`.
  - Client page Fee notes tab `components/customers/customer-invoices-tab.tsx` → `components/invoices/invoice-generation-dialog.tsx`: button **"New fee note"** → dialog **"Generate fee note"** (From Date / To Date / Currency) → **"Fetch Unbilled Time"** → "Select Unbilled Items" → **"Create Draft"** (or "Validate & Create Draft"). A fee note is generated from unbilled time/expenses/disbursements, never from a blank "Create".
  - Line editing lives on the fee-note detail `components/invoices/invoice-line-table.tsx:206` **"Add Line"** → `invoice-line-editor.tsx:46-110` ("Add Line Item": Description / Quantity / Unit Price / Tax Rate, submit **"Add"**); tariff lines via `invoice-detail-client.tsx:158-176` **"Add Tariff Items"** (ModuleGate `lssa_tariff`) → `components/legal/tariff-line-dialog.tsx`; VAT appears as the totals row `taxLabel` (`invoice-totals-section.tsx:34`) and in the line editor "Tax Rate" select.
  - Nothing in the spec's chain reaches the detail page: after the option click the page is the client's Fee notes tab, where no "Add line" button exists, so every subsequent step is skipped by its own `if (visible)` guard and the two soft asserts run against the client page.
  - Even at `41cb211fb` the flow was the same (`create-invoice-button.tsx` at baseline: `router.push(.../customers/${id}?tab=invoices)`; `page.tsx:93` had the same `status === "ACTIVE"` filter).
- **Verdict: FLOW/LOCATOR DRIFT** (spec never matched the product). Secondary: the Day-30 scenario text ("hourly + tariff + disbursement, LSSA auto-populated") is a *product* expectation that the current flow only partly supports — tariff lines exist (dialog), but auto-population is not a feature; that is scenario wording (CLAUDE.md §6, needs authorisation), not a spec locator issue. Related existing gap IDs: G-C10 (time-entry line descriptions), G-C7(e) (tariff dialog number formatting), G-C3 (tariff dialog "Add to Invoice" copy). Also M1 applies: `/15%|vat/i` will match several nodes on a fee-note page (totals row + Tax Rate option + tax type badge) → strict violation → false.
- **Proposed fix (spec):** rewrite to the product flow, mirroring the (fixme'd) pattern in `legal-depth-ii/day-30-45-soa-writeoff.spec.ts:362-382`: `/invoices` → `getByRole("button", { name: "New fee note" })` → `getByRole("option", { name: /^Sipho/ })` → `waitForURL(/\/clients\/[0-9a-f-]{36}\?tab=invoices/)` → `getByRole("tabpanel").getByRole("button", { name: "New fee note" })` → dialog "Generate fee note" → "Fetch Unbilled Time" → select items → "Create Draft" → open the draft → "Add Line" (Description/Quantity/Unit Price → "Add") for the R350 sheriff disbursement line → "Add Tariff Items" for the LSSA line → assert `getByRole("row", { name: /tariff/i })` / totals `getByText("VAT", { exact: true })` with `expect(...).toBeVisible()`. Precondition: Day 7–14 time entries exist for Sipho (they do in the serial run; tests "Carol/Bob: Log … on Sipho" passed).

### A3 · day-30 · "Alice: Create fee note for Apex Holdings (fixed fee R35,000 + VAT)"
- **Spec:** `day-30-billing.spec.ts:159-234`; same chain as A2, then soft assert `getByText(/40[\s,.]?250/i)` (:233).
- **Failure:** `toBeTruthy` false at :233 (run 1; serial-skipped in run 2).
- **Targeted UI:** identical to A2. A fixed-fee fee note with no unbilled time still has to go through "Generate fee note" (the dialog's step 2 lists unbilled items; "Create Draft" is the only creation path from the UI) and then "Add Line" (Description "Fixed fee…", Quantity 1, Unit Price 35000, Tax Rate = VAT 15 %) on the detail page. The R40 250 total renders in `invoice-totals-section.tsx` as `formatCurrency` → `R 40 250,00` (en-ZA: non-breaking/narrow spaces, comma decimals), which the regex `/40[\s,.]?250/` only matches if the thousands separator is a single `\s` char — confirm on repro which separator `formatCurrency` emits (`lib/format.ts`).
- **Verdict: FLOW/LOCATOR DRIFT** (same root as A2), plus a formatting-sensitive regex. Not a product bug (manual lines with VAT are supported).
- **Proposed fix:** same flow as A2 with the manual line; assert the total via `getByText(/40[\s  ,.]*250/)`.

### A4 · day-45 · "Alice: Start new bank reconciliation"
- **Spec:** `day-45-recon-prescription.spec.ts:41-120`. Goes to `/trust-accounting/reconciliation/new`, then immediately soft-asserts `input[type="file"]` (:86), `form, [data-testid*="reconciliation"]` (:97) and `getByText(/balanced|matched|reconciled/i)` (:115).
- **Failure:** all three soft asserts `false` (runs 1 and 2).
- **Targeted UI:** `app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx` — a **5-step wizard** ("Select Account", "Upload Statement", "Auto-Match", "Review & Match", "Complete"). Step 1 (`data-testid="step-select"`) has `<select data-testid="account-select">`, `<input type="date" data-testid="period-end-input">`, `data-testid="step-next-btn"` (disabled until an account and period end are chosen). The file input only exists on step 2 (`components/trust/BankStatementUpload.tsx:76-80`, `data-testid="file-input"`, card `bank-statement-upload`). There is no `<form>` and no testid containing "reconciliation" on step 1; the words "balanced/matched/reconciled" only appear after auto-match (`auto-match-result`: "Auto-matched N lines") or on step 5. The route is unchanged by Phase 87 (no redirect row; app dir only touched by 609E wire renames).
- **Verdict: FLOW/LOCATOR DRIFT.** The spec was written against an imagined single-page form; the product has always been a wizard. Possible SEED GAP on top: step 1 needs at least one trust account in the tenant (see A1 note — the lifecycle suite never creates one explicitly; the "Deposit R250,000" tests in day-07-14 pass, so either a trust account exists or those tests skip softly — verify on repro).
- **Proposed fix:** drive the wizard: select `account-select`, fill `period-end-input`, click `step-next-btn`; then `expect(getByTestId("file-input")).toBeAttached()`; upload a small CSV fixture via `setInputFiles`; click `step-next-btn`; `auto-match-btn`; assert `getByTestId("auto-match-result")` contains "Auto-matched"; "Review Matches" → `ReconciliationSplitPane`. If no CSV fixture/format is available, stop at step 2 and assert the upload card (`bank-statement-upload`) — the spec's own GAP comment already says CSV import is out of scope.

### A5 · day-90 · "Alice: Fee notes list shows multiple fee notes"
- **Spec:** `day-90-review.spec.ts:63-77`. `/invoices` → `locator('table tbody tr, [data-testid*="invoice"], [data-testid*="fee-note"]').count() >= 1` (:73).
- **Failure:** `Expected >= 1, Received 0`.
- **Targeted UI:** `invoices/page.tsx:206-286` renders `<table><tbody><tr>` per invoice, or `EmptyState` ("No fee notes yet") when the list is empty. The selector is fine; the list was **empty**.
- **Why empty:** (1) the e2e boot seed (`compose/seed/seed.sh`, the container's `CMD`) creates only org, 3 members, client "Acme Corp" (ACTIVE) and matter "Website Redesign" — **no invoices** (`lib/invoices.sh` exists but is only run by `rich-seed.sh`, which `e2e-up.sh`/`e2e-reseed.sh` never invoke); (2) Day 30 never creates a fee note (A2/A3 flow drift), and the serial block skips "Create fee note for QuickCollect" and "Fee note lifecycle" after the first failure.
- **Verdict: UPSTREAM/SEED GAP** (downstream of A2/A3), not a locator problem and not a product bug.
- **Proposed fix:** fix A2/A3 first (then the count passes naturally), or make Day 90 self-sufficient by creating one fee note via API in a `beforeAll` (`POST /api/invoices {clientId, currency}` + `POST /api/invoices/{id}/lines`, the shapes used in `compose/seed/lib/invoices.sh:14-30`).

### A6 · day-90 · "Alice: Profitability dashboard — revenue, costs, margins for 4 clients"
- **Spec:** `day-90-review.spec.ts:179-224`. `/profitability` → soft asserts `getByText(/revenue|income|billing/i)` (:204), `/cost|expense/i` (:212), `/margin|profit/i` (:220) via the M1 pattern.
- **Failure:** all three `false` (run 1; did-not-run in run 2).
- **Targeted UI:** `/profitability` 308-redirects to `/reports/profitability` (`frontend/lib/legacy-redirects.ts:45-46`, 612A). Page `app/(app)/org/[slug]/reports/profitability/page.tsx`: h1 "Profitability", cost note "Costs reflect logged expenses only; disbursements are shown as fee-note lines", then either `EmptyState` ("No profitability data yet" / "Set up billing rates in Settings to start tracking matter profitability…margins") or `ProfitabilityContent` = Team Utilization table + `project-profitability-table.tsx` (headers Billable Hours / **Revenue** / **Cost** / **Margin** / **Margin %**) + `customer-profitability-section.tsx` (Customer / **Revenue** / **Cost** / **Margin** / **Margin %**).
- **Why it fails:** M1. With data, "Revenue" matches 2 headers, "Cost"/"Costs" 3 nodes, "Margin|Profit" 5+ nodes (incl. h1 "Profitability") → every `isVisible()` throws strict-mode → `false`. With the empty state, `/margin|profit/i` still matches h1 + empty-state title + description (3 nodes) → false, and `/revenue|income|billing/i` matches "billing rates" once (true) — so the observed 3/3 false means the data path rendered (lifecycle run has time entries). Either way the assertion can never pass as written.
- **Git since 41cb211fb:** only 612A's redirect + 611x copy; table headers unchanged.
- **Verdict: LOCATOR DRIFT (strict mode), not a product bug.** (Product-level copy gaps G-C3 "Customer" header and G-D4 export are separate.)
- **Proposed fix:** `await page.goto(`${BASE}/reports/profitability`)` (or keep `/profitability` and `expect(page).toHaveURL(/\/reports\/profitability/)` to pin the redirect), then `await expect(page.getByRole("columnheader", { name: "Revenue" }).first()).toBeVisible()`, same for "Cost" and "Margin", plus `getByRole("heading", { name: "Profitability", level: 1 })`.

---

## B. Default-config stale specs

### B1 · `settings/packs.spec.ts` PACK-01 (test at :94, failure at :120)
- **Failure (full log):** `expect(getByText('Power of Attorney')).toBeVisible()` → `strict mode violation: resolved to 4 elements`: link "Power of Attorney", its description "Standard power of attorney template…", link "Power of Attorney to Pass Transfer", its description "Power of attorney authorising the conveyancer…". (The brief's ":94" is the test's start line; the packs page/installed-tab steps at :96-112 pass.)
- **Targeted UI:** `/settings/documents?tab=templates` → `app/(app)/org/[slug]/settings/documents/_tabs/templates-content.tsx` renders each template as `<a href=…/templates/{id}/edit>{name}</a>` plus a `<p class="text-xs">` description.
- **Verdict: LOCATOR DRIFT** (pre-existing; identical in 616F).
- **Fix:** `await expect(page.getByRole("link", { name: LEGAL_TEMPLATE_NAME, exact: true })).toBeVisible()`.

### B2 · `projects/project-tasks.spec.ts` PROJ-02 (:119, fails :128) and `projects/project-time.spec.ts` PROJ-03 (:131, fails :140)
- **Failure:** `Test timeout 30000ms` waiting for `getByRole('tab', { name: 'Tasks' })`.
- **Targeted UI:** `components/matters/matter-tabs.tsx` + `components/shared/grouped-tab-bar.tsx` + `lib/constants/matter-tab-groups.ts`. "Tasks" is the first sub-tab of the **Work** group (`tasks`, `documents`, `correspondence`, `generated`). Only group triggers carry `role="tab"` (`data-testid="tab-group-work"`); sub-tabs are `DropdownMenuItem`s (`data-testid="tab-item-tasks"`, role `menuitem`) shown only while the menu is open. Clicking an *inactive* group trigger navigates straight to its first sub-tab (`grouped-tab-bar.tsx:149-157`); clicking the *active* group opens the dropdown. The `?tab=` query is honoured (`matter-tabs.tsx:107-114`, `aliasLegacyTabId`).
- **Second latent failure in the same tests:** `getByRole("tabpanel", { name: "Tasks" })` (:129 / :141). Radix `TabsPrimitive.Content` sets `aria-labelledby` to a Trigger id, but the Trigger list was replaced by `GroupedTabBar`, so the panel has **no accessible name**; passing depth-ii specs use the unnamed `page.getByRole("tabpanel")` (`legal-depth-ii/day-30-45-soa-writeoff.spec.ts:144,183`, `day-14-conveyancing.spec.ts:105,145`).
- **Pattern used by passing specs:** helper `openGroupedTab(page, groupId, tabId)` in `frontend/e2e/helpers/legal-depth-ii.ts:244-263` (click `tab-group-{g}` until `tab-item-{t}` is visible, click it, assert `aria-selected="true"`); `project-crud.spec.ts:244` asserts `getByTestId("tab-group-finance")` aria-selected.
- **Verdict: LOCATOR DRIFT** (pre-existing: `project-tab-groups.ts` and the same `grouped-tab-bar.tsx` lines exist at `41cb211fb`; the e2e-runs F-1 archaeology is correct).
- **Fix:** replace `getByRole("tab", { name: "Tasks" }).click()` with either `await page.goto(`${BASE}/matters/${projectId}?tab=tasks`)` or `await page.getByTestId("tab-group-work").click()` (Tasks is the group's first sub-tab, so a single click on the inactive group lands on it; use the `openGroupedTab` helper — or lift it to `e2e/helpers/` — when the target is not the first sub-tab, e.g. `time`). Replace `getByRole("tabpanel", { name: "Tasks" })` with `page.getByRole("tabpanel")` scoped assertions (e.g. `.getByRole("button", { name: "New Task" })`). Same two edits for the reload at `project-tasks.spec.ts:146` and for `project-time.spec.ts:140-141`.

### B3 · `customers/customer-crud.spec.ts` CUST-01 (:47, fails :63)
- **Failure (error-context.md):** `getByLabel('Name')` resolved to 2 elements: `input[name="name"]` (FormLabel "Name") and `input[name="contactName"]` (FormLabel "Contact Name"). `getByLabel` is substring/case-insensitive by default.
- **Targeted UI:** `components/customers/create-customer-dialog.tsx:314` ("Name"), `:350` ("Email"), `:369` ("Phone (optional)"), `:552` ("Contact Name"), `:565` ("Contact Email"), `:578` ("Contact Phone") — the contact fields are rendered on the same step, so "Email" has the same ambiguity (`getByLabel("Email")` also matches "Contact Email") and `/Phone/` matches both "Phone (optional)" and "Contact Phone".
- **Verdict: LOCATOR DRIFT** (pre-existing per 615D baseline).
- **Fix:** `getByLabel("Name", { exact: true })`, `getByLabel("Email", { exact: true })`, `getByLabel(/^Phone/)` (or `getByRole("textbox", { name: "Name", exact: true })` as the error message itself suggests).

### B4 · `customers/customer-lifecycle.spec.ts` CUST-02 (:99, fails :106)
- **Failure:** timeout waiting for `getByRole('button', { name: 'Change Status' })`. The saved page snapshot (`/tmp/617c-e2e-artefacts/test-results/customers-customer-lifecyc-…/error-context.md`) shows the client page with heading "LC Cust 4jd8", badge "Prospect", **button "Start Onboarding"**, button "More actions", and a setup-steps card "Ready to start onboarding?" with a link "Start Onboarding".
- **What replaced `LifecycleTransitionDropdown`:** since Epic 560A (#1395) the client page uses `components/customers/client-header-card-with-lifecycle.tsx` → `client-header-card.tsx` smart action (labels `client-header-card.tsx:31-43`: "Start Onboarding" for PROSPECT, "Activate Customer" for ONBOARDING, "Complete Offboarding", "Restore", else "Edit"; the target is computed in `app/(app)/org/[slug]/clients/[id]/page.tsx:420-460`). Clicking opens `components/compliance/TransitionConfirmDialog.tsx` (AlertDialog; ONBOARDING meta title "Start Onboarding", confirm label **"Start Onboarding"**; ACTIVE meta "Activate Client"/confirm "Activate"). `LifecycleTransitionDropdown.tsx` still exists but has no importer. The lifecycle transition is therefore available in the header card, not a product gap.
- **Note:** `legal-lifecycle/day-01-03-onboarding.spec.ts:181-194` uses the same dead "Change Status" locator but inside an `if (visible)` guard, so Sipho silently never transitions in the lifecycle run — worth fixing in the same way when that spec is next touched.
- **Verdict: LOCATOR DRIFT** (pre-existing since 560A; not a product gap).
- **Fix:** `await page.getByRole("button", { name: "Start Onboarding" }).click();` then `const dlg = page.getByRole("alertdialog"); await dlg.getByRole("button", { name: "Start Onboarding" }).click();` — drop the `menuitem` step. For the ONBOARDING→ACTIVE UI step (if any later test uses it) the button is "Activate Customer" and the confirm is "Activate".

---

## C. Portal 90-day seed prerequisites (portal/e2e, project `portal-client-90day`)

Seed facts: the e2e seed container runs only `compose/seed/seed.sh` (Dockerfile `CMD ["./seed.sh"]`): org `e2e-test-org`, members alice/bob/carol, client **Acme Corp** (`email: contact@acme.example.com`, transitioned to ACTIVE, FICA checklist completed), matter **Website Redesign**, then `POST /internal/portal/resync/{orgId}` (X-API-KEY). The portal contact `contact@acme.example.com` is auto-provisioned from the client email (`backend/.../portal/PortalContactAutoProvisioner.java:80` → `PortalContactService.createContact`) — the Playwright `global-setup.ts` magic-link exchange relies on it. `rich-seed.sh` (customers/projects/tasks/time-entries/rates-budgets/invoices/documents/comments) is never run by `e2e-up.sh`/`e2e-reseed.sh` and seeds none of the three items below anyway. Firm-side API calls are legitimate seeding (not QA shortcuts) — do them in a Playwright `globalSetup`/`beforeAll` with Alice's mock-IDP JWT (pattern in `frontend/e2e/tests/customers/customer-lifecycle.spec.ts:24-38`), or extend `seed.sh`.

### C1 · Day 3 "Client uploads document and submits info request" (`day-00-07.spec.ts:119`, fails :183)
- **What happened:** the `/home` card is `<Link href="/requests">` "Pending info requests 0" (`portal/app/(authenticated)/home/page.tsx:80-88`); its name matches `/info request|view/i`, so the spec clicked through to the empty `/requests` list, found no file input/submit, and the final `getByText(/submitted|awaiting review|received/i)` was false.
- **Rows to seed (firm API, Alice):**
  1. `GET /api/clients/{acmeId}/portal-contacts` → take the `id` of `contact@acme.example.com` (`CustomerController.java:319`).
  2. `POST /api/information-requests` with `CreateInformationRequestRequest` (`informationrequest/dto/InformationRequestDtos.java:19-26`): `{ clientId: <Acme>, matterId: <Website Redesign>, portalContactId: <contact id>, dueDate: <future>, items: [{ name: "Certified ID copy", responseType: "DOCUMENT"|<ResponseType enum value for file>, required: true, sortOrder: 0 }] }` (`AdHocItemRequest` :31-37; check `ResponseType` enum for the exact file-upload value).
  3. `POST /api/information-requests/{id}/send` (`InformationRequestController.java:61`) — only `SENT`/`IN_PROGRESS` requests with `submittedItems < totalItems` count as pending in the portal (`home/page.tsx:64-69`), and the portal reads the read model (`PortalInformationRequestService.listRequests` → `portalReadModelService.findRequestsByPortalContactId`), which `send` populates via events; call `POST /internal/portal/resync/{orgId}` afterwards if the row does not appear.
- **Spec note:** after seeding, the spec should click the request card link on `/requests` (`href="/requests/{id}"`, `requests/page.tsx:79-80`) rather than the home card; the detail page's item card shows "Upload and submit" and then "Submitted — status: SUBMITTED" (`requests/[id]/page.tsx:190,210`), which matches `/submitted/i`.

### C2 · Day 60 "Client downloads SoA or financial statement" (`day-45-75.spec.ts:133`, fails :177)
- **What happened:** `/matters/{id}` "Documents" section (`matters/[id]/page.tsx:196-201`, `components/document-list.tsx`, rows show `doc.fileName`) is empty, so `getByText(/Statement of Account|financial statement|\.pdf/i)` was false.
- **Visibility rule:** the portal lists documents whose `visibility` is `SHARED` or `PORTAL` (`Document.Visibility.isPortalVisible`, `document/Document.java:259-261`; `PortalResyncService.java:101`; event handler `PortalEventHandler.java:236-298`).
- **Rows to seed (either):**
  - A statement of account: `POST /api/matters/{websiteRedesignId}/statements` with `GenerateStatementRequest { periodStart, periodEnd, templateId? }` (`StatementController.java:29-44`, capability `GENERATE_STATEMENT_OF_ACCOUNT`, module `statements`). `StatementService` writes the generated PDF as a Document with `Visibility.PORTAL` (`StatementService.java:159-166`), file name `<templateSlug>-<base>-<date>.pdf` (`:379`) — so `/\.pdf/i` matches; whether "Statement of Account" appears in the name depends on the pack template slug (the legal-za pack must be installed, which the e2e tenant has).
  - Or a plain upload: `POST /api/matters/{id}/documents/upload-init { fileName: "Statement of Account.pdf", contentType: "application/pdf", size }` → PUT the presigned URL → `POST /api/documents/{id}/confirm` → `PATCH /api/documents/{id}/visibility { visibility: "SHARED" }` (`DocumentController.java:34,79,106,116-123,135`); the seed helper `compose/seed/lib/documents.sh:_upload_doc` already implements the presign flow but never sets visibility.
  - Then `POST /internal/portal/resync/{orgId}` (or rely on the document events).

### C3 · Day 85 "Client changes digest cadence to biweekly" (`day-85-90.spec.ts:91`, fails :147)
- **What happened:** the portal `/settings/notifications` page has **no cadence control** — cadence is firm-level and rendered read-only as "Your firm sends weekly digests." / "Your firm sends bi-weekly digests." / "…has digests turned off." (`portal/app/(authenticated)/settings/notifications/page.tsx:79-86,279`); the client can only toggle "Weekly digest" on/off. The spec's combobox/radio branches both no-op and the assertion `/biweekly|bi-weekly|every other/i` is false because the firm default is `WEEKLY` (`OrgSettingsService.java:141`, `PortalSettings.java:87`).
- **Row to seed:** org settings `portalDigestCadence = BIWEEKLY` (`settings/PortalDigestCadence.java`: `WEEKLY | BIWEEKLY | OFF`; column `OrgSettings.java:281`), via `PATCH /api/settings/portal-digest-cadence { "portalDigestCadence": "BIWEEKLY" }` (`OrgSettingsController.java:178-184`, capability `TEAM_OVERSIGHT` — Alice/owner). The portal reads it through `GET /portal/notification-preferences` → `firmDigestCadence` (`PortalNotificationPreferenceService.java:80`).
- **Spec/scenario note:** the title "Client changes digest cadence" describes a capability the product deliberately does not expose (ADR-258: cadence is firm-wide, per-contact opt-out only). With the seed above the assertion passes, but the step wording is a scenario decision (CLAUDE.md §6) — flag rather than silently keep the misleading title.

---

## D. Process finding P-1 — `pnpm test:e2e -- projects`

- **Mechanism:** `frontend/package.json:14` → `"test:e2e": "playwright test --config e2e/playwright.config.ts"`; pnpm forwards everything after `--` as positional `[test-filter...]` args. Playwright treats each filter as a **regex matched against the absolute spec path** (case-insensitive), so `projects` matches `/Users/rakheendama/Projects/2026/…/e2e/tests/<anything>.spec.ts` — i.e. every file under `testDir: "./tests"` (`e2e/playwright.config.ts:6`), including the legal-lifecycle and depth-ii suites (the default config only `testIgnore`s `**/keycloak/**`). That is exactly the 314-test accidental run recorded in e2e-runs.md.
- **Path-anchored form (works today):** `pnpm test:e2e -- e2e/tests/projects/` (the segment `e2e/tests/projects/` occurs only once in the path; this is what 617C used for the corrected run). Equivalent anchored regex: `-- 'e2e/tests/projects/.*\.spec\.ts'`.
- **Script change options (pick one):**
  1. Add per-suite scripts, e.g. `"test:e2e:projects": "playwright test --config e2e/playwright.config.ts e2e/tests/projects/"`, `"test:e2e:customers"`, `"test:e2e:settings"`, `"test:e2e:navigation"`; or
  2. add Playwright `projects` entries in `e2e/playwright.config.ts` with `testDir`/`testMatch` per suite and run `--project=projects`; or
  3. set the default config's `testIgnore` to also exclude `legal-lifecycle/**` and `legal-depth-ii/**` (they have their own configs), which stops a bad filter from re-running the legal suites against a populated tenant even when the filter is wrong.
  Document the rule in `frontend/e2e/AUTOMATION-NOTES.md`: "filters are path regexes; always anchor with `e2e/tests/<dir>/`".

---

## E. Summary table

| Item | Verdict | Fix owner / shape |
|---|---|---|
| A1 Trust settings LPFF | LOCATOR/FLOW DRIFT | spec: assert section; set rate on `/trust-accounting/interest` "Add Rate" dialog |
| A2 Sipho fee note | FLOW DRIFT (+M1 strict) | spec: Generate-fee-note → draft → Add Line / Add Tariff Items; scenario wording (LSSA auto-populate) needs authorisation |
| A3 Apex fixed fee | FLOW DRIFT (+ currency regex) | spec: same flow, manual line; loosen separator regex |
| A4 Bank reconciliation | FLOW DRIFT; possible SEED GAP (trust account) | spec: drive 5-step wizard; confirm trust account exists |
| A5 Fee notes list ≥1 | UPSTREAM/SEED GAP | fix A2/A3 or API-create one fee note in beforeAll |
| A6 Profitability | LOCATOR DRIFT (strict mode) | spec: `columnheader` + `.first()` + `toBeVisible` |
| B1 PACK-01 | LOCATOR DRIFT | `getByRole("link", { name, exact: true })` |
| B2 PROJ-02/03 | LOCATOR DRIFT | `?tab=tasks` or `tab-group-work`; unnamed `tabpanel` |
| B3 CUST-01 | LOCATOR DRIFT | `getByLabel("Name", { exact: true })` (+ Email/Phone) |
| B4 CUST-02 | LOCATOR DRIFT | header "Start Onboarding" → alertdialog "Start Onboarding" |
| C1 Day 3 | SEED GAP | info request + item, SENT, for Acme's portal contact; spec clicks `/requests/{id}` |
| C2 Day 60 | SEED GAP | statement (`POST /api/matters/{id}/statements`) or SHARED document |
| C3 Day 85 | SEED GAP + scenario wording | `PATCH /api/settings/portal-digest-cadence BIWEEKLY`; title overstates product |
| D P-1 | PROCESS | path-anchored filter; per-suite scripts or testIgnore |
