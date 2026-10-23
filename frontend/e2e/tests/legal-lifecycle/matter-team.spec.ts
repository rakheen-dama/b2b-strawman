/**
 * Matter team — Team tab (Phase 88, Epic 619E; U2: G-B3, N-3; ADR-346)
 *
 * Add, transfer and remove on Details → Team, with the canManage gate mirrored in the UI:
 *   - a template matter with no lead chosen auto-adds its creator as lead (619C)
 *   - the lead (Alice, Owner) adds Carol (Member)
 *   - Carol, a plain member, sees the team but no management controls
 *   - Bob (Admin, NOT on the matter) transfers the lead to Carol
 *   - Carol, now the lead, adds Bob and removes him again
 *
 * Self-contained: creates its own run-scoped matter (no client) and asserts only on it.
 * Runs in the legal-lifecycle config (workers: 1) after day-90; it needs only a seeded stack.
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 \
 *     pnpm test:e2e:legal-lifecycle e2e/tests/legal-lifecycle/matter-team.spec.ts
 */
import { test, expect, type Locator, type Page } from "@playwright/test";
import { randomBytes } from "node:crypto";
import { loginAs } from "../../fixtures/auth";
import { BASE, escapeRegExp, openGroupedTab, openMatter } from "../../helpers/legal-depth-ii";

// Module-level: a failed serial group restarts the worker and re-runs the whole group with a new id.
const RUN_ID = `${Date.now().toString(36)}${randomBytes(2).toString("hex")}`;
const MATTER_NAME = `Team spec ${RUN_ID}`;

/** Opens this run's matter and its Details → Team sub-tab (clicked, not ?tab=team — 619D stickiness). */
async function openTeamTab(page: Page): Promise<Locator> {
  await openMatter(page, MATTER_NAME);
  await openGroupedTab(page, "details", "team");
  const panel = page.getByTestId("matter-team-panel");
  await expect(panel.first()).toBeVisible({ timeout: 15_000 });
  return panel;
}

function memberRow(panel: Locator, name: string): Locator {
  return panel.getByRole("row", { name: new RegExp(escapeRegExp(name)) });
}

/**
 * Adds an org member through the Add Member dialog and waits for their row.
 * Row changes commit with the revalidated RSC. The 15 s waits in this file stay
 * deliberately below the 30 s bell poll: a stall means the next@16.2.6 lost-ping
 * regression is back (fixed by next 16.3.8, #1651).
 */
async function addMember(page: Page, panel: Locator, name: string): Promise<void> {
  await panel.getByRole("button", { name: "Add Member" }).click();
  const dialog = page.getByRole("dialog", { name: "Add Member" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("option", { name: new RegExp(escapeRegExp(name)) }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(memberRow(panel, name).first()).toBeVisible({ timeout: 15_000 });
}

test.describe.serial("Matter team — Team tab (add, transfer, remove)", () => {
  test("Alice: template matter auto-adds her as lead; she adds Carol", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await page.goto(`${BASE}/matters`);
    await page.getByRole("button", { name: "New from Template" }).click();
    const dialog = page.getByRole("dialog", { name: /New from Template/ });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("option", { name: /Litigation \(Personal Injury/ }).click();
    await dialog.getByRole("button", { name: "Next" }).click();
    await dialog.getByLabel(/Matter name/).fill(MATTER_NAME);
    // Client "None", Matter lead "Unassigned" → the creator is auto-added as lead (619C)
    await dialog.getByRole("button", { name: "Create Matter" }).click();
    await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
    await expect(page.getByRole("heading", { level: 1, name: MATTER_NAME })).toBeVisible({
      timeout: 15_000,
    });

    await openGroupedTab(page, "details", "team");
    const panel = page.getByTestId("matter-team-panel");
    await expect(panel.first()).toBeVisible({ timeout: 15_000 });
    const aliceRow = memberRow(panel, "Alice Owner");
    await expect(aliceRow.first()).toBeVisible();
    // The lead row has no row menu (Owner badge shows the org role, so lead-ness is the menu's absence)
    await expect(aliceRow.getByRole("button", { name: "Open menu" })).toHaveCount(0);

    await addMember(page, panel, "Carol Member");
    await expect(
      memberRow(panel, "Carol Member").getByText("Member", { exact: true }).first()
    ).toBeVisible();
  });

  test("Carol (Member): sees the matter and its team, but no management controls", async ({
    page,
  }) => {
    await loginAs(page, "carol");
    const panel = await openTeamTab(page);
    await expect(memberRow(panel, "Carol Member").first()).toBeVisible();
    await expect(memberRow(panel, "Alice Owner").first()).toBeVisible();
    await expect(panel.getByRole("button", { name: "Add Member" })).toHaveCount(0);
    await expect(panel.getByRole("button", { name: "Open menu" })).toHaveCount(0);
  });

  test("Bob (Admin, not on the matter): transfers the lead to Carol", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "bob");
    const panel = await openTeamTab(page);
    await expect(memberRow(panel, "Carol Member").first()).toBeVisible();
    await expect(memberRow(panel, "Bob Admin")).toHaveCount(0);

    const carolRow = memberRow(panel, "Carol Member");
    await carolRow.getByRole("button", { name: "Open menu" }).click();
    await page.getByRole("menuitem", { name: "Transfer Lead" }).click();
    const confirm = page.getByRole("alertdialog", { name: "Transfer Lead Role" });
    await expect(confirm).toBeVisible();
    await expect(confirm).toContainText("Transfer the lead role to Carol Member?");
    await expect(confirm).toContainText(
      "Alice Owner, the current lead, will become a regular member."
    );
    await confirm.getByRole("button", { name: "Transfer", exact: true }).click();
    await expect(confirm).toBeHidden({ timeout: 15_000 });

    await expect(carolRow.getByText("Lead", { exact: true }).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(carolRow.getByRole("button", { name: "Open menu" })).toHaveCount(0);
    await expect(
      memberRow(panel, "Alice Owner").getByRole("button", { name: "Open menu" }).first()
    ).toBeVisible();
  });

  test("Carol (now lead): adds Bob, then removes him", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "carol");
    const panel = await openTeamTab(page);
    await expect(
      memberRow(panel, "Carol Member").getByText("Lead", { exact: true }).first()
    ).toBeVisible();

    await addMember(page, panel, "Bob Admin");

    await memberRow(panel, "Bob Admin").getByRole("button", { name: "Open menu" }).click();
    await page.getByRole("menuitem", { name: "Remove", exact: true }).click();
    await expect(memberRow(panel, "Bob Admin")).toHaveCount(0, { timeout: 15_000 });
    await expect(memberRow(panel, "Carol Member").first()).toBeVisible();
    await expect(memberRow(panel, "Alice Owner").first()).toBeVisible();
  });
});
