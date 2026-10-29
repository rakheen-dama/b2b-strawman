/**
 * FIN-01: Profitability Dashboard — Playwright E2E Tests
 *
 * Tests the profitability page (Reports › Profitability): the three cards — Team Utilization,
 * Matter Profitability, Client Profitability — each listing this run's own billable time.
 *
 * Setup (API, run-unique, 628F R-5): an ACTIVE client, a matter for it, a matter-scoped billing rate
 * for Alice and 1h30 of billable time logged today, so every card has a known row in the default
 * (current month) range whatever else the stack holds.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (seed members)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import {
  createActiveClient,
  createMatter,
  createMatterRate,
  createTask,
  logTime,
  memberNamed,
} from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT = `Profit Client ${RUN_ID}`;
const MATTER = `Profit Matter ${RUN_ID}`;

/** The card titled `title` (CardTitle renders a div, not a heading — hence the data-slot). */
function card(page: Page, title: string) {
  return page
    .locator("#main-content")
    .locator('[data-slot="card"]')
    .filter({ has: page.locator('[data-slot="card-title"]', { hasText: title }) });
}

test.describe("FIN-01: Profitability Dashboard", () => {
  let aliceName: string;

  test.beforeAll(async () => {
    const alice = await memberNamed("Alice");
    aliceName = alice.name;
    const clientId = await createActiveClient(CLIENT, `profit-${RUN_ID}@example.com`);
    const matterId = await createMatter(MATTER, clientId);
    await createMatterRate(matterId, alice.id, 1200);
    const taskId = await createTask(matterId, `Profit Task ${RUN_ID}`);
    await logTime(taskId, 90, `Profitability E2E ${RUN_ID}`);
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
    // /profitability is a legacy redirect to /reports/profitability (lib/legacy-redirects.ts)
    await page.goto(`${base}/profitability`);
    await expect(page).toHaveURL(/\/reports\/profitability/);
    await expect(page.getByRole("heading", { level: 1, name: "Profitability" })).toBeVisible({
      timeout: 15_000,
    });
  });

  test("Page loads with data", async ({ page }) => {
    // Time is on file this month, so the three cards render instead of the empty state
    await expect(page.getByText("No profitability data yet")).toHaveCount(0);
    for (const title of ["Team Utilization", "Matter Profitability", "Client Profitability"]) {
      await expect(card(page, title)).toBeVisible();
    }
  });

  test("Project profitability view", async ({ page }) => {
    const matters = card(page, "Matter Profitability");
    const row = matters.getByRole("row").filter({ hasText: MATTER });
    await expect(row).toHaveCount(1, { timeout: 15_000 });
    await expect(row).toContainText("1.5");

    // Export CSV downloads the matter table for the selected range
    const download = page.waitForEvent("download");
    await matters.getByTestId("profitability-export-csv").click();
    expect((await download).suggestedFilename()).toMatch(
      /^profitability-\d{4}-\d{2}-\d{2}-to-\d{4}-\d{2}-\d{2}\.csv$/
    );
  });

  test("Customer profitability view", async ({ page }) => {
    const clients = card(page, "Client Profitability");
    await expect(clients.getByRole("columnheader", { name: "Client" })).toBeVisible();
    await expect(clients.getByRole("row").filter({ hasText: CLIENT })).toHaveCount(1, {
      timeout: 15_000,
    });
  });

  test("Utilization metrics", async ({ page }) => {
    const utilization = card(page, "Team Utilization");
    await expect(utilization.getByRole("columnheader", { name: "Total Hours" })).toBeVisible();
    const aliceRow = utilization
      .getByRole("row")
      .filter({ has: page.getByRole("cell", { name: aliceName, exact: true }) });
    await expect(aliceRow).toHaveCount(1, { timeout: 15_000 });
    // Alice's hours include this run's 1.5h billable entry
    await expect(aliceRow).toContainText(/\d+\.\dh/);
  });
});
