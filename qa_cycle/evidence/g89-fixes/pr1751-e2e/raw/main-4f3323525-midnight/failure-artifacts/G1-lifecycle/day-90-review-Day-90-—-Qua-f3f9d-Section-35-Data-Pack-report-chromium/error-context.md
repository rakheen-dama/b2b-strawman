# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Quarter Review >> Alice: Generate Section 35 Data Pack report
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:118:7

# Error details

```
Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-section-35-generated.png, writing actual.
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
            - link "Reports" [ref=e126] [cursor=pointer]:
              - /url: /org/e2e-test-org/reports
          - generic [ref=e127]:
            - img [ref=e128]
            - generic "Section 35 Data Pack" [ref=e130]
        - generic [ref=e131]:
          - generic [ref=e133]: e2e-test-org
          - button "AO" [ref=e135]
          - button "Notifications" [ref=e137]:
            - img
            - generic [ref=e138]: 11 unread notifications
            - generic [ref=e139]: "11"
      - main [ref=e140]:
        - generic [ref=e143]:
          - link "Back to Reports" [ref=e145] [cursor=pointer]:
            - /url: /org/e2e-test-org/reports
            - img [ref=e146]
            - text: Back to Reports
          - generic [ref=e148]:
            - heading "Section 35 Data Pack" [level=1] [ref=e149]
            - paragraph [ref=e150]: Composite report combining all trust sub-reports for Section 35 compliance.
          - generic [ref=e151]:
            - generic [ref=e152]:
              - generic [ref=e153]:
                - generic [ref=e154]:
                  - generic [ref=e155]:
                    - text: Trust Account
                    - generic [ref=e156]: "*"
                  - generic [ref=e157]:
                    - combobox "Trust Account *" [ref=e158]:
                      - text: Mathebula & Partners Trust Account ••••6789
                      - img
                    - button "Clear selection" [ref=e159]:
                      - img
                - generic [ref=e160]:
                  - generic [ref=e161]:
                    - text: Financial Year End
                    - generic [ref=e162]: "*"
                  - textbox "Financial Year End *" [ref=e163]: 2026-11-08
              - button "Run Report" [ref=e164]
            - generic [ref=e165]:
              - button "Export CSV" [ref=e166]:
                - img
                - text: Export CSV
              - button "Export PDF" [ref=e167]:
                - img
                - text: Export PDF
            - generic [ref=e168]:
              - generic [ref=e169]:
                - generic [ref=e171]:
                  - paragraph [ref=e172]: Section Count
                  - paragraph [ref=e173]: "7"
                - generic [ref=e175]:
                  - paragraph [ref=e176]: Financial Year Start
                  - paragraph [ref=e177]: 2025-11-09
                - generic [ref=e179]:
                  - paragraph [ref=e180]: Financial Year End
                  - paragraph [ref=e181]: 2026-11-08
                - generic [ref=e183]:
                  - paragraph [ref=e184]: Total Trust Balance At Year End
                  - paragraph [ref=e185]: R 286 525,50
              - generic [ref=e187]:
                - table [ref=e189]:
                  - rowgroup [ref=e190]:
                    - row "Section Item Amount" [ref=e191]:
                      - columnheader "Section" [ref=e192]
                      - columnheader "Item" [ref=e193]
                      - columnheader "Amount" [ref=e194]
                  - rowgroup [ref=e195]:
                    - row "Trust Receipts & Payments 2026-11-08 · DEP/MOROKA/mw6z81829737 · Moroka Family Trust mw6z81829737 (DEPOSIT) R 250 000,00" [ref=e196]:
                      - cell "Trust Receipts & Payments" [ref=e197]
                      - cell "2026-11-08 · DEP/MOROKA/mw6z81829737 · Moroka Family Trust mw6z81829737 (DEPOSIT)" [ref=e198]
                      - cell "R 250 000,00" [ref=e199]
                    - row "Trust Receipts & Payments 2026-11-08 · DEP/QC/mw6z81829737 · QuickCollect Services mw6z81829737 (DEPOSIT) R 45 000,00" [ref=e200]:
                      - cell "Trust Receipts & Payments" [ref=e201]
                      - cell "2026-11-08 · DEP/QC/mw6z81829737 · QuickCollect Services mw6z81829737 (DEPOSIT)" [ref=e202]
                      - cell "R 45 000,00" [ref=e203]
                    - row "Trust Receipts & Payments 2026-11-08 · FEE/MOROKA/mw6z81829737 · Moroka Family Trust mw6z81829737 (FEE_TRANSFER) -R 8 500,00" [ref=e204]:
                      - cell "Trust Receipts & Payments" [ref=e205]
                      - cell "2026-11-08 · FEE/MOROKA/mw6z81829737 · Moroka Family Trust mw6z81829737 (FEE_TRANSFER)" [ref=e206]
                      - cell "-R 8 500,00" [ref=e207]
                    - row "Trust Receipts & Payments 2026-11-08 · INT-CREDIT-308b5727-b702-4354-9a3a-89a854140ca9-2f1d44fd-af7d-4965-a808-a307a5370c2f-205001be · QuickCollect Services mw6z81829737 (INTEREST_CREDIT) R 4,00" [ref=e208]:
                      - cell "Trust Receipts & Payments" [ref=e209]
                      - cell "2026-11-08 · INT-CREDIT-308b5727-b702-4354-9a3a-89a854140ca9-2f1d44fd-af7d-4965-a808-a307a5370c2f-205001be · QuickCollect Services mw6z81829737 (INTEREST_CREDIT)" [ref=e210]
                      - cell "R 4,00" [ref=e211]
                    - row "Trust Receipts & Payments 2026-11-08 · INT-CREDIT-308b5727-b702-4354-9a3a-89a854140ca9-be87af55-fce7-423d-8bae-91740852b639-5e7bf3f7 · Moroka Family Trust mw6z81829737 (INTEREST_CREDIT) R 21,50" [ref=e212]:
                      - cell "Trust Receipts & Payments" [ref=e213]
                      - cell "2026-11-08 · INT-CREDIT-308b5727-b702-4354-9a3a-89a854140ca9-be87af55-fce7-423d-8bae-91740852b639-5e7bf3f7 · Moroka Family Trust mw6z81829737 (INTEREST_CREDIT)" [ref=e214]
                      - cell "R 21,50" [ref=e215]
                    - row "Trust Receipts & Payments 2026-11-08 · INT-LPFF-308b5727-b702-4354-9a3a-89a854140ca9 · - (INTEREST_LPFF) -R 25,52" [ref=e216]:
                      - cell "Trust Receipts & Payments" [ref=e217]
                      - cell "2026-11-08 · INT-LPFF-308b5727-b702-4354-9a3a-89a854140ca9 · - (INTEREST_LPFF)" [ref=e218]
                      - cell "-R 25,52" [ref=e219]
                    - row "Trust Receipts & Payments Total R 286 499,98" [ref=e220]:
                      - cell "Trust Receipts & Payments" [ref=e221]
                      - cell "Total" [ref=e222]
                      - cell "R 286 499,98" [ref=e223]
                    - row "Client Trust Balances QuickCollect Services mw6z81829737 R 45 004,00" [ref=e224]:
                      - cell "Client Trust Balances" [ref=e225]
                      - cell "QuickCollect Services mw6z81829737" [ref=e226]
                      - cell "R 45 004,00" [ref=e227]
                    - row "Client Trust Balances Moroka Family Trust mw6z81829737 R 241 521,50" [ref=e228]:
                      - cell "Client Trust Balances" [ref=e229]
                      - cell "Moroka Family Trust mw6z81829737" [ref=e230]
                      - cell "R 241 521,50" [ref=e231]
                    - row "Client Trust Balances Total R 286 525,50" [ref=e232]:
                      - cell "Client Trust Balances" [ref=e233]
                      - cell "Total" [ref=e234]
                      - cell "R 286 525,50" [ref=e235]
                    - row "Section 86(3) Investments (Firm Discretion) Moroka Family Trust mw6z81829737 · Standard Bank FD-0987654321 R 200 000,00" [ref=e236]:
                      - cell "Section 86(3) Investments (Firm Discretion)" [ref=e237]
                      - cell "Moroka Family Trust mw6z81829737 · Standard Bank FD-0987654321" [ref=e238]
                      - cell "R 200 000,00" [ref=e239]
                    - row "Section 86(3) Investments (Firm Discretion) Total R 200 000,00" [ref=e240]:
                      - cell "Section 86(3) Investments (Firm Discretion)" [ref=e241]
                      - cell "Total" [ref=e242]
                      - cell "R 200 000,00" [ref=e243]
                    - row "Section 86(4) Investments (Client Instruction) QuickCollect Services mw6z81829737 · Nedbank CALL-1122334455 R 30 000,00" [ref=e244]:
                      - cell "Section 86(4) Investments (Client Instruction)" [ref=e245]
                      - cell "QuickCollect Services mw6z81829737 · Nedbank CALL-1122334455" [ref=e246]
                      - cell "R 30 000,00" [ref=e247]
                    - row "Section 86(4) Investments (Client Instruction) Total R 30 000,00" [ref=e248]:
                      - cell "Section 86(4) Investments (Client Instruction)" [ref=e249]
                      - cell "Total" [ref=e250]
                      - cell "R 30 000,00" [ref=e251]
                    - row "Interest Allocation QuickCollect Services mw6z81829737 R 4,00" [ref=e252]:
                      - cell "Interest Allocation" [ref=e253]
                      - cell "QuickCollect Services mw6z81829737" [ref=e254]
                      - cell "R 4,00" [ref=e255]
                    - row "Interest Allocation Moroka Family Trust mw6z81829737 R 21,50" [ref=e256]:
                      - cell "Interest Allocation" [ref=e257]
                      - cell "Moroka Family Trust mw6z81829737" [ref=e258]
                      - cell "R 21,50" [ref=e259]
                    - row "Interest Allocation Total R 25,50" [ref=e260]:
                      - cell "Interest Allocation" [ref=e261]
                      - cell "Total" [ref=e262]
                      - cell "R 25,50" [ref=e263]
                - generic [ref=e264]:
                  - paragraph [ref=e265]: Page 1 of 1
                  - generic [ref=e266]:
                    - button "Previous" [disabled]:
                      - img
                      - text: Previous
                    - button "Next" [disabled]:
                      - text: Next
                      - img
  - generic [ref=e267]:
    - heading "Command Palette" [level=2] [ref=e268]
    - paragraph [ref=e269]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e270]
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
     |       ^ Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-section-35-generated.png, writing actual.
  68 |         fullPage: fullPage ? true : undefined,
  69 |       });
  70 |     }
  71 |   }
  72 | }
  73 | 
```