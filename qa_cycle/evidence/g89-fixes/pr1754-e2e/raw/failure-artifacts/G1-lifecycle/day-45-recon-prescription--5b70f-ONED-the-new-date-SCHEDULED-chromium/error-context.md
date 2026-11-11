# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-45-recon-prescription.spec.ts >> Day 45 — Reconciliation & Prescription >> Alice: Court date lifecycle — Sipho's motion SCHEDULED → POSTPONED, the new date SCHEDULED
- Location: e2e/tests/legal-lifecycle/day-45-recon-prescription.spec.ts:132:7

# Error details

```
Error: expect(locator).toHaveCount(expected) failed

Locator:  getByTestId('court-date-list').getByRole('row').filter({ hasText: 'Motion' })
Expected: 1
Received: 0
Timeout:  15000ms

Call log:
  - Expect "toHaveCount" with timeout 15000ms
  - waiting for getByTestId('court-date-list').getByRole('row').filter({ hasText: 'Motion' })
    19 × locator resolved to 0 elements
       - unexpected value "0"

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
              - img [ref=e44]
              - text: Matters
            - link "New matter" [ref=e48] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters/new
              - img [ref=e49]
              - text: New matter
            - link "Conflict check" [ref=e50] [cursor=pointer]:
              - /url: /org/e2e-test-org/conflict-check
              - img [ref=e51]
              - text: Conflict check
            - link "Adverse parties" [ref=e55] [cursor=pointer]:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img [ref=e56]
              - text: Adverse parties
            - link "AI reviews" [ref=e61] [cursor=pointer]:
              - /url: /org/e2e-test-org/ai/reviews
              - img [ref=e62]
              - text: AI reviews
        - generic [ref=e66]:
          - button "Clients" [expanded] [ref=e67]:
            - generic [ref=e68]: Clients
            - img [ref=e69]
          - generic [ref=e71]:
            - link "Clients" [ref=e72] [cursor=pointer]:
              - /url: /org/e2e-test-org/clients
              - img [ref=e73]
              - text: Clients
            - link "FICA & compliance" [ref=e77] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance
              - img [ref=e78]
              - text: FICA & compliance
            - link "Data requests" [ref=e81] [cursor=pointer]:
              - /url: /org/e2e-test-org/compliance/requests
              - img [ref=e82]
              - text: Data requests
        - button "Fees" [ref=e87]:
          - generic [ref=e88]: Fees
          - img [ref=e89]
        - button "Trust" [ref=e93]:
          - generic [ref=e94]: Trust
          - img [ref=e95]
        - button "Firm" [ref=e99]:
          - generic [ref=e100]: Firm
          - img [ref=e101]
      - link "Platform Admin" [ref=e105] [cursor=pointer]:
        - /url: /platform-admin/access-requests
        - img [ref=e106]
        - text: Platform Admin
      - link "Notifications" [ref=e110] [cursor=pointer]:
        - /url: /org/e2e-test-org/notifications
        - img [ref=e111]
        - text: Notifications
      - generic [ref=e115]:
        - generic [ref=e116]: AO
        - generic [ref=e117]:
          - paragraph [ref=e118]: Alice Owner
          - paragraph [ref=e119]: alice@e2e-test.local
    - generic [ref=e120]:
      - banner [ref=e121]:
        - navigation "Breadcrumb" [ref=e122]:
          - link "E2E Test Organization" [ref=e123] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e124]:
            - img [ref=e125]
            - link "Matters" [ref=e127] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
          - generic [ref=e128]:
            - img [ref=e129]
            - generic "Ndlovu v RAF mwc51syce90d" [ref=e131]
        - generic [ref=e132]:
          - generic [ref=e134]: e2e-test-org
          - button "AO" [ref=e136]
          - button "Notifications" [ref=e138]:
            - img
            - generic [ref=e139]: 5 unread notifications
            - generic [ref=e140]: "5"
      - main [ref=e141]:
        - generic [ref=e144]:
          - generic [ref=e145]:
            - link "Back to Matters" [ref=e146] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e147]
              - text: Back to Matters
            - generic [ref=e149]:
              - button "Generate Document" [ref=e150]:
                - img
                - text: Generate Document
                - img
              - button "Generate Statement of Account" [ref=e151]:
                - img
                - text: Generate Statement of Account
              - button "More actions" [ref=e152]:
                - img
                - generic [ref=e153]: More actions
          - generic [ref=e155]:
            - generic [ref=e156]:
              - heading "Ndlovu v RAF mwc51syce90d" [level=1] [ref=e157]
              - generic [ref=e158]:
                - generic [ref=e159]: Active
                - generic [ref=e160]: LITIGATION
              - generic [ref=e161]:
                - generic [ref=e162]:
                  - term [ref=e163]:
                    - generic [ref=e164]: Case no.
                  - definition [ref=e165]: 2026/12345
                - generic [ref=e166]:
                  - term [ref=e167]:
                    - generic [ref=e168]: ·
                    - generic [ref=e169]: Court
                  - definition [ref=e170]: Gauteng Division, Pretoria
                - generic [ref=e171]:
                  - term [ref=e172]:
                    - generic [ref=e173]: ·
                    - generic [ref=e174]: Opposing Party
                  - definition [ref=e175]: Road Accident Fund
              - link "Sipho Ndlovu mwc51syce90d" [ref=e177] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients/bb15a775-ac38-48fe-8aef-fcfcc5f56dde
            - generic [ref=e178]:
              - button "Close Matter" [ref=e179]:
                - img
                - text: Close Matter
              - button "Complete Matter" [ref=e180]:
                - img
                - text: Complete Matter
              - button [ref=e181]:
                - img
          - generic [ref=e182]:
            - tablist [ref=e183]:
              - tab "Details" [ref=e184] [cursor=pointer]:
                - text: Details
                - img [ref=e185]
              - tab "Overview" [ref=e187] [cursor=pointer]
              - tab "Work" [ref=e188] [cursor=pointer]:
                - text: Work
                - img [ref=e189]
              - tab "Fees" [ref=e191] [cursor=pointer]:
                - text: Fees
                - img [ref=e192]
              - tab "Client" [ref=e194] [cursor=pointer]:
                - text: Client
                - img [ref=e195]
              - tab "Diary" [active] [selected] [ref=e197] [cursor=pointer]: Diary
              - tab "Activity" [ref=e199] [cursor=pointer]:
                - text: Activity
                - img [ref=e200]
            - tabpanel [ref=e202]:
              - generic [ref=e203]:
                - generic [ref=e204]:
                  - heading "Court Dates" [level=3] [ref=e205]
                  - button "New Court Date" [ref=e206]:
                    - img
                    - text: New Court Date
                - paragraph [ref=e208]: No court dates found for this period.
                - generic [ref=e210]:
                  - generic [ref=e211]:
                    - heading "Prescription trackers" [level=3] [ref=e212]
                    - button "New prescription tracker" [ref=e213]:
                      - img
                      - text: New prescription tracker
                  - table [ref=e215]:
                    - rowgroup [ref=e216]:
                      - row "Type Cause of action Prescription date Status Days left Actions" [ref=e217]:
                        - columnheader "Type" [ref=e218]
                        - columnheader "Cause of action" [ref=e219]
                        - columnheader "Prescription date" [ref=e220]
                        - columnheader "Status" [ref=e221]
                        - columnheader "Days left" [ref=e222]
                        - columnheader "Actions" [ref=e223]
                    - rowgroup [ref=e224]:
                      - row "Delict (3yr) 2026-09-12 2029-09-12 Running 1036 Interrupt" [ref=e225]:
                        - cell "Delict (3yr)" [ref=e226]:
                          - generic [ref=e227]: Delict (3yr)
                        - cell "2026-09-12" [ref=e228]
                        - cell "2029-09-12" [ref=e229]
                        - cell "Running" [ref=e230]:
                          - generic [ref=e231]: Running
                        - cell "1036" [ref=e232]
                        - cell "Interrupt" [ref=e233]:
                          - button "Interrupt" [ref=e234]
  - generic [ref=e235]:
    - heading "Command Palette" [level=2] [ref=e236]
    - paragraph [ref=e237]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e238]
```

# Test source

```ts
  39  |   // ── 45.1-45.8: Bank reconciliation ────────────────────────────────
  40  |   test("Alice: Navigate to Trust Reconciliation page", async ({ page }) => {
  41  |     await loginAs(page, "alice");
  42  |     await page.goto(`${BASE}/trust-accounting/reconciliation`);
  43  |     await expect(page.getByRole("heading", { level: 1, name: "Reconciliation" })).toBeVisible({
  44  |       timeout: 15_000,
  45  |     });
  46  |     // The firm's trust account (Day 0) can be reconciled
  47  |     await expect(page.getByTestId("new-reconciliation-btn")).toBeVisible();
  48  |   });
  49  | 
  50  |   test("Alice: Start a bank reconciliation — select the trust account and period end, reach the statement upload step", async ({
  51  |     page,
  52  |   }) => {
  53  |     await loginAs(page, "alice");
  54  |     await page.goto(`${BASE}/trust-accounting/reconciliation`);
  55  |     await clickThrough(
  56  |       page.getByTestId("new-reconciliation-btn"),
  57  |       page.getByRole("heading", { level: 1, name: "New Reconciliation" })
  58  |     );
  59  |     const steps = page.getByTestId("step-indicator");
  60  |     for (const step of ["Select Account", "Upload Statement", "Auto-Match", "Review & Match"]) {
  61  |       await expect(steps).toContainText(step);
  62  |     }
  63  | 
  64  |     // Step 1: account + period end
  65  |     const account = page.getByTestId("account-select");
  66  |     const option = account.locator("option", { hasText: TRUST_ACCOUNT.name });
  67  |     await expect(option).toBeAttached({ timeout: 15_000 });
  68  |     await expect(option).toContainText(`${TRUST_ACCOUNT.bank} - ${TRUST_ACCOUNT.accountNumber}`);
  69  |     await account.selectOption({ value: (await option.getAttribute("value"))! });
  70  |     await page.getByTestId("period-end-input").fill(isoDate());
  71  |     await expect(page.getByTestId("step-next-btn")).toBeEnabled();
  72  |     await page.getByTestId("step-next-btn").click();
  73  | 
  74  |     // Step 2: the bank-statement upload (a CSV picker behind a "Choose CSV File" button)
  75  |     const upload = page.getByTestId("bank-statement-upload");
  76  |     await expect(upload).toBeVisible({ timeout: 15_000 });
  77  |     await expect(upload.getByTestId("file-input")).toBeAttached();
  78  |     await expect(upload.getByTestId("file-input")).toHaveAttribute("accept", ".csv");
  79  |     await expect(upload.getByRole("button", { name: "Choose CSV File" })).toBeVisible();
  80  |     // No statement yet, so the wizard cannot advance
  81  |     await expect(page.getByTestId("step-next-btn")).toBeDisabled();
  82  | 
  83  |     // GAP: Bank CSV upload feature is not yet implemented. The test verifies that the
  84  |     // reconciliation page loads and the manual matching UI is accessible. Actual CSV import
  85  |     // and 3-way reconciliation (bank = cashbook = client ledger) will be tested once the
  86  |     // upload endpoint and parsing logic are built.
  87  |     // (Phase 88 628E: the wizard is driven to its upload step; no CSV is uploaded and auto-match
  88  |     // is not run — OD-7 item 6.)
  89  | 
  90  |     // Screenshot: the reconciliation wizard at the bank-statement upload step
  91  |     await captureScreenshot(page, "day-45-reconciliation-upload-step");
  92  |     await captureScreenshot(page, "bank-reconciliation-upload-step", { curated: true });
  93  |   });
  94  | 
  95  |   // ── 45.9-45.13: Prescription tracking for Sipho ───────────────────
  96  |   test("Alice: Create prescription tracker for Sipho (Delict, 3 years)", async ({ page }) => {
  97  |     await loginAs(page, "alice");
  98  |     await openSiphoDiary(page);
  99  |     const trackers = page.getByTestId("prescription-tracker-list");
  100 |     await expect(trackers).toBeVisible({ timeout: 15_000 });
  101 |     await expect(trackers.locator('[data-testid^="prescription-tracker-row-"]')).toHaveCount(0);
  102 | 
  103 |     await trackers.getByRole("button", { name: "New prescription tracker" }).click();
  104 |     const dialog = page.getByTestId("create-prescription-dialog");
  105 |     await expect(
  106 |       dialog.getByRole("heading", { name: "Create Prescription Tracker" })
  107 |     ).toBeVisible();
  108 |     // Opened from the matter, so the Matter is fixed to Sipho's
  109 |     await expect(dialog.getByLabel("Matter")).toBeDisabled();
  110 |     await expect(dialog.getByLabel("Matter").locator("option:checked")).toHaveText(siphoMatter());
  111 |     // Accident date: 60 days ago → prescribes three years later
  112 |     const causeOfAction = isoDate(-60);
  113 |     await dialog.getByLabel("Cause of Action Date").fill(causeOfAction);
  114 |     await dialog.getByLabel("Prescription Type").selectOption("DELICT_3Y");
  115 |     await dialog.getByRole("button", { name: "Create Tracker" }).click();
  116 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  117 | 
  118 |     const row = trackers.locator('[data-testid^="prescription-tracker-row-"]');
  119 |     await expect(row).toHaveCount(1, { timeout: 15_000 });
  120 |     await expect(row).toContainText("Delict (3yr)");
  121 |     await expect(row).toContainText("Running");
  122 |     await expect(row).toContainText(causeOfAction);
  123 |     const [y, m, d] = causeOfAction.split("-");
  124 |     await expect(row).toContainText(`${Number(y) + 3}-${m}-${d}`);
  125 |     await expect(row.getByRole("button", { name: "Interrupt" })).toBeVisible();
  126 | 
  127 |     await captureScreenshot(page, "day-45-prescription-tracker-active");
  128 |     await captureScreenshot(page, "prescription-tracker-days", { curated: true });
  129 |   });
  130 | 
  131 |   // ── 45.14-45.17: Court date lifecycle ─────────────────────────────
  132 |   test("Alice: Court date lifecycle — Sipho's motion SCHEDULED → POSTPONED, the new date SCHEDULED", async ({
  133 |     page,
  134 |   }) => {
  135 |     await loginAs(page, "alice");
  136 |     await openSiphoDiary(page);
  137 |     const list = page.getByTestId("court-date-list");
  138 |     const motions = list.getByRole("row").filter({ hasText: "Motion" });
> 139 |     await expect(motions).toHaveCount(1, { timeout: 15_000 });
      |                           ^ Error: expect(locator).toHaveCount(expected) failed
  140 |     await expect(motions).toContainText("Scheduled");
  141 | 
  142 |     await motions.getByRole("button", { name: "Actions" }).click();
  143 |     await page.getByRole("menuitem", { name: "Postpone" }).click();
  144 |     const dialog = page.getByTestId("postpone-dialog");
  145 |     await expect(dialog.getByRole("heading", { name: "Postpone Court Date" })).toBeVisible();
  146 |     await dialog.getByLabel("New Date").fill(isoDate(60));
  147 |     await dialog.getByLabel("Reason").fill("Opposing counsel unavailable — postponed by agreement");
  148 |     await dialog.getByRole("button", { name: "Postpone" }).click();
  149 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  150 | 
  151 |     // The original date is Postponed; the backend schedules the new date as its own row
  152 |     await expect(motions).toHaveCount(2, { timeout: 15_000 });
  153 |     await expect(motions.filter({ hasText: "Postponed" })).toHaveCount(1);
  154 |     await expect(motions.filter({ hasText: "Scheduled" })).toHaveCount(1);
  155 | 
  156 |     await captureScreenshot(page, "day-45-court-date-postponed");
  157 |   });
  158 | 
  159 |   // ── 45.18-45.20: Payment on Apex fee note -> PAID ─────────────────
  160 |   test("Alice: Record payment on Apex fee note (R40,250) → PAID", async ({ page }) => {
  161 |     await loginAs(page, "alice");
  162 |     await openFeeNote(page, recordedValue("apexFeeNote"));
  163 |     const main = page.locator("main");
  164 |     await expect(main.getByText("Sent", { exact: true })).toBeVisible();
  165 |     await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("40 250,00"));
  166 | 
  167 |     await main.getByRole("button", { name: "Record Payment" }).click();
  168 |     const form = page.getByTestId("record-payment-form");
  169 |     await expect(form).toBeVisible();
  170 |     await expect(form.getByTestId("payment-form-balance-due")).toContainText(
  171 |       randPattern("40 250,00")
  172 |     );
  173 |     // Amount defaults to the balance due; method defaults to EFT
  174 |     await expect(form.getByLabel("Amount")).toHaveValue("40250");
  175 |     await form.getByLabel("Payment Reference (optional)").fill("EFT APEX-INV-PAYMENT");
  176 |     await form.getByRole("button", { name: "Confirm Payment" }).click();
  177 |     await expect(form).toBeHidden({ timeout: 15_000 });
  178 |     await expect(main.getByText("Paid", { exact: true })).toBeVisible({ timeout: 15_000 });
  179 |     await expect(main.getByRole("button", { name: "Record Payment" })).toHaveCount(0);
  180 |   });
  181 | });
  182 | 
```