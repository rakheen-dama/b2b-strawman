# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-14-conveyancing.spec.ts >> Day 14 — Conveyancing matter + OTP acceptance (Phase 67) >> Alice: Fill property_address, erf_number, deeds_office, seller and purchaser
- Location: e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts:282:7

# Error details

```
Error: expect(page).toHaveScreenshot(expected) failed

  20056 pixels (ratio 0.03 of all image pixels) are different.

  Snapshot: day-14-conveyancing-fields-populated.png

Call log:
  - Expect "toHaveScreenshot(day-14-conveyancing-fields-populated.png)" with timeout 5000ms
    - verifying given screenshot expectation
  - taking page screenshot
    - disabled all CSS animations
  - waiting for fonts to load...
  - fonts loaded
  - 20056 pixels (ratio 0.03 of all image pixels) are different.
  - waiting 100ms before taking screenshot
  - taking page screenshot
    - disabled all CSS animations
  - waiting for fonts to load...
  - fonts loaded
  - captured a stable screenshot
  - 20056 pixels (ratio 0.03 of all image pixels) are different.

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
            - generic "Khumalo Property Holdings — Erf 1234 Sandton Transfer mw6yqow0a7ce" [ref=e131]
        - generic [ref=e132]:
          - generic [ref=e134]: e2e-test-org
          - button "AO" [ref=e136]
          - button "Notifications" [ref=e138]:
            - img
            - generic [ref=e139]: 8 unread notifications
            - generic [ref=e140]: "8"
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
              - heading "Khumalo Property Holdings — Erf 1234 Sandton Transfer mw6yqow0a7ce" [level=1] [ref=e157]
              - generic [ref=e158]:
                - generic [ref=e159]: Active
                - generic [ref=e160]: CONVEYANCING
              - link "Khumalo Property Holdings mw6yqow0a7ce" [ref=e162] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients/1bfc1eeb-153d-4789-990c-71e5dfbecdbd
            - generic [ref=e163]:
              - button "Close Matter" [ref=e164]:
                - img
                - text: Close Matter
              - button "Complete Matter" [ref=e165]:
                - img
                - text: Complete Matter
              - button [ref=e166]:
                - img
          - generic [ref=e167]:
            - tablist [ref=e168]:
              - tab "Details · Custom fields" [active] [selected] [ref=e169] [cursor=pointer]:
                - text: Details
                - generic [ref=e170]: · Custom fields
                - img [ref=e171]
              - tab "Overview" [ref=e174] [cursor=pointer]
              - tab "Work" [ref=e175] [cursor=pointer]:
                - text: Work
                - img [ref=e176]
              - tab "Fees" [ref=e178] [cursor=pointer]:
                - text: Fees
                - img [ref=e179]
              - tab "Client" [ref=e181] [cursor=pointer]:
                - text: Client
                - img [ref=e182]
              - tab "Diary" [ref=e184] [cursor=pointer]
              - tab "Activity" [ref=e185] [cursor=pointer]:
                - text: Activity
                - img [ref=e186]
            - tabpanel [ref=e188]:
              - generic [ref=e189]:
                - generic [ref=e190]:
                  - heading "Field Groups" [level=3] [ref=e191]
                  - generic [ref=e192]:
                    - paragraph [ref=e193]: Field Groups
                    - generic [ref=e194]:
                      - generic [ref=e195]:
                        - text: SA Conveyancing — Matter Details
                        - button "Remove SA Conveyancing — Matter Details" [ref=e196]:
                          - img [ref=e197]
                      - generic [ref=e200]:
                        - text: SA Legal — Matter Details
                        - button "Remove SA Legal — Matter Details" [ref=e201]:
                          - img [ref=e202]
                      - generic [ref=e205]:
                        - text: Matter Info
                        - button "Remove Matter Info" [ref=e206]:
                          - img [ref=e207]
                - generic [ref=e210]:
                  - generic [ref=e211]:
                    - generic [ref=e213]: SA Conveyancing — Matter Details
                    - generic [ref=e215]:
                      - generic [ref=e216]:
                        - generic [ref=e217]: Conveyancing Type
                        - combobox "Conveyancing Type" [ref=e219]:
                          - option "Select..." [selected]
                          - option "Transfer"
                          - option "Bond Registration"
                          - option "Bond Cancellation"
                          - option "Sectional Title"
                          - option "Estate Transfer"
                      - generic [ref=e220]:
                        - generic [ref=e221]: Property Address
                        - textbox "Property Address" [ref=e223]:
                          - /placeholder: Full physical address of the property being transferred
                          - text: 12 Rivonia Road, Sandton, 2196
                      - generic [ref=e224]:
                        - generic [ref=e225]: Erf Number
                        - textbox "Erf Number" [ref=e227]:
                          - /placeholder: Erf/stand number identifying the property
                          - text: "1234"
                      - generic [ref=e228]:
                        - generic [ref=e229]: Deeds Office
                        - combobox "Deeds Office" [ref=e231]:
                          - option "Select..."
                          - option "Johannesburg" [selected]
                          - option "Pretoria"
                          - option "Cape Town"
                          - option "Bloemfontein"
                          - option "Pietermaritzburg"
                          - option "King William's Town"
                          - option "Kimberley"
                          - option "Mahikeng"
                          - option "Mthatha"
                          - option "Vryburg"
                      - generic [ref=e232]:
                        - generic [ref=e233]: Lodgement Date
                        - textbox "Lodgement Date" [ref=e235]
                      - generic [ref=e236]:
                        - generic [ref=e237]: Purchase Price
                        - generic [ref=e239]:
                          - spinbutton "Purchase Price" [ref=e240]
                          - combobox "Currency" [ref=e241]:
                            - option "Currency" [selected]
                            - option "USD"
                            - option "EUR"
                            - option "GBP"
                            - option "ZAR"
                            - option "AUD"
                            - option "CAD"
                            - option "CHF"
                            - option "JPY"
                            - option "CNY"
                            - option "INR"
                      - generic [ref=e242]:
                        - generic [ref=e243]: Transfer Duty
                        - generic [ref=e245]:
                          - spinbutton "Transfer Duty" [ref=e246]
                          - combobox "Currency" [ref=e247]:
                            - option "Currency" [selected]
                            - option "USD"
                            - option "EUR"
                            - option "GBP"
                            - option "ZAR"
                            - option "AUD"
                            - option "CAD"
                            - option "CHF"
                            - option "JPY"
                            - option "CNY"
                            - option "INR"
                      - generic [ref=e248]:
                        - generic [ref=e249]: Bond Institution
                        - combobox "Bond Institution" [ref=e251]:
                          - option "Select..." [selected]
                          - option "ABSA"
                          - option "FNB"
                          - option "Standard Bank"
                          - option "Nedbank"
                          - option "Investec"
                          - option "SA Home Loans"
                          - option "Other"
                          - option "None (Cash Purchase)"
                      - generic [ref=e252]:
                        - generic [ref=e253]: Seller Name
                        - textbox "Seller Name" [ref=e255]:
                          - /placeholder: Full legal name of the seller (transferor)
                          - text: Johannes van der Merwe
                      - generic [ref=e256]:
                        - generic [ref=e257]: Seller ID / Passport Number
                        - textbox "Seller ID / Passport Number" [ref=e259]:
                          - /placeholder: ID number, passport number or registration number of the seller
                      - generic [ref=e260]:
                        - generic [ref=e261]: Purchaser Name
                        - textbox "Purchaser Name" [ref=e263]:
                          - /placeholder: Full legal name of the purchaser (transferee)
                          - text: Khumalo Property Holdings mw6yqow0a7ce
                      - generic [ref=e264]:
                        - generic [ref=e265]: Purchaser ID / Passport Number
                        - textbox "Purchaser ID / Passport Number" [ref=e267]:
                          - /placeholder: ID number, passport number or registration number of the purchaser
                      - generic [ref=e268]:
                        - generic [ref=e269]: Occupation Date
                        - textbox "Occupation Date" [ref=e271]
                      - generic [ref=e272]:
                        - generic [ref=e273]: Bond Number
                        - textbox "Bond Number" [ref=e275]:
                          - /placeholder: Number of the existing mortgage bond over the property (for bond cancellation)
                  - generic [ref=e276]:
                    - generic [ref=e278]: SA Legal — Matter Details
                    - generic [ref=e280]:
                      - generic [ref=e281]:
                        - generic [ref=e282]: Case Number
                        - textbox "Case Number" [ref=e284]:
                          - /placeholder: Court-assigned case number for the matter
                      - generic [ref=e285]:
                        - generic [ref=e286]: Court
                        - textbox "Court" [ref=e288]:
                          - /placeholder: Name of the court in which the matter is heard
                      - generic [ref=e289]:
                        - generic [ref=e290]: Opposing Party
                        - textbox "Opposing Party" [ref=e292]:
                          - /placeholder: Name of the primary opposing party
                      - generic [ref=e293]:
                        - generic [ref=e294]: Opposing Attorney
                        - textbox "Opposing Attorney" [ref=e296]:
                          - /placeholder: Name of the opposing attorney or firm
                      - generic [ref=e297]:
                        - generic [ref=e298]: Advocate
                        - textbox "Advocate" [ref=e300]:
                          - /placeholder: Name of the instructed advocate (if briefed)
                      - generic [ref=e301]:
                        - generic [ref=e302]: Date of Instruction
                        - textbox "Date of Instruction" [ref=e304]
                      - generic [ref=e305]:
                        - generic [ref=e306]: Estimated Value
                        - spinbutton "Estimated Value" [ref=e308]
                  - generic [ref=e309]:
                    - generic [ref=e311]: Matter Info
                    - generic [ref=e314]:
                      - generic [ref=e315]: Category
                      - textbox "Category" [ref=e317]:
                        - /placeholder: Matter category or type
                  - button "Save Custom Fields" [ref=e319]:
                    - img
                    - text: Save Custom Fields
  - generic [ref=e320]:
    - heading "Command Palette" [level=2] [ref=e321]
    - paragraph [ref=e322]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e323]
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