# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-30-billing.spec.ts >> Day 30 — First Billing Cycle >> Alice: Generate Sipho's fee note from unbilled time, add an LSSA tariff item and a sheriff disbursement line (VAT 15%)
- Location: e2e/tests/legal-lifecycle/day-30-billing.spec.ts:90:7

# Error details

```
Error: expect(page).toHaveScreenshot(expected) failed

  12649 pixels (ratio 0.02 of all image pixels) are different.

  Snapshot: day-30-fee-note-tariff-completed.png

Call log:
  - Expect "toHaveScreenshot(day-30-fee-note-tariff-completed.png)" with timeout 5000ms
    - verifying given screenshot expectation
  - taking page screenshot
    - disabled all CSS animations
  - waiting for fonts to load...
  - fonts loaded
  - 12649 pixels (ratio 0.02 of all image pixels) are different.
  - waiting 100ms before taking screenshot
  - taking page screenshot
    - disabled all CSS animations
  - waiting for fonts to load...
  - fonts loaded
  - captured a stable screenshot
  - 12649 pixels (ratio 0.02 of all image pixels) are different.

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
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
            - link "Fee notes" [ref=e126] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
          - generic [ref=e127]:
            - img [ref=e128]
            - generic "Fee note" [ref=e130]
        - generic [ref=e131]:
          - generic [ref=e133]: e2e-test-org
          - button "AO" [ref=e135]
          - button "Notifications" [ref=e137]:
            - img
            - generic [ref=e138]: 5 unread notifications
            - generic [ref=e139]: "5"
      - main [ref=e140]:
        - generic [ref=e143]:
          - generic [ref=e144]:
            - link "Back to fee notes" [ref=e145] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
              - img [ref=e146]
              - text: Back to fee notes
            - button "Generate Document" [ref=e148]:
              - img
              - text: Generate Document
              - img
          - generic [ref=e149]:
            - generic [ref=e150]:
              - generic [ref=e151]:
                - generic [ref=e152]:
                  - 'heading "Draft fee note Help: Fee note numbering" [level=1] [ref=e153]':
                    - text: Draft fee note
                    - 'button "Help: Fee note numbering" [ref=e154] [cursor=pointer]':
                      - img [ref=e155]
                  - generic [ref=e158]: Draft
                - generic [ref=e159]:
                  - generic [ref=e160]: "Client: Sipho Ndlovu mw72qd7rb4f2"
                  - generic [ref=e161]: "Currency: ZAR"
              - generic [ref=e162]:
                - button "Preview" [ref=e163]:
                  - img
                  - text: Preview
                - button "Approve" [ref=e164]
                - button "Delete Draft" [ref=e165]
            - generic [ref=e166]:
              - heading "Fee note details" [level=2] [ref=e167]
              - generic [ref=e168]:
                - generic [ref=e169]:
                  - generic [ref=e170]: Due Date
                  - textbox "Due Date" [ref=e171]
                - generic [ref=e172]:
                  - generic [ref=e173]: Payment Terms
                  - textbox "Payment Terms" [ref=e174]:
                    - /placeholder: e.g. Net 30
                - generic [ref=e175]:
                  - generic [ref=e176]: PO Number
                  - textbox "PO Number" [ref=e177]:
                    - /placeholder: e.g. PO-2026-001
                - generic [ref=e178]:
                  - generic [ref=e179]: Tax Type
                  - combobox "Tax Type" [ref=e180]:
                    - option "Select tax type…" [selected]
                    - option "VAT"
                    - option "GST"
                    - option "Sales Tax"
                    - option "None"
                - generic [ref=e181]:
                  - generic [ref=e182]: Billing Period Start
                  - textbox "Billing Period Start" [ref=e183]
                - generic [ref=e184]:
                  - generic [ref=e185]: Billing Period End
                  - textbox "Billing Period End" [ref=e186]
                - generic [ref=e187]:
                  - generic [ref=e188]: Notes
                  - textbox "Notes" [ref=e189]:
                    - /placeholder: Additional notes...
              - button "Save Changes" [ref=e191]
            - generic [ref=e192]:
              - generic [ref=e193]:
                - heading "Line Items" [level=2] [ref=e194]
                - button "Add Line" [ref=e195]:
                  - img
                  - text: Add Line
              - table [ref=e197]:
                - rowgroup [ref=e198]:
                  - row "Description Matter Qty Rate Amount Tax Actions" [ref=e199]:
                    - columnheader "Description" [ref=e200]
                    - columnheader "Matter" [ref=e201]
                    - columnheader "Qty" [ref=e202]
                    - columnheader "Rate" [ref=e203]
                    - columnheader "Amount" [ref=e204]
                    - columnheader "Tax" [ref=e205]
                    - columnheader "Actions" [ref=e206]
                - rowgroup [ref=e207]:
                  - row "Time Entries" [ref=e208]:
                    - cell "Time Entries" [ref=e209]
                  - row "2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund Ndlovu v RAF mw72qd7rb4f2 2 R 1 200,00 R 2 400,00 VAT — Standard (15%) R 360,00 Edit 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund Delete 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund" [ref=e210]:
                    - cell "2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund" [ref=e211]:
                      - generic [ref=e212]: 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund
                    - cell "Ndlovu v RAF mw72qd7rb4f2" [ref=e213]
                    - cell "2" [ref=e214]
                    - cell "R 1 200,00" [ref=e215]
                    - cell "R 2 400,00" [ref=e216]
                    - cell "VAT — Standard (15%) R 360,00" [ref=e217]:
                      - generic [ref=e218]: VAT — Standard (15%) R 360,00
                    - cell "Edit 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund Delete 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund" [ref=e219]:
                      - generic [ref=e220]:
                        - button "Edit 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund" [ref=e221]:
                          - img [ref=e222]
                        - button "Delete 2026-11-08 · Bob Admin · Draft letter of demand to Road Accident Fund" [ref=e225]:
                          - img [ref=e226]
                  - row "2026-11-08 · Carol Member · Collate medical records for the letter of demand Ndlovu v RAF mw72qd7rb4f2 0.75 R 550,00 R 412,50 VAT — Standard (15%) R 61,88 Edit 2026-11-08 · Carol Member · Collate medical records for the letter of demand Delete 2026-11-08 · Carol Member · Collate medical records for the letter of demand" [ref=e229]:
                    - cell "2026-11-08 · Carol Member · Collate medical records for the letter of demand" [ref=e230]:
                      - generic [ref=e231]: 2026-11-08 · Carol Member · Collate medical records for the letter of demand
                    - cell "Ndlovu v RAF mw72qd7rb4f2" [ref=e232]
                    - cell "0.75" [ref=e233]
                    - cell "R 550,00" [ref=e234]
                    - cell "R 412,50" [ref=e235]
                    - cell "VAT — Standard (15%) R 61,88" [ref=e236]:
                      - generic [ref=e237]: VAT — Standard (15%) R 61,88
                    - cell "Edit 2026-11-08 · Carol Member · Collate medical records for the letter of demand Delete 2026-11-08 · Carol Member · Collate medical records for the letter of demand" [ref=e238]:
                      - generic [ref=e239]:
                        - button "Edit 2026-11-08 · Carol Member · Collate medical records for the letter of demand" [ref=e240]:
                          - img [ref=e241]
                        - button "Delete 2026-11-08 · Carol Member · Collate medical records for the letter of demand" [ref=e244]:
                          - img [ref=e245]
                  - row "2026-11-08 · Carol Member · Research case law — personal injury precedents Ndlovu v RAF mw72qd7rb4f2 1.5 R 550,00 R 825,00 VAT — Standard (15%) R 123,75 Edit 2026-11-08 · Carol Member · Research case law — personal injury precedents Delete 2026-11-08 · Carol Member · Research case law — personal injury precedents" [ref=e248]:
                    - cell "2026-11-08 · Carol Member · Research case law — personal injury precedents" [ref=e249]:
                      - generic [ref=e250]: 2026-11-08 · Carol Member · Research case law — personal injury precedents
                    - cell "Ndlovu v RAF mw72qd7rb4f2" [ref=e251]
                    - cell "1.5" [ref=e252]
                    - cell "R 550,00" [ref=e253]
                    - cell "R 825,00" [ref=e254]
                    - cell "VAT — Standard (15%) R 123,75" [ref=e255]:
                      - generic [ref=e256]: VAT — Standard (15%) R 123,75
                    - cell "Edit 2026-11-08 · Carol Member · Research case law — personal injury precedents Delete 2026-11-08 · Carol Member · Research case law — personal injury precedents" [ref=e257]:
                      - generic [ref=e258]:
                        - button "Edit 2026-11-08 · Carol Member · Research case law — personal injury precedents" [ref=e259]:
                          - img [ref=e260]
                        - button "Delete 2026-11-08 · Carol Member · Research case law — personal injury precedents" [ref=e263]:
                          - img [ref=e264]
                  - row "Tariff Items" [ref=e267]:
                    - cell "Tariff Items" [ref=e268]
                  - row "1(a) - Instructions to sue or defend Tariff — 1 R 780,00 R 780,00 VAT — Standard (15%) R 117,00 Edit 1(a) - Instructions to sue or defend Delete 1(a) - Instructions to sue or defend" [ref=e269]:
                    - cell "1(a) - Instructions to sue or defend Tariff" [ref=e270]:
                      - generic [ref=e271]:
                        - text: 1(a) - Instructions to sue or defend
                        - generic [ref=e272]: Tariff
                    - cell "—" [ref=e273]
                    - cell "1" [ref=e274]
                    - cell "R 780,00" [ref=e275]
                    - cell "R 780,00" [ref=e276]
                    - cell "VAT — Standard (15%) R 117,00" [ref=e277]:
                      - generic [ref=e278]: VAT — Standard (15%) R 117,00
                    - cell "Edit 1(a) - Instructions to sue or defend Delete 1(a) - Instructions to sue or defend" [ref=e279]:
                      - generic [ref=e280]:
                        - button "Edit 1(a) - Instructions to sue or defend" [ref=e281]:
                          - img [ref=e282]
                        - button "Delete 1(a) - Instructions to sue or defend" [ref=e285]:
                          - img [ref=e286]
                  - row "Other" [ref=e289]:
                    - cell "Other" [ref=e290]
                  - row "Disbursement — Sheriff service fee — 1 R 350,00 R 350,00 VAT — Standard (15%) R 52,50 Edit Disbursement — Sheriff service fee Delete Disbursement — Sheriff service fee" [ref=e291]:
                    - cell "Disbursement — Sheriff service fee" [ref=e292]:
                      - generic [ref=e293]: Disbursement — Sheriff service fee
                    - cell "—" [ref=e294]
                    - cell "1" [ref=e295]
                    - cell "R 350,00" [ref=e296]
                    - cell "R 350,00" [ref=e297]
                    - cell "VAT — Standard (15%) R 52,50" [ref=e298]:
                      - generic [ref=e299]: VAT — Standard (15%) R 52,50
                    - cell "Edit Disbursement — Sheriff service fee Delete Disbursement — Sheriff service fee" [ref=e300]:
                      - generic [ref=e301]:
                        - button "Edit Disbursement — Sheriff service fee" [ref=e302]:
                          - img [ref=e303]
                        - button "Delete Disbursement — Sheriff service fee" [ref=e306]:
                          - img [ref=e307]
            - button "Add Tariff Items" [ref=e311]:
              - img
              - text: Add Tariff Items
            - button "Add Disbursements" [ref=e313]:
              - img
              - text: Add Disbursements
            - generic [ref=e315]:
              - generic [ref=e316]:
                - generic [ref=e317]: Subtotal
                - generic [ref=e318]: R 4 767,50
              - generic [ref=e319]:
                - generic [ref=e320]: VAT — Standard (15%)
                - generic [ref=e321]: R 715,13
              - generic [ref=e322]:
                - generic [ref=e323]: Total
                - generic [ref=e324]: R 5 482,63
          - generic [ref=e325]:
            - img [ref=e326]
            - heading "No custom fields configured" [level=2] [ref=e329]
            - paragraph [ref=e330]: Custom fields let you track additional information specific to your workflow.
            - link "Configure Fields" [ref=e331] [cursor=pointer]:
              - /url: /org/e2e-test-org/settings/matters?tab=custom-fields
          - generic [ref=e332]:
            - heading "Generated Documents" [level=2] [ref=e333]
            - paragraph [ref=e335]: No documents generated yet
          - generic [ref=e336]:
            - heading "Collection activity" [level=2] [ref=e337]
            - paragraph [ref=e339]: No collection activity for this fee note yet.
          - generic [ref=e340]:
            - heading "Audit trail" [level=2] [ref=e341]
            - list [ref=e343]:
              - listitem [ref=e344]:
                - button "INFO Fee note created Alice Owner just now" [ref=e345]:
                  - generic [ref=e346]: INFO
                  - generic [ref=e347]: Fee note created
                  - generic [ref=e349]: Alice Owner
                  - generic [ref=e350]: just now
  - generic [ref=e351]:
    - heading "Command Palette" [level=2] [ref=e352]
    - paragraph [ref=e353]: Search pages and settings
  - region "Notifications alt+T":
    - list:
      - listitem [ref=e354]:
        - img [ref=e356]
        - generic [ref=e359]: Added 1 tariff line to fee note
  - alert [ref=e360]: Kazi — Practice management, built for Africa
```

# Test source

```ts
  1  | import { expect, Page, Locator } from "@playwright/test";
  2  | import { mkdirSync, writeFileSync } from "node:fs";
  3  | import path from "node:path";
  4  | 
  5  | const CURATED_DIR = path.resolve(
  6  |   __dirname,
  7  |   "..",
  8  |   "..",
  9  |   "..",
  10 |   "documentation",
  11 |   "screenshots",
  12 |   "legal-vertical"
  13 | );
  14 | 
  15 | export interface ScreenshotOptions {
  16 |   /** Save a curated screenshot for documentation (default: false = regression mode) */
  17 |   curated?: boolean;
  18 |   /** Capture the full scrollable page (default: false) */
  19 |   fullPage?: boolean;
  20 |   /** Capture a specific element instead of the full page */
  21 |   locator?: Locator;
  22 | }
  23 | 
  24 | /**
  25 |  * Validate that the screenshot name does not escape the target directory.
  26 |  */
  27 | function assertSafeName(name: string, baseDir: string): string {
  28 |   const resolved = path.resolve(baseDir, `${name}.png`);
  29 |   if (!resolved.startsWith(path.resolve(baseDir))) {
  30 |     throw new Error(`Screenshot name "${name}" would write outside curated directory`);
  31 |   }
  32 |   return resolved;
  33 | }
  34 | 
  35 | /**
  36 |  * Capture a screenshot in one of two modes:
  37 |  *
  38 |  * **Regression mode** (default): Uses Playwright's `toHaveScreenshot()` to compare
  39 |  * against committed baselines. Baselines are stored in `e2e/screenshots/legal-lifecycle/`.
  40 |  *
  41 |  * **Curated mode** (`curated: true`): Saves a standalone PNG to
  42 |  * `documentation/screenshots/legal-vertical/` for use in walkthroughs and blog posts.
  43 |  */
  44 | export async function captureScreenshot(
  45 |   page: Page,
  46 |   name: string,
  47 |   options?: ScreenshotOptions
  48 | ): Promise<void> {
  49 |   const { curated = false, fullPage = false, locator } = options ?? {};
  50 | 
  51 |   if (curated) {
  52 |     mkdirSync(CURATED_DIR, { recursive: true });
  53 |     const filePath = assertSafeName(name, CURATED_DIR);
  54 | 
  55 |     if (locator) {
  56 |       const buffer = await locator.screenshot();
  57 |       writeFileSync(filePath, buffer);
  58 |     } else {
  59 |       const buffer = await page.screenshot({ fullPage });
  60 |       writeFileSync(filePath, buffer);
  61 |     }
  62 |   } else {
  63 |     // fullPage is only valid for page-level assertions, not locator assertions
  64 |     if (locator) {
  65 |       await expect(locator).toHaveScreenshot(`${name}.png`);
  66 |     } else {
> 67 |       await expect(page).toHaveScreenshot(`${name}.png`, {
     |                          ^ Error: expect(page).toHaveScreenshot(expected) failed
  68 |         fullPage: fullPage ? true : undefined,
  69 |       });
  70 |     }
  71 |   }
  72 | }
  73 | 
```