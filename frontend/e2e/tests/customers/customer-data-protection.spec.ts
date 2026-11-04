/**
 * DP-01: Customer Data Protection — Playwright E2E Tests
 *
 * Tests customer data protection actions: download data export (seed client Acme Corp), anonymize
 * preview (Acme Corp, cancelled), execute anonymization (own client), the read-only ANONYMIZED
 * client page and the API refusing a transition out of ANONYMIZED.
 *
 * Seed rows are read-only (Phase 88 628F R-5): only this file's own clients are anonymized.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh (boot seed: Acme Corp)
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { api, apiOk, createClient } from "../../helpers/api";
import { SEED, acmeId } from "../../helpers/seed";

const ORG = "e2e-test-org";
const base = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);

/** A fresh PROSPECT client without a billing address; returns its id. */
async function createOwnClient(name: string): Promise<string> {
  const email = `${name.replace(/\W+/g, "-").toLowerCase()}@example.com`;
  return (await createClient(name, email, { withAddress: false })).id;
}

/** Creates a client and anonymizes it through the API; returns its id. */
async function createAnonymizedClient(name: string): Promise<string> {
  const id = await createOwnClient(name);
  await apiOk(`/api/clients/${id}/anonymize`, {
    method: "POST",
    body: { confirmationName: name, reason: "E2E" },
  });
  return id;
}

async function openClientOverflow(page: Page) {
  await page.getByTestId("client-overflow-trigger").click();
}

test.describe("DP-01: Customer Data Protection", () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Download customer data export", async ({ page }) => {
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients/${await acmeId()}`);
    await expect(
      page.getByRole("heading", { level: 1, name: SEED.client, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });

    await openClientOverflow(page);
    await page.getByRole("menuitem", { name: "Export Data" }).click();
    // The dialog title follows the step: "Download All Data" → "Generating Export" → "Export Ready"
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("heading", { name: "Download All Data" })).toBeVisible();
    await dialog.getByRole("button", { name: "Export Now" }).click();
    await expect(dialog.getByRole("heading", { name: "Export Ready" })).toBeVisible({
      timeout: 30_000,
    });
    // "N files · size" from the 200 ExportStatusResponse (G88-F43: was "files · NaN undefined")
    await expect(dialog).toContainText(/\d+ files? · \d+(\.\d+)? (B|KB|MB|GB)/);
    const archive = dialog.getByRole("link", { name: "Download Archive" });
    await expect(archive).toHaveAttribute("href", /^https?:\/\/.+/);
    const href = await archive.getAttribute("href");
    expect(href).toBeTruthy();
    // The presigned URL is signed for the host-mapped LocalStack (AWS_S3_PRESIGNER_ENDPOINT
    // http://localhost:4567 in compose/docker-compose.e2e.yml), so the runner can fetch it.
    const download = await page.request.get(href as string);
    expect(download.status()).toBe(200);
  });

  test("Anonymize customer preview shows entity counts", async ({ page }) => {
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients/${await acmeId()}`);
    await expect(
      page.getByRole("heading", { level: 1, name: SEED.client, exact: true })
    ).toBeVisible({
      timeout: 10_000,
    });

    await openClientOverflow(page);
    await page.getByRole("menuitem", { name: "Anonymize" }).click();
    const dialog = page.getByRole("alertdialog", { name: "Anonymize Client" });
    await expect(dialog).toBeVisible();
    // The preview lists the affected entities (Acme Corp has the seeded matter and fee notes)
    await expect(dialog).toContainText(/Matters: \d+/, { timeout: 15_000 });
    await expect(dialog).toContainText(/Fee notes: [1-9]\d*/);
    await expect(dialog).toContainText(/Portal contacts: \d+/);
    await expect(dialog).toContainText(/Documents: \d+/);
    await expect(dialog).toContainText(/Time entries: \d+/);
    await expect(dialog).toContainText(/Comments: \d+/);
    await expect(dialog).toContainText(/Custom field values: \d+/);
    await expect(dialog.getByRole("button", { name: "Continue" })).toBeEnabled();

    // Preview only — the seed client must stay intact
    await dialog.getByRole("button", { name: "Cancel" }).click();
    await expect(dialog).toBeHidden();
    await expect(
      page.getByRole("heading", { level: 1, name: SEED.client, exact: true })
    ).toBeVisible();
  });

  test("Execute anonymization", async ({ page }) => {
    const name = `DP Anon ${RUN_ID}`;
    const id = await createOwnClient(name);

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients/${id}`);
    await expect(page.getByRole("heading", { level: 1, name, exact: true })).toBeVisible({
      timeout: 10_000,
    });

    await openClientOverflow(page);
    await page.getByRole("menuitem", { name: "Anonymize" }).click();
    // The dialog title follows the step: "Anonymize Client" → "Confirm Anonymization" → "… Complete"
    const dialog = page.getByRole("alertdialog");
    await expect(dialog.getByRole("heading", { name: "Anonymize Client" })).toBeVisible();
    const proceed = dialog.getByRole("button", { name: "Continue" });
    await expect(proceed).toBeEnabled({ timeout: 15_000 });
    await proceed.click();

    await expect(dialog.getByRole("heading", { name: "Confirm Anonymization" })).toBeVisible();
    await dialog.getByPlaceholder(name).fill(name);
    await dialog.getByRole("button", { name: "Execute Anonymization" }).click();
    await expect(dialog.getByRole("heading", { name: "Anonymization Complete" })).toBeVisible({
      timeout: 30_000,
    });
    await expect(dialog).toContainText("Client anonymized: Yes");
    await expect(dialog).toContainText(/Documents deleted: \d+/);
    await expect(dialog).toContainText(/Comments redacted: \d+/);
    await expect(dialog).toContainText(/Portal contacts anonymized: \d+/);
    await expect(dialog).toContainText(/Fee notes preserved: \d+/);
    await dialog.getByRole("button", { name: "Close" }).click();
    await expect(dialog).toBeHidden();

    await page.reload();
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Anonymized", {
      timeout: 10_000,
    });
    await expect(
      page.getByRole("heading", { level: 1, name: /^Anonymized Client / })
    ).toBeVisible();
  });

  test("ANONYMIZED customer is read-only", async ({ page }) => {
    const id = await createAnonymizedClient(`DP ReadOnly ${RUN_ID}`);

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${base}/clients/${id}`);
    await expect(page.getByText("Client data anonymized")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Anonymized");
    // The product hides (does not disable) the edit paths of an anonymized client
    await expect(page.getByTestId("smart-primary-action")).toHaveCount(0);
    await openClientOverflow(page);
    await expect(page.getByRole("menuitem", { name: "Export Data" })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: "Edit Client" })).toHaveCount(0);
    await expect(page.getByRole("menuitem", { name: "Anonymize" })).toHaveCount(0);
  });

  test("Cannot transition out of ANONYMIZED via API", async () => {
    const id = await createAnonymizedClient(`DP Terminal ${RUN_ID}`);

    // Phase 87 S2 (ADR-337): API path — ANONYMIZED is terminal (LifecycleStatus.ALLOWED_TRANSITIONS)
    const res = await api(`/api/clients/${id}/transition`, {
      method: "POST",
      body: { targetStatus: "ACTIVE", notes: "E2E: must be refused" },
    });
    expect(res.status).toBe(400);
    expect(await res.text()).toContain("ANONYMIZED");
  });
});
