/**
 * DP-02: Data Protection Settings — Playwright E2E Tests
 *
 * Tests data protection settings: view tab, jurisdiction, information officer,
 * retention policies, processing register, PAIA manual, DSAR management.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { createClient } from "../../helpers/api";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

/**
 * Opens Settings › Firm › Data protection. It is one page (no inner tabs): jurisdiction,
 * information officer, DSAR link, retention policies, processing register, PAIA manual.
 */
async function openDataProtection(page: Page) {
  // Phase 87 S9 (ADR-344): /settings/data-protection → /settings/firm?tab=data-protection
  await page.goto(`${base}/settings/firm?tab=data-protection`);
  await expect(
    page.getByRole("heading", { level: 1, name: "Data Protection", exact: true })
  ).toBeVisible({
    timeout: 10_000,
  });
}

/** Opens Settings › Firm › Data requests (the DSAR list). */
async function openDataRequests(page: Page) {
  await page.goto(`${base}/settings/firm?tab=data-requests`);
  await expect(page.getByRole("heading", { level: 1, name: "DSAR Requests" })).toBeVisible({
    timeout: 10_000,
  });
}

// The CUSTOMER record type is labelled with the legal noun (entityNounLabel → "Client")
const RETENTION_DAYS = "Retention days for Client";
const SAVE_RETENTION = "Save Client policy";

/**
 * Phase 87 S9 (616F, ADR-344 — spec-body fix handed off by 616E): set the data-protection
 * jurisdiction to South Africa through the real control. JurisdictionSelectorSection
 * (components/settings/jurisdiction-selector.tsx) is a Radix Select that saves on selection — it
 * has no Save button — and the FIRST selection opens the "Set Data Protection Jurisdiction"
 * AlertDialog, which must be confirmed. The e2e tenant starts without a jurisdiction. Idempotent:
 * when South Africa is already selected it leaves the control alone. Persistence oracle:
 * PaiaManualSection gets the jurisdiction from the server render, so "Generate PAIA Manual" only
 * turns enabled once the save + router.refresh() have landed.
 */
async function setJurisdictionToSouthAfrica(page: Page) {
  const trigger = page
    .getByRole("combobox")
    .filter({ hasText: /Select a jurisdiction|South Africa/ });
  await expect(trigger).toBeVisible({ timeout: 10_000 });
  if (!/South Africa/.test((await trigger.textContent()) ?? "")) {
    await trigger.click();
    await page.getByRole("option", { name: /South Africa/ }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Confirm" }).click();
  }
  await expect(page.getByRole("button", { name: "Generate PAIA Manual" })).toBeEnabled({
    timeout: 15_000,
  });
}

test.describe("DP-02: Data Protection Settings", () => {
  // The client retention period is tenant-wide: restore it after the file (R-5)
  let originalRetentionDays: string | undefined;

  test.afterAll(async ({ browser }) => {
    if (originalRetentionDays === undefined) return; // the edit test never read it
    const page = await browser.newPage();
    await loginAs(page, "alice");
    await openDataProtection(page);
    await page.getByLabel(RETENTION_DAYS).fill(originalRetentionDays);
    await page.getByRole("button", { name: SAVE_RETENTION }).click();
    await expect(page.getByRole("button", { name: SAVE_RETENTION })).toBeEnabled();
    await page.reload();
    await expect(page.getByLabel(RETENTION_DAYS)).toHaveValue(originalRetentionDays, {
      timeout: 10_000,
    });
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View data protection tab", async ({ page }) => {
    await openDataProtection(page);
    await expect(page.getByRole("heading", { name: "Retention Policies" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Processing Register" })).toBeVisible();
  });

  test("Set jurisdiction to South Africa", async ({ page }) => {
    await openDataProtection(page);
    // Phase 87 S9 (616F, ADR-344): the jurisdiction control saves on selection (no Save button) and
    // its first selection must be confirmed in an AlertDialog. The old generic combobox + "save"
    // click never confirmed it, and once a jurisdiction exists it clicked the Information Officer's
    // disabled Save instead (30 s timeout on a re-run).
    await setJurisdictionToSouthAfrica(page);
  });

  test("Set information officer", async ({ page }) => {
    await openDataProtection(page);
    // Phase 87 S9 (616F, ADR-344 — spec-body fix handed off by 616E): InformationOfficerSection
    // (components/settings/information-officer-section.tsx) renders #officer-name / #officer-email
    // (no name attribute) and its own "Save", disabled until a value differs from the saved one.
    // The unique suffix keeps a re-run against the same stack dirty.
    const nameInput = page.locator("#officer-name");
    const emailInput = page.locator("#officer-email");
    await expect(nameInput).toBeVisible({ timeout: 10_000 });
    await nameInput.fill(`Test Officer ${RUN_ID}`);
    await emailInput.fill("officer@test.com");

    // exact: the retention rows' icon buttons are named "Save {TYPE} policy"
    await page.getByRole("button", { name: "Save", exact: true }).click();
    // Phase 87 S9 (616F, ADR-344 — D-3): outcome observed on the e2e stack before asserting it
    await expect(page.getByText("Saved successfully.")).toBeVisible({ timeout: 10_000 });
  });

  test("View retention policies", async ({ page }) => {
    await openDataProtection(page);
    await expect(page.getByRole("heading", { name: "Retention Policies" })).toBeVisible();
    // The client retention row (compliance packs; ZA defaults once the jurisdiction is set)
    await expect(page.getByLabel(RETENTION_DAYS)).toBeVisible({ timeout: 10_000 });
  });

  test("Edit retention period", async ({ page }) => {
    await openDataProtection(page);
    const days = page.getByLabel(RETENTION_DAYS);
    await expect(days).toBeVisible({ timeout: 10_000 });
    originalRetentionDays = await days.inputValue();
    // Never shorten retention below five years
    const value = String(1825 + (Date.now() % 100));
    await days.fill(value);
    const save = page.getByRole("button", { name: SAVE_RETENTION });
    await save.click();
    await expect(save).toBeEnabled({ timeout: 10_000 });

    // No toast: persistence is the oracle
    await page.reload();
    await expect(page.getByLabel(RETENTION_DAYS)).toHaveValue(value, { timeout: 10_000 });
  });

  test("View processing register", async ({ page }) => {
    await openDataProtection(page);
    await expect(page.getByRole("heading", { name: "Processing Register" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Add Activity" })).toBeVisible();
  });

  test("Generate PAIA manual", async ({ page }) => {
    await openDataProtection(page);
    // Phase 87 S9 (616F, ADR-344 — spec-body fix handed off by 616E): "Generate PAIA Manual" is
    // disabled until a data-protection jurisdiction is set (components/data-protection/
    // paia-manual-section.tsx:53) and the e2e tenant starts without one — set it first
    // (idempotent), then generate.
    await setJurisdictionToSouthAfrica(page);
    const generateBtn = page.getByRole("button", { name: "Generate PAIA Manual" });
    await generateBtn.click();
    // Phase 87 S9 (616F, ADR-344 — D-3): outcome observed on the e2e stack before asserting it
    await expect(page.getByText(/PAIA manual generated:/)).toBeVisible({ timeout: 15_000 });
  });

  test("View DSAR list", async ({ page }) => {
    await openDataProtection(page);
    // Family tab "Data requests" → the DSAR list
    await page.getByRole("tab", { name: "Data requests" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "DSAR Requests" })).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByRole("button", { name: "Log New Request" })).toBeVisible();
  });

  test("Create DSAR", async ({ page }) => {
    // Logged against this file's own client (seed rows are read-only, R-5)
    const clientName = `DSAR Client ${RUN_ID}`;
    const clientId = (
      await createClient(clientName, `dsar-${RUN_ID}@example.com`, { withAddress: false })
    ).id;

    await openDataRequests(page);
    await page.getByRole("button", { name: "Log New Request" }).click();
    const dialog = page.getByRole("dialog", { name: "Log DSAR Request" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Subject Name").fill(`E2E DSAR ${RUN_ID}`);
    await dialog.getByLabel("Client ID").fill(clientId);
    await dialog.getByRole("button", { name: "Log Request" }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    // Listed under the client's name (the list shows subjectName ?? clientName)
    await expect(page.getByRole("row").filter({ hasText: clientName })).toHaveCount(1, {
      timeout: 10_000,
    });
  });
});
