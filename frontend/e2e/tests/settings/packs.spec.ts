/**
 * PACK-01: Pack Lifecycle -- Playwright E2E Tests
 *
 * Tests: seeded pack is installed with its templates, editing a pack template blocks
 *        uninstall, reverting the template's content clears the "edited" block.
 *
 * Phase 86 (Epic 604): the e2e seed provisions legal-za, so the legal-za template pack (like
 * every other pack in the catalogue — common, compliance-za) is auto-installed. There is no
 * longer an uninstalled pack to drive the install flow, and uninstalling the legal pack would
 * strip templates other specs rely on, so this suite is non-destructive: it asserts the
 * installed state and exercises the edit → blocked → revert guard without uninstalling.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (org: e2e-test-org, profile: legal-za)
 *
 * Run:
 *   cd frontend
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test settings/packs --config e2e/playwright.config.ts
 */
import { test, expect } from "@playwright/test";
import { loginAs, getApiToken } from "../../fixtures/auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

/** Pack auto-installed by the legal-za profile */
const TARGET_PACK_ID = "legal-za";
const TARGET_PACK_NAME = "SA Legal Templates";
/** A well-known template from the legal-za pack */
const LEGAL_TEMPLATE_NAME = "Power of Attorney";

/**
 * Find a template by name via the backend API and return its detail (id + content).
 */
async function findTemplateByName(
  token: string,
  name: string
): Promise<{
  id: string;
  name: string;
  content: Record<string, unknown>;
  css: string | null;
} | null> {
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

/** Marker the edit step adds to the template content (the pack hash then no longer matches). */
const E2E_EDIT_MARKER = "_e2e_modified";

/**
 * Reverts the pack template to its pack content by PUT-ing the content back without the e2e edit
 * marker, so its content hash matches the pack again. (POST /api/templates/{id}/reset only resets
 * ORG_CUSTOM clones — a pack template answers 400 "Only ORG_CUSTOM templates can be reset".)
 * Returns the PUT status, or null when the template carried no e2e edit.
 */
async function revertTemplateViaApi(token: string, name: string): Promise<number | null> {
  const template = await findTemplateByName(token, name);
  expect(template, `template "${name}"`).toBeTruthy();
  if (!(E2E_EDIT_MARKER in template!.content)) return null;
  const { [E2E_EDIT_MARKER]: _marker, ...content } = template!.content;
  void _marker;
  const res = await fetch(`${BACKEND_URL}/api/templates/${template!.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ name: template!.name, content, css: template!.css ?? undefined }),
  });
  return res.status;
}

/**
 * Read the backend uninstall check for the target pack.
 */
async function fetchUninstallCheck(
  token: string
): Promise<{ canUninstall: boolean; blockingReason: string | null }> {
  const res = await fetch(`${BACKEND_URL}/api/packs/${TARGET_PACK_ID}/uninstall-check`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok).toBeTruthy();
  return res.json();
}

test.describe.serial("PACK-01: Pack Lifecycle", () => {
  // Cleanup: never leave the pack template edited, so reruns start clean
  test.afterAll(async () => {
    const token = await getApiToken("alice");
    await revertTemplateViaApi(token, LEGAL_TEMPLATE_NAME);
  });

  // ---- Test 1: Seeded pack is installed and its templates exist ----
  test("Seeded legal-za pack is installed and its templates are present", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S9 (ADR-344): /settings/packs → /settings/connections?tab=packs
    await page.goto(`${BASE}/settings/connections?tab=packs`);

    // Verify packs page loads
    const heading = page.locator("h1").filter({ hasText: "Packs" });
    await expect(heading).toBeVisible({ timeout: 10000 });
    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Installed tab lists the legal-za pack
    const installedTab = page.getByRole("tab", { name: /Installed/i });
    await expect(installedTab).toBeVisible({ timeout: 5000 });
    await installedTab.click();

    const installedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
    await expect(installedCard).toBeVisible({ timeout: 10000 });
    await expect(installedCard.locator("h3")).toContainText(TARGET_PACK_NAME);

    // Templates page shows a legal-za pack template
    // Phase 87 S9 (ADR-344): /settings/templates → /settings/documents?tab=templates
    await page.goto(`${BASE}/settings/documents?tab=templates`);
    await expect(page.locator("h1").filter({ hasText: "Templates" })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.locator("body")).not.toContainText("Something went wrong");
    // Each template row is a link named after it (plus a description that may repeat the words)
    await expect(page.getByRole("link", { name: LEGAL_TEMPLATE_NAME, exact: true })).toBeVisible({
      timeout: 10_000,
    });
  });

  // ---- Test 2: Edit blocks uninstall, revert clears the edit block (479.3) ----
  test("Uninstall blocked when template edited, edit block cleared after revert", async ({
    page,
  }) => {
    await loginAs(page, "alice");

    // Step 1: Edit a pack template via the backend API to trigger hash mismatch
    const token = await getApiToken("alice");
    await revertTemplateViaApi(token, LEGAL_TEMPLATE_NAME);
    const template = await findTemplateByName(token, LEGAL_TEMPLATE_NAME);
    expect(template).toBeTruthy();
    const templateId = template!.id;

    const updateRes = await fetch(`${BACKEND_URL}/api/templates/${templateId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        name: template!.name,
        content: { ...template!.content, [E2E_EDIT_MARKER]: true },
        css: template!.css ?? undefined,
      }),
    });
    expect(updateRes.ok).toBeTruthy();

    // Step 2: Packs page > Installed tab shows Uninstall disabled with the "edited" reason
    // Phase 87 S9 (ADR-344): /settings/packs → /settings/connections?tab=packs
    await page.goto(`${BASE}/settings/connections?tab=packs`);
    await page.getByRole("tab", { name: /Installed/i }).click();

    const installedCard = page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`);
    await expect(installedCard).toBeVisible({ timeout: 10000 });

    const uninstallBtn = installedCard.getByRole("button", { name: "Uninstall" });
    await expect(uninstallBtn).toBeVisible({ timeout: 10000 });
    await expect(uninstallBtn).toBeDisabled({ timeout: 10000 });

    // The button is wrapped in a <span> with TooltipTrigger
    // (its parent: a focusable span, since a disabled button fires no pointer events)
    const tooltipTrigger = uninstallBtn.locator("..");
    await tooltipTrigger.hover();

    const tooltipContent = page.locator("[data-radix-popper-content-wrapper]");
    await expect(tooltipContent).toBeVisible({ timeout: 3000 });
    await expect(tooltipContent).toContainText(/edited|modified/i);

    // Step 3: Revert the template to its pack content (the hash matches the pack again)
    expect(await revertTemplateViaApi(token, LEGAL_TEMPLATE_NAME)).toBe(200);

    // Step 4: The "edited" gate is cleared. Other gates (documents generated from, or clones
    // of, legal templates by sibling specs) may still legitimately block uninstall, so assert
    // on the edit reason rather than on the button becoming enabled.
    const check = await fetchUninstallCheck(token);
    expect(check.blockingReason ?? "").not.toMatch(/edited/i);

    // Phase 87 S9 (ADR-344): /settings/packs → /settings/connections?tab=packs
    await page.goto(`${BASE}/settings/connections?tab=packs`);
    await page.getByRole("tab", { name: /Installed/i }).click();
    await expect(
      page.locator(`[data-testid="pack-card-${TARGET_PACK_ID}"]`).locator("h3")
    ).toContainText(TARGET_PACK_NAME, { timeout: 10000 });
  });
});
