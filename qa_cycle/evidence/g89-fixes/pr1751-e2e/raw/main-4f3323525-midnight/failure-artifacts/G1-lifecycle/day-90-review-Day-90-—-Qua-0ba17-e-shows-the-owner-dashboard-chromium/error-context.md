# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Quarter Review >> Alice: Home shows the owner dashboard
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:217:7

# Error details

```
Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-dashboard-overview-active.png, writing actual.
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
              - img [ref=e26]
              - text: Home
            - link "My work" [ref=e29] [cursor=pointer]:
              - /url: /org/e2e-test-org/my-work
              - img [ref=e30]
              - text: My work
            - link "Diary" [ref=e33] [cursor=pointer]:
              - /url: /org/e2e-test-org/diary
              - img [ref=e34]
              - text: Diary
        - generic [ref=e37]:
          - button "Matters" [expanded] [ref=e38]:
            - generic [ref=e39]: Matters
            - img [ref=e40]
          - generic [ref=e42]:
            - link "Matters" [ref=e43] [cursor=pointer]:
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
            - generic "Home" [ref=e127]
        - generic [ref=e128]:
          - generic [ref=e130]: e2e-test-org
          - button "AO" [ref=e132]
          - button "Notifications" [ref=e134]:
            - img
            - generic [ref=e135]: 11 unread notifications
            - generic [ref=e136]: "11"
      - main [ref=e137]:
        - generic [ref=e140]:
          - generic [ref=e141]:
            - heading "Home" [level=1] [ref=e142]
            - paragraph [ref=e143]: What needs you today
          - generic [ref=e144]:
            - generic [ref=e146]:
              - generic [ref=e147]:
                - generic [ref=e148]: Today's diary
                - link "Open diary" [ref=e150] [cursor=pointer]:
                  - /url: /org/e2e-test-org/diary?view=day
              - paragraph [ref=e152]: Nothing in the diary today
            - generic [ref=e154]:
              - generic [ref=e155]:
                - generic [ref=e156]: Unbilled WIP
                - link "Open unbilled work" [ref=e158] [cursor=pointer]:
                  - /url: /org/e2e-test-org/invoices/unbilled
              - generic [ref=e159]:
                - generic [ref=e160]:
                  - paragraph [ref=e161]: R 16 250,00
                  - paragraph [ref=e162]: 6h 30m of unbilled time
                - list "Unbilled WIP by fee earner" [ref=e163]:
                  - listitem [ref=e164]:
                    - generic [ref=e165]:
                      - generic [ref=e166]: Alice Owner
                      - generic [ref=e167]: Oldest from 08 Nov 2026
                    - generic [ref=e168]: 6h 30m
                    - generic [ref=e169]: R 16 250,00
                - paragraph [ref=e170]:
                  - generic [ref=e171]: Disbursements not yet billed
                  - generic [ref=e172]: R 0,00
          - generic [ref=e173]:
            - generic [ref=e175]:
              - generic [ref=e176]:
                - generic [ref=e177]: Debtors
                - link "Open collections" [ref=e179] [cursor=pointer]:
                  - /url: /org/e2e-test-org/invoices/collections
              - generic [ref=e180]:
                - generic [ref=e181]:
                  - paragraph [ref=e182]: "3"
                  - paragraph [ref=e183]: clients owe fees
                - list "Largest debtors" [ref=e184]:
                  - listitem [ref=e185]:
                    - generic [ref=e186]:
                      - generic [ref=e187]:
                        - generic [ref=e188]: Acme Corp
                        - generic [ref=e189]: Not yet overdue
                      - generic [ref=e190]: R 8 625,00
                    - generic [ref=e191]:
                      - 'generic "Current: 8625" [ref=e192]'
                      - 'generic "30 days: 0"'
                      - 'generic "60 days: 0"'
                      - 'generic "90+ days: 0"'
                    - generic [ref=e193]: Current R 8 625,00, 30 days R 0,00, 60 days R 0,00, 90+ days R 0,00
                  - listitem [ref=e194]:
                    - generic [ref=e195]:
                      - generic [ref=e196]:
                        - generic [ref=e197]: Sipho Ndlovu mw6z81829737
                        - generic [ref=e198]: Not yet overdue
                      - generic [ref=e199]: R 5 482,63
                    - generic [ref=e200]:
                      - 'generic "Current: 5482.63" [ref=e201]'
                      - 'generic "30 days: 0"'
                      - 'generic "60 days: 0"'
                      - 'generic "90+ days: 0"'
                    - generic [ref=e202]: Current R 5 482,63, 30 days R 0,00, 60 days R 0,00, 90+ days R 0,00
                  - listitem [ref=e203]:
                    - generic [ref=e204]:
                      - generic [ref=e205]:
                        - generic [ref=e206]: Moroka Family Trust mw6z81829737
                        - generic [ref=e207]: Not yet overdue
                      - generic [ref=e208]: R 125,00
                    - generic [ref=e209]:
                      - 'generic "Current: 125" [ref=e210]'
                      - 'generic "30 days: 0"'
                      - 'generic "60 days: 0"'
                      - 'generic "90+ days: 0"'
                    - generic [ref=e211]: Current R 125,00, 30 days R 0,00, 60 days R 0,00, 90+ days R 0,00
                - list "Aging legend" [ref=e212]:
                  - listitem [ref=e213]: Current
                  - listitem [ref=e215]: 30 days
                  - listitem [ref=e217]: 60 days
                  - listitem [ref=e219]: 90+ days
            - generic [ref=e222]:
              - generic [ref=e223]:
                - generic [ref=e224]: Trust position
                - link "Open reconciliation" [ref=e226] [cursor=pointer]:
                  - /url: /org/e2e-test-org/trust-accounting/reconciliation
              - generic [ref=e227]:
                - generic [ref=e228]:
                  - paragraph [ref=e229]: "-R 25,52"
                  - generic [ref=e230]: Difference -R 25,52
                - paragraph [ref=e231]:
                  - text: Mathebula & Partners Trust Account
                  - generic [ref=e232]: ", Standard Bank"
                - generic [ref=e233]:
                  - term [ref=e234]: Bank cashbook
                  - definition [ref=e235]: R 286 499,98
                  - term [ref=e236]: Client ledgers
                  - definition [ref=e237]: R 286 525,50
                  - term [ref=e238]: Last reconciliation
                  - definition [ref=e239]: Never reconciled
                  - term [ref=e240]: Awaiting approval
                  - definition [ref=e241]: "2"
          - generic [ref=e242]:
            - generic [ref=e244]:
              - generic [ref=e245]:
                - generic [ref=e246]: FICA outstanding
                - generic [ref=e247]:
                  - link "View all" [ref=e248] [cursor=pointer]:
                    - /url: /org/e2e-test-org/clients?view=system:fica-outstanding
                  - link "Open compliance" [ref=e249] [cursor=pointer]:
                    - /url: /org/e2e-test-org/compliance
              - generic [ref=e250]:
                - generic [ref=e251]:
                  - paragraph [ref=e252]: "3"
                  - paragraph [ref=e253]: of 5 active clients without completed FICA
                - list "Clients with FICA outstanding" [ref=e254]:
                  - listitem [ref=e255]:
                    - link "Apex Holdings mw6z81829737 Not started 0 days" [ref=e256] [cursor=pointer]:
                      - /url: /org/e2e-test-org/clients/f5931125-694a-4a45-b546-13c99fe5c303
                      - generic [ref=e257]: Apex Holdings mw6z81829737
                      - generic [ref=e258]: Not started
                      - generic [ref=e259]: 0 days
                  - listitem [ref=e260]:
                    - link "Moroka Family Trust mw6z81829737 In progress 0 days" [ref=e261] [cursor=pointer]:
                      - /url: /org/e2e-test-org/clients/5e7bf3f7-4494-400d-9455-99aae86af59f
                      - generic [ref=e262]: Moroka Family Trust mw6z81829737
                      - generic [ref=e263]: In progress
                      - generic [ref=e264]: 0 days
                  - listitem [ref=e265]:
                    - link "QuickCollect Services mw6z81829737 Not started 0 days" [ref=e266] [cursor=pointer]:
                      - /url: /org/e2e-test-org/clients/205001be-7324-486c-82e7-3e01778c5cc7
                      - generic [ref=e267]: QuickCollect Services mw6z81829737
                      - generic [ref=e268]: Not started
                      - generic [ref=e269]: 0 days
              - link "Incomplete profiles" [ref=e271] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients?showIncomplete=true
            - generic [ref=e273]:
              - generic [ref=e275]: Matter Health
              - generic [ref=e276]:
                - generic [ref=e277]:
                  - button "All" [ref=e278]
                  - button "At Risk" [ref=e279]
                  - button "Over estimate" [ref=e280]
                - generic [ref=e281]:
                  - columnheader "Sort by matter name" [ref=e282]: Matter
                  - columnheader "Sort by health status" [ref=e283]:
                    - text: Status
                    - img [ref=e284]
                  - generic [ref=e286]: Progress
                  - generic [ref=e287]: Hours
                  - generic [ref=e288]: Tasks
                - button "Website Redesign Acme Corp Unknown 0% 0h 0/0" [ref=e290]:
                  - generic [ref=e291]:
                    - paragraph [ref=e292]: Website Redesign
                    - paragraph [ref=e293]: Acme Corp
                  - generic "Unknown" [ref=e295]
                  - generic [ref=e299]: 0%
                  - generic [ref=e300]: 0h
                  - generic [ref=e301]: 0/0
              - button "View all matters →" [ref=e303]
            - generic [ref=e305]:
              - generic [ref=e307]: Recent Activity
              - generic [ref=e308]:
                - generic [ref=e309]:
                  - generic [ref=e310]: AO
                  - generic [ref=e312]:
                    - img [ref=e313]
                    - paragraph [ref=e315]: Alice Owner generated a statement of account "statement-of-account-website-redesign-2026-11-07.pdf"
                  - generic [ref=e316]: 2 minutes ago
                - generic [ref=e317]:
                  - generic [ref=e318]: AO
                  - generic [ref=e320]:
                    - img [ref=e321]
                    - paragraph [ref=e323]: Information request REQ-0001 sent to Acme Corp
                  - generic [ref=e324]: 2 minutes ago
                - generic [ref=e325]:
                  - generic [ref=e326]: AO
                  - generic [ref=e328]:
                    - img [ref=e329]
                    - paragraph [ref=e331]: Alice Owner created information request REQ-0001
                  - generic [ref=e332]: 2 minutes ago
                - generic [ref=e333]:
                  - generic [ref=e334]: AO
                  - generic [ref=e336]:
                    - img [ref=e337]
                    - paragraph [ref=e339]: Alice Owner sent fee note INV-0001
                  - generic [ref=e340]: 2 minutes ago
                - generic [ref=e341]:
                  - generic [ref=e342]: AO
                  - generic [ref=e344]:
                    - img [ref=e345]
                    - paragraph [ref=e347]: Alice Owner approved fee note INV-0001
                  - generic [ref=e348]: 2 minutes ago
                - generic [ref=e349]:
                  - generic [ref=e350]: AO
                  - generic [ref=e352]:
                    - img [ref=e353]
                    - paragraph [ref=e355]: Alice Owner opened the matter "Website Redesign"
                  - generic [ref=e356]: 2 minutes ago
              - button "View all activity →" [ref=e358]
            - generic [ref=e360]:
              - generic [ref=e362]:
                - img [ref=e363]
                - text: Sensitive events
                - generic [ref=e365]: Last 7 days
              - generic [ref=e366]:
                - generic [ref=e367]:
                  - generic [ref=e368]:
                    - generic [ref=e369]: NOTICE
                    - generic [ref=e370]: "4"
                  - generic [ref=e371]:
                    - generic [ref=e372]: WARNING
                    - generic [ref=e373]: "0"
                  - generic [ref=e374]:
                    - generic [ref=e375]: CRITICAL
                    - generic [ref=e376]: "0"
                - paragraph [ref=e377]: No sensitive events in the last 7 days.
              - link "View all →" [ref=e379] [cursor=pointer]:
                - /url: /org/e2e-test-org/settings/audit-log?severities=WARNING%2CCRITICAL&preset=sensitive&from=2026-10-08T22%3A32%3A44.794Z
  - generic [ref=e380]:
    - heading "Command Palette" [level=2] [ref=e381]
    - paragraph [ref=e382]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e383]
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
     |       ^ Error: A snapshot doesn't exist at /Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/frontend/e2e/screenshots/legal-lifecycle/day-90-dashboard-overview-active.png, writing actual.
  68 |         fullPage: fullPage ? true : undefined,
  69 |       });
  70 |     }
  71 |   }
  72 | }
  73 | 
```