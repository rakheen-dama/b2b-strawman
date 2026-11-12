# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-30-45-soa-writeoff.spec.ts >> Day 30-45 — Statement of Account + Write-off (Phase 67) >> [setup] Alice (Thandi): Log 2h on Sipho's matter — letter of demand
- Location: e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts:227:7

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: getByRole('tabpanel')
Expected pattern: /Letter of demand\s*2h/
Received string:  "FromToNo time tracked yetLog time on tasks to see matter time summaries here"
Timeout: 15000ms

Call log:
  - Expect "toContainText" with timeout 15000ms
  - waiting for getByRole('tabpanel')
    16 × locator resolved to <div tabindex="0" role="tabpanel" data-state="active" class="pt-6 outline-none" data-orientation="horizontal" id="radix-_r_1j_-content-time" aria-labelledby="radix-_r_1j_-trigger-time">…</div>
       - unexpected value "FromToNo time tracked yetLog time on tasks to see matter time summaries here"

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e3]:
    - generic [ref=e6]:
      - generic [ref=e7]:
        - generic [ref=e9]: Kazi
        - generic [ref=e12]: E2E Test Organization
        - list [ref=e13]:
          - listitem [ref=e14]:
            - button "Search, Command K" [ref=e15]:
              - img [ref=e16]
              - generic [ref=e19]: Search...
              - generic [ref=e20]: ⌘K
      - navigation "Main navigation" [ref=e22]:
        - generic [ref=e24]:
          - button "Today" [expanded] [ref=e25]:
            - text: Today
            - img [ref=e26]
          - list [ref=e30]:
            - listitem [ref=e31]:
              - link "Home" [ref=e32] [cursor=pointer]:
                - /url: /org/e2e-test-org/dashboard
                - img [ref=e33]
                - generic [ref=e36]: Home
            - listitem [ref=e37]:
              - link "My work" [ref=e38] [cursor=pointer]:
                - /url: /org/e2e-test-org/my-work
                - img [ref=e39]
                - generic [ref=e42]: My work
            - listitem [ref=e43]:
              - link "Diary" [ref=e44] [cursor=pointer]:
                - /url: /org/e2e-test-org/diary
                - img [ref=e45]
                - generic [ref=e47]: Diary
        - generic [ref=e49]:
          - button "Matters" [expanded] [ref=e50]:
            - text: Matters
            - img [ref=e51]
          - list [ref=e55]:
            - listitem [ref=e56]:
              - link "Matters" [ref=e57] [cursor=pointer]:
                - /url: /org/e2e-test-org/matters
                - img [ref=e58]
                - generic [ref=e62]: Matters
            - listitem [ref=e63]:
              - link "New matter" [ref=e64] [cursor=pointer]:
                - /url: /org/e2e-test-org/matters/new
                - img [ref=e65]
                - generic [ref=e66]: New matter
            - listitem [ref=e67]:
              - link "Conflict check" [ref=e68] [cursor=pointer]:
                - /url: /org/e2e-test-org/conflict-check
                - img [ref=e69]
                - generic [ref=e73]: Conflict check
            - listitem [ref=e74]:
              - link "Adverse parties" [ref=e75] [cursor=pointer]:
                - /url: /org/e2e-test-org/legal/adverse-parties
                - img [ref=e76]
                - generic [ref=e81]: Adverse parties
            - listitem [ref=e82]:
              - link "AI reviews" [ref=e83] [cursor=pointer]:
                - /url: /org/e2e-test-org/ai/reviews
                - img [ref=e84]
                - generic [ref=e87]: AI reviews
        - generic [ref=e89]:
          - button "Clients" [expanded] [ref=e90]:
            - text: Clients
            - img [ref=e91]
          - list [ref=e95]:
            - listitem [ref=e96]:
              - link "Clients" [ref=e97] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients
                - img [ref=e98]
                - generic [ref=e102]: Clients
            - listitem [ref=e103]:
              - link "FICA & compliance" [ref=e104] [cursor=pointer]:
                - /url: /org/e2e-test-org/compliance
                - img [ref=e105]
                - generic [ref=e108]: FICA & compliance
            - listitem [ref=e109]:
              - link "Data requests" [ref=e110] [cursor=pointer]:
                - /url: /org/e2e-test-org/compliance/requests
                - img [ref=e111]
                - generic [ref=e114]: Data requests
        - button "Fees" [ref=e117]:
          - text: Fees
          - img [ref=e118]
        - button "Trust" [ref=e122]:
          - text: Trust
          - img [ref=e123]
        - button "Firm" [ref=e127]:
          - text: Firm
          - img [ref=e128]
      - generic [ref=e130]:
        - list [ref=e131]:
          - listitem [ref=e132]:
            - link "Platform Admin" [ref=e133] [cursor=pointer]:
              - /url: /platform-admin/access-requests
              - img [ref=e134]
              - generic [ref=e136]: Platform Admin
        - list [ref=e137]:
          - listitem [ref=e138]:
            - link "Notifications" [ref=e139] [cursor=pointer]:
              - /url: /org/e2e-test-org/notifications
              - img [ref=e140]
              - generic [ref=e143]: Notifications
        - generic [ref=e144]:
          - generic [ref=e145]: AO
          - generic [ref=e146]:
            - paragraph [ref=e147]: Alice Owner
            - paragraph [ref=e148]: alice@e2e-test.local
    - generic [ref=e149]:
      - banner [ref=e150]:
        - button "Toggle menu" [ref=e151]:
          - img
          - generic [ref=e152]: Toggle menu
        - navigation "Breadcrumb" [ref=e153]:
          - link "E2E Test Organization" [ref=e154] [cursor=pointer]:
            - /url: /org/e2e-test-org/dashboard
          - generic [ref=e155]:
            - img [ref=e156]
            - link "Matters" [ref=e158] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
          - generic [ref=e159]:
            - img [ref=e160]
            - generic "Ndlovu v RAF — Depth II mwdhz4js0790" [ref=e162]
        - generic [ref=e163]:
          - button "Search, Command K" [ref=e164]:
            - img [ref=e165]
            - generic [ref=e168]: Search
            - generic: ⌘K
          - button "Notifications" [ref=e170]:
            - img
            - generic [ref=e171]: 4 unread notifications
            - generic [ref=e172]: "4"
          - generic [ref=e174]: e2e-test-org
          - button "AO" [ref=e176]
      - main [ref=e177]:
        - generic [ref=e180]:
          - generic [ref=e181]:
            - link "Back to Matters" [ref=e182] [cursor=pointer]:
              - /url: /org/e2e-test-org/matters
              - img [ref=e183]
              - text: Back to Matters
            - generic [ref=e185]:
              - button "Generate Document" [ref=e186]:
                - img
                - text: Generate Document
                - img
              - button "Generate Statement of Account" [ref=e187]:
                - img
                - text: Generate Statement of Account
              - button "More actions" [ref=e188]:
                - img
                - generic [ref=e189]: More actions
          - generic [ref=e191]:
            - generic [ref=e192]:
              - heading "Ndlovu v RAF — Depth II mwdhz4js0790" [level=1] [ref=e193]
              - generic [ref=e194]:
                - generic [ref=e195]: Active
                - generic [ref=e196]: LITIGATION
              - link "Sipho Ndlovu mwdhz4js0790" [ref=e198] [cursor=pointer]:
                - /url: /org/e2e-test-org/clients/30ededca-d755-4a73-b0de-b59cca904c7c
            - generic [ref=e199]:
              - button "Close Matter" [ref=e200]:
                - img
                - text: Close Matter
              - button "Complete Matter" [ref=e201]:
                - img
                - text: Complete Matter
              - button [ref=e202]:
                - img
          - generic [ref=e203]:
            - tablist [ref=e204]:
              - tab "Details" [ref=e205] [cursor=pointer]:
                - text: Details
                - img [ref=e206]
              - tab "Overview" [ref=e208] [cursor=pointer]
              - tab "Work" [ref=e209] [cursor=pointer]:
                - text: Work
                - img [ref=e210]
              - tab "Fees · Time recording" [active] [selected] [ref=e212] [cursor=pointer]:
                - text: Fees
                - generic [ref=e213]: · Time recording
                - img [ref=e214]
              - tab "Client" [ref=e217] [cursor=pointer]:
                - text: Client
                - img [ref=e218]
              - tab "Diary" [ref=e220] [cursor=pointer]
              - tab "Activity" [ref=e221] [cursor=pointer]:
                - text: Activity
                - img [ref=e222]
            - tabpanel [ref=e224]:
              - generic [ref=e225]:
                - generic [ref=e226]:
                  - generic [ref=e227]: From
                  - textbox [ref=e228]
                  - generic [ref=e229]: To
                  - textbox [ref=e230]
                - generic [ref=e231]:
                  - img [ref=e233]
                  - heading "No time tracked yet" [level=2] [ref=e236]
                  - paragraph [ref=e237]: Log time on tasks to see matter time summaries here
  - generic [ref=e238]:
    - heading "Command Palette" [level=2] [ref=e239]
    - paragraph [ref=e240]: Search pages and settings
  - region "Notifications alt+T"
  - alert [ref=e241]: Ndlovu v RAF — Depth II mwdhz4js0790
```

# Test source

```ts
  151 |   if (expected.sheriffFeeListed) {
  152 |     await expect(preview).toContainText(SHERIFF_FEE_DESCRIPTION);
  153 |   } else {
  154 |     await expect(preview).not.toContainText(SHERIFF_FEE_DESCRIPTION);
  155 |   }
  156 |   await expect(preview).toContainText(
  157 |     new RegExp(`Total disbursements:\\s*${expected.disbursementsTotal.source}`)
  158 |   );
  159 |   // Trust activity: Sipho's client trust ledger (client-scoped) with the Day 5 deposit + payment
  160 |   const runId = currentDepthIiRun();
  161 |   await expect(preview).toContainText("Trust Activity");
  162 |   await expect(preview).toContainText(new RegExp(`Deposits[\\s\\S]*DEP/${runId}`));
  163 |   await expect(preview).toContainText(new RegExp(`Payments[\\s\\S]*PAY/DEEDS/${runId}`));
  164 |   await expect(preview).toContainText(/Trust balance held:\s*R\s?[1-9]/);
  165 |   // Summary (fee-note basis, D9): no fee note issued yet, so nothing owing; the unbilled fees +
  166 |   // disbursements are the work in progress, shown but never counted as owing
  167 |   await expect(preview).toContainText("Summary");
  168 |   await expect(preview).toContainText(/Closing balance owing:\s*R\s?0,00/);
  169 |   const wip = test.info().retry === 0 ? expected.unbilledWip : /R\s?[1-9][\d\s\u00a0\u202f]*,\d{2}/;
  170 |   await expect(preview).toContainText(
  171 |     new RegExp(`Unbilled work in progress \\(not yet billed\\):\\s*${wip.source}`)
  172 |   );
  173 |   // The printed amounts, for the saved statement's row
  174 |   const text = (await preview.textContent()) ?? "";
  175 |   return {
  176 |     closingBalanceOwing: printedAmount(text, "Closing balance owing"),
  177 |     trustBalanceHeld: printedAmount(text, "Trust balance held"),
  178 |   };
  179 | }
  180 | 
  181 | async function openSheriffFeeDetail(page: Page) {
  182 |   await openDisbursementList(page, siphoMatterName());
  183 |   const row = page
  184 |     .locator('[data-testid^="disbursement-row-"]')
  185 |     .filter({ hasText: siphoMatterName() })
  186 |     .filter({ hasText: SHERIFF_FEE_DESCRIPTION })
  187 |     .first();
  188 |   await expect(row).toContainText("Approved", { timeout: 15_000 });
  189 |   const detail = page.getByTestId("disbursement-detail");
  190 |   // Ready once its detail page has rendered
  191 |   await clickThrough(row, detail.getByText(SHERIFF_FEE_DESCRIPTION, { exact: true }));
  192 |   return detail;
  193 | }
  194 | 
  195 | /** data-testid of the statement row saved by "Save Statement" (same worker within the group). */
  196 | let savedStatementRowId = "";
  197 | 
  198 | test.describe.serial("Day 30-45 — Statement of Account + Write-off (Phase 67)", () => {
  199 |   // ── Day 0 prerequisite: a billing rate for Alice on Sipho's client ──
  200 |   // A freshly seeded tenant has no billing rates (legal-lifecycle Day 0 sets org rates), so
  201 |   // time would be billed at R0. A client-level override keeps the setup scoped to this run.
  202 |   test("[setup] Alice (Thandi): Billing rate override for Alice on Sipho's client", async ({
  203 |     page,
  204 |   }) => {
  205 |     await loginAs(page, "alice");
  206 |     await openClient(page, siphoClientName());
  207 |     await openGroupedTab(page, "finance", "rates");
  208 |     const aliceRate = page
  209 |       .getByRole("tabpanel")
  210 |       .getByRole("row")
  211 |       .filter({ hasText: "Alice Owner" });
  212 |     // A retried group keeps the first attempt's override (a second one would overlap it)
  213 |     if (test.info().retry > 0 && (await aliceRate.count()) > 0) return;
  214 |     await page.getByRole("tabpanel").getByRole("button", { name: "Add Override" }).first().click();
  215 |     // Phase 88 628A (ADR-357): label → "Add Client Rate Override"
  216 |     const dialog = page.getByRole("dialog", { name: "Add Client Rate Override" });
  217 |     await expect(dialog).toBeVisible();
  218 |     await selectNativeOption(dialog.getByLabel("Member"), "Alice Owner");
  219 |     await dialog.getByLabel("Hourly Rate").fill("2500");
  220 |     await dialog.getByLabel("Effective From").fill(isoDate(-90));
  221 |     await dialog.getByRole("button", { name: "Create Override" }).click();
  222 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  223 |     await expect(aliceRate).toContainText(/2[\s\u00a0\u202f]?500,00/, { timeout: 15_000 });
  224 |   });
  225 | 
  226 |   // ── Day 7 prerequisite: fee-earning time on Sipho's matter ────────
  227 |   test("[setup] Alice (Thandi): Log 2h on Sipho's matter — letter of demand", async ({ page }) => {
  228 |     await loginAs(page, "alice");
  229 |     await openMatter(page, siphoMatterName());
  230 |     await openGroupedTab(page, "work", "tasks");
  231 | 
  232 |     const taskRow = page.getByRole("row").filter({ hasText: "Letter of demand" }).first();
  233 |     await expect(taskRow).toBeVisible({ timeout: 15_000 });
  234 |     await taskRow.getByRole("button", { name: "Log Time" }).click();
  235 | 
  236 |     const dialog = page.getByRole("dialog", { name: "Log Time" });
  237 |     await expect(dialog).toBeVisible();
  238 |     await dialog.locator("#time-hours").fill("2");
  239 |     await dialog.locator("#time-minutes").fill("0");
  240 |     await dialog.locator("#time-date").fill(isoDate());
  241 |     await dialog.locator("#time-description").fill(timeDescription());
  242 |     await expect(dialog.locator("#time-billable")).toBeChecked();
  243 |     await dialog.getByRole("button", { name: "Log Time" }).click();
  244 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  245 | 
  246 |     // Finance → Time summarises the entry by task and by member (a retried group adds to
  247 |     // the first attempt's 2h; the entry itself is checked line-by-line in the statement)
  248 |     await openGroupedTab(page, "finance", "time");
  249 |     const timePanel = page.getByRole("tabpanel");
  250 |     const hours = test.info().retry === 0 ? "2h" : "\\d+h";
> 251 |     await expect(timePanel).toContainText(new RegExp(`Letter of demand\\s*${hours}`), {
      |                             ^ Error: expect(locator).toContainText(expected) failed
  252 |       timeout: 15_000,
  253 |     });
  254 |     await expect(timePanel).toContainText(new RegExp(`Alice Owner\\s*${hours}`));
  255 |   });
  256 | 
  257 |   // ── 30.1-30.7: Generate Statement of Account on Sipho's matter ────
  258 |   test("Alice (Thandi): Open Statements tab on oldest matter", async ({ page }) => {
  259 |     await loginAs(page, "alice");
  260 |     const tab = await openStatementsTab(page);
  261 |     await expect(tab.getByRole("heading", { name: "Statements of Account" })).toBeVisible();
  262 |     await expect(tab.getByTestId("open-generate-statement-btn")).toBeVisible();
  263 |     // A retried group finds the first attempt's statement already listed
  264 |     if (test.info().retry === 0) {
  265 |       await expect(tab.getByTestId("statements-empty")).toBeVisible();
  266 |       await page.waitForLoadState("networkidle");
  267 |       await captureScreenshot(page, "day-30-matter-statements-tab");
  268 |     }
  269 |   });
  270 | 
  271 |   // Regression (was FIXME depth-ii:soa-preview-unbounded-scale, G-C4): the preview stays inside
  272 |   // the dialog (626F A4PreviewWrapper contentRect + contain:inline-size, min-w-0 form).
  273 |   test("Alice: Generate Statement of Account with HTML preview", async ({ page }) => {
  274 |     await loginAs(page, "alice");
  275 |     const tab = await openStatementsTab(page);
  276 |     const dialog = await generateStatement(page, tab);
  277 |     await verifyStatementPreview(dialog);
  278 | 
  279 |     // The footer actions must stay inside the dialog
  280 |     const dialogBox = await dialog.boundingBox();
  281 |     const downloadBox = await dialog.getByTestId("statement-download-btn").boundingBox();
  282 |     expect(downloadBox!.x + downloadBox!.width).toBeLessThanOrEqual(
  283 |       dialogBox!.x + dialogBox!.width
  284 |     );
  285 | 
  286 |     // Not networkidle: the sandboxed srcdoc preview iframe can keep it from settling (harness
  287 |     // hang, PR #1749 e2e gate). The preview content is asserted above; the dialog is ready once
  288 |     // its download action is.
  289 |     await expect(dialog.getByTestId("statement-download-btn")).toBeEnabled();
  290 |     await captureScreenshot(page, "day-30-statement-of-account-preview");
  291 |     await captureScreenshot(page, "statement-of-account-preview", { curated: true });
  292 |   });
  293 | 
  294 |   test("Alice: Save Statement — listed on the matter, PDF downloadable from the Documents tab", async ({
  295 |     page,
  296 |   }) => {
  297 |     await loginAs(page, "alice");
  298 |     const tab = await openStatementsTab(page);
  299 |     const rows = tab.locator('[data-testid^="statement-row-"]');
  300 |     const rowIds = () => rows.evaluateAll((els) => els.map((e) => e.getAttribute("data-testid")!));
  301 |     const before = new Set(await rowIds());
  302 | 
  303 |     // "Preview & Save" generates the statement, saves it to the matter and renders the preview
  304 |     const dialog = await generateStatement(page, tab);
  305 |     const printed = await verifyStatementPreview(dialog);
  306 |     await expect(page.getByText("Statement generated")).toBeVisible();
  307 |     // Dismiss with Escape (the dialog footer sits below the fold at the default 1280×720 viewport)
  308 |     await page.keyboard.press("Escape");
  309 |     await expect(dialog).toBeHidden();
  310 | 
  311 |     // The saved statement is listed on the matter with its balances — located as the one
  312 |     // row that was not there before (independent of the API's list order)
  313 |     await expect
  314 |       .poll(async () => (await rowIds()).filter((id) => !before.has(id)), { timeout: 15_000 })
  315 |       .toHaveLength(1);
  316 |     savedStatementRowId = (await rowIds()).find((id) => !before.has(id))!;
  317 |     const row = tab.getByTestId(savedStatementRowId);
  318 |     // Columns: Generated | Closing balance owing | Trust balance held | Actions
  319 |     await expect(row.getByRole("cell").nth(1)).toHaveText(printed.closingBalanceOwing);
  320 |     await expect(row.getByRole("cell").nth(2)).toHaveText(printed.trustBalanceHeld);
  321 |     await expect(row.locator('[data-testid^="statement-download-"]')).toBeEnabled();
  322 |     await page.waitForLoadState("networkidle");
  323 |     await captureScreenshot(page, "day-30-statement-saved-list");
  324 | 
  325 |     // The statement PDF is also saved to the matter's documents; the Documents-tab download
  326 |     // hands the browser the S3 presigned URL, which the browser fetches.
  327 |     const fileName = new RegExp(
  328 |       `^Download statement-of-account-${escapeRegExp(
  329 |         siphoMatterName()
  330 |           .toLowerCase()
  331 |           .replace(/[^a-z0-9]+/g, "-")
  332 |       )}`
  333 |     );
  334 |     await openGroupedTab(page, "work", "documents");
  335 |     const docDownload = page
  336 |       .getByRole("tabpanel")
  337 |       .getByRole("button", { name: /^Download statement-of-account-.*\.pdf$/ })
  338 |       .first();
  339 |     await expect(docDownload).toBeVisible({ timeout: 15_000 });
  340 |     await expect(docDownload).toHaveAccessibleName(fileName);
  341 |     const [pdf] = await Promise.all([
  342 |       page.waitForResponse((r) => /\/statement-of-account-[^/?]*\.pdf\?/.test(r.url())),
  343 |       docDownload.click(),
  344 |     ]);
  345 |     expect(pdf.status()).toBe(200);
  346 |     expect(pdf.headers()["content-type"]).toContain("pdf");
  347 |   });
  348 | 
  349 |   // FIXME(depth-ii:e2e-s3-presigner-unreachable) E2E ENVIRONMENT LIMITATION — not a product
  350 |   // bug. The Statements-tab "Download" button calls downloadGeneratedDocumentAction
  351 |   // (settings/documents/_tabs/template-generation-actions.ts), a server action that fetches
```