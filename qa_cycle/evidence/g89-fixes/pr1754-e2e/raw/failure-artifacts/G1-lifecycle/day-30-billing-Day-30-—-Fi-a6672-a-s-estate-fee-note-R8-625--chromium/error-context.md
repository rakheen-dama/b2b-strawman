# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-30-billing.spec.ts >> Day 30 — First Billing Cycle >> Alice: Create, approve and send Moroka's estate fee note (R8,625)
- Location: e2e/tests/legal-lifecycle/day-30-billing.spec.ts:175:7

# Error details

```
Error: expect(locator).toBeChecked() failed

Locator: getByRole('dialog', { name: /Generate fee note|Select Unbilled Items/ }).getByRole('checkbox', { name: 'Select all entries for Estate Late Peter Moroka mwc51syce90d' })
Expected: checked
Timeout: 15000ms
Error: element(s) not found

Call log:
  - Expect "toBeChecked" with timeout 15000ms
  - waiting for getByRole('dialog', { name: /Generate fee note|Select Unbilled Items/ }).getByRole('checkbox', { name: 'Select all entries for Estate Late Peter Moroka mwc51syce90d' })

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
              - /url: /org/e2e-test-org/clients
              - text: Clients
          - generic:
            - img
            - generic: Moroka Family Trust mwc51syce90d
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
                - link:
                  - /url: /org/e2e-test-org/clients
                  - img
                  - text: Back to Clients
              - generic:
                - generic:
                  - generic:
                    - generic:
                      - heading [level=1]: Moroka Family Trust mwc51syce90d
                      - generic:
                        - generic:
                          - generic: Onboarding
                      - paragraph: moroka.trust.mwc51syce90d@example.com
                      - paragraph: Since 11 Nov 2026 · 1 matter
                    - generic:
                      - button: Activate Client
                      - button:
                        - img
                        - text: Generate Document
                        - img
                      - button:
                        - img
                        - generic: More actions
              - generic:
                - tablist:
                  - tab:
                    - text: Details
                    - img
                  - tab: Overview
                  - tab:
                    - text: Matters
                    - img
                  - tab [selected]:
                    - text: Fees
                    - generic: · Fee notes
                    - img
                  - tab:
                    - text: FICA & compliance
                    - img
                  - tab: Activity
                - tabpanel:
                  - generic:
                    - generic:
                      - generic:
                        - heading [level=2]: Fee notes
                      - button:
                        - img
                        - text: New fee note
                    - generic:
                      - img
                      - heading [level=2]: No fee notes yet
                      - paragraph: Generate fee note from unbilled time entries
  - generic:
    - heading [level=2]: Command Palette
    - paragraph: Search pages and settings
  - region "Notifications alt+T"
  - alert: Kazi — Practice management, built for Africa
  - dialog "Select Unbilled Items" [active] [ref=e2]:
    - generic [ref=e3]:
      - generic [ref=e4]:
        - heading "Select Unbilled Items" [level=2] [ref=e5]
        - paragraph [ref=e6]: 0 items selected for Moroka Family Trust mwc51syce90d
      - generic [ref=e7]:
        - button "Back" [ref=e8]:
          - img
          - text: Back
        - paragraph [ref=e9]: No unbilled time entries found for this period.
        - generic [ref=e10]:
          - generic [ref=e11]: Total (0 items)
          - generic [ref=e12]: R 0,00
        - generic [ref=e13]:
          - button "Cancel" [ref=e14]
          - button "Validate & Create Draft" [disabled]
      - button "Close" [ref=e15]:
        - img
        - generic [ref=e16]: Close
```

# Test source

```ts
  326 | 
  327 | /** The Trust → Transactions row holding `reference`. */
  328 | export function trustTransactionRow(page: Page, reference: string): Locator {
  329 |   return page.locator("main tbody tr", { hasText: reference });
  330 | }
  331 | 
  332 | /**
  333 |  * Records a client trust deposit (Record Transaction → Record Deposit) and asserts its row lands
  334 |  * RECORDED — deposits never wait for approval; the approval path is the fee transfer (Day 30).
  335 |  */
  336 | export async function recordTrustDeposit(
  337 |   page: Page,
  338 |   opts: { client: string; matter?: string; amount: string; reference: string; description: string }
  339 | ): Promise<void> {
  340 |   await openTrustTransactions(page);
  341 |   await page.getByTestId("record-transaction-button").click();
  342 |   await page.getByRole("menuitem", { name: "Record Deposit" }).click();
  343 |   const dialog = page.getByRole("dialog", { name: "Record Deposit" });
  344 |   await expect(dialog).toBeVisible();
  345 |   await dialog.getByTestId("trust-deposit-customer-trigger").click();
  346 |   await page
  347 |     .getByRole("option", { name: new RegExp(`^${escapeRegExp(opts.client)}(\\s|$)`) })
  348 |     .click();
  349 |   if (opts.matter) {
  350 |     await dialog.getByTestId("trust-deposit-matter-trigger").click();
  351 |     await page.getByRole("option", { name: new RegExp(escapeRegExp(opts.matter)) }).click();
  352 |   }
  353 |   await dialog.getByLabel("Amount").fill(opts.amount);
  354 |   await dialog.getByLabel("Reference").fill(opts.reference);
  355 |   await dialog.getByLabel(/Description/).fill(opts.description);
  356 |   await dialog.getByRole("button", { name: "Record Deposit" }).click();
  357 |   await expect(dialog).toBeHidden({ timeout: 15_000 });
  358 |   await expect(trustTransactionRow(page, opts.reference)).toContainText("RECORDED", {
  359 |     timeout: 15_000,
  360 |   });
  361 | }
  362 | 
  363 | /**
  364 |  * Records a trust FEE_TRANSFER (Record Transaction → Record Fee Transfer) from a client's trust
  365 |  * balance to a fee note, asserts its single row lands AWAITING APPROVAL, and returns the
  366 |  * transaction UUID (from the row's `transaction-row-<uuid>` test id). The dialog takes raw UUIDs
  367 |  * (UX follow-up: client / fee-note pickers, components/trust/RecordFeeTransferDialog.tsx).
  368 |  */
  369 | export async function recordFeeTransfer(
  370 |   page: Page,
  371 |   opts: { clientId: string; feeNoteId: string; amount: string; reference: string }
  372 | ): Promise<string> {
  373 |   await openTrustTransactions(page);
  374 |   await page.getByTestId("record-transaction-button").click();
  375 |   await page.getByRole("menuitem", { name: "Record Fee Transfer" }).click();
  376 |   const dialog = page.getByRole("dialog", { name: "Record Fee Transfer" });
  377 |   await expect(dialog).toBeVisible();
  378 |   await dialog.getByLabel("Client ID").fill(opts.clientId);
  379 |   await dialog.getByLabel("Fee note ID").fill(opts.feeNoteId);
  380 |   await dialog.getByLabel("Amount").fill(opts.amount);
  381 |   await dialog.getByLabel("Reference").fill(opts.reference);
  382 |   await dialog.getByRole("button", { name: "Record Fee Transfer" }).click();
  383 |   await expect(dialog).toBeHidden({ timeout: 15_000 });
  384 | 
  385 |   const row = trustTransactionRow(page, opts.reference);
  386 |   await expect(row).toHaveCount(1, { timeout: 15_000 });
  387 |   await expect(row).toContainText("AWAITING APPROVAL");
  388 |   const id = (await row.getAttribute("data-testid"))?.replace(/^transaction-row-/, "");
  389 |   if (!id) throw new Error(`Fee transfer row "${opts.reference}" has no transaction-row-<id>`);
  390 |   return id;
  391 | }
  392 | 
  393 | // ── Fee notes (Days 30–90) ───────────────────────────────────────────────────────────────────
  394 | 
  395 | /** Opens a client's Fees → Fee notes tab. */
  396 | export async function openClientFeeNotes(page: Page, client: string): Promise<void> {
  397 |   await openClient(page, client);
  398 |   await openGroupedTab(page, "finance", "invoices");
  399 | }
  400 | 
  401 | /**
  402 |  * On a client's Fee notes tab: New fee note → Fetch Unbilled Time → Validate & Create Draft →
  403 |  * Create Draft, for the unbilled items of `matter` (`items` = how many the dialog must select).
  404 |  * Opens the created draft (the one fee-note link that was not listed before) and returns its UUID.
  405 |  */
  406 | export async function generateFeeNoteDraft(
  407 |   page: Page,
  408 |   { matter, items }: { matter: string; items: number }
  409 | ): Promise<string> {
  410 |   const panel = page.getByRole("tabpanel");
  411 |   const newFeeNote = panel.getByRole("button", { name: "New fee note" });
  412 |   await expect(newFeeNote).toBeEnabled({ timeout: 15_000 });
  413 |   const feeNoteLinks = () =>
  414 |     panel
  415 |       .locator('a[href*="/invoices/"]')
  416 |       .evaluateAll((els) => els.map((e) => e.getAttribute("href")!));
  417 |   const before = new Set(await feeNoteLinks());
  418 | 
  419 |   await newFeeNote.click();
  420 |   const generate = page.getByRole("dialog", { name: /Generate fee note|Select Unbilled Items/ });
  421 |   await expect(generate).toBeVisible({ timeout: 15_000 });
  422 |   await generate.getByRole("button", { name: "Fetch Unbilled Time" }).click();
  423 |   // Step 2: the matter's unbilled items, all pre-selected and all rated (Day 0 rate cards)
  424 |   await expect(
  425 |     generate.getByRole("checkbox", { name: `Select all entries for ${matter}` })
> 426 |   ).toBeChecked({ timeout: 15_000 });
      |     ^ Error: expect(locator).toBeChecked() failed
  427 |   // (the dialog always says "items": "1 items selected for …")
  428 |   await expect(generate).toContainText(`${items} items selected for`);
  429 |   await expect(generate.getByTestId("null-rate-warning")).toHaveCount(0);
  430 |   await generate.getByRole("button", { name: "Validate & Create Draft" }).click();
  431 |   await generate.getByRole("button", { name: /^Create Draft/ }).click();
  432 |   await expect(generate).toBeHidden({ timeout: 15_000 });
  433 | 
  434 |   await expect
  435 |     .poll(async () => (await feeNoteLinks()).filter((href) => !before.has(href)), {
  436 |       timeout: 15_000,
  437 |     })
  438 |     .toHaveLength(1);
  439 |   const created = (await feeNoteLinks()).find((href) => !before.has(href))!;
  440 |   expect(created).toMatch(/\/invoices\/[0-9a-f-]{36}$/);
  441 |   const createdRow = panel.getByRole("row").filter({ has: page.locator(`a[href="${created}"]`) });
  442 |   await expect(createdRow).toContainText("Draft");
  443 |   await clickThrough(
  444 |     createdRow.locator(`a[href="${created}"]`).first(),
  445 |     page.getByRole("heading", { level: 1, name: "Draft fee note" })
  446 |   );
  447 |   await expect(page.locator("main")).toContainText(matter);
  448 |   return idFromUrl(page, "invoices");
  449 | }
  450 | 
  451 | /** Opens a fee note by its UUID and waits for its header. */
  452 | export async function openFeeNote(page: Page, id: string): Promise<void> {
  453 |   await page.goto(`${BASE}/invoices/${id}`);
  454 |   await expect(page.getByRole("heading", { level: 1 })).toBeVisible({ timeout: 15_000 });
  455 |   await expect(page.locator("main").getByText(/^Client: /)).toBeVisible();
  456 | }
  457 | 
  458 | /** Adds a manual line (Add Line → Add Line Item) to the open draft fee note, at the default tax rate. */
  459 | export async function addFeeNoteLine(
  460 |   page: Page,
  461 |   { description, quantity, unitPrice }: { description: string; quantity: string; unitPrice: string }
  462 | ): Promise<void> {
  463 |   const main = page.locator("main");
  464 |   await main.getByRole("button", { name: "Add Line" }).click();
  465 |   // The editor has no role / test id and its labels have no htmlFor — product follow-up: a
  466 |   // data-testid + labelled inputs (components/invoices/invoice-line-editor.tsx:45-81). Until then:
  467 |   // the innermost div holding the "Add Line Item" heading, and its two number inputs by position.
  468 |   const form = main.locator("div").filter({
  469 |     has: page.getByRole("heading", { level: 3, name: "Add Line Item" }),
  470 |   });
  471 |   const editor = form.last();
  472 |   await expect(editor).toBeVisible();
  473 |   await editor.getByPlaceholder("Line item description").fill(description);
  474 |   // Quantity and Unit Price are the editor's two (unlabelled) number inputs, in that order
  475 |   const numbers = editor.locator('input[type="number"]');
  476 |   await numbers.nth(0).fill(quantity);
  477 |   await numbers.nth(1).fill(unitPrice);
  478 |   await editor.getByRole("button", { name: "Add", exact: true }).click();
  479 |   await expect(page.getByRole("heading", { level: 3, name: "Add Line Item" })).toBeHidden({
  480 |     timeout: 15_000,
  481 |   });
  482 |   await expect(main.getByRole("row").filter({ hasText: description })).toHaveCount(1, {
  483 |     timeout: 15_000,
  484 |   });
  485 | }
  486 | 
  487 | /**
  488 |  * A row of the open fee note's totals block ("Subtotal", "VAT — Standard (15%)", "Total" …): the
  489 |  * row whose text starts with `label` (so "Total" does not match "Subtotal"). The block has no role
  490 |  * or test id — product follow-up: a `data-testid` on the block and per row
  491 |  * (components/invoices/invoice-totals-section.tsx:12-48); until then it is found from "Subtotal"
  492 |  * by DOM position (`xpath=../..`).
  493 |  */
  494 | export function feeNoteTotalsRow(page: Page, label: string): Locator {
  495 |   const totals = page.locator("main").getByText("Subtotal", { exact: true }).locator("xpath=../..");
  496 |   return totals.locator(":scope > div").filter({ hasText: new RegExp(`^${escapeRegExp(label)}`) });
  497 | }
  498 | 
```