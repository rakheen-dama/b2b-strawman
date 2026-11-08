# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-60-interest.spec.ts >> Day 60 — Interest Run & Investments >> Alice: Interest run — create for the last 30 days and calculate allocations at the 6.5% LPFF rate
- Location: e2e/tests/legal-lifecycle/day-60-interest.spec.ts:97:7

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByTestId('interest-run-wizard').getByTestId('allocations-table').getByRole('row').filter({ hasText: 'Moroka Family Trust mw72qd7rb4f2' })
Expected pattern: /(?<!\d)(?<!\d[\s\u00a0\u202f])7[\s\u00a0\u202f]?790,32/
Received string:  "Moroka Family Trust mw72qd7rb4f2R 8 064,52R 44,52LPFF arrangement rate 50.00%R 22,26R 22,26"
Timeout: 5000ms

Call log:
  - Expect "toContainText" with timeout 5000ms
  - waiting for getByTestId('interest-run-wizard').getByTestId('allocations-table').getByRole('row').filter({ hasText: 'Moroka Family Trust mw72qd7rb4f2' })
    9 × locator resolved to <tr class="border-b border-slate-100 last:border-0 dark:border-slate-800">…</tr>
      - unexpected value "Moroka Family Trust mw72qd7rb4f2R 8 064,52R 44,52LPFF arrangement rate 50.00%R 22,26R 22,26"

```

# Page snapshot

```yaml
- generic:
  - link:
    - /url: "#main-content"
    - text: Skip to content
  - generic:
    - complementary:
      - generic:
        - generic: Kazi
      - generic:
        - generic: E2E Test Organization
      - button:
        - img
        - text: Search...
        - generic: ⌘K
      - navigation:
        - generic:
          - button [expanded]:
            - generic: Today
            - img
          - generic:
            - link:
              - /url: /org/e2e-test-org/dashboard
              - img
              - text: Home
            - link:
              - /url: /org/e2e-test-org/my-work
              - img
              - text: My work
            - link:
              - /url: /org/e2e-test-org/diary
              - img
              - text: Diary
        - generic:
          - button [expanded]:
            - generic: Matters
            - img
          - generic:
            - link:
              - /url: /org/e2e-test-org/matters
              - img
              - text: Matters
            - link:
              - /url: /org/e2e-test-org/matters/new
              - img
              - text: New matter
            - link:
              - /url: /org/e2e-test-org/conflict-check
              - img
              - text: Conflict check
            - link:
              - /url: /org/e2e-test-org/legal/adverse-parties
              - img
              - text: Adverse parties
            - link:
              - /url: /org/e2e-test-org/ai/reviews
              - img
              - text: AI reviews
        - generic:
          - button [expanded]:
            - generic: Clients
            - img
          - generic:
            - link:
              - /url: /org/e2e-test-org/clients
              - img
              - text: Clients
            - link:
              - /url: /org/e2e-test-org/compliance
              - img
              - text: FICA & compliance
            - link:
              - /url: /org/e2e-test-org/compliance/requests
              - img
              - text: Data requests
        - generic:
          - button:
            - generic: Fees
            - img
        - generic:
          - button:
            - generic: Trust
            - img
        - generic:
          - button:
            - generic: Firm
            - img
      - generic:
        - link:
          - /url: /platform-admin/access-requests
          - img
          - text: Platform Admin
      - generic:
        - link:
          - /url: /org/e2e-test-org/notifications
          - img
          - text: Notifications
      - generic:
        - generic: AO
        - generic:
          - paragraph: Alice Owner
          - paragraph: alice@e2e-test.local
    - generic:
      - banner:
        - navigation:
          - link:
            - /url: /org/e2e-test-org/dashboard
            - text: E2E Test Organization
          - generic:
            - img
            - link:
              - /url: /org/e2e-test-org/trust-accounting
              - text: Trust accounting
          - generic:
            - img
            - generic: Interest
        - generic:
          - generic:
            - generic: e2e-test-org
          - generic:
            - button: AO
          - generic:
            - button "Notifications":
              - img
              - generic: 5 unread notifications
              - generic: "5"
      - main:
        - generic:
          - generic:
            - generic:
              - generic:
                - generic:
                  - heading [level=1]: Interest
                  - paragraph: Interest runs and LPFF rate management
              - generic:
                - generic:
                  - generic:
                    - generic:
                      - generic: Interest Runs
                      - generic: 1 run found
                    - button: New Interest Run
                - generic:
                  - generic:
                    - table:
                      - rowgroup:
                        - row:
                          - columnheader: Period
                          - columnheader: Total Interest
                          - columnheader: LPFF Share
                          - columnheader: Client Share
                          - columnheader: Status
                          - columnheader: Posted Date
                          - columnheader: Actions
                      - rowgroup:
                        - row:
                          - cell: 09 Oct 2026 — 08 Nov 2026
                          - cell: R 52,53
                          - cell: R 26,27
                          - cell: R 26,26
                          - cell:
                            - generic: Draft
                          - cell: —
                          - cell:
                            - generic:
                              - generic:
                                - button: Review
                                - button: Delete draft
                              - paragraph: Awaiting approval by another member
              - generic:
                - generic:
                  - generic:
                    - generic:
                      - generic: LPFF Rate History
                      - generic: 1 rate configured
                    - button: Add Rate
                - generic:
                  - generic:
                    - table:
                      - rowgroup:
                        - row:
                          - columnheader: Effective From
                          - columnheader: Rate %
                          - columnheader: LPFF Share %
                      - rowgroup:
                        - row:
                          - cell: 08 Nov 2025
                          - cell: 6.50%
                          - cell: 50.00%
  - generic:
    - heading "Command Palette" [level=2]
    - paragraph: Search pages and settings
  - region "Notifications alt+T"
  - alert
  - dialog "New Interest Run" [active] [ref=e2]:
    - generic [ref=e3]:
      - generic [ref=e4]:
        - heading "New Interest Run" [level=2] [ref=e5]
        - paragraph [ref=e6]: Step 2 of 4 — Calculate
      - generic [ref=e7]:
        - generic [ref=e8]:
          - generic [ref=e9]: "1"
          - generic [ref=e10]: Create
        - generic [ref=e12]:
          - generic [ref=e13]: "2"
          - generic [ref=e14]: Calculate
        - generic [ref=e16]:
          - generic [ref=e17]: "3"
          - generic [ref=e18]: Approve
        - generic [ref=e20]:
          - generic [ref=e21]: "4"
          - generic [ref=e22]: Post
      - generic [ref=e23]:
        - generic [ref=e25]:
          - generic [ref=e26]:
            - paragraph [ref=e27]: "Period: 2026-10-09 to 2026-11-08"
            - paragraph [ref=e28]: "Run ID: 76392ac5-1802-45af-b4e9-8183a76554ee"
          - generic [ref=e29]: Draft
        - table [ref=e31]:
          - rowgroup [ref=e32]:
            - row "Client Avg Daily Balance Gross Interest LPFF Rate LPFF Share Client Share" [ref=e33]:
              - columnheader "Client" [ref=e34]
              - columnheader "Avg Daily Balance" [ref=e35]
              - columnheader "Gross Interest" [ref=e36]
              - columnheader "LPFF Rate" [ref=e37]
              - columnheader "LPFF Share" [ref=e38]
              - columnheader "Client Share" [ref=e39]
          - rowgroup [ref=e40]:
            - row "Moroka Family Trust mw72qd7rb4f2 R 8 064,52 R 44,52 LPFF arrangement rate 50.00% R 22,26 R 22,26" [ref=e41]:
              - cell "Moroka Family Trust mw72qd7rb4f2" [ref=e42]
              - cell "R 8 064,52" [ref=e43]
              - cell "R 44,52" [ref=e44]
              - cell "LPFF arrangement rate 50.00%" [ref=e45]
              - cell "R 22,26" [ref=e46]
              - cell "R 22,26" [ref=e47]
            - row "QuickCollect Services mw72qd7rb4f2 R 1 451,61 R 8,01 LPFF arrangement rate 50.00% R 4,01 R 4,00" [ref=e48]:
              - cell "QuickCollect Services mw72qd7rb4f2" [ref=e49]
              - cell "R 1 451,61" [ref=e50]
              - cell "R 8,01" [ref=e51]
              - cell "LPFF arrangement rate 50.00%" [ref=e52]
              - cell "R 4,01" [ref=e53]
              - cell "R 4,00" [ref=e54]
        - generic [ref=e55]:
          - button "Cancel" [ref=e56]
          - button "Review" [ref=e57]
      - button "Close" [ref=e58]:
        - img
        - generic [ref=e59]: Close
```

# Test source

```ts
  34  |   recordValue,
  35  |   siphoClient,
  36  |   siphoMatter,
  37  | } from "../../helpers/legal-lifecycle";
  38  | 
  39  | const SIPHO_SUMMONS_TIME = "Draft combined summons — RAF claim";
  40  | 
  41  | async function openInvestments(page: Page): Promise<void> {
  42  |   await page.goto(`${BASE}/trust-accounting/investments`);
  43  |   await expect(page.getByTestId("investments-page")).toBeVisible({ timeout: 15_000 });
  44  |   await expect(page.getByRole("heading", { level: 1, name: "Investments" })).toBeVisible();
  45  | }
  46  | 
  47  | /** Places a trust investment for a client (the dialog takes the client's raw UUID). */
  48  | async function placeInvestment(
  49  |   page: Page,
  50  |   opts: {
  51  |     clientId: string;
  52  |     institution: string;
  53  |     accountNumber: string;
  54  |     principal: string;
  55  |     ratePercent: string;
  56  |     maturityDate?: string;
  57  |     basis: "Firm (surplus trust funds)" | "Client instruction";
  58  |     notes: string;
  59  |   }
  60  | ): Promise<void> {
  61  |   await openInvestments(page);
  62  |   await page.getByRole("button", { name: "Place Investment" }).click();
  63  |   const dialog = page.getByTestId("place-investment-dialog");
  64  |   await expect(dialog.getByRole("heading", { name: "Place Investment" })).toBeVisible();
  65  |   await dialog.getByLabel("Client ID").fill(opts.clientId);
  66  |   await dialog.getByLabel("Institution").fill(opts.institution);
  67  |   await dialog.getByLabel("Account Number").fill(opts.accountNumber);
  68  |   await dialog.getByLabel("Principal").fill(opts.principal);
  69  |   await dialog.getByLabel("Interest Rate %").fill(opts.ratePercent);
  70  |   if (opts.maturityDate) {
  71  |     await dialog.getByLabel("Maturity Date (optional)").fill(opts.maturityDate);
  72  |   }
  73  |   await dialog.getByLabel("Notes (optional)").fill(opts.notes);
  74  |   await dialog.getByRole("radio", { name: opts.basis }).check();
  75  |   await expect(dialog.getByTestId("investment-basis-help")).toHaveText(
  76  |     opts.basis === "Client instruction" ? /5%|statutory/i : /arrangement/i
  77  |   );
  78  |   // Pressed from the keyboard: on the suite's 1280×720 viewport the dialog is taller than the
  79  |   // screen and does not scroll, so its "Place Investment" button sits off-screen (follow-up:
  80  |   // components/ui/dialog.tsx:56-70 has no max-height / overflow-y-auto)
  81  |   await dialog.getByRole("button", { name: "Place Investment" }).press("Enter");
  82  |   await expect(dialog).toBeHidden({ timeout: 15_000 });
  83  | }
  84  | 
  85  | test.describe.serial("Day 60 — Interest Run & Investments", () => {
  86  |   // ── 60.1-60.10: Interest run with LPFF split ─────────────────────
  87  |   test("Alice: Navigate to Trust Interest page", async ({ page }) => {
  88  |     await loginAs(page, "alice");
  89  |     await page.goto(`${BASE}/trust-accounting/interest`);
  90  |     await expect(page.getByTestId("interest-page")).toBeVisible({ timeout: 15_000 });
  91  |     await expect(page.getByRole("heading", { level: 1, name: "Interest" })).toBeVisible();
  92  |     await expect(page.getByRole("button", { name: "New Interest Run" })).toBeVisible();
  93  |     // Day 0's LPFF rate is in force
  94  |     await expect(page.getByTestId("lpff-rates-table")).toContainText("6.50%");
  95  |   });
  96  | 
  97  |   test("Alice: Interest run — create for the last 30 days and calculate allocations at the 6.5% LPFF rate", async ({
  98  |     page,
  99  |   }) => {
  100 |     await loginAs(page, "alice");
  101 |     await page.goto(`${BASE}/trust-accounting/interest`);
  102 |     await page.getByRole("button", { name: "New Interest Run" }).click();
  103 |     const wizard = page.getByTestId("interest-run-wizard");
  104 |     await expect(wizard).toContainText("Step 1 of 4");
  105 |     const periodStart = isoDate(-30);
  106 |     const periodEnd = isoDate();
  107 |     await wizard.getByLabel("Period Start").fill(periodStart);
  108 |     await wizard.getByLabel("Period End").fill(periodEnd);
  109 |     await wizard.getByRole("button", { name: "Create Run" }).click();
  110 | 
  111 |     await expect(wizard).toContainText("Step 2 of 4", { timeout: 15_000 });
  112 |     await expect(wizard).toContainText(`Period: ${periodStart} to ${periodEnd}`);
  113 |     await wizard.getByRole("button", { name: "Calculate Interest" }).click();
  114 | 
  115 |     // One allocation per client with a trust balance in the period (31 days, both funded today):
  116 |     // Moroka R241,500 and QuickCollect R45,000 for one day at 6.5% p.a. (actual/365), split with
  117 |     // the LPFF under the firm's arrangement
  118 |     const allocations = wizard.getByTestId("allocations-table");
  119 |     await expect(allocations).toBeVisible({ timeout: 15_000 });
  120 |     for (const header of ["Avg Daily Balance", "Gross Interest", "LPFF Share", "Client Share"]) {
  121 |       await expect(allocations.getByRole("columnheader", { name: header })).toBeVisible();
  122 |     }
  123 |     await expect(
  124 |       allocations.getByRole("columnheader", { name: "Client", exact: true })
  125 |     ).toBeVisible();
  126 |     for (const [key, name, avgDaily, gross] of [
  127 |       ["moroka", morokaClient(), "7 790,32", "43,01"],
  128 |       ["qc", qcClient(), "1 451,61", "8,01"],
  129 |     ] as const) {
  130 |       // G89-F10: the row names the client (never its UUID) and the LPFF arrangement share (50%)
  131 |       const row = allocations.getByRole("row").filter({ hasText: name });
  132 |       await expect(row, `${key} allocation`).toHaveCount(1);
  133 |       await expect(row).not.toContainText(clientId(key));
> 134 |       await expect(row).toContainText(randPattern(avgDaily));
      |                         ^ Error: expect(locator).toContainText(expected) failed
  135 |       await expect(row.getByRole("cell").nth(2)).toContainText(randPattern(gross));
  136 |       await expect(row).toContainText("LPFF arrangement rate 50.00%");
  137 |     }
  138 | 
  139 |     // The run's creator reviews (never approves) her own run — 630B hides self-approval via
  140 |     // MyCapabilitiesResponse.memberId
  141 |     await expect(wizard.getByRole("button", { name: "Approve", exact: true })).toHaveCount(0);
  142 |     await wizard.getByRole("button", { name: "Review", exact: true }).click();
  143 |     await expect(wizard).toContainText("Step 3 of 4");
  144 |     await expect(wizard.getByRole("heading", { name: "Interest Run Totals" })).toBeVisible();
  145 |     for (const total of ["Total Interest", "LPFF Share", "Client Share"]) {
  146 |       await expect(wizard.getByText(total, { exact: true })).toBeVisible();
  147 |     }
  148 | 
  149 |     // Screenshot: interest run with LPFF split
  150 |     await captureScreenshot(page, "day-60-interest-lpff-calculated");
  151 |     await captureScreenshot(page, "interest-run-lpff-split", { curated: true });
  152 | 
  153 |     // The run's creator cannot approve it: Approve Run is disabled client-side and the backend
  154 |     // still refuses self-approval (InterestService.approveInterestRun)
  155 |     await expect(wizard.getByRole("button", { name: "Approve Run" })).toBeDisabled();
  156 |     await expect(wizard.getByTestId("interest-run-awaiting-approval")).toHaveText(
  157 |       "Awaiting approval by another member"
  158 |     );
  159 |     await page.keyboard.press("Escape");
  160 |     await expect(wizard).toBeHidden();
  161 | 
  162 |     // The calculated run persists as a Draft awaiting a second member (G88-F39)
  163 |     await page.reload();
  164 |     const run = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
  165 |     await expect(run).toHaveCount(1, { timeout: 15_000 });
  166 |     await expect(run.getByTestId("run-status")).toHaveText("Draft");
  167 |     await expect(run.getByRole("button", { name: "Review", exact: true })).toBeVisible();
  168 |     await expect(run.getByRole("button", { name: "Review & approve" })).toHaveCount(0);
  169 |     await expect(run).toContainText("Awaiting approval by another member");
  170 |     await expect(run.getByRole("button", { name: "Delete draft" })).toBeVisible();
  171 |   });
  172 | 
  173 |   test("Bob: Review & approve Alice's interest run as the second member", async ({ page }) => {
  174 |     await loginAs(page, "bob");
  175 |     await page.goto(`${BASE}/trust-accounting/interest`);
  176 |     const run = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
  177 |     await expect(run).toHaveCount(1, { timeout: 15_000 });
  178 |     await expect(run.getByTestId("run-status")).toHaveText("Draft");
  179 |     await run.getByRole("button", { name: "Review & approve" }).click();
  180 |     const wizard = page.getByTestId("interest-run-wizard");
  181 |     await expect(wizard).toContainText("Step 3 of 4", { timeout: 15_000 });
  182 |     await wizard.getByRole("button", { name: "Approve Run" }).click();
  183 |     await expect(wizard).toContainText("Step 4 of 4", { timeout: 15_000 });
  184 |     await expect(wizard.getByRole("heading", { name: "Approved Interest Run" })).toBeVisible();
  185 |     await page.keyboard.press("Escape");
  186 |     await expect(wizard).toBeHidden();
  187 | 
  188 |     // The table behind the wizard refreshed: Approved, no Delete draft, Post offered
  189 |     await expect(run.getByTestId("run-status")).toHaveText("Approved", { timeout: 15_000 });
  190 |     await expect(run.getByRole("button", { name: "Delete draft" })).toHaveCount(0);
  191 |     await expect(run.getByRole("button", { name: "Post", exact: true })).toBeVisible();
  192 |   });
  193 | 
  194 |   test("Alice: Post the approved interest run to the client ledgers", async ({ page }) => {
  195 |     await loginAs(page, "alice");
  196 |     await page.goto(`${BASE}/trust-accounting/interest`);
  197 |     const run = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
  198 |     await expect(run).toHaveCount(1, { timeout: 15_000 });
  199 |     await expect(run.getByTestId("run-status")).toHaveText("Approved");
  200 |     await run.getByRole("button", { name: "Post", exact: true }).click();
  201 |     const wizard = page.getByTestId("interest-run-wizard");
  202 |     await expect(wizard).toContainText("Step 4 of 4", { timeout: 15_000 });
  203 |     await wizard.getByRole("button", { name: "Post to Ledger" }).click();
  204 |     await expect(wizard).toBeHidden({ timeout: 15_000 });
  205 | 
  206 |     // The run is Posted with a posted date and no further actions …
  207 |     await expect(run.getByTestId("run-status")).toHaveText("Posted", { timeout: 15_000 });
  208 |     await expect(run.getByTestId("run-posted-at")).not.toHaveText("—");
  209 |     await expect(run.getByRole("button")).toHaveCount(0);
  210 |     // … and each allocated client (Moroka, QuickCollect) has an Interest Credit on the trust ledger
  211 |     await openTrustTransactions(page);
  212 |     await expect(
  213 |       page.locator("main tbody tr").filter({ hasText: "Interest Credit" }),
  214 |       "one Interest Credit per allocated client"
  215 |     ).toHaveCount(2, { timeout: 15_000 });
  216 |   });
  217 | 
  218 |   // ── 60.11-60.17: Investment — Moroka R200,000 Fixed Deposit ───────
  219 |   test("Alice: Navigate to Investment Register", async ({ page }) => {
  220 |     await loginAs(page, "alice");
  221 |     await openInvestments(page);
  222 |     await expect(page.getByRole("button", { name: "Place Investment" })).toBeVisible();
  223 |     await expect(page.getByTestId("empty-investments")).toBeVisible();
  224 |   });
  225 | 
  226 |   test("Alice: Place investment — Moroka R200,000 Standard Bank Fixed Deposit (s86(3))", async ({
  227 |     page,
  228 |   }) => {
  229 |     await loginAs(page, "alice");
  230 |     await placeInvestment(page, {
  231 |       clientId: clientId("moroka"),
  232 |       institution: "Standard Bank",
  233 |       accountNumber: "FD-0987654321",
  234 |       principal: "200000",
```