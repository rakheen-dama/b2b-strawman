/**
 * Day 5 — Phase 67: Disbursements (Epics 486–488)
 *
 * Exercises the disbursement lifecycle on the legal-za profile:
 *   - Bob (Admin) creates a sheriff-fee OFFICE_ACCOUNT disbursement + submits
 *   - Bob creates a deeds-office TRUST_ACCOUNT disbursement linked to an approved
 *     DISBURSEMENT_PAYMENT trust transaction + submits
 *   - Thandi (Owner, alice fixture) approves the trust-linked one (needs
 *     APPROVE_TRUST_PAYMENT capability)
 *   - Bob approves the office-account one (APPROVE_DISBURSEMENT)
 *   - Verify unbilled summary "Unbilled Disbursements: R x,xxx" on matter detail
 *
 * Terminology: always "Disbursement" (never "Expense").
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Legal-za profile active on e2e-test-org
 *   3. legal-lifecycle has run (client "Sipho Ndlovu <run>" exists)
 *   4. settings.enabledModules includes "disbursements" (otherwise the list page 404s)
 *
 * Self-sufficiency: the lifecycle Day 1 "Create Litigation matter for Sipho" step
 * currently fails, and lifecycle never creates a trust account, so this spec
 * creates its own litigation matter for Sipho (from the Litigation template) and
 * the trust account / deposit / deeds-office payment it needs — all through the UI.
 * The matter name is shared with the Day 30-45 and Day 85 specs via
 * helpers/legal-depth-ii.ts.
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import {
  BASE,
  escapeRegExp,
  isoDate,
  openMatter,
  openGroupedTab,
  recordSiphoClient,
  selectNativeOption,
  siphoClientName,
  siphoMatterName,
  startDepthIiRun,
} from "../../helpers/legal-depth-ii";

const SIPHO_CLIENT = /^Sipho Ndlovu/;
const SHERIFF_FEE_DESCRIPTION = "Sheriff fee — service of process";
const DEEDS_FEE_DESCRIPTION = "Deeds office search fee";
const TRUST_DEPOSIT_AMOUNT = "5000.00";
const DEEDS_FEE_AMOUNT = "1250.00";

/** Reference of the approved trust payment the deeds-office disbursement links to. */
function deedsPaymentReference(): string {
  return `PAY/DEEDS/${siphoMatterName().split(" ").pop()}`;
}

async function openNewDisbursementDialog(page: Page) {
  await page.goto(`${BASE}/legal/disbursements`);
  await expect(page.getByTestId("disbursements-list-page")).toBeVisible({ timeout: 15_000 });
  await page.getByTestId("create-disbursement-trigger").click();
  const dialog = page.getByTestId("create-disbursement-dialog");
  await expect(dialog).toBeVisible();
  return dialog;
}

async function openDisbursementDetail(page: Page, description: string) {
  await page.goto(`${BASE}/legal/disbursements`);
  const row = page
    .locator('[data-testid^="disbursement-row-"]')
    .filter({ hasText: siphoMatterName() })
    .filter({ hasText: description })
    .first();
  await expect(row, `disbursement "${description}" should be listed`).toBeVisible({
    timeout: 15_000,
  });
  await row.click();
  await page.waitForURL(/\/legal\/disbursements\/[0-9a-f-]{36}$/, { timeout: 15_000 });
  // Load the detail page by its URL so it is fully hydrated before we act on it:
  // `networkidle` does not cover client-side transitions, and an action fired while
  // the row-click transition is still committing gets its result discarded.
  await page.goto(page.url());
  await page.waitForLoadState("networkidle");
  const detail = page.getByTestId("disbursement-detail");
  await expect(detail).toBeVisible({ timeout: 15_000 });
  await expect(detail).toContainText(description);
  return detail;
}

async function openTrustTransactions(page: Page) {
  await page.goto(`${BASE}/trust-accounting/transactions`);
  await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({
    timeout: 15_000,
  });
}

test.describe.serial("Day 5 — Disbursements (Phase 67)", () => {
  // ── Day 1 prerequisite: Sipho's litigation matter ─────────────────
  test("Alice (Thandi): Open Sipho's litigation matter from the Litigation template", async ({
    page,
  }) => {
    const matterName = siphoMatterName(startDepthIiRun());
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await page.getByRole("button", { name: "New from Template" }).click();

    const dialog = page.getByRole("dialog", { name: /New from Template/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("option", { name: /Litigation \(Personal Injury/ }).click();
    await dialog.getByRole("button", { name: "Next" }).click();

    await dialog.getByLabel(/Matter name/).fill(matterName);
    // Lifecycle Day 1 creates "Sipho Ndlovu <run>"; record the exact client used
    recordSiphoClient(await selectNativeOption(dialog.getByLabel(/^Client/), SIPHO_CLIENT));
    await dialog.getByRole("button", { name: "Create Matter" }).click();

    await page.waitForURL(/\/projects\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: matterName })).toBeVisible();
    await expect(page.locator("main")).toContainText(/Sipho Ndlovu/);
  });

  // ── 5.5: Navigate to disbursements list page ──────────────────────
  test("Bob: Navigate to Legal → Disbursements list page", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/legal/disbursements`);

    const listPage = page.getByTestId("disbursements-list-page");
    const notFound = page.getByText(/could not be found/i);
    await expect(listPage.or(notFound)).toBeVisible({ timeout: 15_000 });
    test.skip(await notFound.isVisible(), "disbursements module not enabled on legal-za profile");

    await expect(page.getByRole("heading", { level: 1, name: "Disbursements" })).toBeVisible();
    await expect(page.getByTestId("create-disbursement-trigger")).toBeVisible();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursements-list-empty");
  });

  // ── 5.6-5.8: Bob creates sheriff-fee OFFICE_ACCOUNT disbursement ──
  test("Bob: Create sheriff-fee OFFICE_ACCOUNT disbursement + submit", async ({ page }) => {
    await loginAs(page, "bob");
    const dialog = await openNewDisbursementDialog(page);

    await selectNativeOption(dialog.getByLabel("Matter"), siphoMatterName());
    await selectNativeOption(dialog.getByLabel("Client"), siphoClientName());
    await dialog.getByLabel("Category").selectOption("SHERIFF_FEES");
    // Sheriff fees are statutory pass-through charges — VAT auto-seeds to zero-rated.
    await expect(dialog.getByLabel("VAT Treatment")).toHaveValue("ZERO_RATED_PASS_THROUGH");
    await dialog.getByLabel("Description").fill(SHERIFF_FEE_DESCRIPTION);
    await dialog.getByTestId("disbursement-amount-input").fill("750.00");
    await expect(dialog.getByRole("radio", { name: "Office Account" })).toBeChecked();
    await dialog.getByLabel("Incurred Date").fill(isoDate());
    await dialog.getByLabel("Supplier", { exact: true }).fill("Sheriff Johannesburg North");
    await dialog.getByLabel(/Supplier Reference/).fill("SHF-2026-0457");
    await dialog.getByRole("button", { name: "Create Disbursement" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // 5.8: new row is DRAFT → open detail → Submit for Approval → Pending
    const row = page
      .locator('[data-testid^="disbursement-row-"]')
      .filter({ hasText: siphoMatterName() })
      .filter({ hasText: SHERIFF_FEE_DESCRIPTION });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("Sheriff Fees");
    await expect(row).toContainText("R 750,00");
    await expect(row).toContainText("Draft");

    const detail = await openDisbursementDetail(page, SHERIFF_FEE_DESCRIPTION);
    await detail.getByTestId("disbursement-submit-for-approval-button").click();
    await expect(detail.getByText("Pending", { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(detail.getByTestId("disbursement-submit-for-approval-button")).toBeHidden();
    await expect(detail).toContainText("Office Account");

    await page.goto(`${BASE}/legal/disbursements`);
    await expect(row).toContainText("Pending", { timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursement-sheriff-fee-created");
  });

  // ── 5.9 prerequisite: approved trust payment on Sipho's matter ────
  test("Alice + Bob: Fund Sipho's trust ledger and approve a deeds-office trust payment", async ({
    page,
  }) => {
    // Alice (Thandi): make sure the firm has a trust account (lifecycle Day 0 never creates one)
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting`);
    const addAccount = page.getByRole("button", { name: "Add Account" });
    const recordTransaction = page.getByRole("link", { name: "Record Transaction" });
    await expect(addAccount.or(recordTransaction).first()).toBeVisible({ timeout: 15_000 });
    if (await addAccount.isVisible()) {
      await addAccount.click();
      const account = page.getByRole("dialog", { name: "Add Trust Account" });
      await account.getByLabel("Account Name").fill("Mathebula & Partners Trust Account");
      await account.getByLabel("Bank Name").fill("Standard Bank");
      await account.getByLabel("Branch Code").fill("051001");
      await account.getByLabel("Account Number").fill("0123456789");
      await account.getByRole("button", { name: "Create Account" }).click();
      await expect(account).toBeHidden({ timeout: 15_000 });
      await expect(recordTransaction).toBeVisible({ timeout: 15_000 });
    }

    // Alice: R5,000 client deposit into trust for Sipho's matter
    await openTrustTransactions(page);
    await page.getByTestId("record-transaction-button").click();
    await page.getByRole("menuitem", { name: "Record Deposit" }).click();
    const deposit = page.getByRole("dialog", { name: "Record Deposit" });
    await deposit.getByRole("combobox").first().click();
    await page
      .getByRole("option", { name: new RegExp(`^${escapeRegExp(siphoClientName())}(\\s|$)`) })
      .click();
    await deposit.getByRole("combobox").nth(1).click();
    await page.getByRole("option", { name: new RegExp(escapeRegExp(siphoMatterName())) }).click();
    await deposit.getByLabel("Amount").fill(TRUST_DEPOSIT_AMOUNT);
    await deposit.getByLabel("Reference").fill(`DEP/${siphoMatterName().split(" ").pop()}`);
    await deposit.getByLabel(/Description/).fill("Client deposit — RAF claim disbursements");
    await deposit.getByRole("button", { name: "Record Deposit" }).click();
    await expect(deposit).toBeHidden({ timeout: 15_000 });
    await expect(
      page.locator("main tbody tr", { hasText: `DEP/${siphoMatterName().split(" ").pop()}` })
    ).toContainText("RECORDED", { timeout: 15_000 });

    // Bob: records the R1,250 payment to the Deeds Office from Sipho's trust ledger
    await loginAs(page, "bob");
    await openTrustTransactions(page);
    await page.getByTestId("record-transaction-button").click();
    await page.getByRole("menuitem", { name: "Record Payment" }).click();
    const payment = page.getByRole("dialog", { name: "Record Payment" });
    await payment.getByTestId("trust-payment-customer-trigger").click();
    await page
      .getByRole("option", { name: new RegExp(`^${escapeRegExp(siphoClientName())}(\\s|$)`) })
      .click();
    await payment.getByTestId("trust-payment-matter-trigger").click();
    await page.getByRole("option", { name: new RegExp(escapeRegExp(siphoMatterName())) }).click();
    await payment.getByLabel("Amount").fill(DEEDS_FEE_AMOUNT);
    await payment.getByLabel("Reference").fill(deedsPaymentReference());
    await payment
      .getByLabel(/Description/)
      .fill(`${DEEDS_FEE_DESCRIPTION} — Deeds Office Pretoria`);
    await payment.getByRole("button", { name: "Record Payment" }).click();
    await expect(payment).toBeHidden({ timeout: 15_000 });
    const paymentRow = page.locator("main tbody tr", { hasText: deedsPaymentReference() });
    await expect(paymentRow).toContainText("AWAITING APPROVAL", { timeout: 15_000 });

    // Alice (Thandi, APPROVE_TRUST_PAYMENT): the recorder may not self-approve
    await loginAs(page, "alice");
    await openTrustTransactions(page);
    await paymentRow.getByRole("button", { name: "Approve" }).click();
    await expect(paymentRow).toContainText("APPROVED", { timeout: 15_000 });
    await expect(paymentRow).toContainText("Payment");
  });

  // ── 5.9: Bob creates deeds-office TRUST_ACCOUNT disbursement + trust link ──
  // PRODUCT BUG (reproduced on the e2e stack): the trust-link dialog never lists the
  // approved trust payment. `fetchApprovedTrustDisbursementPayments`
  // (app/(app)/org/[slug]/legal/disbursements/actions.ts) keeps only rows with
  // transactionType === "DISBURSEMENT_PAYMENT", but the trust ledger records payments as
  // "PAYMENT" (chk_trust_txn_type has no DISBURSEMENT_PAYMENT value and
  // DisbursementService.EXPECTED_TRUST_TRANSACTION_TYPE is "PAYMENT"). The dialog shows
  // "No approved disbursement-payment trust transactions found for this matter." even
  // with the approved PAY/DEEDS/<run> payment above, so a TRUST_ACCOUNT disbursement
  // cannot be created from the UI.
  test.fixme("Bob: Create deeds-office TRUST_ACCOUNT disbursement with trust link", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    const dialog = await openNewDisbursementDialog(page);

    await selectNativeOption(dialog.getByLabel("Matter"), siphoMatterName());
    await selectNativeOption(dialog.getByLabel("Client"), siphoClientName());
    await dialog.getByLabel("Category").selectOption("DEEDS_OFFICE_FEES");
    await dialog.getByLabel("Description").fill(DEEDS_FEE_DESCRIPTION);
    await dialog.getByRole("radio", { name: "Trust Account" }).click();
    await expect(dialog.getByTestId("trust-link-slot")).toBeVisible();
    await dialog.getByTestId("trust-link-button").click();

    const linkDialog = page.getByTestId("trust-transaction-link-dialog");
    await expect(linkDialog).toBeVisible();
    const txRow = linkDialog
      .locator('[data-testid^="trust-tx-row-"]')
      .filter({ hasText: deedsPaymentReference() });
    await expect(txRow).toBeVisible({ timeout: 15_000 });
    await txRow.getByRole("radio").check();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "trust-link-dialog", { curated: true });
    await linkDialog.getByTestId("trust-tx-link-confirm").click();

    await expect(dialog.getByTestId("linked-trust-tx")).toContainText(deedsPaymentReference());
    // Amount is locked to the linked trust payment
    await expect(dialog.getByTestId("disbursement-amount-input")).toHaveValue(/1250/);
    await dialog.getByLabel("Incurred Date").fill(isoDate());
    await dialog.getByLabel("Supplier", { exact: true }).fill("Deeds Office Pretoria");
    await dialog.getByRole("button", { name: "Create Disbursement" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    const detail = await openDisbursementDetail(page, DEEDS_FEE_DESCRIPTION);
    await expect(detail).toContainText("Trust Account");
    await expect(detail.getByTestId("trust-tx-link")).toBeVisible();
    await detail.getByTestId("disbursement-submit-for-approval-button").click();
    await expect(detail.getByText("Pending", { exact: true })).toBeVisible({ timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursement-deeds-office-trust-linked");
  });

  // ── 5.10: Approval of the trust-linked disbursement (Thandi/Owner) ─
  // Blocked by the same trust-link product bug as 5.9: the trust-linked disbursement
  // cannot be created, so there is nothing for Thandi to approve.
  test.fixme("Alice (Thandi): Approve trust-linked disbursement", async ({ page }) => {
    await loginAs(page, "alice");
    const detail = await openDisbursementDetail(page, DEEDS_FEE_DESCRIPTION);
    await expect(detail).toContainText("Trust Account");
    await detail.getByTestId("disbursement-approve-button").click();
    const approve = page.getByTestId("disbursement-approve-dialog");
    await expect(approve).toBeVisible();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "disbursement-approval-dialog", { curated: true });
    await approve
      .getByTestId("disbursement-approve-notes-input")
      .fill("Deeds office search paid from trust — approved");
    await approve.getByTestId("disbursement-confirm-approve-button").click();
    await expect(approve).toBeHidden({ timeout: 15_000 });
    await expect(detail.getByText("Approved", { exact: true })).toBeVisible();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-05-disbursement-approved-detail");
  });

  // ── 5.11: Bob approves the office-account (sheriff) disbursement ──
  test("Bob: Approve sheriff-fee OFFICE_ACCOUNT disbursement", async ({ page }) => {
    await loginAs(page, "bob");
    const detail = await openDisbursementDetail(page, SHERIFF_FEE_DESCRIPTION);
    await expect(detail.getByTestId("disbursement-approval-panel")).toBeVisible();
    await detail.getByTestId("disbursement-approve-button").click();

    const approve = page.getByTestId("disbursement-approve-dialog");
    await expect(approve).toBeVisible();
    await approve
      .getByTestId("disbursement-approve-notes-input")
      .fill("Sheriff return of service received — approved for billing");
    await approve.getByTestId("disbursement-confirm-approve-button").click();
    await expect(approve).toBeHidden({ timeout: 15_000 });

    await expect(detail.getByText("Approved", { exact: true })).toBeVisible();
    await expect(detail.getByText("Unbilled", { exact: true })).toBeVisible();
    await expect(detail.getByTestId("disbursement-approval-panel")).toBeHidden();
    await expect(detail).toContainText("Sheriff return of service received");
  });

  // ── 5.12 (part 1): disbursements on Sipho's matter Finance tab ────
  test("Alice: Sipho's matter Finance → Disbursements tab lists the disbursements", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatterName());
    await openGroupedTab(page, "finance", "disbursements");

    const tab = page.getByTestId("project-disbursements-tab");
    await expect(tab).toBeVisible();
    const sheriffRow = tab
      .locator('[data-testid^="disbursement-row-"]')
      .filter({ hasText: SHERIFF_FEE_DESCRIPTION });
    await expect(sheriffRow).toContainText("Approved", { timeout: 15_000 });
    await expect(sheriffRow).toContainText("Unbilled");
    await expect(sheriffRow).toContainText("R 750,00");

    // 5.13 terminology: legal-za uses "Disbursement" — the generic Expenses tab is hidden
    await expect(tab.getByRole("heading", { name: "Disbursements" })).toBeVisible();
    await expect(page.locator("main")).not.toContainText(/Unbilled\s+Expenses/i);
    // Finance sub-tab menu offers Disbursements and no separate Expenses entry
    await page.getByTestId("tab-group-finance").click();
    await expect(page.getByTestId("tab-item-disbursements")).toBeVisible();
    await expect(page.getByTestId("tab-item-expenses")).toHaveCount(0);
    await page.keyboard.press("Escape");
    await expect(page.getByTestId("tab-item-disbursements")).toBeHidden();
    await captureScreenshot(page, "day-05-matter-unbilled-disbursements-summary");

    // The curated `disbursement-list-view` screenshot shows the org-level list page.
    await page.goto(`${BASE}/legal/disbursements`);
    await expect(page.getByTestId("disbursement-list")).toBeVisible({ timeout: 15_000 });
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "disbursement-list-view", { curated: true });
  });

  // ── 5.12 (part 2): "Unbilled Disbursements: R2,000.00" summary ────
  // PRODUCT GAP (reproduced): the matter Finance → Disbursements tab
  // (components/legal/project-disbursements-tab.tsx) renders only the disbursement table —
  // there is no "Unbilled Disbursements" summary on the matter at all. The R2,000.00
  // figure also needs the trust-linked deeds-office disbursement (blocked, see 5.9).
  test.fixme("Alice: Verify Unbilled Disbursements summary on Sipho's matter", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatterName());
    await openGroupedTab(page, "finance", "disbursements");
    await expect(page.getByTestId("project-disbursements-tab")).toContainText(
      /Unbilled\s+Disbursements:?\s*R\s?2[\s,]?000[.,]00/
    );
  });
});
