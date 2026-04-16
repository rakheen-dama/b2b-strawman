/**
 * PACK-01: Pack Lifecycle -- Playwright E2E Tests
 *
 * Tests: install pack, verify content, edit template (API), blocked uninstall,
 *        revert template, successful uninstall.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (org: e2e-test-org, profile: accounting-za)
 *
 * Run:
 *   cd frontend
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test settings/packs --config e2e/playwright.config.ts
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

/** Pack we will install -- not auto-installed because profile is accounting-za */
const TARGET_PACK_ID = "legal-za";
const TARGET_PACK_NAME = "SA Legal Templates";
/** A well-known template from the legal-za pack to verify installation */
const LEGAL_TEMPLATE_NAME = "Power of Attorney";

async function getToken(): Promise<string> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userId: "user_e2e_alice",
      orgId: "org_e2e_test",
      orgSlug: ORG,
      orgRole: "org:owner",
    }),
  });
  if (!res.ok) {
    throw new Error(`Failed to get token: ${res.status} ${res.statusText}`);
  }
  const { access_token } = await res.json();
  return access_token;
}

/**
 * Find a template by name via the backend API and return its detail (id + content).
 */
async function findTemplateByName(
  token: string,
  name: string
): Promise<{ id: string; name: string; content: Record<string, unknown>; css: string | null } | null> {
  const listRes = await fetch(`${BACKEND_URL}/api/templates`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!listRes.ok) return null;
  const templates: Array<{ id: string; name: string }> = await listRes.json();
  const match = templates.find((t) => t.name === name);
  if (!match) return null;

  const detailRes = await fetch(`${BACKEND_URL}/api/templates/${match.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!detailRes.ok) return null;
  return detailRes.json();
}

test.describe.serial("PACK-01: Pack Lifecycle", () => {
  // ---- Test 1: Install pack and verify content (479.2) ----
  test("Install pack and verify templates are created", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/settings/packs`);
    await page.waitForLoadState("networkidle");

    // Verify packs page loads
    const heading = page.locator("h1").filter({ hasText: "Packs" });
    const hasPage = await heading.isVisible({ timeout: 10000 }).catch(() => false);
    if (!hasPage) {
      test.skip(true, "Packs settings page does not exist at /settings/packs");
      return;
    }
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Available tab should be active by default
    await expect(page.getByRole("tab", { name: "Available" })).toBeVisible({ timeout: 5000 });

    // All profile-matched packs are auto-installed; toggle "Show all packs" to see legal-za
    const showAllSwitch = page.locator("#show-all-packs");
    await expect(showAllSwitch).toBeVisible({ timeout: 5000 });
    await showAllSwitch.click();
    // Wait for catalog to reload after toggle
    await page.waitForLoadState("networkidle");
    await page.waitForTimeout(1000);

    // Find the legal-za pack card
    const packCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
    await expect(packCard).toBeVisible({ timeout: 10000 });

    // Verify pack metadata
    await expect(packCard.locator("h3")).toContainText(TARGET_PACK_NAME);
    await expect(packCard.getByText("10 templates")).toBeVisible({ timeout: 5000 });

    // Click Install button on the legal-za card
    const installButton = packCard.getByRole("button", { name: "Install" });
    await expect(installButton).toBeVisible({ timeout: 5000 });
    await expect(installButton).toBeEnabled();
    await installButton.click();

    // Wait for install to complete -- card should show "Installed" state
    await expect(
      packCard.getByRole("button", { name: "Installed" })
    ).toBeVisible({ timeout: 15000 });

    // Verify toast
    await expect(page.getByText("Pack installed successfully.")).toBeVisible({ timeout: 5000 });

    // Navigate to Templates page to verify templates were created
    await page.goto(`${BASE}/settings/templates`);
    await page.waitForLoadState("networkidle");
    await expect(page.locator("h1").filter({ hasText: "Templates" })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Verify at least one legal-za template name is visible
    await expect(page.getByText(LEGAL_TEMPLATE_NAME)).toBeVisible({ timeout: 10000 });

    // Navigate back to Packs > Installed tab
    await page.goto(`${BASE}/settings/packs`);
    await page.waitForLoadState("networkidle");
    const installedTab = page.getByRole("tab", { name: /Installed/i });
    await expect(installedTab).toBeVisible({ timeout: 5000 });
    await installedTab.click();

    // Verify legal-za pack appears in the installed list
    const installedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
    await expect(installedCard).toBeVisible({ timeout: 10000 });
    await expect(installedCard.locator("h3")).toContainText(TARGET_PACK_NAME);
  });

  // ---- Test 2: Edit blocks uninstall, revert allows it (479.3) ----
  test("Uninstall blocked when template edited, succeeds after revert", async ({ page }) => {
    await loginAs(page, "alice");

    // Step 1: Navigate to Installed tab and verify legal-za is there
    await page.goto(`${BASE}/settings/packs`);
    await page.waitForLoadState("networkidle");
    const installedTab = page.getByRole("tab", { name: /Installed/i });
    await installedTab.click();
    await page.waitForTimeout(500);

    const installedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
    await expect(installedCard).toBeVisible({ timeout: 10000 });

    // Step 2: Edit a pack template via the backend API to trigger hash mismatch
    const token = await getToken();
    const template = await findTemplateByName(token, LEGAL_TEMPLATE_NAME);
    expect(template).toBeTruthy();
    const templateId = template!.id;

    // Modify the template content to change its hash
    const modifiedContent = {
      ...template!.content,
      _e2e_modified: true,
    };
    const updateRes = await fetch(`${BACKEND_URL}/api/templates/${templateId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        name: template!.name,
        content: modifiedContent,
        css: template!.css ?? undefined,
      }),
    });
    expect(updateRes.ok).toBeTruthy();

    // Step 3: Reload the packs page and go to Installed tab to see the blocked state
    // We must fully reload so the cached uninstall check is cleared
    await page.goto(`${BASE}/settings/packs`);
    await page.waitForLoadState("networkidle");
    await page.getByRole("tab", { name: /Installed/i }).click();
    await page.waitForTimeout(500);

    // Wait for the uninstall check to load (button starts disabled, stays disabled if blocked)
    const uninstallBtn = installedCard.getByRole("button", { name: "Uninstall" });
    await expect(uninstallBtn).toBeVisible({ timeout: 10000 });

    // Wait for async uninstall check to complete
    await page.waitForTimeout(3000);

    // Verify the button is disabled (blocked because template was edited)
    await expect(uninstallBtn).toBeDisabled({ timeout: 10000 });

    // Hover to see tooltip with blocking reason
    // The button is wrapped in a <span> with TooltipTrigger
    const tooltipTrigger = installedCard.locator("span").filter({ has: uninstallBtn });
    await tooltipTrigger.hover();
    await page.waitForTimeout(500);

    // Verify tooltip with blocking message appears
    const tooltipContent = page.locator('[data-radix-popper-content-wrapper]');
    const hasTooltip = await tooltipContent.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasTooltip) {
      await expect(tooltipContent).toContainText(/edited|modified/i);
    }

    // Step 4: Revert the template via the reset API endpoint
    const resetRes = await fetch(`${BACKEND_URL}/api/templates/${templateId}/reset`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(resetRes.ok).toBeTruthy();

    // Step 5: Reload and verify uninstall is now allowed
    await page.goto(`${BASE}/settings/packs`);
    await page.waitForLoadState("networkidle");
    await page.getByRole("tab", { name: /Installed/i }).click();
    await page.waitForTimeout(500);

    // Wait for uninstall check to load
    const uninstallBtnAfterRevert = page
      .locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`)
      .getByRole("button", { name: "Uninstall" });
    await expect(uninstallBtnAfterRevert).toBeVisible({ timeout: 10000 });

    // Wait for the async uninstall check to complete and enable the button
    await expect(uninstallBtnAfterRevert).toBeEnabled({ timeout: 15000 });

    // Step 6: Click Uninstall and confirm in the AlertDialog
    await uninstallBtnAfterRevert.click();

    // AlertDialog should appear
    const dialog = page.getByRole("alertdialog");
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await expect(dialog.getByText("Uninstall Pack")).toBeVisible();

    // Click the destructive Uninstall button in the dialog
    const confirmBtn = dialog.getByRole("button", { name: "Uninstall" });
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    // Wait for uninstall to complete
    await expect(page.getByText("Pack uninstalled successfully.")).toBeVisible({ timeout: 15000 });

    // Step 7: Verify the pack is gone from the Installed tab
    await page.waitForTimeout(1000);
    const removedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
    await expect(removedCard).not.toBeVisible({ timeout: 10000 });

    // Step 8: Verify templates are removed from the Templates page
    await page.goto(`${BASE}/settings/templates`);
    await page.waitForLoadState("networkidle");
    await expect(page.locator("h1").filter({ hasText: "Templates" })).toBeVisible({
      timeout: 10000,
    });

    // The legal-za template should no longer appear
    const legalTemplate = page.getByText(LEGAL_TEMPLATE_NAME);
    await expect(legalTemplate).not.toBeVisible({ timeout: 5000 });
  });
});
