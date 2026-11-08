# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Quarter Review >> Alice: Profitability report — revenue, cost and margin columns for this month's matters
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:191:7

# Error details

```
Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-profitability-dashboard-loaded.png, writing actual.
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
            - generic "Profitability" [ref=e130]
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
            - heading "Profitability" [level=1] [ref=e145]
            - paragraph [ref=e146]: Team utilization, matter profitability, and client profitability across your organization
            - paragraph [ref=e147]: Costs reflect logged expenses only; disbursements are shown as fee-note lines
          - generic [ref=e148]:
            - generic [ref=e149]:
              - generic [ref=e151]:
                - generic [ref=e152]:
                  - text: Team Utilization
                  - 'button "Help: Utilisation rate" [ref=e153] [cursor=pointer]':
                    - img [ref=e154]
                - generic [ref=e157]:
                  - generic [ref=e158]: From
                  - textbox "From" [ref=e159]: 2026-11-01
                  - generic [ref=e160]: To
                  - textbox "To" [ref=e161]: 2026-11-08
              - table [ref=e164]:
                - rowgroup [ref=e165]:
                  - row "Name Total Hours Billable Non-Billable Utilization %" [ref=e166]:
                    - columnheader [ref=e167]
                    - columnheader "Name" [ref=e168]:
                      - button "Name" [ref=e169]:
                        - text: Name
                        - img [ref=e170]
                    - columnheader "Total Hours" [ref=e173]:
                      - button "Total Hours" [ref=e174]:
                        - text: Total Hours
                        - img [ref=e175]
                    - columnheader "Billable" [ref=e178]:
                      - button "Billable" [ref=e179]:
                        - text: Billable
                        - img [ref=e180]
                    - columnheader "Non-Billable" [ref=e183]:
                      - button "Non-Billable" [ref=e184]:
                        - text: Non-Billable
                        - img [ref=e185]
                    - columnheader "Utilization %" [ref=e188]:
                      - button "Utilization %" [ref=e189]:
                        - text: Utilization %
                        - img [ref=e190]
                - rowgroup [ref=e193]:
                  - row "Expand currency breakdown Alice Owner 8.5h 8.5h 0.0h 100.0%" [ref=e194]:
                    - cell "Expand currency breakdown" [ref=e195]:
                      - button "Expand currency breakdown" [ref=e196]:
                        - img [ref=e197]
                    - cell "Alice Owner" [ref=e199]
                    - cell "8.5h" [ref=e200]
                    - cell "8.5h" [ref=e201]
                    - cell "0.0h" [ref=e202]
                    - cell "100.0%" [ref=e203]:
                      - generic [ref=e207]: 100.0%
                  - row "Expand currency breakdown Bob Admin 3.0h 3.0h 0.0h 100.0%" [ref=e208]:
                    - cell "Expand currency breakdown" [ref=e209]:
                      - button "Expand currency breakdown" [ref=e210]:
                        - img [ref=e211]
                    - cell "Bob Admin" [ref=e213]
                    - cell "3.0h" [ref=e214]
                    - cell "3.0h" [ref=e215]
                    - cell "0.0h" [ref=e216]
                    - cell "100.0%" [ref=e217]:
                      - generic [ref=e221]: 100.0%
                  - row "Expand currency breakdown Carol Member 2.3h 2.3h 0.0h 100.0%" [ref=e222]:
                    - cell "Expand currency breakdown" [ref=e223]:
                      - button "Expand currency breakdown" [ref=e224]:
                        - img [ref=e225]
                    - cell "Carol Member" [ref=e227]
                    - cell "2.3h" [ref=e228]
                    - cell "2.3h" [ref=e229]
                    - cell "0.0h" [ref=e230]
                    - cell "100.0%" [ref=e231]:
                      - generic [ref=e235]: 100.0%
            - generic [ref=e236]:
              - generic [ref=e238]:
                - generic [ref=e239]:
                  - text: Matter Profitability
                  - 'button "Help: Profitability margin" [ref=e240] [cursor=pointer]':
                    - img [ref=e241]
                - generic [ref=e244]:
                  - generic [ref=e245]: From
                  - textbox "From" [ref=e246]: 2026-11-01
                  - generic [ref=e247]: To
                  - textbox "To" [ref=e248]: 2026-11-08
                  - button "Export CSV" [ref=e249]:
                    - img
                    - text: Export CSV
              - table [ref=e252]:
                - rowgroup [ref=e253]:
                  - row "Matter Client Currency Billable Hours Revenue Cost Margin Margin %" [ref=e254]:
                    - columnheader "Matter" [ref=e255]:
                      - button "Matter" [ref=e256]:
                        - text: Matter
                        - img [ref=e257]
                    - columnheader "Client" [ref=e260]:
                      - button "Client" [ref=e261]:
                        - text: Client
                        - img [ref=e262]
                    - columnheader "Currency" [ref=e265]
                    - columnheader "Billable Hours" [ref=e266]:
                      - button "Billable Hours" [ref=e267]:
                        - text: Billable Hours
                        - img [ref=e268]
                    - columnheader "Revenue" [ref=e271]:
                      - button "Revenue" [ref=e272]:
                        - text: Revenue
                        - img [ref=e273]
                    - columnheader "Cost" [ref=e276]:
                      - button "Cost" [ref=e277]:
                        - text: Cost
                        - img [ref=e278]
                    - columnheader "Margin" [ref=e281]:
                      - button "Margin" [ref=e282]:
                        - text: Margin
                        - img [ref=e283]
                    - columnheader "Margin %" [ref=e286]:
                      - button "Margin %" [ref=e287]:
                        - text: Margin %
                        - img [ref=e288]
                - rowgroup [ref=e291]:
                  - row "Estate Late Peter Moroka mw6z81829737 Moroka Family Trust mw6z81829737 ZAR 7.0h R 17 500,00 R 7 000,00 R 10 500,00 60.0%" [ref=e292]:
                    - cell "Estate Late Peter Moroka mw6z81829737" [ref=e293]
                    - cell "Moroka Family Trust mw6z81829737" [ref=e294]
                    - cell "ZAR" [ref=e295]
                    - cell "7.0h" [ref=e296]
                    - cell "R 17 500,00" [ref=e297]
                    - cell "R 7 000,00" [ref=e298]
                    - cell "R 10 500,00" [ref=e299]
                    - cell "60.0%" [ref=e300]:
                      - generic [ref=e301]: 60.0%
                  - row "Ndlovu v RAF mw6z81829737 Sipho Ndlovu mw6z81829737 ZAR 5.3h R 6 137,50 R 2 450,00 R 3 687,50 60.1%" [ref=e302]:
                    - cell "Ndlovu v RAF mw6z81829737" [ref=e303]
                    - cell "Sipho Ndlovu mw6z81829737" [ref=e304]
                    - cell "ZAR" [ref=e305]
                    - cell "5.3h" [ref=e306]
                    - cell "R 6 137,50" [ref=e307]
                    - cell "R 2 450,00" [ref=e308]
                    - cell "R 3 687,50" [ref=e309]
                    - cell "60.1%" [ref=e310]:
                      - generic [ref=e311]: 60.1%
                  - row "Apex Shareholder Agreement mw6z81829737 Apex Holdings mw6z81829737 ZAR 0.5h R 1 250,00 R 500,00 R 750,00 60.0%" [ref=e312]:
                    - cell "Apex Shareholder Agreement mw6z81829737" [ref=e313]
                    - cell "Apex Holdings mw6z81829737" [ref=e314]
                    - cell "ZAR" [ref=e315]
                    - cell "0.5h" [ref=e316]
                    - cell "R 1 250,00" [ref=e317]
                    - cell "R 500,00" [ref=e318]
                    - cell "R 750,00" [ref=e319]
                    - cell "60.0%" [ref=e320]:
                      - generic [ref=e321]: 60.0%
                  - 'row "QC Debt Recovery #1 mw6z81829737 QuickCollect Services mw6z81829737 ZAR 1.0h R 1 200,00 R 500,00 R 700,00 58.3%" [ref=e322]':
                    - 'cell "QC Debt Recovery #1 mw6z81829737" [ref=e323]'
                    - cell "QuickCollect Services mw6z81829737" [ref=e324]
                    - cell "ZAR" [ref=e325]
                    - cell "1.0h" [ref=e326]
                    - cell "R 1 200,00" [ref=e327]
                    - cell "R 500,00" [ref=e328]
                    - cell "R 700,00" [ref=e329]
                    - cell "58.3%" [ref=e330]:
                      - generic [ref=e331]: 58.3%
            - generic [ref=e332]:
              - generic [ref=e334]:
                - generic [ref=e335]: Client Profitability
                - generic [ref=e336]:
                  - generic [ref=e337]: From
                  - textbox "From" [ref=e338]: 2026-11-01
                  - generic [ref=e339]: To
                  - textbox "To" [ref=e340]: 2026-11-08
              - table [ref=e343]:
                - rowgroup [ref=e344]:
                  - row "Client Currency Revenue Cost Margin Margin %" [ref=e345]:
                    - columnheader [ref=e346]
                    - columnheader "Client" [ref=e347]:
                      - button "Client" [ref=e348]:
                        - text: Client
                        - img [ref=e349]
                    - columnheader "Currency" [ref=e352]
                    - columnheader "Revenue" [ref=e353]:
                      - button "Revenue" [ref=e354]:
                        - text: Revenue
                        - img [ref=e355]
                    - columnheader "Cost" [ref=e358]:
                      - button "Cost" [ref=e359]:
                        - text: Cost
                        - img [ref=e360]
                    - columnheader "Margin" [ref=e363]:
                      - button "Margin" [ref=e364]:
                        - text: Margin
                        - img [ref=e365]
                    - columnheader "Margin %" [ref=e368]:
                      - button "Margin %" [ref=e369]:
                        - text: Margin %
                        - img [ref=e370]
                - rowgroup [ref=e373]:
                  - row "Moroka Family Trust mw6z81829737 ZAR R 17 500,00 R 7 000,00 R 10 500,00 60.0%" [ref=e374] [cursor=pointer]:
                    - cell [ref=e375]:
                      - img [ref=e376]
                    - cell "Moroka Family Trust mw6z81829737" [ref=e378]
                    - cell "ZAR" [ref=e379]
                    - cell "R 17 500,00" [ref=e380]
                    - cell "R 7 000,00" [ref=e381]
                    - cell "R 10 500,00" [ref=e382]
                    - cell "60.0%" [ref=e383]:
                      - generic [ref=e384]: 60.0%
                  - row "Sipho Ndlovu mw6z81829737 ZAR R 6 137,50 R 2 450,00 R 3 687,50 60.1%" [ref=e385] [cursor=pointer]:
                    - cell [ref=e386]:
                      - img [ref=e387]
                    - cell "Sipho Ndlovu mw6z81829737" [ref=e389]
                    - cell "ZAR" [ref=e390]
                    - cell "R 6 137,50" [ref=e391]
                    - cell "R 2 450,00" [ref=e392]
                    - cell "R 3 687,50" [ref=e393]
                    - cell "60.1%" [ref=e394]:
                      - generic [ref=e395]: 60.1%
                  - row "Apex Holdings mw6z81829737 ZAR R 1 250,00 R 500,00 R 750,00 60.0%" [ref=e396] [cursor=pointer]:
                    - cell [ref=e397]:
                      - img [ref=e398]
                    - cell "Apex Holdings mw6z81829737" [ref=e400]
                    - cell "ZAR" [ref=e401]
                    - cell "R 1 250,00" [ref=e402]
                    - cell "R 500,00" [ref=e403]
                    - cell "R 750,00" [ref=e404]
                    - cell "60.0%" [ref=e405]:
                      - generic [ref=e406]: 60.0%
                  - row "QuickCollect Services mw6z81829737 ZAR R 1 200,00 R 500,00 R 700,00 58.3%" [ref=e407] [cursor=pointer]:
                    - cell [ref=e408]:
                      - img [ref=e409]
                    - cell "QuickCollect Services mw6z81829737" [ref=e411]
                    - cell "ZAR" [ref=e412]
                    - cell "R 1 200,00" [ref=e413]
                    - cell "R 500,00" [ref=e414]
                    - cell "R 700,00" [ref=e415]
                    - cell "58.3%" [ref=e416]:
                      - generic [ref=e417]: 58.3%
  - generic [ref=e418]:
    - heading "Command Palette" [level=2] [ref=e419]
    - paragraph [ref=e420]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e421]
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
     |       ^ Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-profitability-dashboard-loaded.png, writing actual.
  68 |         fullPage: fullPage ? true : undefined,
  69 |       });
  70 |     }
  71 |   }
  72 | }
  73 | 
```