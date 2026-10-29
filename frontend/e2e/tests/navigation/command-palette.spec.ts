/**
 * CMD-01: Command Palette — Playwright E2E Tests
 *
 * Tests the command palette (Cmd+K): opens, search navigates,
 * Escape closes.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

/**
 * Opens the palette with Cmd/Ctrl+K (one handler serves both). A keypress that lands before the
 * provider hydrates is dropped, so retry until the dialog is open.
 */
async function openPalette(page: Page) {
  const palette = page.getByRole("dialog", { name: "Command Palette" });
  await expect(async () => {
    if (!(await palette.isVisible())) await page.keyboard.press("ControlOrMeta+k");
    await expect(palette).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 10_000 });
  return palette;
}

test.describe("CMD-01: Command Palette", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${base}/dashboard`);
    await expect(
      page.locator("#main-content").getByRole("heading", { level: 1 }).first()
    ).toBeVisible({
      timeout: 15_000,
    });
  });

  test("Cmd+K opens palette", async ({ page }) => {
    const palette = await openPalette(page);
    await expect(palette.getByPlaceholder("Search pages, settings...")).toBeVisible();
  });

  test("Search navigates to page", async ({ page }) => {
    const palette = await openPalette(page);
    // Phase 87 611B (ADR-338): label amendment
    await palette.getByPlaceholder("Search pages, settings...").fill("Fee notes");
    const item = palette.getByRole("option", { name: "Fee notes" });
    await expect(item).toBeVisible();
    await item.click();
    // Phase 87 611B: auto-waiting URL assertion — the client navigation takes 1.0–1.7s on a used stack
    await expect(page).toHaveURL(/\/invoices$/);
    await expect(palette).toBeHidden();
  });

  test("Escape closes palette", async ({ page }) => {
    const palette = await openPalette(page);
    await page.keyboard.press("Escape");
    await expect(palette).toBeHidden();
  });
});
