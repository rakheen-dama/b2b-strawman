import { test, expect } from "@playwright/test";
import { loginAs } from "../fixtures/keycloak-auth";

/**
 * Demo recording: Legal-ZA 90-day walkthrough highlights
 * Run: E2E_AUTH_MODE=keycloak npx playwright test demo-recording --config e2e/playwright.config.ts --headed
 *
 * Records video of key demo moments for Mathebula & Partners law firm.
 * Requires the Keycloak dev stack with existing demo data.
 */

const ORG_SLUG = "mathebula-partners";
const BASE = `/org/${ORG_SLUG}`;
const PAUSE = 2500; // ms between screens for readability
const SHORT = 1500;

test.use({
  video: { mode: "on", size: { width: 1440, height: 900 } },
  viewport: { width: 1440, height: 900 },
  launchOptions: { slowMo: 100 },
});

test("Legal-ZA 90-day demo walkthrough", async ({ page }) => {
  test.setTimeout(300_000); // 5 min max

  // ── Scene 1: Login as Thandi (Owner / Senior Partner) ──
  await loginAs(page, "thandi@mathebula-test.local", "SecureP@ss1");
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 2: Dashboard — Legal terminology + branding ──
  await page.goto(`${BASE}/dashboard`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 3: Sidebar — Legal navigation groups ──
  // Sidebar should show: Matters, Clients, Court Calendar, Trust Accounting, etc.
  await page.waitForTimeout(SHORT);

  // ── Scene 4: Clients list ──
  await page.goto(`${BASE}/customers`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // Click into Sipho Dlamini
  const siphoRow = page.getByText("Sipho Dlamini").first();
  if (await siphoRow.isVisible()) {
    await siphoRow.click();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(PAUSE);

    // Show tabs: Matters, Documents, Onboarding
    await page.goBack();
    await page.waitForLoadState("networkidle");
  }

  // Click into Moroka Family Trust
  const morokaRow = page.getByText("Moroka").first();
  if (await morokaRow.isVisible()) {
    await morokaRow.click();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(PAUSE);
    await page.goBack();
    await page.waitForLoadState("networkidle");
  }

  await page.waitForTimeout(SHORT);

  // ── Scene 5: Matters list ──
  await page.goto(`${BASE}/projects`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // Click into Sipho litigation matter
  const siphoMatter = page.getByText("Sipho Dlamini v.").first();
  if (await siphoMatter.isVisible()) {
    await siphoMatter.click();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(PAUSE);

    // Show Action Items tab
    const actionItemsTab = page.getByRole("tab", { name: /action items/i });
    if (await actionItemsTab.isVisible()) {
      await actionItemsTab.click();
      await page.waitForTimeout(SHORT);
    }

    // Show Time tab
    const timeTab = page.getByRole("tab", { name: /time/i });
    if (await timeTab.isVisible()) {
      await timeTab.click();
      await page.waitForTimeout(SHORT);
    }

    // Show Documents tab
    const docsTab = page.getByRole("tab", { name: /documents/i });
    if (await docsTab.isVisible()) {
      await docsTab.click();
      await page.waitForTimeout(SHORT);
    }

    // Show Activity tab
    const activityTab = page.getByRole("tab", { name: /activity/i });
    if (await activityTab.isVisible()) {
      await activityTab.click();
      await page.waitForTimeout(PAUSE);
    }
  }

  // ── Scene 6: Court Calendar ──
  await page.goto(`${BASE}/court-calendar`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 7: Conflict Check ──
  await page.goto(`${BASE}/conflict-check`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 8: Trust Accounting ──
  await page.goto(`${BASE}/trust-accounting`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // Show sub-pages if tabs exist
  const transactionsTab = page.getByRole("tab", { name: /transactions/i });
  if (await transactionsTab.isVisible().catch(() => false)) {
    await transactionsTab.click();
    await page.waitForTimeout(SHORT);
  }

  const ledgersTab = page.getByRole("tab", { name: /ledgers/i });
  if (await ledgersTab.isVisible().catch(() => false)) {
    await ledgersTab.click();
    await page.waitForTimeout(SHORT);
  }

  // ── Scene 9: Fee Notes ──
  await page.goto(`${BASE}/invoices`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // Click into first fee note
  const feeNoteRow = page.getByText("INV-").first();
  if (await feeNoteRow.isVisible()) {
    await feeNoteRow.click();
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(PAUSE);
    await page.goBack();
    await page.waitForLoadState("networkidle");
  }

  // ── Scene 10: Profitability ──
  await page.goto(`${BASE}/profitability`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 11: Reports ──
  await page.goto(`${BASE}/reports`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 12: My Work ──
  await page.goto(`${BASE}/my-work`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 13: Team ──
  await page.goto(`${BASE}/team`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 14: Settings — General ──
  await page.goto(`${BASE}/settings`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 15: Settings — Rates ──
  await page.goto(`${BASE}/settings/rates`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 16: Settings — Custom Fields ──
  await page.goto(`${BASE}/settings/custom-fields`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Scene 17: Settings — Templates ──
  await page.goto(`${BASE}/settings/templates`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);

  // ── Final pause ──
  await page.goto(`${BASE}/dashboard`);
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(PAUSE);
});
