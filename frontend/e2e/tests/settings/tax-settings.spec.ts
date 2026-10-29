/**
 * SET-03: Tax Settings — Playwright E2E Tests
 *
 * Tests tax rate management: view rates (legal-za VAT 15%), create a rate, and the VAT line on
 * the boot seed's sent fee note (read-only).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { SEED, acmeId, apiGet } from "../../helpers/seed";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

const RUN_ID = Date.now().toString(36).slice(-4);

async function openTaxSettings(page: Page) {
  // Phase 87 S9 (ADR-344): /settings/tax → /settings/fees?tab=tax
  await page.goto(`${base}/settings/fees?tab=tax`);
  await expect(page.getByRole("heading", { level: 1, name: "Tax Settings" })).toBeVisible({
    timeout: 10_000,
  });
}

test.describe("SET-03: Tax Settings", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View tax rates", async ({ page }) => {
    await openTaxSettings(page);
    // legal-za seeds "VAT — Standard" at 15% (exact cell: a row's "Deactivate" also contains "vat")
    const vat = page
      .getByRole("row")
      .filter({ has: page.getByRole("cell", { name: "VAT — Standard", exact: true }) });
    await expect(vat).toContainText("15.00%");
    await expect(page.getByRole("button", { name: "Add Tax Rate" })).toBeVisible();
  });

  test("Create tax rate", async ({ page }) => {
    const rateName = `E2E Rate ${RUN_ID}`;
    await openTaxSettings(page);

    await page.getByRole("button", { name: "Add Tax Rate" }).click();
    const dialog = page.getByRole("dialog", { name: "Add Tax Rate" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Name", { exact: true }).fill(rateName);
    await dialog.getByLabel("Rate (%)").fill("10");
    await dialog.getByRole("button", { name: "Create", exact: true }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    await expect(
      page.getByRole("row").filter({ has: page.getByRole("cell", { name: rateName, exact: true }) })
    ).toContainText("10.00%", { timeout: 10_000 });
  });

  test("Tax applies to invoices", async ({ page }) => {
    // The boot seed's approved + sent fee note (Acme Corp / Website Redesign) carries VAT
    const notes = await apiGet<{ notes: string; invoiceNumber: string | null }[]>(
      `/api/invoices?clientId=${await acmeId()}`
    );
    const sent = notes.find((n) => n.notes === SEED.feeNoteSentNotes);
    expect(sent?.invoiceNumber, "seeded sent fee note").toMatch(/^INV-\d{4}$/);

    await page.goto(`${base}/invoices`);
    const link = page.getByRole("link", { name: sent!.invoiceNumber!, exact: true });
    await expect(link).toBeVisible({ timeout: 10_000 });
    await link.click();
    await expect(page).toHaveURL(/\/invoices\/[0-9a-f-]{36}/);
    // Per-line tax breakdown: "<rate name> (<percent>%)" — legal-za's "VAT — Standard" at 15%
    await expect(page.getByText(/^VAT — Standard \(15(\.0+)?%\)$/).first()).toBeVisible({
      timeout: 10_000,
    });
  });
});
