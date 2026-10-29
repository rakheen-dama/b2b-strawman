/**
 * AUTO-01: Automation CRUD — Playwright E2E Tests
 *
 * Tests automation rule management (Phase 86: list + enable/disable only): view rules,
 * disable, enable, view execution history.
 *
 * The toggled rule ("Investment Maturity Reminder", a date-approaching reminder no other spec relies
 * on) is shared tenant state: the tests disable then re-enable it, and `afterAll` restores the pack's
 * enabled state (628F R-5).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (rules installed with the legal-za pack — automation-templates/legal-za.json)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { apiOk } from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const TOGGLED_RULE = "Investment Maturity Reminder";
const LEGAL_ZA_RULES = [
  "Matter Onboarding Reminder",
  "Investment Maturity Reminder",
  "Reconciliation Overdue Reminder",
  "Pending Approval Aging",
];

interface Rule {
  id: string;
  name: string;
  enabled: boolean;
}

async function ruleNamed(name: string): Promise<Rule> {
  const rules = await apiOk<Rule[]>("/api/automation-rules");
  const rule = rules.find((r) => r.name === name);
  expect(rule, `automation rule "${name}"`).toBeDefined();
  return rule!;
}

/** Settings › Connections › Automations (/settings/automations is a legacy redirect to it). */
async function openAutomations(page: Page) {
  await page.goto(`${base}/settings/automations`);
  await expect(page).toHaveURL(/\/settings\/connections\?tab=automations/);
  await expect(page.getByRole("heading", { level: 1, name: "Automations" })).toBeVisible({
    timeout: 15_000,
  });
}

/** Clicks the rule's switch and waits for the saved state to survive a reload. */
async function toggleTo(page: Page, enabled: boolean) {
  const toggle = page.getByRole("switch", { name: `Toggle ${TOGGLED_RULE}` });
  await expect(toggle).toHaveAttribute("aria-checked", String(!enabled));
  await toggle.click();
  await expect(page.getByText("Rule toggled successfully")).toBeVisible({ timeout: 15_000 });
  await expect(toggle).toHaveAttribute("aria-checked", String(enabled));
  await page.reload();
  await expect(page.getByRole("switch", { name: `Toggle ${TOGGLED_RULE}` })).toHaveAttribute(
    "aria-checked",
    String(enabled),
    { timeout: 15_000 }
  );
}

/** The pack installs the rule enabled — the state every run starts from and leaves behind. */
async function ensureEnabled() {
  const rule = await ruleNamed(TOGGLED_RULE);
  if (!rule.enabled) {
    await apiOk(`/api/automation-rules/${rule.id}/toggle`, { method: "POST" });
  }
}

test.describe.serial("AUTO-01: Automation CRUD", () => {
  // A run that died between "Disable" and "Enable" left the rule off: repair, then restore after
  test.beforeAll(ensureEnabled);
  test.afterAll(ensureEnabled);

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View seeded automation rules", async ({ page }) => {
    await openAutomations(page);
    const rows = page.getByTestId("automation-row");
    for (const name of LEGAL_ZA_RULES) {
      const row = rows.filter({ hasText: name });
      await expect(row, name).toHaveCount(1);
      await expect(row.getByRole("switch", { name: `Toggle ${name}` })).toBeVisible();
    }
  });

  test("Disable automation rule", async ({ page }) => {
    await openAutomations(page);
    await toggleTo(page, false);
    expect((await ruleNamed(TOGGLED_RULE)).enabled).toBe(false);
  });

  test("Enable automation rule", async ({ page }) => {
    await openAutomations(page);
    await toggleTo(page, true);
    expect((await ruleNamed(TOGGLED_RULE)).enabled).toBe(true);
  });

  test("View execution history", async ({ page }) => {
    await openAutomations(page);
    // The link sits under the heading and again under the rule table (admins only)
    const link = page.getByRole("link", { name: /View Execution Log/ }).first();
    await expect(link).toBeVisible();
    await link.click();
    await expect(page).toHaveURL(/\/settings\/connections\/automations\/executions/);
    await expect(page.getByRole("heading", { level: 1, name: "Execution Log" })).toBeVisible({
      timeout: 15_000,
    });
  });
});
