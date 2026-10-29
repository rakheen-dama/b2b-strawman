/**
 * TAG-01: Tags — Playwright E2E Tests
 *
 * Tests tag management: view tags, create tag with colour, apply a tag to a matter.
 *
 * Seed rows are read-only (Phase 88 628F R-5): the tag is applied to this file's own matter.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { seedToken } from "../../helpers/seed";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const RUN_ID = Date.now().toString(36).slice(-4);

async function openTagSettings(page: import("@playwright/test").Page) {
  // Phase 87 S9 (ADR-344): /settings/tags → /settings/matters?tab=tags
  await page.goto(`${base}/settings/matters?tab=tags`);
  await expect(page.getByRole("heading", { level: 1, name: /^Tags/ })).toBeVisible({
    timeout: 10_000,
  });
}

test.describe("TAG-01: Tags", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View tags", async ({ page }) => {
    await openTagSettings(page);
    // "Add Tag" in the header (plus the empty-state CTA when the org has no tags yet)
    await expect(page.getByRole("button", { name: "Add Tag" }).first()).toBeVisible();
  });

  test("Create tag", async ({ page }) => {
    const tagName = `E2E Tag ${RUN_ID}`;
    await openTagSettings(page);

    await page.getByRole("button", { name: "Add Tag" }).first().click();
    const dialog = page.getByRole("dialog", { name: "Create Tag" });
    await expect(dialog).toBeVisible();
    await dialog.getByLabel("Name", { exact: true }).fill(tagName);
    // The colour field is a text input (hex)
    await dialog.getByLabel("Color (optional)").fill("#3B82F6");
    await dialog.getByRole("button", { name: "Create", exact: true }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    await expect(page.getByRole("cell", { name: tagName, exact: true })).toBeVisible({
      timeout: 10_000,
    });
  });

  test("Apply tag to entity", async ({ page }) => {
    const matterName = `Tag Matter ${RUN_ID}`;
    const tagName = `Applied Tag ${RUN_ID}`;
    const token = await seedToken("alice");
    // Phase 87 S1 (ADR-337): API path
    const res = await fetch(`${BACKEND_URL}/api/matters`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: JSON.stringify({ name: matterName, description: "TAG-01 apply-tag target" }),
    });
    expect(res.status).toBe(201);
    const matterId = ((await res.json()) as { id: string }).id;

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/matters/${matterId}`);
    await expect(page.getByRole("heading", { level: 1, name: matterName })).toBeVisible({
      timeout: 10_000,
    });
    // Tags live on Details → Details (the default matter tab is Overview)
    await openGroupedTab(page, "details", "details");
    const panel = page.getByRole("tabpanel");
    await panel.getByRole("button", { name: "Add Tag" }).click();

    // Inline-create a run-unique tag from the picker (owner may create tags inline)
    await page.getByPlaceholder("Search tags...").fill(tagName);
    await page.getByRole("option", { name: `Create "${tagName}"` }).click();
    await expect(panel.getByRole("button", { name: `Remove ${tagName}` })).toBeVisible({
      timeout: 10_000,
    });

    // Persisted on the matter
    await page.reload();
    await openGroupedTab(page, "details", "details");
    await expect(
      page.getByRole("tabpanel").getByRole("button", { name: `Remove ${tagName}` })
    ).toBeVisible({ timeout: 10_000 });
  });
});
