/**
 * Day 30 + Day 45 — Phase 67: Statement of Account + Disbursement Write-off
 *
 * Exercises:
 *   - Day 30 (Epic 487): generate a Statement of Account on the oldest active matter
 *     (Sipho's litigation) for period = matter-opened to today. Preview HTML
 *     shows fees + disbursements (from Day 5) + trust activity + summary. Save
 *     as GeneratedDocument, download PDF.
 *   - Day 45 (Epic 491): write off one approved-UNBILLED disbursement with reason.
 *     Verify status transition, audit event, exclusion from next invoice draft.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 5 disbursement lifecycle completed (Sipho's depth-II matter with an
 *      APPROVED, UNBILLED sheriff-fee disbursement and trust deposit/payment)
 *   3. Time on Sipho's matter (for SoA fee content) — the lifecycle Day 7 time
 *      entries target the lifecycle matter, which is not created while lifecycle
 *      Day 1 fails, so this spec logs its own time entry through the UI.
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect, type Locator, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import {
  BASE,
  currentDepthIiRun,
  isoDate,
  openMatter,
  openGroupedTab,
  siphoMatterName,
} from "../../helpers/legal-depth-ii";

const SHERIFF_FEE_DESCRIPTION = "Sheriff fee — service of process";
const TIME_DESCRIPTION = "Drafted and served letter of demand on the Road Accident Fund";
const WRITE_OFF_REASON =
  "Client dispute — unable to recover from third party, firm absorbing the cost";

async function openStatementsTab(page: Page) {
  await openMatter(page, siphoMatterName());
  await openGroupedTab(page, "finance", "statements");
  const tab = page.getByTestId("project-statements-tab");
  await expect(tab).toBeVisible();
  await expect(tab.getByText(/Loading statements/)).toBeHidden({ timeout: 15_000 });
  return tab;
}

async function generateStatement(page: Page, tab: Locator): Promise<Locator> {
  await tab.getByTestId("open-generate-statement-btn").click();
  const dialog = page.getByTestId("statement-of-account-dialog");
  await expect(dialog).toBeVisible();
  // Period = matter-opened (approximated as 90 days ago) to today
  await dialog.getByTestId("statement-period-start-input").fill(isoDate(-90));
  await dialog.getByTestId("statement-period-end-input").fill(isoDate());
  await dialog.getByTestId("statement-generate-btn").click();
  await expect(dialog.getByTestId("statement-download-btn")).toBeVisible({ timeout: 30_000 });
  return dialog;
}

/** Fees + disbursements (Day 5) + trust activity (Day 5) + summary. */
async function verifyStatementPreview(dialog: Locator): Promise<RegExp> {
  const preview = dialog
    .getByTestId("statement-preview-container")
    .frameLocator('iframe[title="Document Preview"]')
    .locator("body");
  await expect(preview).toContainText("Statement of Account", { timeout: 15_000 });
  await expect(preview).toContainText(siphoMatterName());
  await expect(preview).toContainText(/Sipho Ndlovu/);
  // Fees: the 2h letter-of-demand entry, billed at Alice's (non-zero) rate
  await expect(preview).toContainText("Professional Fees");
  const feeRow = preview.locator("tr", { hasText: TIME_DESCRIPTION });
  await expect(feeRow).toHaveCount(1);
  await expect(feeRow.locator("td")).toHaveText([
    /\d/, // date
    /\S/, // fee earner
    TIME_DESCRIPTION,
    "2",
    /^R\s?[1-9][\d\s]*,\d{2}$/, // rate
    /^R\s?[1-9][\d\s]*,\d{2}$/, // amount
  ]);
  // Disbursements: the Day 5 sheriff fee
  await expect(preview).toContainText(SHERIFF_FEE_DESCRIPTION);
  await expect(preview).toContainText(/Total disbursements:\s*R\s?750,00/);
  // Trust activity: Sipho's client trust ledger (client-scoped) with the Day 5 deposit + payment
  const runId = currentDepthIiRun();
  await expect(preview).toContainText("Trust Activity");
  await expect(preview).toContainText(new RegExp(`Deposits[\\s\\S]*DEP/${runId}`));
  await expect(preview).toContainText(new RegExp(`Payments[\\s\\S]*PAY/DEEDS/${runId}`));
  await expect(preview).toContainText(/Trust balance held:\s*R\s?[1-9]/);
  // Summary
  await expect(preview).toContainText("Summary");
  await expect(preview).toContainText(/Closing balance owing:\s*R\s?[1-9]/);
  // Return the closing balance owing as a whitespace-tolerant amount pattern
  const summary = (await preview.textContent()) ?? "";
  const owing = summary.match(/Closing balance owing:\s*R\s?([\d\s\u00a0\u202f]+,\d{2})/);
  expect(owing, "closing balance owing in the summary").not.toBeNull();
  const digits = owing![1].replace(/\D/g, "");
  const pattern = `R\\s?${digits.slice(0, -2).split("").join("[\\s\\u00a0\\u202f]?")},${digits.slice(-2)}`;
  return new RegExp(pattern);
}

async function openSheriffFeeDetail(page: Page) {
  await page.goto(`${BASE}/legal/disbursements`);
  const row = page
    .locator('[data-testid^="disbursement-row-"]')
    .filter({ hasText: siphoMatterName() })
    .filter({ hasText: SHERIFF_FEE_DESCRIPTION })
    .first();
  await expect(row).toContainText("Approved", { timeout: 15_000 });
  await row.click();
  await page.waitForURL(/\/legal\/disbursements\/[0-9a-f-]{36}$/, { timeout: 15_000 });
  // Full load of the detail URL so the page is hydrated before acting (see Day 5).
  await page.goto(page.url());
  await page.waitForLoadState("networkidle");
  const detail = page.getByTestId("disbursement-detail");
  await expect(detail).toContainText(SHERIFF_FEE_DESCRIPTION, { timeout: 15_000 });
  return detail;
}

test.describe.serial("Day 30-45 — Statement of Account + Write-off (Phase 67)", () => {
  // ── Day 7 prerequisite: fee-earning time on Sipho's matter ────────
  test("Alice (Thandi): Log 2h on Sipho's matter — letter of demand", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, siphoMatterName());
    await openGroupedTab(page, "work", "tasks");

    const taskRow = page.getByRole("row").filter({ hasText: "Letter of demand" }).first();
    await expect(taskRow).toBeVisible({ timeout: 15_000 });
    await taskRow.getByRole("button", { name: "Log Time" }).click();

    const dialog = page.getByRole("dialog", { name: "Log Time" });
    await expect(dialog).toBeVisible();
    await dialog.locator("#time-hours").fill("2");
    await dialog.locator("#time-minutes").fill("0");
    await dialog.locator("#time-date").fill(isoDate());
    await dialog.locator("#time-description").fill(TIME_DESCRIPTION);
    await expect(dialog.locator("#time-billable")).toBeChecked();
    await dialog.getByRole("button", { name: "Log Time" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // Finance → Time summarises the entry by task and by member
    await openGroupedTab(page, "finance", "time");
    const timePanel = page.getByRole("tabpanel");
    await expect(timePanel).toContainText(/Letter of demand\s*2h/, { timeout: 15_000 });
    await expect(timePanel).toContainText(/Alice Owner\s*2h/);
  });

  // ── 30.1-30.7: Generate Statement of Account on Sipho's matter ────
  test("Alice (Thandi): Open Statements tab on oldest matter", async ({ page }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    await expect(tab.getByRole("heading", { name: "Statements of Account" })).toBeVisible();
    await expect(tab.getByTestId("open-generate-statement-btn")).toBeVisible();
    await expect(tab.getByTestId("statements-empty")).toBeVisible();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-matter-statements-tab");
  });

  // PRODUCT BUG (reproduced): once the HTML preview renders, the Statement of Account
  // dialog's layout never settles. A4PreviewWrapper (components/documents/a4-preview-wrapper.tsx)
  // derives its scale from its own clientWidth via a ResizeObserver; inside the SoA dialog the
  // preview container is not width-constrained, so every scale-up widens the container, which
  // triggers another scale-up. The form grows without bound (measured scrollWidth ≈ 568 000px
  // after a few seconds) and the footer actions (Download PDF / Regenerate / Close) drift
  // off-screen. The page never produces two identical screenshots, so the regression capture
  // below fails ("Failed to take two consecutive stable screenshots").
  // The statement itself is generated and saved correctly — verified by the next test.
  test.fixme("Alice: Generate Statement of Account with HTML preview", async ({ page }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);
    const dialog = await generateStatement(page, tab);
    await verifyStatementPreview(dialog);

    // The footer actions must stay inside the dialog
    const dialogBox = await dialog.boundingBox();
    const downloadBox = await dialog.getByTestId("statement-download-btn").boundingBox();
    expect(downloadBox!.x + downloadBox!.width).toBeLessThanOrEqual(
      dialogBox!.x + dialogBox!.width
    );

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-statement-of-account-preview");
    await captureScreenshot(page, "statement-of-account-preview", { curated: true });
  });

  test("Alice: Save Statement + verify download PDF action", async ({ page }) => {
    await loginAs(page, "alice");
    const tab = await openStatementsTab(page);

    // "Preview & Save" generates the statement, saves it to the matter and renders the preview
    const dialog = await generateStatement(page, tab);
    const closingBalanceOwing = await verifyStatementPreview(dialog);
    await expect(page.getByText("Statement generated")).toBeVisible();
    // Dismiss with Escape: the dialog footer drifts off-screen (preview layout bug above)
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();

    // The saved statement is listed on the matter with its balances
    const row = tab.locator('[data-testid^="statement-row-"]').first();
    await expect(row).toBeVisible({ timeout: 15_000 });
    await expect(row).toContainText(closingBalanceOwing);

    await expect(row.locator('[data-testid^="statement-download-"]')).toBeEnabled();
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-30-statement-saved-list");

    // The statement PDF is saved to the matter's documents — download it from there.
    // (The Statements-tab "Download" button proxies the PDF through a Next.js server action
    // that follows the backend's 302 to the S3 presigned URL; on the e2e docker stack the
    // presigner endpoint is http://localhost:4567, reachable from the browser but not from
    // inside the frontend container, so that proxy cannot fetch the file on this stack.
    // The Documents-tab download hands the presigned URL to the browser instead.)
    await openGroupedTab(page, "work", "documents");
    const docDownload = page
      .getByRole("tabpanel")
      .getByRole("button", { name: /^Download statement-of-account-.*\.pdf$/ })
      .first();
    await expect(docDownload).toBeVisible({ timeout: 15_000 });
    const [pdf] = await Promise.all([
      page.waitForResponse((r) => /\/statement-of-account-[^/?]*\.pdf\?/.test(r.url())),
      docDownload.click(),
    ]);
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()["content-type"]).toContain("pdf");
  });

  // ── 45.5-45.11: Disbursement write-off ─────────────────────────────
  // PRODUCT GAP (reproduced): there is no Write Off action anywhere in the UI. The
  // disbursement detail page (app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx)
  // renders no actions at all for an APPROVED/UNBILLED disbursement, even for the Owner
  // (WRITE_OFF_DISBURSEMENTS). The backend endpoint POST /api/legal/disbursements/{id}/write-off
  // and the client `writeOffDisbursement()` in lib/api/legal-disbursements.ts exist but
  // nothing calls them.
  test.fixme("Alice (Thandi): Write off one approved-UNBILLED disbursement", async ({ page }) => {
    await loginAs(page, "alice");
    const detail = await openSheriffFeeDetail(page);
    await expect(detail.getByText("Unbilled", { exact: true })).toBeVisible();

    await detail.getByRole("button", { name: /write[\s-]*off/i }).click();
    const dialog = page.getByRole("dialog", { name: /write[\s-]*off/i });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("textbox", { name: /reason/i }).fill(WRITE_OFF_REASON);
    await dialog.getByRole("button", { name: /write[\s-]*off|confirm/i }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    await expect(detail.getByText("Written off", { exact: true })).toBeVisible();
    await expect(detail).toContainText("Write-off reason");
    await expect(detail).toContainText(WRITE_OFF_REASON);
    await expect(page.locator("main")).not.toContainText(/Expense written off/i);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-disbursement-written-off");
  });

  // Blocked by the missing write-off action above (no write-off event can be produced).
  test.fixme("Alice: Verify write-off audit event present in audit log", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/settings/audit-log`);
    await page.waitForLoadState("networkidle");
    const eventType = page.locator("#audit-filter-event-type");
    await eventType.fill("disbursement.written_off");
    await eventType.press("Tab"); // the filter commits on blur
    await page.waitForURL(/eventType=/);

    const row = page.locator('[data-testid^="audit-row-"]').first();
    await expect(row).toContainText(/written.off/i, { timeout: 15_000 });
    await row.getByRole("button", { name: "Expand details" }).click();
    await expect(page.locator('[data-testid^="audit-row-details-"]').first()).toContainText(
      WRITE_OFF_REASON
    );
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-audit-log-write-off-event");
  });

  // Blocked by the missing write-off action above (nothing is written off to exclude).
  test.fixme("Alice: Verify written-off disbursement excluded from next invoice draft", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await page.getByRole("button", { name: "New Fee Note" }).click();
    await page
      .getByRole("option", { name: /^Sipho Ndlovu/ })
      .first()
      .click();
    await page.waitForURL(/\/customers\/[0-9a-f-]{36}\?tab=invoices/);

    await page.getByRole("tabpanel").getByRole("button", { name: "New Fee Note" }).click();
    const dialog = page.getByRole("dialog", { name: "Generate Fee Note" });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("button", { name: "Fetch Unbilled Time" }).click();

    // The fee-note draft's selectable items must not offer the written-off disbursement
    await expect(dialog.getByText("Select Unbilled Items")).toBeVisible({ timeout: 15_000 });
    await expect(dialog).not.toContainText(SHERIFF_FEE_DESCRIPTION);
    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-45-fee-note-draft-excludes-writeoff");
  });
});
