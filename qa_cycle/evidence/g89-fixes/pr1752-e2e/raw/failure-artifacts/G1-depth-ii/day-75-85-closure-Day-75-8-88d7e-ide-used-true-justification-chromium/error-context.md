# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-75-85-closure.spec.ts >> Day 75-85 — Matter Closure (Phase 67) >> Alice: Verify override audit event captures override_used=true + justification
- Location: e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts:391:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])').filter({ hasText: 'matter_closure.closed' }).first()
Expected: visible
Timeout: 15000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 15000ms
  - waiting for locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])').filter({ hasText: 'matter_closure.closed' }).first()

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e3]:
    - complementary [ref=e4]:
      - generic [ref=e6]: Kazi
      - generic [ref=e10]: E2E Test Organization
      - button "Search, Command K" [ref=e12]:
        - img [ref=e13]
        - text: Search...
        - generic [ref=e16]: ⌘K
      - navigation "Main navigation" [ref=e17]:
        - generic [ref=e18]:
          - button "Today" [expanded] [ref=e19]:
            - generic [ref=e20]: Today
            - img [ref=e21]
          - generic [ref=e23]:
            - link "Home" [ref=e24] [cursor=pointer]:
              - /url: /org/e2e-test-org/dashboard
              - img [ref=e25]
              - text: Home
            - link "My work" [ref=e28] [cursor=pointer]:
              - /url: /org/e2e-test-org/my-work
              - img [ref=e29]
              - text: My work
            - link "Diary" [ref=e32] [cursor=pointer]:
              - /url: /org/e2e-test-org/diary
              - img [ref=e33]
              - text: Diary
        - generic [ref=e36]:
          - button "Matters" [expanded] [ref=e37]:
            - generic [ref=e38]: Matters
            - img [ref=e39]
          - generic [ref=e41]:
            - link "Matters" [ref=e42] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e43]
              - text: Matters
            - link "New matter" [ref=e47] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters/new
              - img [ref=e48]
              - text: New matter
            - link "Conflict check" [ref=e49] [cursor=pointer]:
              - /url: /org/e2e-test-org/conflict-check
              - img [ref=e50]
              - text: Conflict check
            - link "Adverse parties" [ref=e54] [cursor=pointer]:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img [ref=e55]
              - text: Adverse parties
            - link "AI reviews" [ref=e60] [cursor=pointer]:
              - /url: /org/e2e-test-org/ai/reviews
              - img [ref=e61]
              - text: AI reviews
        - generic [ref=e65]:
          - button "Clients" [expanded] [ref=e66]:
            - generic [ref=e67]: Clients
            - img [ref=e68]
          - generic [ref=e70]:
            - link "Clients" [ref=e71] [cursor=pointer]:
              - /url: /org/e2e-test-org/clients
              - img [ref=e72]
              - text: Clients
            - link "FICA & compliance" [ref=e76] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance
              - img [ref=e77]
              - text: FICA & compliance
            - link "Data requests" [ref=e80] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance/requests
              - img [ref=e81]
              - text: Data requests
        - button "Fees" [ref=e86]:
          - generic [ref=e87]: Fees
          - img [ref=e88]
        - button "Trust" [ref=e92]:
          - generic [ref=e93]: Trust
          - img [ref=e94]
        - button "Firm" [ref=e98]:
          - generic [ref=e99]: Firm
          - img [ref=e100]
      - link "Platform Admin" [ref=e104] [cursor=pointer]:
        - /url: /platform-admin/access-requests
        - img [ref=e105]
        - text: Platform Admin
      - link "Notifications" [ref=e109] [cursor=pointer]:
        - /url: /org/e2e-test-org/notifications
        - img [ref=e110]
        - text: Notifications
      - generic [ref=e114]:
        - generic [ref=e115]: AO
        - generic [ref=e116]:
          - paragraph [ref=e117]: Alice Owner
          - paragraph [ref=e118]: alice@e2e-test.local
    - generic [ref=e119]:
      - banner [ref=e120]:
        - navigation "Breadcrumb" [ref=e121]:
          - link "E2E Test Organization" [ref=e122] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e123]:
            - img [ref=e124]
            - link "Settings" [ref=e126] [cursor=pointer]:
              - /url: /org/e2e-test-org/settings
          - generic [ref=e127]:
            - img [ref=e128]
            - generic "Firm" [ref=e130]
        - generic [ref=e131]:
          - generic [ref=e133]: e2e-test-org
          - button "AO" [ref=e135]
          - button "Notifications" [ref=e137]:
            - img
            - generic [ref=e138]: 11 unread notifications
            - generic [ref=e139]: "11"
      - main [ref=e140]:
        - generic [ref=e143]:
          - complementary [ref=e144]:
            - navigation "Settings navigation" [ref=e145]:
              - list [ref=e146]:
                - listitem [ref=e147]:
                  - link "Firm" [ref=e148] [cursor=pointer]:
                    - /url: /org/e2e-test-org/settings/firm
                    - img [ref=e149]
                    - text: Firm
                  - list [ref=e153]:
                    - listitem [ref=e154]:
                      - link "General" [ref=e155] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=general
                    - listitem [ref=e156]:
                      - link "Roles" [ref=e157] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=roles
                    - listitem [ref=e158]:
                      - link "Audit trail" [ref=e159] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=audit-trail
                    - listitem [ref=e160]:
                      - link "Data protection" [ref=e161] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=data-protection
                    - listitem [ref=e162]:
                      - link "Data requests" [ref=e163] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=data-requests
                    - listitem [ref=e164]:
                      - link "Compliance" [ref=e165] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=compliance
                    - listitem [ref=e166]:
                      - link "Features" [ref=e167] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=features
                    - listitem [ref=e168]:
                      - link "Subscription" [ref=e169] [cursor=pointer]:
                        - /url: /org/e2e-test-org/settings/firm?tab=subscription
                - listitem [ref=e170]:
                  - link "Matters" [ref=e171] [cursor=pointer]:
                    - /url: /org/e2e-test-org/settings/matters
                    - img [ref=e172]
                    - text: Matters
                - listitem [ref=e176]:
                  - link "Documents" [ref=e177] [cursor=pointer]:
                    - /url: /org/e2e-test-org/settings/documents
                    - img [ref=e178]
                    - text: Documents
                - listitem [ref=e181]:
                  - link "Fees & Trust" [ref=e182] [cursor=pointer]:
                    - /url: /org/e2e-test-org/settings/fees
                    - img [ref=e183]
                    - text: Fees & Trust
                - listitem [ref=e186]:
                  - link "Connections" [ref=e187] [cursor=pointer]:
                    - /url: /org/e2e-test-org/settings/connections
                    - img [ref=e188]
                    - text: Connections
          - generic [ref=e191]:
            - tablist "Firm settings" [ref=e193]:
              - tab "General" [ref=e194]
              - tab "Roles" [ref=e195]
              - tab "Audit trail" [selected] [ref=e196]
              - tab "Data protection" [ref=e197]
              - tab "Data requests" [ref=e198]
              - tab "Compliance" [ref=e199]
              - tab "Features" [ref=e200]
              - tab "Subscription" [ref=e201]
            - tabpanel "Audit trail" [ref=e202]:
              - generic [ref=e203]:
                - generic [ref=e204]:
                  - generic [ref=e205]:
                    - heading "Audit log" [level=1] [ref=e206]
                    - paragraph [ref=e207]: Read-only feed of audit events. Filter by date range, severity, actor, event type, or entity.
                  - button "Export" [ref=e209]:
                    - img
                    - text: Export
                    - img
                - generic [ref=e210]:
                  - generic [ref=e211]:
                    - generic [ref=e213]: Preset
                    - generic [ref=e214]:
                      - generic [ref=e215]: Filter preset
                      - combobox "Filter preset" [ref=e216]:
                        - generic: — Choose a preset —
                        - img
                  - generic [ref=e217]:
                    - generic [ref=e219]: Filters
                    - generic [ref=e220]:
                      - generic [ref=e221]:
                        - generic [ref=e222]:
                          - text: From
                          - textbox "From" [ref=e223]
                        - generic [ref=e224]:
                          - text: To
                          - textbox "To" [ref=e225]
                        - generic [ref=e226]:
                          - text: Actor
                          - combobox "Actor" [ref=e227]:
                            - generic: All actors
                            - img
                        - generic [ref=e228]:
                          - text: Entity type
                          - combobox "Entity type" [ref=e229]:
                            - generic: All entity types
                            - img
                        - generic [ref=e230]:
                          - text: Event type
                          - combobox "Event type" [active] [ref=e231]:
                            - generic [ref=e232]: Matter closure closed
                            - img
                        - generic [ref=e233]:
                          - text: Entity ID
                          - textbox "Entity ID" [ref=e234]:
                            - /placeholder: UUID
                      - generic [ref=e235]:
                        - text: Severity
                        - group "Severity filter" [ref=e236]:
                          - button "INFO" [ref=e237]
                          - button "NOTICE" [ref=e238]
                          - button "WARNING" [ref=e239]
                          - button "CRITICAL" [ref=e240]
                      - button "Clear filters" [ref=e242]
                  - generic [ref=e243]:
                    - generic [ref=e245]:
                      - text: Events
                      - generic [ref=e246]: 2 total
                    - table [ref=e250]:
                      - rowgroup [ref=e251]:
                        - row "Time Severity Event Actor Entity" [ref=e252]:
                          - columnheader [ref=e253]
                          - columnheader "Time" [ref=e254]
                          - columnheader "Severity" [ref=e255]
                          - columnheader "Event" [ref=e256]
                          - columnheader "Actor" [ref=e257]
                          - columnheader "Entity" [ref=e258]
                      - rowgroup [ref=e259]:
                        - row "Expand details 11 Nov 2026, 01:08 INFO Matter closure closed Alice Owner Matter 01a67337" [ref=e260]:
                          - cell "Expand details" [ref=e261]:
                            - button "Expand details" [ref=e262]:
                              - img [ref=e263]
                          - cell "11 Nov 2026, 01:08" [ref=e265]
                          - cell "INFO" [ref=e266]:
                            - generic [ref=e267]: INFO
                          - cell "Matter closure closed" [ref=e268]:
                            - generic [ref=e269]: Matter closure closed
                          - cell "Alice Owner" [ref=e270]:
                            - generic [ref=e271]: Alice Owner
                          - cell "Matter 01a67337" [ref=e272]:
                            - link "Matter 01a67337" [ref=e273] [cursor=pointer]:
                              - /url: /org/e2e-test-org/matters/01a67337-aa61-445f-b116-f014943f210c
                        - row "Expand details 11 Nov 2026, 01:08 INFO Matter closure closed Alice Owner Matter 985abfbf" [ref=e274]:
                          - cell "Expand details" [ref=e275]:
                            - button "Expand details" [ref=e276]:
                              - img [ref=e277]
                          - cell "11 Nov 2026, 01:08" [ref=e279]
                          - cell "INFO" [ref=e280]:
                            - generic [ref=e281]: INFO
                          - cell "Matter closure closed" [ref=e282]:
                            - generic [ref=e283]: Matter closure closed
                          - cell "Alice Owner" [ref=e284]:
                            - generic [ref=e285]: Alice Owner
                          - cell "Matter 985abfbf" [ref=e286]:
                            - link "Matter 985abfbf" [ref=e287] [cursor=pointer]:
                              - /url: /org/e2e-test-org/matters/985abfbf-d396-424f-8992-6aa35c54cacd
                  - navigation "Audit log pagination" [ref=e288]:
                    - generic [ref=e289]: Page 1 of 1
                    - generic [ref=e290]:
                      - button "Previous" [disabled] [ref=e291]
                      - button "Next" [disabled] [ref=e292]
  - generic [ref=e293]:
    - heading "Command Palette" [level=2] [ref=e294]
    - paragraph [ref=e295]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e296]
```

# Test source

```ts
  1   | /**
  2   |  * Day 75 + Day 85 — Phase 67: Matter closure (happy path + override)
  3   |  *
  4   |  * Exercises:
  5   |  *   - Day 75 (Epic 489): Thandi closes a clean matter (all 9 gates pass,
  6   |  *     trust balance = R0). Reason = CONCLUDED, generateClosureLetter = true.
  7   |  *     Verify matter → CLOSED, closure letter created, and the Overview retention
  8   |  *     card shows the scheduled deletion date, close date + 5 years (ADR-354).
  9   |  *   - Day 85 (Epic 490): Matter with failing trust-balance gate:
  10  |  *       Pass A — Bob (Admin, no OVERRIDE_MATTER_CLOSURE): dialog advances to
  11  |  *                step 3 "Cannot close", override toggle hidden; API returns 409.
  12  |  *       Pass B — Thandi (Owner, has OVERRIDE_MATTER_CLOSURE): override toggle
  13  |  *                visible, enter justification ≥20 chars, close → CLOSED,
  14  |  *                audit event records override_used=true + justification verbatim.
  15  |  *
  16  |  * Prerequisites:
  17  |  *   1. E2E stack running: bash compose/scripts/e2e-up.sh (matter_closure module enabled —
  18  |  *      a seed default for legal-za)
  19  |  *   2. Day 5 + Day 30 + Day 45 flows completed in this run — Sipho's depth-II matter holds
  20  |  *      an unresolved trust balance (Day 5 deposit R5,000 − deeds payment R1,250), which is
  21  |  *      the failing-gates matter for Day 85
  22  |  *
  23  |  * Standalone: no seeded or lifecycle matter is "clean" (all gates green needs a final bill
  24  |  * SENT, no open tasks, no trust balance), so Day 75 prepares one through the UI: a new
  25  |  * run-scoped client "Apex Holdings <run>", a template-free matter for it, one approved
  26  |  * counsel-fee disbursement, and a fee note billing it that is approved and sent.
  27  |  *
  28  |  * Run:
  29  |  *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
  30  |  */
  31  | import { test, expect, type Page } from "@playwright/test";
  32  | import { loginAs } from "../../fixtures/auth";
  33  | import { captureScreenshot } from "../../helpers/screenshot";
  34  | import {
  35  |   attemptRunId,
  36  |   BASE,
  37  |   clickThrough,
  38  |   createClient,
  39  |   escapeRegExp,
  40  |   isoDate,
  41  |   openClient,
  42  |   openClosureDialog,
  43  |   openDisbursementList,
  44  |   openGroupedTab,
  45  |   openMatter,
  46  |   selectNativeOption,
  47  |   siphoMatterName,
  48  |   startClientOnboarding,
  49  | } from "../../helpers/legal-depth-ii";
  50  | 
  51  | // Scoped to the depth-II run and the attempt (a CI retry of this group prepares fresh records)
  52  | const apexClient = () => `Apex Holdings ${attemptRunId()}`;
  53  | const cleanMatterName = () => `Apex Holdings — Shareholders' Agreement Review ${attemptRunId()}`;
  54  | const counselFeeDescription = () =>
  55  |   `Counsel's opinion on shareholders' agreement ${attemptRunId()}`;
  56  | const OVERRIDE_JUSTIFICATION =
  57  |   "Trust remainder of R12,000 earmarked for final Section 35 payout pending master approval — closing under owner override";
  58  | 
  59  | const CLOSURE_GATES = [
  60  |   "TRUST_BALANCE_ZERO",
  61  |   "ALL_DISBURSEMENTS_APPROVED",
  62  |   "ALL_DISBURSEMENTS_SETTLED",
  63  |   "FINAL_BILL_ISSUED",
  64  |   "NO_OPEN_COURT_DATES",
  65  |   "NO_OPEN_PRESCRIPTIONS",
  66  |   "ALL_TASKS_RESOLVED",
  67  |   "ALL_INFO_REQUESTS_CLOSED",
  68  |   "ALL_ACCEPTANCE_REQUESTS_FINAL",
  69  | ];
  70  | 
  71  | async function openAuditLogFilteredBy(page: Page, eventType: string) {
  72  |   // Phase 87 S9 (ADR-344): /settings/audit-log → /settings/firm?tab=audit-trail
  73  |   await page.goto(`${BASE}/settings/firm?tab=audit-trail`);
  74  |   await page.waitForLoadState("networkidle");
  75  |   // 625D: Event type is a searchable combobox. Typing the full key and pressing Enter commits it:
  76  |   // the exact facet key is the first option, or — for a key-shaped query not in the facets — the
  77  |   // "Event key starts with …" prefix item is.
  78  |   const filter = page.locator("#audit-filter-event-type");
  79  |   await expect(filter).toBeVisible({ timeout: 15_000 });
  80  |   await filter.click();
  81  |   const search = page.getByPlaceholder("Search event types…");
  82  |   await expect(search).toBeVisible();
  83  |   await search.fill(eventType);
  84  |   await search.press("Enter");
  85  |   await expect(page).toHaveURL(new RegExp(`eventType=${escapeRegExp(eventType)}`));
  86  |   const row = page
  87  |     .locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])')
  88  |     .filter({ hasText: eventType })
  89  |     .first();
> 90  |   await expect(row).toBeVisible({ timeout: 15_000 });
      |                     ^ Error: expect(locator).toBeVisible() failed
  91  |   return row;
  92  | }
  93  | 
  94  | test.describe.serial("Day 75-85 — Matter Closure (Phase 67)", () => {
  95  |   // ── 75 pre-state: a clean matter (final bill sent, nothing open) ──
  96  |   test("[setup] Alice (Thandi): Create client Apex Holdings and start FICA onboarding", async ({
  97  |     page,
  98  |   }) => {
  99  |     await loginAs(page, "alice");
  100 |     // Billing address + tax number: fee-note generation and sending require them
  101 |     await createClient(page, {
  102 |       name: apexClient(),
  103 |       type: "Company",
  104 |       email: `apex.holdings.${attemptRunId()}@example.com`,
  105 |       taxNumber: "9012345678",
  106 |     });
  107 |     // New matters cannot be opened for a PROSPECT client
  108 |     await startClientOnboarding(page);
  109 |   });
  110 | 
  111 |   test("[setup] Alice (Thandi): Open a clean commercial matter for Apex Holdings", async ({
  112 |     page,
  113 |   }) => {
  114 |     await loginAs(page, "alice");
  115 |     // Phase 87 S4 (ADR-344): route rename
  116 |     await page.goto(`${BASE}/matters`);
  117 |     // "New Matter" (not "New from Template"): no template → no open tasks
  118 |     await page.getByRole("button", { name: "New Matter" }).click();
  119 |     const dialog = page.getByRole("dialog", { name: "Create Matter" });
  120 |     await expect(dialog).toBeVisible();
  121 |     await dialog.getByLabel("Name", { exact: true }).fill(cleanMatterName());
  122 |     await dialog
  123 |       .getByLabel(/^Description/)
  124 |       .fill("Review of the Apex Holdings shareholders' agreement ahead of the 2026 AGM.");
  125 |     await selectNativeOption(dialog.getByLabel(/^Client/), apexClient());
  126 |     await dialog.getByLabel(/^Work Type/).fill("COMMERCIAL");
  127 |     await dialog.getByRole("button", { name: "Create Matter" }).click();
  128 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  129 | 
  130 |     await openMatter(page, cleanMatterName());
  131 |     await expect(page.locator("main")).toContainText(apexClient());
  132 |     await expect(page.locator("main")).toContainText("COMMERCIAL");
  133 |   });
  134 | 
  135 |   test("[setup] Bob: Record + approve counsel's fee disbursement on the Apex matter", async ({
  136 |     page,
  137 |   }) => {
  138 |     await loginAs(page, "bob");
  139 |     await openDisbursementList(page, cleanMatterName());
  140 |     await page.getByTestId("create-disbursement-trigger").click();
  141 |     const dialog = page.getByTestId("create-disbursement-dialog");
  142 |     await expect(dialog).toBeVisible();
  143 |     await selectNativeOption(dialog.getByLabel("Matter"), cleanMatterName());
  144 |     await selectNativeOption(dialog.getByLabel("Client"), apexClient());
  145 |     await dialog.getByLabel("Category").selectOption("COUNSEL_FEES");
  146 |     await expect(dialog.getByLabel("VAT Treatment")).toHaveValue("STANDARD_15");
  147 |     await dialog.getByLabel("Description").fill(counselFeeDescription());
  148 |     await dialog.getByTestId("disbursement-amount-input").fill("3500.00");
  149 |     await dialog.getByLabel("Incurred Date").fill(isoDate());
  150 |     await dialog.getByLabel("Supplier", { exact: true }).fill("Adv. N. Mokoena SC");
  151 |     await dialog.getByRole("button", { name: "Create Disbursement" }).click();
  152 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  153 | 
  154 |     const row = page
  155 |       .locator('[data-testid^="disbursement-row-"]')
  156 |       .filter({ hasText: counselFeeDescription() });
  157 |     await expect(row).toContainText("Draft", { timeout: 15_000 });
  158 |     // Ready once its detail page has rendered
  159 |     const detail = page.getByTestId("disbursement-detail");
  160 |     await clickThrough(row, detail.getByText(counselFeeDescription(), { exact: true }));
  161 |     await expect(page).toHaveURL(/\/legal\/disbursements\/[0-9a-f-]{36}$/);
  162 |     await detail.getByTestId("disbursement-submit-for-approval-button").click();
  163 |     await expect(detail.getByTestId("disbursement-approval-panel")).toBeVisible({
  164 |       timeout: 15_000,
  165 |     });
  166 |     await detail.getByTestId("disbursement-approve-button").click();
  167 |     const approve = page.getByTestId("disbursement-approve-dialog");
  168 |     await approve.getByTestId("disbursement-confirm-approve-button").click();
  169 |     await expect(approve).toBeHidden({ timeout: 15_000 });
  170 |     await expect(detail.getByText("Approved", { exact: true })).toBeVisible();
  171 |   });
  172 | 
  173 |   test("[setup] Alice (Thandi): Bill the Apex matter — fee note drafted, approved and sent", async ({
  174 |     page,
  175 |   }) => {
  176 |     await loginAs(page, "alice");
  177 |     await openClient(page, apexClient());
  178 |     await openGroupedTab(page, "finance", "invoices");
  179 |     const panel = page.getByRole("tabpanel");
  180 |     const newFeeNote = panel.getByRole("button", { name: "New Fee Note" });
  181 |     await expect(newFeeNote).toBeVisible({ timeout: 15_000 });
  182 |     const feeNoteLinks = () =>
  183 |       panel
  184 |         .locator('a[href*="/invoices/"]')
  185 |         .evaluateAll((els) => els.map((e) => e.getAttribute("href")!));
  186 |     const before = new Set(await feeNoteLinks());
  187 | 
  188 |     await newFeeNote.click();
  189 |     // Phase 87 611B (ADR-338): label amendment
  190 |     const generate = page.getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ });
```