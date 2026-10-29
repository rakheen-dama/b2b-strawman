/**
 * FIN-02: Reports — Playwright E2E Tests
 *
 * Tests the reports hub, running the Fee Note Aging report with its parameter, and exporting the
 * run to CSV.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present — the boot seed's SENT fee note (Acme Corp) is outstanding, so the aging
 *      report has at least one row (read-only, 628F R-5)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { todayUtc } from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

/** Opens the Fee Note Aging report and runs it as of today. */
async function runAgingReport(page: Page) {
  await page.goto(`${base}/reports/invoice-aging`);
  await expect(page.getByRole("heading", { level: 1, name: "Fee Note Aging Report" })).toBeVisible({
    timeout: 15_000,
  });
  const exportCsv = page.getByRole("button", { name: "Export CSV" });
  // Export stays disabled until a run (components/reports/report-runner.tsx)
  await expect(exportCsv).toBeDisabled();
  await page.getByLabel("As of Date").fill(todayUtc());
  await page.getByRole("button", { name: "Run Report" }).click();
  const table = page.locator("#main-content").getByRole("table");
  await expect(table.getByRole("columnheader", { name: "Fee Note #" })).toBeVisible({
    timeout: 15_000,
  });
  await expect(
    table
      .getByRole("row")
      .filter({ hasText: /INV-\d{4}/ })
      .first()
  ).toBeVisible();
  await expect(exportCsv).toBeEnabled();
}

test.describe("FIN-02: Reports", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Reports hub loads", async ({ page }) => {
    await page.goto(`${base}/reports`);
    await expect(page.getByRole("heading", { level: 1, name: "Reports" })).toBeVisible({
      timeout: 15_000,
    });
    const main = page.locator("#main-content");
    await expect(main.getByRole("heading", { level: 2, name: "Firm performance" })).toBeVisible();
    await expect(main.getByRole("link", { name: /^Profitability/ })).toBeVisible();
    await expect(main.getByRole("link", { name: /^Fee Note Aging Report/ })).toBeVisible();
  });

  test("Run a report with parameters", async ({ page }) => {
    await page.goto(`${base}/reports`);
    const card = page
      .locator("#main-content")
      .getByRole("link", { name: /^Fee Note Aging Report/ });
    await expect(card).toBeVisible({ timeout: 15_000 });
    await card.click();
    await expect(page).toHaveURL(/\/reports\/invoice-aging$/);
    await runAgingReport(page);
  });

  test("Export report to CSV", async ({ page }) => {
    await runAgingReport(page);
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Export CSV" }).click();
    expect((await download).suggestedFilename()).toBe("invoice-aging-report.csv");
  });
});
