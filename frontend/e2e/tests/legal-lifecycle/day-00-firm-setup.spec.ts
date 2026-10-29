/**
 * Day 0 — Firm Setup (Mathebula & Partners, Johannesburg)
 *
 * Alice (Owner) configures the firm for the legal-za vertical profile: currency ZAR, brand colour,
 * billing and cost rates for the three members, the seeded VAT rate, the legal custom fields and
 * matter templates, the trust account and its LPFF rate, and the legal modules in the navigation.
 *
 * The first test starts the lifecycle run (helpers/legal-lifecycle.ts): every later day names its
 * records from that run id. Firm-level setup asserts-or-creates (rates, trust account, LPFF rate),
 * so Day 0 also passes on a reused stack.
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Playwright browsers installed: npx playwright install chromium
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { BASE, isoDate } from "../../helpers/legal-depth-ii";
import {
  ensureTrustAccount,
  randPattern,
  startLifecycleRun,
  TRUST_ACCOUNT,
} from "../../helpers/legal-lifecycle";

/** Scenario 0.42 / 0.43 rate cards (ZAR per hour). */
const MEMBERS = [
  { name: "Alice Owner", role: "owner", billing: "2500", cost: "1000" },
  { name: "Bob Admin", role: "admin", billing: "1200", cost: "500" },
  { name: "Carol Member", role: "member", billing: "550", cost: "200" },
] as const;

/**
 * Rates take effect a year back, so time entries the later days back-date (and any billing period
 * they pick) are rated. The LPFF rate uses the same date: an interest run needs a rate effective on
 * or before its period start (InterestService).
 */
const EFFECTIVE_FROM = isoDate(-365);

/** "2500" → "2 500,00" (formatCurrency en-ZA grouping, matched NBSP-tolerant by randPattern). */
function rand(amount: string): string {
  return `${amount.replace(/\B(?=(\d{3})+$)/g, " ")},00`;
}

/** Home inside the app shell's <main> (a second, hidden `home-page` node renders outside it). */
function homePage(page: Page) {
  return page.locator("#main-content").getByTestId("home-page");
}

async function openRates(page: Page): Promise<void> {
  // Phase 87 S9 (ADR-344): /settings/rates → /settings/fees?tab=tariffs
  await page.goto(`${BASE}/settings/fees?tab=tariffs`);
  await expect(page.getByRole("heading", { level: 1, name: "Rates & Currency" })).toBeVisible({
    timeout: 15_000,
  });
}

/**
 * Sets one member's billing or cost rate unless the rate card already holds it (a reused stack):
 * the row offers "Add rate for …" until a rate exists, then "Edit … rate for …".
 */
async function ensureMemberRate(
  page: Page,
  kind: "billing" | "cost",
  member: (typeof MEMBERS)[number]
): Promise<void> {
  const amount = kind === "billing" ? member.billing : member.cost;
  const row = page.getByTestId(`${kind}-rate-${member.role}`);
  const add = row.getByRole("button", { name: `Add rate for ${member.name}` });
  const edit = row.getByRole("button", { name: `Edit ${kind} rate for ${member.name}` });
  await expect(add.or(edit)).toBeVisible({ timeout: 15_000 });
  if (await add.isVisible()) {
    await add.click();
    const dialog = page.getByRole("dialog", { name: "Add Rate" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel(kind === "billing" ? "Hourly Rate" : "Hourly Cost").fill(amount);
    await dialog.getByLabel("Effective From").fill(EFFECTIVE_FROM);
    await dialog.getByRole("button", { name: "Create Rate" }).click();
    await expect(dialog).toBeHidden({ timeout: 15_000 });
  }
  await expect(row).toContainText(randPattern(rand(amount)), { timeout: 15_000 });
}

test.describe.serial("Day 0 — Firm Setup", () => {
  // ── 0.1: Login and Home ───────────────────────────────────────────
  test("Alice: Home loads after login", async ({ page }) => {
    // Starts the lifecycle run: every client/matter name in Days 1–90 derives from this id.
    startLifecycleRun();
    await loginAs(page, "alice");
    await page.goto(`${BASE}/dashboard`);
    await expect(page).toHaveURL(/dashboard/);
    await expect(homePage(page)).toBeVisible({ timeout: 15_000 });
  });

  // ── 0.2-0.3: Legal-native navigation ─────────────────────────────
  test("Alice: Legal-za profile active — sidebar shows legal terms", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/dashboard`);
    const nav = page.getByRole("navigation", { name: "Main navigation" });
    await expect(nav).toBeVisible({ timeout: 15_000 });

    // Legal-native shell (Phase 87, ADR-338): the sidebar copy is literal legal words — "Matters"
    // instead of "Projects", "Clients" instead of "Customers". There is no profile switch.
    for (const label of ["Matters", "Clients", "Diary", "Conflict check"]) {
      await expect(nav.getByRole("link", { name: label, exact: true })).toBeVisible();
    }
    for (const label of ["Projects", "Customers", "Invoices", "Court Calendar"]) {
      await expect(nav.getByRole("link", { name: label, exact: true })).toHaveCount(0);
    }
  });

  // ── 0.4-0.6: Currency ZAR, brand colour ──────────────────────────
  test("Alice: Settings — currency is ZAR", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/general → /settings/firm?tab=general
    await page.goto(`${BASE}/settings/firm?tab=general`);
    await expect(page.locator("main")).toBeVisible();
    await expect(page.getByText(/ZAR/)).toBeVisible();
  });

  test("Alice: Settings — set brand colour #1B3A4B", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/general → /settings/firm?tab=general
    await page.goto(`${BASE}/settings/firm?tab=general`);
    const brandColor = page.getByLabel("Brand Color");
    await expect(brandColor).toBeVisible({ timeout: 15_000 });
    await brandColor.fill("#1B3A4B");
    await page.getByRole("button", { name: "Save Settings" }).click();
    await expect(page.getByText("Settings saved successfully.")).toBeVisible({ timeout: 15_000 });

    // 0.39: persists on reload
    await page.reload();
    await expect(page.getByLabel("Brand Color")).toHaveValue(/^#1B3A4B$/i, { timeout: 15_000 });
  });

  // ── 0.7-0.13: Billing rates and cost rates ───────────────────────
  test("Alice: Settings — rate cards page loads", async ({ page }) => {
    await loginAs(page, "alice");
    await openRates(page);
    await expect(page.getByRole("tab", { name: "Billing Rates" })).toBeVisible();
    await expect(page.getByRole("tab", { name: "Cost Rates" })).toBeVisible();
  });

  test("Alice: Settings — create billing rates Alice R2,500, Bob R1,200, Carol R550", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await openRates(page);
    for (const member of MEMBERS) await ensureMemberRate(page, "billing", member);
  });

  test("Alice: Settings — create cost rates for Alice R1000, Bob R500, Carol R200", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await openRates(page);
    await page.getByRole("tab", { name: "Cost Rates" }).click();
    await expect(page.getByRole("heading", { name: "Cost Rates" })).toBeVisible();
    for (const member of MEMBERS) await ensureMemberRate(page, "cost", member);
  });

  // ── 0.14-0.15: VAT 15% ───────────────────────────────────────────
  test("Alice: Settings — tax rates page loads", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/tax → /settings/fees?tab=tax
    await page.goto(`${BASE}/settings/fees?tab=tax`);
    await expect(page.getByTestId("tax-rate-row").first()).toBeVisible({ timeout: 15_000 });
  });

  test("Alice: Settings — VAT 15% is seeded as the default tax rate", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/tax → /settings/fees?tab=tax
    await page.goto(`${BASE}/settings/fees?tab=tax`);
    // 0.45: the legal-za profile seeds "VAT — Standard" 15% as the default (V1 baseline +
    // VerticalProfileReconciliationSeeder), so there is nothing to create.
    const vat = page.getByTestId("tax-rate-row").filter({
      has: page.getByTestId("tax-rate-name").getByText("VAT — Standard", { exact: true }),
    });
    await expect(vat).toHaveCount(1, { timeout: 15_000 });
    await expect(vat.getByTestId("tax-rate-value")).toHaveText("15.00%");
    await expect(vat.getByTestId("tax-rate-default")).toHaveText("Default");
  });

  // ── 0.16: Team page ──────────────────────────────────────────────
  test("Alice: Team page shows Alice, Bob, Carol", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/team`);
    await expect(page.getByText("Alice").first()).toBeVisible();
    await expect(page.getByText("Bob").first()).toBeVisible();
    await expect(page.getByText("Carol").first()).toBeVisible();
  });

  // ── 0.17: Custom fields — legal field pack ───────────────────────
  test("Alice: Custom fields — SA Legal matter fields loaded (case number, court, opposing party)", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/custom-fields → /settings/matters?tab=custom-fields
    await page.goto(`${BASE}/settings/matters?tab=custom-fields`);
    const group = page.getByTestId("field-group-legal_za_matter");
    await expect(group).toBeVisible({ timeout: 15_000 });
    const rows = page.getByTestId("field-row");
    for (const field of ["Case Number", "Court", "Opposing Party"]) {
      await expect(
        // the name cell also holds the field's description, so anchor the name at its start
        rows.filter({ has: page.getByRole("cell", { name: new RegExp(`^${field}`) }) }).first(),
        `custom field "${field}" should be listed`
      ).toBeVisible();
    }
  });

  // ── 0.18: Matter templates ────────────────────────────────────────
  test("Alice: 6 legal matter templates listed", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/project-templates → /settings/matters?tab=templates
    await page.goto(`${BASE}/settings/matters?tab=templates`);
    // The legal-za template pack (project-template-packs/legal-za.json)
    const templates = [
      "Litigation (Road Accident Fund -- RAF)",
      "Litigation (Personal Injury / General)",
      "Deceased Estate Administration",
      "Collections (Debt Recovery)",
      "Commercial (Corporate & Contract)",
      "Property Transfer (Conveyancing)",
    ];
    for (const template of templates) {
      await expect(page.getByText(template, { exact: true }).first()).toBeVisible({
        timeout: 15_000,
      });
    }
  });

  // ── 0.19-0.20 (scenario 0.61-0.62): Trust account ────────────────
  test("Alice: Trust account — Mathebula & Partners Trust Account (GENERAL, Standard Bank) exists", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // Creates the primary GENERAL account only if the firm has none (628E R-4)
    await ensureTrustAccount(page);

    // Phase 87 S9 (ADR-344): /settings/trust-accounting → /settings/fees?tab=trust-accounting
    await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
    const accounts = page.getByTestId("trust-accounts-section");
    await expect(accounts).toBeVisible({ timeout: 15_000 });
    await expect(accounts.getByText(TRUST_ACCOUNT.name, { exact: true })).toBeVisible();
    // 0.64: the new account starts at a zero balance
    await page.goto(`${BASE}/trust-accounting`);
    await expect(page.getByRole("link", { name: "Record Transaction" })).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── 0.21 (scenario 0.63): LPFF rate 6.5% ─────────────────────────
  test("Alice: LPFF rate 6.5% — added on Trust › Interest, listed on the trust settings tab", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/interest`);
    await expect(page.getByRole("heading", { level: 1, name: "Interest" })).toBeVisible({
      timeout: 15_000,
    });
    const section = page.getByTestId("lpff-rates-section");
    const empty = section.getByTestId("empty-rates");
    const table = section.getByTestId("lpff-rates-table");
    await expect(empty.or(table)).toBeVisible({ timeout: 15_000 });
    // A reused stack already holds this run's-equivalent rate (6.50% from EFFECTIVE_FROM); add it
    // only when it is absent — two rates with the same effective date are not a scenario step.
    const existing = table.getByRole("row").filter({ hasText: "6.50%" });
    if ((await existing.count()) === 0) {
      await section.getByRole("button", { name: "Add Rate" }).click();
      const dialog = page.getByTestId("lpff-rate-dialog");
      await expect(dialog).toBeVisible();
      await dialog.getByLabel("Effective From").fill(EFFECTIVE_FROM);
      await dialog.getByLabel("Rate %").fill("6.5");
      // The scenario gives no LPFF share; 50% is a stated test value.
      await dialog.getByLabel("LPFF Share %").fill("50");
      await dialog.getByLabel(/Notes/).fill("Scenario 0.63 test rate");
      await dialog.getByRole("button", { name: "Add Rate" }).click();
      await expect(dialog).toBeHidden({ timeout: 15_000 });
    }
    await expect(table.getByRole("row").filter({ hasText: "6.50%" }).first()).toBeVisible({
      timeout: 15_000,
    });

    // The read-only LPFF card on Settings › Fees & Trust › Trust accounting shows the same rate
    await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
    const settingsRates = page.getByTestId("lpff-rates-section");
    await expect(settingsRates.getByRole("row").filter({ hasText: "6.50%" }).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── 0.22: Legal modules in the navigation ────────────────────────
  test("Alice: Legal modules enabled (trust, court, conflict, tariff)", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/dashboard`);
    const nav = page.getByRole("navigation", { name: "Main navigation" });
    await expect(nav).toBeVisible({ timeout: 15_000 });

    // Court dates live in the Diary (613D); conflict checks + adverse parties in the Matters zone
    await expect(nav.getByRole("link", { name: "Diary", exact: true })).toBeVisible();
    await expect(nav.getByRole("link", { name: "Conflict check", exact: true })).toBeVisible();
    await expect(nav.getByRole("link", { name: "Adverse parties", exact: true })).toBeVisible();

    // The Fees and Trust zones start collapsed (lib/nav-items.ts defaultExpanded: false)
    await nav.getByRole("button", { name: "Fees" }).click();
    await expect(nav.getByRole("link", { name: "Tariffs", exact: true })).toBeVisible();
    await nav.getByRole("button", { name: "Trust" }).click();
    for (const label of ["Overview", "Transactions", "Client ledgers", "Interest"]) {
      await expect(nav.getByRole("link", { name: label, exact: true })).toBeVisible();
    }
  });

  // ── 0.23: Screenshot — Home with legal nav ───────────────────────
  test("Alice: Capture dashboard with legal navigation", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/dashboard`);
    await expect(homePage(page)).toBeVisible({ timeout: 15_000 });

    // Regression baseline
    await captureScreenshot(page, "day-00-dashboard-legal-nav-active");
  });
});
