/**
 * DOC-02: Document Generation — Playwright E2E Tests
 *
 * Tests: generate a document from a template on the matter page (preview with resolved
 * variables, save to the matter's generated documents), the client-page path (product blocker),
 * downloading the generated PDF, and the generated documents list.
 *
 * Generation runs on this run's own ACTIVE client + matter (628F R-5). The list check reads the
 * boot seed's Statement of Account on Website Redesign (compose/seed/lib/statements.sh) — read-only. There is no /documents page (removed in #814): generated documents
 * live on the matter's Work → Generated Docs tab.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data present: bash compose/seed/seed.sh (legal-za; run by e2e-up.sh)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e e2e/tests/documents/document-generation
 */
import { test, expect, type Page } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { openGroupedTab } from "../../helpers/grouped-tabs";
import { createActiveClient, createMatter } from "../../helpers/api";
import { websiteRedesignId } from "../../helpers/seed";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;
const RUN_ID = Date.now().toString(36).slice(-4);
const CLIENT = `DocGen Client ${RUN_ID}`;
const MATTER = `DocGen Matter ${RUN_ID}`;
/** A matter template without clause associations (one-step dialog: preview → save). */
const TEMPLATE = "Matter Summary Report";

/** Opens the matter's Generate Document menu and picks `TEMPLATE`; returns the dialog. */
async function openGenerateDialog(page: Page, matterId: string) {
  await page.goto(`${BASE}/matters/${matterId}`);
  await expect(page.getByRole("heading", { level: 1, name: MATTER })).toBeVisible({
    timeout: 15_000,
  });
  // A dropdown of the active matter templates (components/templates/GenerateDocumentDropdown.tsx)
  await page.getByRole("button", { name: "Generate Document" }).click();
  await page.getByRole("menuitem", { name: TEMPLATE, exact: true }).click();
  // The prerequisite check passes for an ACTIVE client's matter, so the dialog opens
  const dialog = page.getByRole("dialog", { name: `Generate: ${TEMPLATE}` });
  await expect(dialog).toBeVisible({ timeout: 15_000 });
  return dialog;
}

test.describe.serial("DOC-02: Document Generation", () => {
  let matterId: string;

  test.beforeAll(async () => {
    const clientId = await createActiveClient(CLIENT, `docgen-${RUN_ID}@example.com`);
    matterId = await createMatter(MATTER, clientId);
  });

  test.beforeEach(async ({ page }) => {
    await loginAs(page, "alice");
  });

  test("Preview shows resolved variables", async ({ page }) => {
    const dialog = await openGenerateDialog(page, matterId);
    // The preview renders into an iframe (components/documents/a4-preview-wrapper.tsx)
    const preview = dialog.frameLocator('iframe[title="Document Preview"]').locator("body");
    await expect(preview).toContainText(MATTER, { timeout: 15_000 });
    await expect(preview).toContainText(CLIENT);
    await expect(preview).not.toContainText("{{");
  });

  test("Generate document from template on the matter page", async ({ page }) => {
    const dialog = await openGenerateDialog(page, matterId);
    const save = dialog.getByRole("button", { name: /^(Save to Documents|Save anyway)$/ });
    await expect(save).toBeEnabled({ timeout: 15_000 });
    await save.click();
    await expect(dialog.getByText("Document saved successfully")).toBeVisible({ timeout: 15_000 });

    // Listed on the matter's Work → Generated Docs tab
    await page.goto(`${BASE}/matters/${matterId}`);
    await openGroupedTab(page, "work", "generated");
    const list = page.getByTestId("generated-documents-list");
    await expect(list.getByRole("row").filter({ hasText: TEMPLATE })).toHaveCount(1, {
      timeout: 15_000,
    });
  });

  test("Generate a document from the client page", async ({ page }) => {
    test.fixme(
      true,
      "628F-BLOCKER: client overflow 'Generate Document' pushes ?action=generate-document and nothing reads `action` (the client page no longer mounts GenerateDocumentDropdown, Epic 560A) — components/customers/client-overflow-menu.tsx:120"
    );
    await page.goto(`${BASE}/clients`);
    await page.getByRole("link", { name: CLIENT, exact: true }).click();
    await expect(page.getByRole("heading", { level: 1, name: CLIENT })).toBeVisible();
    await page.getByTestId("client-overflow-trigger").click();
    await page.getByRole("menuitem", { name: "Generate Document" }).click();
    // Intended: a template choice for the client, then the Generate dialog
    await expect(page.getByRole("dialog", { name: /^Generate: / })).toBeVisible();
  });

  test("Download PDF", async ({ page }) => {
    // The Generate dialog renders the PDF on the fly and downloads it as "<template>.pdf".
    // (Not used: the generated-documents list's per-row download — on the e2e stack it follows a
    // presigned S3 URL on the browser-facing host port (AWS_S3_PRESIGNER_ENDPOINT=localhost:4567)
    // from inside the frontend container and fails; environment follow-up, evidence
    // qa_cycle/evidence/phase88-rewalk/628F/builder/blockers/generated-doc-download-env.txt)
    const dialog = await openGenerateDialog(page, matterId);
    const downloadButton = dialog.getByRole("button", { name: /^(Download PDF|Download anyway)$/ });
    await expect(downloadButton).toBeEnabled({ timeout: 15_000 });
    const download = page.waitForEvent("download");
    await downloadButton.click();
    expect((await download).suggestedFilename()).toBe(`${TEMPLATE}.pdf`);
  });

  test("Generated documents list", async ({ page }) => {
    await page.goto(`${BASE}/matters/${await websiteRedesignId()}`);
    await openGroupedTab(page, "work", "generated");
    const list = page.getByTestId("generated-documents-list");
    const table = list.getByRole("table");
    await expect(table).toBeVisible({ timeout: 15_000 });
    for (const header of ["Template Name", "Generated By", "Date", "File Size", "Format"]) {
      await expect(table.getByRole("columnheader", { name: header, exact: true })).toBeVisible();
    }
    await expect(table.getByRole("cell", { name: /Statement of Account/ }).first()).toBeVisible();
  });
});
