/**
 * AUTO-02: Automation Triggers — Playwright E2E Tests
 *
 * Tests what the firm sees of each rule's trigger: the trigger type shown per installed rule and the
 * Execution Log's rule / status filters.
 *
 * Not covered here (628F R-3 follow-up): a rule actually FIRING on its event (e.g. a client status
 * change) and a disabled rule NOT firing — that needs an event raised in setup and the Execution Log
 * polled for the resulting execution. These tests used to carry those titles while asserting nothing.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (rules installed with the legal-za pack — automation-templates/legal-za.json)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

/** The four legal-za rules and their trigger badges (components/automations/trigger-type-badge.tsx). */
const LEGAL_ZA_RULES: { name: string; trigger: string }[] = [
  { name: "Matter Onboarding Reminder", trigger: "Client Status" },
  { name: "Investment Maturity Reminder", trigger: "Date Approaching" },
  { name: "Reconciliation Overdue Reminder", trigger: "Date Approaching" },
  { name: "Pending Approval Aging", trigger: "Date Approaching" },
];

async function openExecutionLog(page: Page) {
  await page.goto(`${base}/settings/connections/automations/executions`);
  await expect(page.getByRole("heading", { level: 1, name: "Execution Log" })).toBeVisible({
    timeout: 15_000,
  });
}

test.describe("AUTO-02: Automation Triggers", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Each installed rule shows its trigger type", async ({ page }) => {
    await page.goto(`${base}/settings/connections?tab=automations`);
    await expect(page.getByRole("heading", { level: 1, name: "Automations" })).toBeVisible({
      timeout: 15_000,
    });
    for (const { name, trigger } of LEGAL_ZA_RULES) {
      const row = page.getByTestId("automation-row").filter({ hasText: name });
      await expect(row, name).toHaveCount(1);
      await expect(row.getByText(trigger, { exact: true })).toBeVisible();
    }
  });

  test("Execution Log filters by every installed rule", async ({ page }) => {
    await openExecutionLog(page);
    await page.getByRole("combobox").filter({ hasText: "All Rules" }).click();
    const listbox = page.getByRole("listbox");
    await expect(listbox.getByRole("option", { name: "All Rules" })).toBeVisible();
    for (const { name } of LEGAL_ZA_RULES) {
      await expect(listbox.getByRole("option", { name, exact: true })).toBeVisible();
    }
  });

  test("Execution Log filters by execution status", async ({ page }) => {
    await openExecutionLog(page);
    await page.getByRole("combobox").filter({ hasText: "All Statuses" }).click();
    const listbox = page.getByRole("listbox");
    for (const status of ["All Statuses", "Running", "Completed", "Failed", "Skipped"]) {
      await expect(listbox.getByRole("option", { name: status, exact: true })).toBeVisible();
    }
  });
});
