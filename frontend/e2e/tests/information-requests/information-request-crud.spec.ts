/**
 * IREQ-01: Information Request CRUD — Playwright E2E Tests
 *
 * Information requests live on the matter / client "Information requests" tab (there is no
 * /information-requests list page — only /information-requests/[id]). Tests: the seeded request on
 * the matter tab, creating a draft, sending it, the sent request's detail, and its progress.
 *
 * Seed rows are read-only (628F R-5): the boot seed's SENT request on Website Redesign (to
 * contact@acme.example.com) is only viewed. Creating and sending use this run's own ACTIVE client
 * (portal contact auto-provisioned from its email) and matter — sending a second request to the Acme
 * contact would also spend that contact's magic-link budget, which the portal suite needs.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present (compose/seed/lib/information-requests.sh)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { apiOk, createActiveClient, createMatter } from "../../helpers/api";
import { SEED, websiteRedesignId } from "../../helpers/seed";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT = `IReq Client ${RUN_ID}`;
const CLIENT_EMAIL = `ireq-${RUN_ID}@example.com`;
const ITEM = `Latest bank statement ${RUN_ID}`;

/** Opens a matter's Client → Information requests tab and returns the (unnamed) panel. */
async function openRequestsTab(page: Page, matterId: string) {
  await page.goto(`${base}/matters/${matterId}`);
  await openGroupedTab(page, "client", "requests");
  const panel = page.getByRole("tabpanel");
  await expect(panel.getByRole("button", { name: "New Request" })).toBeVisible({ timeout: 15_000 });
  return panel;
}

/** The requests-table row whose request link is `number` (REQ-nnnn). */
function requestRow(page: Page, number: string) {
  return page
    .getByRole("tabpanel")
    .getByRole("row")
    .filter({ has: page.getByRole("link", { name: number, exact: true }) });
}

test.describe.serial("IREQ-01: Information Request CRUD", () => {
  let matterId: string;
  let requestNumber: string;

  test.beforeAll(async () => {
    const clientId = await createActiveClient(CLIENT, CLIENT_EMAIL);
    matterId = await createMatter(`IReq Matter ${RUN_ID}`, clientId);
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("View information requests list", async ({ page }) => {
    const panel = await openRequestsTab(page, await websiteRedesignId());
    for (const header of ["Request", "Contact", "Status", "Progress", "Sent"]) {
      await expect(panel.getByRole("columnheader", { name: header, exact: true })).toBeVisible();
    }
    // The boot seed's request: REQ-nnnn, SENT
    const seeded = panel
      .getByRole("row")
      .filter({ has: page.getByRole("link", { name: /^REQ-\d{4}$/ }) })
      .filter({ hasText: "Sent" });
    await expect(seeded.first()).toBeVisible();
  });

  test("Create information request", async ({ page }) => {
    const panel = await openRequestsTab(page, matterId);
    await expect(panel.getByText("No information requests yet")).toBeVisible();
    await panel.getByRole("button", { name: "New Request" }).click();

    const dialog = page.getByRole("dialog", { name: "Create Information Request" });
    await expect(dialog).toBeVisible();
    // The client's only portal contact is preselected; no template = ad-hoc items
    await expect(dialog.getByRole("combobox", { name: "Portal Contact" })).toContainText(
      CLIENT_EMAIL,
      { timeout: 15_000 }
    );
    await expect(dialog.getByRole("combobox", { name: "Template" })).toContainText(
      "Ad-hoc (no template)"
    );
    await dialog.getByRole("button", { name: "Add Item" }).click();
    await dialog.getByTestId("ad-hoc-item-name-0").fill(ITEM);
    // Pressed from the keyboard, as the lifecycle suite does (day-75-complex): on the 1280×720
    // viewport the dialog with an item row is taller than the screen and does not scroll, so its
    // footer sits off-screen (follow-up: components/ui/dialog.tsx has no max-height / overflow-y-auto)
    await dialog.getByRole("button", { name: "Save as Draft" }).press("Enter");
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    // The new draft is listed on the matter (persisted: checked after a reload)
    await openRequestsTab(page, matterId);
    const link = page.getByRole("tabpanel").getByRole("link", { name: /^REQ-\d{4}$/ });
    await expect(link).toHaveCount(1, { timeout: 15_000 });
    requestNumber = (await link.textContent())!.trim();
    await expect(requestRow(page, requestNumber)).toContainText("Draft");
  });

  test("Send information request", async ({ page }) => {
    await openRequestsTab(page, matterId);
    await requestRow(page, requestNumber).getByRole("link", { name: requestNumber }).click();
    await expect(page).toHaveURL(/\/information-requests\/[0-9a-f-]{36}/);
    await expect(page.getByRole("heading", { level: 1, name: requestNumber })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText("Draft", { exact: true })).toBeVisible();

    // "Send Request" exists only for a DRAFT (request-detail-client.tsx)
    await page.getByRole("button", { name: "Send Request" }).click();
    await expect(page.getByText("Request sent.")).toBeVisible({ timeout: 15_000 });
    await page.reload();
    await expect(page.getByText("Sent", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: "Send Request" })).toHaveCount(0);
    const requests = await apiOk<{ requestNumber: string; status: string }[]>(
      `/api/information-requests?projectId=${matterId}`
    );
    expect(requests.find((r) => r.requestNumber === requestNumber)?.status).toBe("SENT");
  });

  test("Sent request detail shows the portal contact and requested items", async ({ page }) => {
    // The boot seed's SENT request (read-only: the overflow menu is opened, nothing is clicked)
    await openRequestsTab(page, await websiteRedesignId());
    const seededLink = page
      .getByRole("tabpanel")
      .getByRole("row")
      .filter({ hasText: "Sent" })
      .getByRole("link", { name: /^REQ-\d{4}$/ })
      .first();
    await seededLink.click();
    await expect(page.getByRole("heading", { level: 1, name: /^REQ-\d{4}$/ })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(SEED.contactEmail, { exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { level: 2, name: /^Items \(\d+\)$/ })).toBeVisible();
    await expect(page.getByText(SEED.infoRequestItem, { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Send Request" })).toHaveCount(0);
  });

  test("Track completion status", async ({ page }) => {
    // Nothing returned yet: 0 of the request's 1 item accepted, on the list and on the detail
    await openRequestsTab(page, matterId);
    const row = requestRow(page, requestNumber);
    await expect(row).toContainText("Sent");
    await expect(row).toContainText("0/1 accepted");
    await row.getByRole("link", { name: requestNumber }).click();
    await expect(page.getByRole("heading", { level: 1, name: requestNumber })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText("0/1 accepted")).toBeVisible();
    await expect(page.getByText(ITEM, { exact: true })).toBeVisible();
  });
});
