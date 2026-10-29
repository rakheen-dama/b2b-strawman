/**
 * Day 75 — Complex Engagement & Adverse Parties
 *
 * A second Sipho matter (a property dispute), two more adverse parties linked to Sipho's matters
 * (the Road Accident Fund from Day 14 as the opposing party in the RAF claim), a conflict "stress
 * test" (an existing client and two adverse parties), progress on the Moroka estate (tasks done,
 * six hours logged), an information request to Moroka, and a second Apex matter.
 *
 * Records come from the lifecycle run (helpers/legal-lifecycle.ts): Days 0–60 must have run on the
 * same stack.
 *
 * Adverse parties are linked to a matter before they are searched: a conflict check that matches an
 * adverse party with no matter link crashes the result panel (628E-BLOCKER,
 * components/legal/conflict-check-result.tsx:141 — see the Day 14 conflict test).
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 *
 * Run:
 *   E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import {
  BASE,
  isoDate,
  openClient,
  openMatter,
  selectNativeOption,
} from "../../helpers/legal-depth-ii";
import {
  apexAnnualReturns,
  apexClient,
  createTemplateMatter,
  logTimeOnTask,
  morokaClient,
  morokaMatter,
  recordMatterId,
  runConflictCheck,
  siphoClient,
  siphoMatter,
  siphoPropertyMatter,
} from "../../helpers/legal-lifecycle";

const RAF = "Road Accident Fund";
const MOKOENA = "Thabo Mokoena";
const PILLAY = "Pillay Holdings (Pty) Ltd";

/** Adds a party to the adverse-party registry (Legal → Adverse parties). */
async function addAdverseParty(page: Page, name: string, partyType: string): Promise<void> {
  await page.goto(`${BASE}/legal/adverse-parties`);
  await expect(page.getByTestId("adverse-party-registry")).toBeVisible({ timeout: 15_000 });
  const rows = page.getByTestId("adverse-party-table").getByRole("row").filter({ hasText: name });
  await expect(rows).toHaveCount(0);
  await page.getByTestId("create-adverse-party-trigger").click();
  const dialog = page.getByTestId("adverse-party-dialog");
  await expect(dialog.getByRole("heading", { name: "Add Adverse Party" })).toBeVisible();
  await dialog.getByLabel(/^Name/).fill(name);
  await dialog.getByLabel(/^Party Type/).selectOption(partyType);
  await dialog.getByRole("button", { name: "Create Party" }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(rows).toHaveCount(1, { timeout: 15_000 });
}

/** Links a registry party to the open matter (Client → Adverse parties → Link Adverse Party). */
async function linkAdverseParty(
  page: Page,
  opts: { party: string; client: string; relationship: string; description: string }
): Promise<void> {
  await openGroupedTab(page, "client", "adverse-parties");
  const tab = page.getByTestId("project-adverse-parties-tab");
  await tab.getByTestId("link-adverse-party-trigger").click();
  const dialog = page.getByTestId("link-adverse-party-to-project-dialog");
  await expect(dialog.getByRole("heading", { name: "Link Adverse Party" })).toBeVisible();
  // Day 14 may have left more than one "Road Accident Fund" on a reused stack: any one will do,
  // but on a fresh stack each name is unique
  await selectNativeOption(dialog.getByLabel(/^Adverse Party/), opts.party);
  await selectNativeOption(dialog.getByLabel(/^Client/), opts.client);
  await dialog.getByLabel(/^Relationship/).selectOption(opts.relationship);
  await dialog.getByLabel("Description").fill(opts.description);
  await dialog.getByRole("button", { name: "Link Party" }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(tab.getByRole("row").filter({ hasText: opts.party })).toHaveCount(1, {
    timeout: 15_000,
  });
}

test.describe.serial("Day 75 — Complex Engagement & Adverse Parties", () => {
  // ── 75.1-75.2: Sipho 2nd matter (Litigation template) ────────────
  test("Alice: Create 2nd litigation matter for Sipho from template", async ({ page }) => {
    await loginAs(page, "alice");
    const id = await createTemplateMatter(page, {
      template: /^Litigation \(Personal Injury \/ General\)/,
      name: siphoPropertyMatter(),
      client: siphoClient(),
    });
    recordMatterId("siphoProperty", id);
    // The template's tasks came with it
    await openGroupedTab(page, "work", "tasks");
    await expect(
      page
        .getByRole("tabpanel")
        .getByRole("button", { name: "Open task detail for Letter of demand" })
    ).toBeVisible({ timeout: 15_000 });
    // Sipho's client page lists both of his matters
    await openClient(page, siphoClient());
    await openGroupedTab(page, "work", "matters");
    const panel = page.getByRole("tabpanel");
    await expect(panel.getByRole("link", { name: siphoMatter() })).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByRole("link", { name: siphoPropertyMatter() })).toBeVisible();
  });

  // ── 75.3-75.6: Adverse party registry additions ───────────────────
  test("Alice: Add adverse parties Mokoena and Pillay; link RAF, Mokoena and Pillay to Sipho's matters", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    // The Road Accident Fund is already in the registry (Day 14)
    await addAdverseParty(page, MOKOENA, "NATURAL_PERSON");
    await addAdverseParty(page, PILLAY, "COMPANY");

    await openMatter(page, siphoMatter());
    await linkAdverseParty(page, {
      party: RAF,
      client: siphoClient(),
      relationship: "OPPOSING_PARTY",
      description: "Defendant — RAF claim",
    });
    await openMatter(page, siphoPropertyMatter());
    await linkAdverseParty(page, {
      party: MOKOENA,
      client: siphoClient(),
      relationship: "OPPOSING_PARTY",
      description: "Neighbour — boundary encroachment",
    });
    await linkAdverseParty(page, {
      party: PILLAY,
      client: siphoClient(),
      relationship: "WITNESS",
      description: "Managing agent of the adjoining property",
    });
  });

  // ── 75.7-75.10: Conflict stress test ──────────────────────────────
  // The surname alone is too short a fuzzy match for the client record ("Sipho Ndlovu <run>"); it
  // matches both of Sipho's matters by name
  test('Bob: Conflict stress test — search "Ndlovu" flags Sipho\'s two matters (Potential Conflict)', async ({
    page,
  }) => {
    await loginAs(page, "bob");
    const result = await runConflictCheck(page, "Ndlovu");
    await expect(result.getByRole("heading", { level: 3 })).toHaveText("Potential Conflict");
    for (const matter of [siphoMatter(), siphoPropertyMatter()]) {
      const match = result.getByRole("row").filter({ hasText: matter });
      await expect(match, matter).toHaveCount(1);
      await expect(match).toContainText("MATTER NAME");
    }
  });

  test('Bob: Conflict stress test — search "Road Accident Fund" finds the adverse party on the RAF matter', async ({
    page,
  }) => {
    await loginAs(page, "bob");
    const result = await runConflictCheck(page, RAF);
    await expect(result.getByRole("heading", { level: 3 })).toHaveText("Conflict Found");
    const match = result.getByRole("row").filter({ hasText: siphoMatter() });
    await expect(match).toHaveCount(1);
    await expect(match).toContainText(RAF);
    await expect(match).toContainText("OPPOSING PARTY");
  });

  test('Bob: Conflict stress test — search "Mokoena" finds the adverse party on the property matter', async ({
    page,
  }) => {
    await loginAs(page, "bob");
    const result = await runConflictCheck(page, "Mokoena");
    await expect(result.getByRole("heading", { level: 3 })).toHaveText(
      /^(Potential Conflict|Conflict Found)$/
    );
    const match = result.getByRole("row").filter({ hasText: siphoPropertyMatter() });
    await expect(match).toHaveCount(1);
    await expect(match).toContainText(MOKOENA);
    await expect(match).toContainText("OPPOSING PARTY");

    // Screenshot: conflict adverse party detection
    await captureScreenshot(page, "day-75-conflict-adverse-detected");
  });

  // ── 75.11-75.15: Estate progression (Moroka) ─────────────────────
  test("Alice: Progress Moroka estate — claim and complete two estate tasks", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, morokaMatter());
    await openGroupedTab(page, "work", "tasks");
    const panel = page.getByRole("tabpanel");
    const tasks = [
      "Advertise for creditors (Gazette + local newspaper)",
      "Open estate bank account",
    ];
    const taskRow = (task: string) =>
      panel
        .getByRole("row")
        .filter({ has: page.getByRole("button", { name: `Open task detail for ${task}` }) });
    for (const task of tasks) {
      const row = taskRow(task);
      await expect(row, `task "${task}"`).toHaveCount(1, { timeout: 15_000 });
      // Unassigned OPEN task: Claim (→ In Progress, assigned to Alice), then Done
      await row.getByRole("button", { name: "Claim" }).click();
      await expect(row).toContainText("In Progress", { timeout: 15_000 });
      await row.getByRole("button", { name: "Done" }).click();
      // A done task leaves the default (open + in-progress) view
      await expect(row).toHaveCount(0, { timeout: 15_000 });
    }
    // …and is listed under the "Done" filter
    const doneChip = panel.locator("button[aria-pressed]").filter({ hasText: /^Done$/ });
    await doneChip.click();
    await expect(doneChip).toHaveAttribute("aria-pressed", "true");
    for (const task of tasks) {
      await expect(taskRow(task), `done task "${task}"`).toHaveCount(1, { timeout: 15_000 });
      await expect(taskRow(task)).toContainText("Done");
    }
  });

  test("Alice: Log 360min on Moroka estate matter", async ({ page }) => {
    await loginAs(page, "alice");
    await openMatter(page, morokaMatter());
    await logTimeOnTask(page, {
      task: "Prepare Liquidation & Distribution account",
      hours: 6,
      minutes: 0,
      date: isoDate(),
      description: "Preparation of Liquidation & Distribution account",
      rate: "2 500,00",
    });
    // Fees → Time recording shows the six hours against the task
    await openGroupedTab(page, "finance", "time");
    await expect(page.getByRole("tabpanel")).toContainText(
      /Prepare Liquidation & Distribution account\s*6h/,
      { timeout: 15_000 }
    );
  });

  test("Alice: Send information request to Moroka (beneficiary details)", async ({ page }) => {
    await loginAs(page, "alice");
    await openClient(page, morokaClient());
    await openGroupedTab(page, "compliance", "requests");
    const panel = page.getByRole("tabpanel");
    await panel.getByRole("button", { name: "New Request" }).click();
    const dialog = page.getByRole("dialog", { name: "Create Information Request" });
    await expect(dialog).toBeVisible();
    // Ad-hoc request to the client's (auto-provisioned) portal contact
    await expect(dialog.getByTestId("add-ad-hoc-item-button")).toBeVisible({ timeout: 15_000 });
    await dialog.getByTestId("add-ad-hoc-item-button").click();
    await dialog
      .getByTestId("ad-hoc-item-name-0")
      .fill("Beneficiary details — full names, ID numbers and bank confirmations");
    // Pressed from the keyboard: on the suite's 1280×720 viewport the dialog is taller than the
    // screen and does not scroll, so its footer buttons sit off-screen (follow-up:
    // components/ui/dialog.tsx:56-70 has no max-height / overflow-y-auto)
    await dialog.getByTestId("create-request-send-now").press("Enter");
    await expect(dialog).toBeHidden({ timeout: 15_000 });
    const request = panel.locator("tbody tr");
    await expect(request).toHaveCount(1, { timeout: 15_000 });
    await expect(request.getByRole("link", { name: /^REQ-\d+$/ })).toBeVisible();
    await expect(request.getByRole("cell", { name: "Sent", exact: true })).toBeVisible();
    await expect(request).toContainText("0/1 accepted");
  });

  // ── 75.16-75.18: Apex 2nd matter (Commercial template) ────────────
  test("Alice: Create 2nd Commercial matter for Apex (annual returns)", async ({ page }) => {
    await loginAs(page, "alice");
    const id = await createTemplateMatter(page, {
      template: /^Commercial \(Corporate & Contract\)/,
      name: apexAnnualReturns(),
      client: apexClient(),
    });
    recordMatterId("apexAnnualReturns", id);
    await openGroupedTab(page, "work", "tasks");
    await expect(
      page
        .getByRole("tabpanel")
        .getByRole("button", { name: "Open task detail for Due diligence review" })
    ).toBeVisible({ timeout: 15_000 });
  });
});
