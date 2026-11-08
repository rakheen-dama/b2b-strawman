# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: day-90-review.spec.ts >> Day 90 — Role-Based Access >> Alice: Record a R500 trust fee transfer for Sipho — awaiting approval
- Location: e2e/tests/legal-lifecycle/day-90-review.spec.ts:326:7

# Error details

```
Error: expect(locator).toBeHidden() failed

Locator:  getByRole('dialog', { name: 'Record Fee Transfer' })
Expected: hidden
Received: visible
Timeout:  15000ms

Call log:
  - Expect "toBeHidden" with timeout 15000ms
  - waiting for getByRole('dialog', { name: 'Record Fee Transfer' })
    19 × locator resolved to <div role="dialog" tabindex="-1" data-state="open" data-slot="dialog-content" id="radix-_R_b1av5ubt9iv5ubtb_" aria-labelledby="radix-_R_b1av5ubt9iv5ubtbH1_" aria-describedby="radix-_R_b1av5ubt9iv5ubtbH2_" class="data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 fixed top-[50%] left-[50%] z-50 w-full max-w-[calc(100%-2rem)] translate-x-[-50%] translate-y-[-50%] duration-150 outline-none sm:max-w-lg">…</div>
       - unexpected value "visible"

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
            - generic: Transactions
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
                  - heading [level=1]: Transactions
                  - paragraph: Trust account transaction history
                - button:
                  - img
                  - text: Record Transaction
              - generic:
                - generic: "Status:"
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions
                  - text: All
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?status=RECORDED
                  - text: Recorded
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?status=AWAITING_APPROVAL
                  - text: Awaiting Approval
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?status=APPROVED
                  - text: Approved
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?status=REJECTED
                  - text: Rejected
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?status=REVERSED
                  - text: Reversed
              - generic:
                - generic: "Type:"
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions
                  - text: All
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=DEPOSIT
                  - text: Deposit
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=PAYMENT
                  - text: Payment
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=TRANSFER_IN
                  - text: Transfer In
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=TRANSFER_OUT
                  - text: Transfer Out
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=FEE_TRANSFER
                  - text: Fee Transfer
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=REFUND
                  - text: Refund
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=INTEREST_CREDIT
                  - text: Interest Credit
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=INTEREST_LPFF
                  - text: Interest LPFF
                - link:
                  - /url: /org/e2e-test-org/trust-accounting/transactions?type=REVERSAL
                  - text: Reversal
              - generic:
                - generic:
                  - generic: From
                  - textbox
                - generic:
                  - generic: To
                  - textbox
                - generic:
                  - generic: Client ID
                  - textbox:
                    - /placeholder: Client UUID
                - generic:
                  - generic: Matter ID
                  - textbox:
                    - /placeholder: Matter UUID
              - generic:
                - generic:
                  - generic: Transaction History
                  - generic: 2 transactions found
                - generic:
                  - generic:
                    - table:
                      - rowgroup:
                        - row:
                          - columnheader: Date
                          - columnheader: Reference
                          - columnheader: Type
                          - columnheader: Amount
                          - columnheader: Status
                          - columnheader: Actions
                      - rowgroup:
                        - row:
                          - cell: 08 Nov 2026
                          - cell: DEP/MOROKA/mw72qd7rb4f2
                          - cell:
                            - generic: Deposit
                          - cell:
                            - generic:
                              - img
                              - text: R 250 000,00
                          - cell:
                            - generic: RECORDED
                          - cell
                        - row:
                          - cell: 08 Nov 2026
                          - cell: DEP/QC/mw72qd7rb4f2
                          - cell:
                            - generic: Deposit
                          - cell:
                            - generic:
                              - img
                              - text: R 45 000,00
                          - cell:
                            - generic: RECORDED
                          - cell
  - generic:
    - heading [level=2]: Command Palette
    - paragraph: Search pages and settings
  - region "Notifications alt+T"
  - alert
  - dialog "Record Fee Transfer" [active] [ref=e2]:
    - generic [ref=e3]:
      - generic [ref=e4]:
        - heading "Record Fee Transfer" [level=2] [ref=e5]
        - paragraph [ref=e6]: Transfer fees from trust to the office account against an unpaid fee note.
      - generic [ref=e7]:
        - generic [ref=e8]:
          - generic [ref=e9]: Client ID
          - textbox "Client ID" [ref=e10]:
            - /placeholder: Client UUID
            - text: 209c5065-9560-4e45-91e4-ab1c7e8c5f7c
        - generic [ref=e11]:
          - generic [ref=e12]: Fee note ID
          - textbox "Fee note ID" [ref=e13]:
            - /placeholder: Fee note UUID
            - text: c5da6893-1ea0-499b-8cb8-ba89ed5d2a63
        - generic [ref=e14]:
          - generic [ref=e15]: Amount
          - spinbutton "Amount" [ref=e16]: "500"
        - generic [ref=e17]:
          - generic [ref=e18]: Reference
          - textbox "Reference" [ref=e19]:
            - /placeholder: e.g. FEE/2026/001
            - text: FEE/SIPHO/mw72qd7rb4f2
        - paragraph [ref=e20]: "Fee note must be in APPROVED, SENT or PARTIALLY_PAID status, current status: DRAFT"
        - generic [ref=e21]:
          - button "Cancel" [ref=e22]
          - button "Record Fee Transfer" [ref=e23]
      - button "Close" [ref=e24]:
        - img
        - generic [ref=e25]: Close
```

# Test source

```ts
  283 | }
  284 | 
  285 | /** The firm's trust account (scenario 0.62; the 628F seed uses the identical name and bank). */
  286 | export const TRUST_ACCOUNT = {
  287 |   name: "Mathebula & Partners Trust Account",
  288 |   bank: "Standard Bank",
  289 |   branchCode: "051001",
  290 |   accountNumber: "0123456789",
  291 | } as const;
  292 | 
  293 | /**
  294 |  * Creates the primary GENERAL trust account if the firm has none (a second primary GENERAL account
  295 |  * is rejected by a unique index). Deterministic: waits for either state before branching.
  296 |  */
  297 | export async function ensureTrustAccount(page: Page): Promise<void> {
  298 |   await page.goto(`${BASE}/trust-accounting`);
  299 |   await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({
  300 |     timeout: 15_000,
  301 |   });
  302 |   const addAccount = page.getByRole("button", { name: "Add Account" });
  303 |   const recordTransaction = page.getByRole("link", { name: "Record Transaction" });
  304 |   await expect(addAccount.or(recordTransaction).first()).toBeVisible({ timeout: 15_000 });
  305 |   if (await addAccount.isVisible()) {
  306 |     await addAccount.click();
  307 |     const dialog = page.getByRole("dialog", { name: "Add Trust Account" });
  308 |     await expect(dialog).toBeVisible();
  309 |     await dialog.getByLabel("Account Name").fill(TRUST_ACCOUNT.name);
  310 |     await dialog.getByLabel("Bank Name").fill(TRUST_ACCOUNT.bank);
  311 |     await dialog.getByLabel("Branch Code").fill(TRUST_ACCOUNT.branchCode);
  312 |     await dialog.getByLabel("Account Number").fill(TRUST_ACCOUNT.accountNumber);
  313 |     await dialog.getByRole("button", { name: "Create Account" }).click();
  314 |     await expect(dialog).toBeHidden({ timeout: 15_000 });
  315 |   }
  316 |   await expect(recordTransaction).toBeVisible({ timeout: 15_000 });
  317 | }
  318 | 
  319 | /** Opens Trust → Transactions. */
  320 | export async function openTrustTransactions(page: Page): Promise<void> {
  321 |   await page.goto(`${BASE}/trust-accounting/transactions`);
  322 |   await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({
  323 |     timeout: 15_000,
  324 |   });
  325 | }
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
> 383 |   await expect(dialog).toBeHidden({ timeout: 15_000 });
      |                        ^ Error: expect(locator).toBeHidden() failed
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
  426 |   ).toBeChecked({ timeout: 15_000 });
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
```