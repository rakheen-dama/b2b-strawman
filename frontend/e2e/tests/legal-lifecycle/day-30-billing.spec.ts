/**
 * Day 30 — First Billing Cycle
 *
 * Four fee notes, each generated from the client's unbilled work (client → Fees → Fee notes →
 * New fee note → Fetch Unbilled Time → Create Draft): Sipho (time + an LSSA tariff item + a sheriff
 * disbursement line, VAT 15%), Apex (fixed fee R35,000 + VAT = R40,250), Moroka (approved, then
 * settled from trust by a FEE_TRANSFER that a second user approves) and QuickCollect (collections
 * time + a court-filing line). Sipho's and Apex's fee notes go DRAFT → APPROVED → SENT, and Apex's
 * matter gets a R150,000 fee estimate.
 *
 * Tariff items are added from the LSSA tariff browser ("Add Tariff Items"); their amounts come from
 * the tariff schedule — nothing is auto-populated onto the fee note.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–14 must have run on the
 * same stack (rates, trust account, clients, template matters, Day 7–14 time and deposits).
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { BASE, escapeRegExp, isoDate, openMatter } from "../../helpers/legal-depth-ii";
import {
  addFeeNoteLine,
  apexClient,
  apexMatter,
  clientId,
  currentLifecycleRun,
  feeNoteTotalsRow,
  generateFeeNoteDraft,
  logTimeOnTask,
  morokaClient,
  morokaMatter,
  openClientFeeNotes,
  openFeeNote,
  openTrustTransactions,
  qcClient,
  qcMatter,
  randPattern,
  recordedValue,
  recordFeeTransfer,
  recordValue,
  siphoClient,
  siphoMatter,
  trustTransactionRow,
} from "../../helpers/legal-lifecycle";

const TARIFF_ITEM = "Instructions to sue or defend";
const SHERIFF_LINE = "Disbursement — Sheriff service fee";
const APEX_FIXED_FEE = "Fixed fee — Shareholder agreement review and drafting";
const APEX_INTAKE_TIME = "Scoping call — shareholder agreement (fixed-fee engagement)";
const MOROKA_EXECUTOR_LINE = "Executor's remuneration — interim account";
const QC_TIME = "Verify claim documentation — first debtor batch";
const QC_FILING_LINE = "Disbursement — Court filing fee";
const feeTransferRef = () => `FEE/MOROKA/${currentLifecycleRun()}`;

/** Approves the open draft fee note and sends it (no confirm dialogs; an override panel may ask). */
async function approveAndSend(page: Page): Promise<void> {
  const main = page.locator("main");
  await expect(page.getByRole("heading", { level: 1, name: "Draft fee note" })).toBeVisible();
  await main.getByRole("button", { name: "Approve", exact: true }).click();
  await expect(main.getByText("Approved", { exact: true })).toBeVisible({ timeout: 15_000 });
  // Approval assigns the sequential fee-note number
  await expect(page.getByRole("heading", { level: 1, name: "Draft fee note" })).toHaveCount(0);
  await main.getByRole("button", { name: "Send fee note" }).click();
  const override = page.getByTestId("send-override-dialog");
  const sent = main.getByText("Sent", { exact: true });
  await expect(sent.or(override).first()).toBeVisible({ timeout: 15_000 });
  if (await override.isVisible()) {
    await override.getByRole("button", { name: "Send Anyway" }).click();
  }
  await expect(sent).toBeVisible({ timeout: 15_000 });
}

test.describe.serial("Day 30 — First Billing Cycle", () => {
  test("Alice: Navigate to fee notes page", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await expect(page.getByRole("heading", { level: 1, name: "Fee notes" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("button", { name: "New fee note" })).toBeVisible();
  });

  // ── 30.1-30.12: Sipho fee note (time + LSSA tariff item + sheriff disbursement) ──
  test("Alice: Generate Sipho's fee note from unbilled time, add an LSSA tariff item and a sheriff disbursement line (VAT 15%)", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // "New fee note" on the Fee notes list picks the client, then opens its Fee notes tab
    await page.goto(`${BASE}/invoices`);
    await page.getByRole("button", { name: "New fee note" }).click();
    await page.getByPlaceholder("Search clients...").fill(siphoClient());
    await page.getByRole("option", { name: new RegExp(`^${escapeRegExp(siphoClient())}`) }).click();
    await page.waitForURL(new RegExp(`/clients/${clientId("sipho")}\\?tab=invoices`), {
      timeout: 15_000,
    });

    // Day 7–14 time: Carol 1h30 + 0h45 at R550, Bob 2h at R1,200 = R3,637.50
    const feeNoteId = await generateFeeNoteDraft(page, { matter: siphoMatter(), items: 3 });
    recordValue("siphoFeeNote", feeNoteId);
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("3 637,50"));

    // LSSA tariff item from the tariff browser
    const main = page.locator("main");
    await main.getByRole("button", { name: "Add Tariff Items" }).click();
    const tariff = page.getByTestId("tariff-line-dialog");
    await expect(tariff).toBeVisible();
    await tariff.getByTestId("tariff-item-search").fill(TARIFF_ITEM);
    await tariff
      .locator('[data-testid^="tariff-item-"]')
      .filter({ hasText: TARIFF_ITEM })
      .last()
      .click();
    await expect(tariff.locator('[data-testid^="selected-item-"]')).toHaveCount(1);
    await tariff.getByTestId("add-to-invoice-btn").click();
    await expect(tariff).toBeHidden({ timeout: 15_000 });
    // The tariff schedule's amount for item 1(a) (LSSA 2024/25 High Court party-and-party)
    const tariffLine = main.getByRole("row").filter({ hasText: TARIFF_ITEM });
    await expect(tariffLine).toHaveCount(1, { timeout: 15_000 });
    await expect(tariffLine).toContainText(randPattern("780,00"));

    // Sheriff's service fee as a manual line
    await addFeeNoteLine(page, { description: SHERIFF_LINE, quantity: "1", unitPrice: "350" });

    // VAT at the seeded default rate on every line: 3,637.50 + 780 + 350 = 4,767.50 (+ 715.13)
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("4 767,50"));
    await expect(feeNoteTotalsRow(page, "VAT — Standard (15")).toContainText(randPattern("715,13"));
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("5 482,63"));

    // Screenshot: fee note with tariff lines
    await captureScreenshot(page, "day-30-fee-note-tariff-completed");
    await captureScreenshot(page, "fee-note-tariff-disbursement", { curated: true });
  });

  // ── 30.13-30.15: Apex fee note (fixed fee R35,000 + VAT) ─────────
  test("Alice: Create Apex fee note — fixed fee R35,000 + VAT = R40,250", async ({ page }) => {
    await loginAs(page, "alice");
    // A fee note is generated from unbilled work, so the fixed-fee engagement's scoping call is
    // recorded first; it is billed as the fixed fee, so its hourly line is removed from the draft.
    await openMatter(page, apexMatter());
    await logTimeOnTask(page, {
      task: "Client intake & scope of work",
      hours: 0,
      minutes: 30,
      date: isoDate(),
      description: APEX_INTAKE_TIME,
      rate: "2 500,00",
    });

    await openClientFeeNotes(page, apexClient());
    const feeNoteId = await generateFeeNoteDraft(page, { matter: apexMatter(), items: 1 });
    recordValue("apexFeeNote", feeNoteId);

    const main = page.locator("main");
    // Time lines read "<date> · <member> · <time entry description>"
    const intakeLine = main.getByRole("row").filter({ hasText: APEX_INTAKE_TIME });
    await expect(intakeLine).toHaveCount(1);
    await intakeLine.getByRole("button", { name: /^Delete / }).click();
    await expect(intakeLine).toHaveCount(0, { timeout: 15_000 });

    await addFeeNoteLine(page, { description: APEX_FIXED_FEE, quantity: "1", unitPrice: "35000" });
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("35 000,00"));
    await expect(feeNoteTotalsRow(page, "VAT — Standard (15")).toContainText(
      randPattern("5 250,00")
    );
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("40 250,00"));
  });

  // ── 30.16-30.21: Moroka fee note settled from trust (FEE_TRANSFER R8,500) ──
  test("Alice: Create, approve and send Moroka's estate fee note (R8,625)", async ({ page }) => {
    await loginAs(page, "alice");
    await openClientFeeNotes(page, morokaClient());
    // Day 7: Alice 1h at R2,500 on the Master's report
    const feeNoteId = await generateFeeNoteDraft(page, { matter: morokaMatter(), items: 1 });
    recordValue("morokaFeeNote", feeNoteId);
    await addFeeNoteLine(page, {
      description: MOROKA_EXECUTOR_LINE,
      quantity: "1",
      unitPrice: "5000",
    });
    // (2,500 + 5,000) + 15% VAT
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("8 625,00"));

    // The fee note is sent before trust settles it: a transfer can be *recorded* against an
    // APPROVED fee note, but approving it posts a payment, which only a SENT fee note accepts
    // (follow-up: TrustTransactionService.recordFeeTransfer vs InvoiceService.postPayment)
    await approveAndSend(page);
  });

  test("Alice: Record trust FEE_TRANSFER R8,500 for Moroka — awaits approval, her own approval is refused", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // The dialog takes raw UUIDs (follow-up: pickers); both come from the run state
    const txId = await recordFeeTransfer(page, {
      clientId: clientId("moroka"),
      feeNoteId: recordedValue("morokaFeeNote"),
      amount: "8500",
      reference: feeTransferRef(),
    });
    await expect(trustTransactionRow(page, feeTransferRef())).toContainText(
      randPattern("8 500,00")
    );
    recordValue("morokaFeeTransfer", txId);

    // The recorder cannot be the sole approver
    const actions = page.getByTestId(`approval-actions-${txId}`);
    await actions.getByRole("button", { name: "Approve" }).click();
    await expect(actions).toContainText(/recorder cannot be the sole approver|Self-approval/i, {
      timeout: 15_000,
    });
    await page.reload();
    await expect(trustTransactionRow(page, feeTransferRef())).toContainText("AWAITING APPROVAL", {
      timeout: 15_000,
    });
  });

  test("Bob: Approve Moroka's fee transfer — Moroka trust balance R241,500", async ({ page }) => {
    await loginAs(page, "bob");
    await openTrustTransactions(page);
    const txId = recordedValue("morokaFeeTransfer");
    const row = page.getByTestId(`transaction-row-${txId}`);
    await expect(row).toContainText("AWAITING APPROVAL", { timeout: 15_000 });
    await page
      .getByTestId(`approval-actions-${txId}`)
      .getByRole("button", { name: "Approve" })
      .click();
    await expect(row).toContainText("APPROVED", { timeout: 15_000 });
    await expect(row).not.toContainText("AWAITING");

    // R250,000 − R8,500
    await page.goto(`${BASE}/trust-accounting/client-ledgers`);
    await expect(page.getByTestId("client-ledgers-page")).toBeVisible({ timeout: 15_000 });
    await page.getByTestId("client-search").fill(morokaClient());
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await expect(page.getByTestId(`ledger-row-${clientId("moroka")}`)).toContainText(
      randPattern("241 500,00"),
      { timeout: 15_000 }
    );

    // The transfer settled R8,500 of the R8,625 fee note
    await openFeeNote(page, recordedValue("morokaFeeNote"));
    await expect(page.getByTestId("invoice-balance-due")).toContainText(randPattern("125,00"));

    // Screenshot
    await captureScreenshot(page, "day-30-trust-fee-transfer-approved");
    await captureScreenshot(page, "trust-fee-transfer", { curated: true });
  });

  // ── 30.22-30.24: QuickCollect fee note ────────────────────────────
  test("Bob logs QuickCollect collections time; Alice creates its fee note with a court-filing line", async ({
    page,
  }) => {
    // Bob works the first collections task, so QuickCollect has unbilled time to bill
    await loginAs(page, "bob");
    await openMatter(page, qcMatter());
    await logTimeOnTask(page, {
      task: "Client intake & verify claim documentation",
      hours: 1,
      minutes: 0,
      date: isoDate(),
      description: QC_TIME,
      rate: "1 200,00",
    });

    await loginAs(page, "alice");
    await openClientFeeNotes(page, qcClient());
    const feeNoteId = await generateFeeNoteDraft(page, { matter: qcMatter(), items: 1 });
    recordValue("qcFeeNote", feeNoteId);
    await addFeeNoteLine(page, { description: QC_FILING_LINE, quantity: "1", unitPrice: "200" });
    // (1,200 + 200) + 15% VAT
    await expect(feeNoteTotalsRow(page, "Subtotal")).toContainText(randPattern("1 400,00"));
    await expect(feeNoteTotalsRow(page, "Total")).toContainText(randPattern("1 610,00"));
  });

  // ── 30.25-30.30: Fee note lifecycle DRAFT -> APPROVED -> SENT ─────
  test("Alice: Fee note lifecycle — Sipho's and Apex's fee notes DRAFT → APPROVED → SENT", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    const numbers: string[] = [];
    for (const key of ["siphoFeeNote", "apexFeeNote"] as const) {
      await openFeeNote(page, recordedValue(key));
      await approveAndSend(page);
      numbers.push((await page.getByRole("heading", { level: 1 }).innerText()).trim());
    }
    // Sequential numbering: two distinct numbers
    expect(numbers[0]).not.toEqual(numbers[1]);

    // The list shows this run's fee notes with their statuses
    await page.goto(`${BASE}/invoices`);
    await expect(page.getByRole("heading", { level: 1, name: "Fee notes" })).toBeVisible();
    for (const [key, status] of [
      ["siphoFeeNote", "Sent"],
      ["apexFeeNote", "Sent"],
      ["morokaFeeNote", "Partially Paid"],
      ["qcFeeNote", "Draft"],
    ] as const) {
      const row = page
        .locator("main tbody tr")
        .filter({ has: page.locator(`a[href$="/invoices/${recordedValue(key)}"]`) });
      await expect(row, `${key} row`).toContainText(status, { timeout: 15_000 });
    }

    // Screenshot: fee note list with mixed statuses
    await captureScreenshot(page, "day-30-fee-note-list-mixed");
  });

  // ── 30.31-30.33: Fee estimate for Apex (R150,000) ─────────────────
  test("Alice: Set fee estimate on Apex matter (R150,000)", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, apexMatter());
    await openGroupedTab(page, "finance", "budget");
    await page.getByRole("tabpanel").getByRole("button", { name: "Set fee estimate" }).click();
    const dialog = page.getByRole("dialog", { name: "Set fee estimate" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Fee estimate amount").fill("150000");
    await dialog.getByRole("button", { name: "Save fee estimate" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    const panel = page.getByRole("tabpanel");
    await expect(panel.getByText("Fee estimate status")).toBeVisible({ timeout: 15_000 });
    await expect(panel).toContainText(randPattern("150 000,00"));
  });
});
