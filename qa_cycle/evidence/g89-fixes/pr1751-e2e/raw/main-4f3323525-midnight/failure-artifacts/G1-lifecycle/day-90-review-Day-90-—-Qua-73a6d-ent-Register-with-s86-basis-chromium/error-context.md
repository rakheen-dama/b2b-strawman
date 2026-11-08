# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Quarter Review >> Alice: Trust reports — Investment Register with s86 basis
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:159:7

# Error details

```
Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-investment-register-loaded.png, writing actual.
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
            - link "Trust accounting" [ref=e126] [cursor=pointer]:
              - /url: /org/e2e-test-org/trust-accounting
          - generic [ref=e127]:
            - img [ref=e128]
            - generic "Investments" [ref=e130]
        - generic [ref=e131]:
          - generic [ref=e133]: e2e-test-org
          - button "AO" [ref=e135]
          - button "Notifications" [ref=e137]:
            - img
            - generic [ref=e138]: 11 unread notifications
            - generic [ref=e139]: "11"
      - main [ref=e140]:
        - generic [ref=e143]:
          - generic [ref=e144]:
            - generic [ref=e145]:
              - heading "Investments" [level=1] [ref=e146]
              - paragraph [ref=e147]: Trust investment register and maturity tracking
            - button "Place Investment" [ref=e148]
          - generic [ref=e149]:
            - generic [ref=e151]:
              - generic [ref=e152]:
                - generic [ref=e153]: Investment Register
                - generic [ref=e154]: 2 investments found
              - combobox [ref=e155]:
                - generic: All
                - img
            - table [ref=e158]:
              - rowgroup [ref=e159]:
                - row "Client Basis Institution Principal Interest Rate LPFF Rate Deposit Date Maturity Date Interest Earned Status Actions" [ref=e160]:
                  - columnheader "Client" [ref=e161]
                  - columnheader "Basis" [ref=e162]
                  - columnheader "Institution" [ref=e163]
                  - columnheader "Principal" [ref=e164]
                  - columnheader "Interest Rate" [ref=e165]
                  - columnheader "LPFF Rate" [ref=e166]
                  - columnheader "Deposit Date" [ref=e167]
                  - columnheader "Maturity Date" [ref=e168]
                  - columnheader "Interest Earned" [ref=e169]
                  - columnheader "Status" [ref=e170]
                  - columnheader "Actions" [ref=e171]
              - rowgroup [ref=e172]:
                - row "Moroka Family Trust mw6z81829737 Firm Standard Bank R 200 000,00 8.50% Arrangement 08 Nov 2026 06 Feb 2027 R 0,00 ACTIVE Record Interest Withdraw" [ref=e173]:
                  - cell "Moroka Family Trust mw6z81829737" [ref=e174]
                  - cell "Firm" [ref=e175]:
                    - generic [ref=e176]: Firm
                  - cell "Standard Bank" [ref=e177]
                  - cell "R 200 000,00" [ref=e178]
                  - cell "8.50%" [ref=e179]
                  - cell "Arrangement" [ref=e180]
                  - cell "08 Nov 2026" [ref=e181]
                  - cell "06 Feb 2027" [ref=e182]
                  - cell "R 0,00" [ref=e183]
                  - cell "ACTIVE" [ref=e184]:
                    - generic [ref=e185]: ACTIVE
                  - cell "Record Interest Withdraw" [ref=e186]:
                    - generic [ref=e187]:
                      - button "Record Interest" [ref=e188]
                      - button "Withdraw" [ref=e189]
                - row "QuickCollect Services mw6z81829737 Client Instruction Nedbank R 30 000,00 7.25% 5% (statutory) 08 Nov 2026 Call deposit R 0,00 ACTIVE Record Interest Withdraw" [ref=e190]:
                  - cell "QuickCollect Services mw6z81829737" [ref=e191]
                  - cell "Client Instruction" [ref=e192]:
                    - generic [ref=e193]: Client Instruction
                  - cell "Nedbank" [ref=e194]
                  - cell "R 30 000,00" [ref=e195]
                  - cell "7.25%" [ref=e196]
                  - cell "5% (statutory)" [ref=e197]
                  - cell "08 Nov 2026" [ref=e198]
                  - cell "Call deposit" [ref=e199]
                  - cell "R 0,00" [ref=e200]
                  - cell "ACTIVE" [ref=e201]:
                    - generic [ref=e202]: ACTIVE
                  - cell "Record Interest Withdraw" [ref=e203]:
                    - generic [ref=e204]:
                      - button "Record Interest" [ref=e205]
                      - button "Withdraw" [ref=e206]
  - generic [ref=e207]:
    - heading "Command Palette" [level=2] [ref=e208]
    - paragraph [ref=e209]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e210]
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
     |       ^ Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-investment-register-loaded.png, writing actual.
  68 |         fullPage: fullPage ? true : undefined,
  69 |       });
  70 |     }
  71 |   }
  72 | }
  73 | 
```