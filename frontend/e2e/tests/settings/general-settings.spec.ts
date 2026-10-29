/**
 * SET-01: General Settings — Playwright E2E Tests
 *
 * Tests org branding settings: view org name/currency, update brand colour,
 * update document footer.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { apiGet } from "../../helpers/seed";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

async function openGeneralSettings(page: Page) {
  // Phase 87 S9 (ADR-344): /settings/general → /settings/firm?tab=general (General is the Firm family's first tab; /settings redirects to /settings/firm)
  await page.goto(`${base}/settings/firm?tab=general`);
  await expect(page.getByRole("heading", { level: 1, name: /^General/ })).toBeVisible({
    timeout: 10_000,
  });
}

/** Saves the General form and waits for the confirmation. */
async function saveSettings(page: Page) {
  await page.getByRole("button", { name: "Save Settings" }).click();
  await expect(page.getByText("Settings saved successfully.")).toBeVisible({ timeout: 10_000 });
}

test.describe("SET-01: General Settings", () => {
  // Brand colour and footer are tenant-wide (portal, PDFs): restore them after the file (R-5)
  let original: { brandColor: string; documentFooterText: string };

  test.beforeAll(async () => {
    const settings = await apiGet<{ brandColor: string | null; documentFooterText: string | null }>(
      "/api/settings"
    );
    original = {
      brandColor: settings.brandColor ?? "",
      documentFooterText: settings.documentFooterText ?? "",
    };
  });

  test.afterAll(async ({ browser }) => {
    const page = await browser.newPage();
    await loginAs(page, "alice");
    await openGeneralSettings(page);
    await page.getByLabel("Brand Color", { exact: true }).fill(original.brandColor);
    await page.getByLabel("Document Footer Text").fill(original.documentFooterText);
    await saveSettings(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View org name and currency", async ({ page }) => {
    await openGeneralSettings(page);
    await expect(page.getByRole("heading", { level: 2, name: "Currency" })).toBeVisible();
  });

  test("Update brand colour", async ({ page }) => {
    await openGeneralSettings(page);
    // The hex text input (the native colour swatch next to it is unlabelled)
    const colour = page.getByLabel("Brand Color", { exact: true });
    await colour.fill("#1a8f6e");
    await saveSettings(page);

    await page.reload();
    await expect(page.getByLabel("Brand Color", { exact: true })).toHaveValue("#1a8f6e", {
      timeout: 10_000,
    });
  });

  test("Update document footer", async ({ page }) => {
    const testFooter = `Test Footer ${Date.now()}`;
    await openGeneralSettings(page);
    await page.getByLabel("Document Footer Text").fill(testFooter);
    await saveSettings(page);

    await page.reload();
    await expect(page.getByLabel("Document Footer Text")).toHaveValue(testFooter, {
      timeout: 10_000,
    });
  });
});
