import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;

// Phase 87 S6a (ADR-344): nav — page names are the six-zone sidebar labels (lib/nav-items.ts);
// Profitability's single entry is Firm › Reports, so it is visited at its canonical URL.
const NAV_ITEMS = [
  { name: "Home", path: `${base}/dashboard` },
  { name: "My work", path: `${base}/my-work` },
  // Phase 87 S6b (ADR-344): nav — one Diary replaces Calendar + Court calendar (613D)
  { name: "Diary", path: `${base}/diary` },
  // Phase 87 S4 (ADR-344): route rename
  { name: "Matters", path: `${base}/matters` },
  // Phase 87 S4 (ADR-344): route rename
  { name: "Clients", path: `${base}/clients` },
  { name: "FICA & compliance", path: `${base}/compliance` },
  { name: "Fee notes", path: `${base}/invoices` },
  { name: "Profitability", path: `${base}/reports/profitability` },
  { name: "Reports", path: `${base}/reports` },
  { name: "Team", path: `${base}/team` },
  { name: "Notifications", path: `${base}/notifications` },
  { name: "Settings", path: `${base}/settings` },
];

test.describe("NAV-01: Sidebar Navigation", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  for (const item of NAV_ITEMS) {
    test(`${item.name} page loads`, async ({ page }) => {
      await page.goto(item.path);
      // The page's own content renders in the org layout's <main> (not the error boundary)
      const main = page.locator("#main-content");
      await expect(main.getByRole("heading", { level: 1 }).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(main).not.toContainText("Something went wrong");
    });
  }
});

// Phase 87 S6a (ADR-344): nav — six zones (ADR-339). Alice (owner) on the legal-za e2e org passes
// every capability gate and has every legal-za module on, so she sees all 28 items. Fees, Trust and
// Firm start collapsed and their items are not in the DOM until the zone header is clicked.
const ZONES = [
  { label: "Today", items: ["Home", "My work", "Diary"] },
  {
    label: "Matters",
    items: ["Matters", "New matter", "Conflict check", "Adverse parties", "AI reviews"],
  },
  { label: "Clients", items: ["Clients", "FICA & compliance", "Data requests"] },
  {
    label: "Fees",
    items: ["Unbilled work", "Fee notes", "Fee runs", "Debtors", "Disbursements", "Tariffs"],
  },
  {
    label: "Trust",
    items: [
      "Overview",
      "Transactions",
      "Client ledgers",
      "Reconciliation",
      "Interest",
      "Investments",
      "Trust reports",
    ],
  },
  { label: "Firm", items: ["Team", "Reports", "Audit trail", "Settings"] },
];

test.describe("NAV-02: Sidebar zones", () => {
  test("six zones in order with 28 items in order", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${base}/dashboard`);
    const nav = page.getByRole("navigation", { name: "Main navigation" });

    await expect(nav.locator("button[aria-expanded]")).toHaveText(ZONES.map((z) => z.label));

    for (const zone of ZONES) {
      const header = nav.getByRole("button", { name: zone.label, exact: true });
      // Retry the toggle: a click that lands before NavZone hydrates is dropped.
      await expect(async () => {
        if ((await header.getAttribute("aria-expanded")) === "false") await header.click();
        await expect(header).toHaveAttribute("aria-expanded", "true", { timeout: 1000 });
      }).toPass();
    }

    const labels = ZONES.flatMap((z) => z.items);
    expect(labels).toHaveLength(28);
    await expect(nav.getByRole("link")).toHaveText(labels);
  });
});
