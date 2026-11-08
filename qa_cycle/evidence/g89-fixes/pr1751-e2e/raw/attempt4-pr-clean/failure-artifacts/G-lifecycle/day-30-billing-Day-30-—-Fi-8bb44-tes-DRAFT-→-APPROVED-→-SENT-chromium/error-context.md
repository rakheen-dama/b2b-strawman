# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-30-billing.spec.ts >> Day 30 — First Billing Cycle >> Alice: Fee note lifecycle — Sipho's and Apex's fee notes DRAFT → APPROVED → SENT
- Location: e2e/tests/legal-lifecycle/day-30-billing.spec.ts:282:7

# Error details

```
Error: expect(page).toHaveScreenshot(expected) failed

  9510 pixels (ratio 0.02 of all image pixels) are different.

  Snapshot: day-30-fee-note-list-mixed.png

Call log:
  - Expect "toHaveScreenshot(day-30-fee-note-list-mixed.png)" with timeout 5000ms
    - verifying given screenshot expectation
  - taking page screenshot
    - disabled all CSS animations
  - waiting for fonts to load...
  - fonts loaded
  - 9510 pixels (ratio 0.02 of all image pixels) are different.
  - waiting 100ms before taking screenshot
  - taking page screenshot
    - disabled all CSS animations
  - waiting for fonts to load...
  - fonts loaded
  - captured a stable screenshot
  - 9510 pixels (ratio 0.02 of all image pixels) are different.

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
            - generic "Fee notes" [ref=e126]
        - generic [ref=e127]:
          - generic [ref=e129]: e2e-test-org
          - button "AO" [ref=e131]
          - button "Notifications" [ref=e133]:
            - img
            - generic [ref=e134]: 8 unread notifications
            - generic [ref=e135]: "8"
      - main [ref=e136]:
        - generic [ref=e139]:
          - generic [ref=e140]:
            - generic [ref=e141]:
              - 'heading "Fee notes Help: Fee note lifecycle" [level=1] [ref=e142]':
                - text: Fee notes
                - 'button "Help: Fee note lifecycle" [ref=e143] [cursor=pointer]':
                  - img [ref=e144]
              - generic [ref=e147]: "6"
            - button "New fee note" [ref=e148]:
              - img
              - text: New fee note
          - generic [ref=e149]:
            - link "Fee notes" [ref=e150] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
            - link "Fee runs" [ref=e151] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices/billing-runs
          - generic [ref=e152]:
            - generic [ref=e153]:
              - paragraph [ref=e154]: Total outstanding
              - paragraph [ref=e155]: R 54 482,63
            - generic [ref=e156]:
              - paragraph [ref=e157]: Overdue
              - paragraph [ref=e158]: R 0,00
            - generic [ref=e159]:
              - paragraph [ref=e160]: Approved, not sent
              - paragraph [ref=e161]: R 0,00
            - generic [ref=e162]:
              - paragraph [ref=e163]: Paid This Month
              - paragraph [ref=e164]: R 0,00
          - generic [ref=e165]:
            - generic [ref=e166]: "Filter:"
            - link "All" [ref=e167] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices
            - link "Draft" [ref=e168] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=DRAFT
            - link "Approved" [ref=e169] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=APPROVED
            - link "Sent" [ref=e170] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=SENT
            - link "Partially Paid" [ref=e171] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=PARTIALLY_PAID
            - link "Paid" [ref=e172] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=PAID
            - link "Void" [ref=e173] [cursor=pointer]:
              - /url: /org/e2e-test-org/invoices?status=VOID
          - table [ref=e175]:
            - rowgroup [ref=e176]:
              - row "Fee note Client Status Issue Date Due Date Total Currency" [ref=e177]:
                - columnheader "Fee note" [ref=e178]
                - columnheader "Client" [ref=e179]
                - columnheader "Status" [ref=e180]
                - columnheader "Issue Date" [ref=e181]
                - columnheader "Due Date" [ref=e182]
                - columnheader "Total" [ref=e183]
                - columnheader "Currency" [ref=e184]
            - rowgroup [ref=e185]:
              - row "Draft QuickCollect Services mw73e6pff426 Draft — — R 1 610,00 ZAR" [ref=e186]:
                - cell "Draft" [ref=e187]:
                  - link "Draft" [ref=e188] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/3ed5431b-d45f-4728-969b-1d20b1253861
                - cell "QuickCollect Services mw73e6pff426" [ref=e189]
                - cell "Draft" [ref=e190]:
                  - generic [ref=e192]: Draft
                - cell "—" [ref=e193]
                - cell "—" [ref=e194]
                - cell "R 1 610,00" [ref=e195]
                - cell "ZAR" [ref=e196]
              - row "INV-0002 Moroka Family Trust mw73e6pff426 Partially Paid 8 Nov 2026 8 Dec 2026 R 8 625,00 Balance R 125,00 ZAR" [ref=e197]:
                - cell "INV-0002" [ref=e198]:
                  - link "INV-0002" [ref=e199] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/e5f142f5-225f-408f-a1f5-641316870b7a
                - cell "Moroka Family Trust mw73e6pff426" [ref=e200]
                - cell "Partially Paid" [ref=e201]:
                  - generic [ref=e203]: Partially Paid
                - cell "8 Nov 2026" [ref=e204]
                - cell "8 Dec 2026" [ref=e205]
                - cell "R 8 625,00 Balance R 125,00" [ref=e206]:
                  - text: R 8 625,00
                  - generic [ref=e207]: Balance R 125,00
                - cell "ZAR" [ref=e208]
              - row "INV-0004 Apex Holdings mw73e6pff426 Sent 8 Nov 2026 8 Dec 2026 R 40 250,00 ZAR" [ref=e209]:
                - cell "INV-0004" [ref=e210]:
                  - link "INV-0004" [ref=e211] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/1e446464-0086-4615-b8cf-a9214de7363f
                - cell "Apex Holdings mw73e6pff426" [ref=e212]
                - cell "Sent" [ref=e213]:
                  - generic [ref=e215]: Sent
                - cell "8 Nov 2026" [ref=e216]
                - cell "8 Dec 2026" [ref=e217]
                - cell "R 40 250,00" [ref=e218]
                - cell "ZAR" [ref=e219]
              - row "INV-0003 Sipho Ndlovu mw73e6pff426 Sent 8 Nov 2026 8 Dec 2026 R 5 482,63 ZAR" [ref=e220]:
                - cell "INV-0003" [ref=e221]:
                  - link "INV-0003" [ref=e222] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/cfc4643c-ee05-4599-9bbd-5fcbe8b0e0ab
                - cell "Sipho Ndlovu mw73e6pff426" [ref=e223]
                - cell "Sent" [ref=e224]:
                  - generic [ref=e226]: Sent
                - cell "8 Nov 2026" [ref=e227]
                - cell "8 Dec 2026" [ref=e228]
                - cell "R 5 482,63" [ref=e229]
                - cell "ZAR" [ref=e230]
              - row "INV-0001 Acme Corp Sent 8 Nov 2026 8 Dec 2026 R 8 625,00 ZAR" [ref=e231]:
                - cell "INV-0001" [ref=e232]:
                  - link "INV-0001" [ref=e233] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/a7c33961-dc5c-4595-a4f1-86417ca3280a
                - cell "Acme Corp" [ref=e234]
                - cell "Sent" [ref=e235]:
                  - generic [ref=e237]: Sent
                - cell "8 Nov 2026" [ref=e238]
                - cell "8 Dec 2026" [ref=e239]
                - cell "R 8 625,00" [ref=e240]
                - cell "ZAR" [ref=e241]
              - row "Draft Acme Corp Draft — — R 3 450,00 ZAR" [ref=e242]:
                - cell "Draft" [ref=e243]:
                  - link "Draft" [ref=e244] [cursor=pointer]:
                    - /url: /org/e2e-test-org/invoices/e6fe16fe-d89d-4fae-89ef-91c4e1717c3c
                - cell "Acme Corp" [ref=e245]
                - cell "Draft" [ref=e246]:
                  - generic [ref=e248]: Draft
                - cell "—" [ref=e249]
                - cell "—" [ref=e250]
                - cell "R 3 450,00" [ref=e251]
                - cell "ZAR" [ref=e252]
  - generic [ref=e253]:
    - heading "Command Palette" [level=2] [ref=e254]
    - paragraph [ref=e255]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e256]
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