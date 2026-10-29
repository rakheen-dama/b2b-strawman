/**
 * Days 1-3 — Client Onboarding
 *
 * Bob runs a conflict check and opens each of the four clients (Sipho Ndlovu, Apex Holdings,
 * Moroka Family Trust, QuickCollect Services) with the billing address and tax number fee notes
 * need, and moves each from PROSPECT to ONBOARDING. Sipho completes the FICA checklist (→ ACTIVE).
 * Alice opens each client's first matter from its legal template (so the matter carries the
 * template's tasks), adds Carol to Sipho's matter team, and (Day 5) fills the matter header fields.
 *
 * Names come from the lifecycle run started by Day 0 (helpers/legal-lifecycle.ts); the client and
 * matter UUIDs are recorded there for Days 7–90.
 *
 * Phase 88 628E: rewritten strict — every step is real or asserted (no `isVisible().catch` guards).
 * The conflict checks expect "No Conflict", which holds only on a fresh stack (a reused stack
 * still holds earlier runs' clients and matters with the same base names).
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 0 tests completed (starts the lifecycle run)
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
  createClient,
  escapeRegExp,
  openClient,
  openMatter,
  startClientOnboarding,
  type NewClient,
} from "../../helpers/legal-depth-ii";
import {
  apexClient,
  apexMatter,
  createTemplateMatter,
  currentLifecycleRun,
  idFromUrl,
  type LifecycleClient,
  morokaClient,
  morokaMatter,
  qcClient,
  qcMatter,
  recordClientId,
  recordMatterId,
  runConflictCheck,
  siphoClient,
  siphoMatter,
} from "../../helpers/legal-lifecycle";

/** Conflict check on the scenario's plain name → "No Conflict" (fresh stack). */
async function expectNoConflict(page: Page, name: string): Promise<void> {
  const result = await runConflictCheck(page, name);
  await expect(result.getByRole("heading", { level: 3 })).toHaveText("No Conflict");
}

/**
 * Creates a lifecycle client (address included — fee notes need it), records its UUID and, unless
 * `prospectOnly`, moves it PROSPECT → ONBOARDING (matters cannot be opened for a prospect).
 */
async function createLifecycleClient(
  page: Page,
  key: LifecycleClient,
  client: Omit<NewClient, "email"> & { emailLocal: string },
  { prospectOnly = false }: { prospectOnly?: boolean } = {}
): Promise<void> {
  const { emailLocal, ...rest } = client;
  await createClient(page, {
    ...rest,
    email: `${emailLocal}.${currentLifecycleRun()}@example.com`,
  });
  await expect(page.getByTestId("lifecycle-badge")).toHaveText("Prospect");
  recordClientId(key, idFromUrl(page, "clients"));
  if (!prospectOnly) await startClientOnboarding(page);
}

/** FICA checklist items of "Legal Individual Client Onboarding" (dependency order; pack.json). */
const FICA_ITEMS: { name: string; requiresDocument: boolean }[] = [
  { name: "Proof of Identity", requiresDocument: true },
  { name: "Proof of Address", requiresDocument: true },
  { name: "Beneficial Ownership Declaration", requiresDocument: true },
  { name: "Source of Funds Declaration", requiresDocument: true },
  { name: "Engagement Letter Signed", requiresDocument: true },
  { name: "Conflict Check Performed", requiresDocument: false },
  { name: "Power of Attorney Signed", requiresDocument: true },
  { name: "FICA Risk Assessment", requiresDocument: false },
  { name: "Sanctions Screening", requiresDocument: false },
];
const ficaPack = () => `sipho-fica-pack-${currentLifecycleRun()}.pdf`;

// ═══════════════════════════════════════════════════════════════════
//  DAY 1 — First Client: Sipho Ndlovu (Individual, Litigation)
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 1 — Sipho Ndlovu Onboarding", () => {
  // ── 1.1-1.3: Conflict check "Sipho Ndlovu" -> CLEAR ──────────────
  test("Bob: Conflict check for Sipho Ndlovu — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await expectNoConflict(page, "Sipho Ndlovu");
    await captureScreenshot(page, "day-01-conflict-check-clear");
    await captureScreenshot(page, "conflict-check-clear-result", { curated: true });
  });

  // ── 1.4-1.9: Create client Sipho Ndlovu ──────────────────────────
  test("Bob: Create client Sipho Ndlovu (Individual)", async ({ page }) => {
    await loginAs(page, "bob");
    await createLifecycleClient(
      page,
      "sipho",
      {
        name: siphoClient(),
        type: "Individual",
        emailLocal: "sipho.ndlovu",
        // The tax number auto-fills from the SA ID number for individuals
        idNumber: "8501015800087",
      },
      { prospectOnly: true }
    );
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("link", { name: siphoClient(), exact: true })).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── 1.10-1.15: Transition PROSPECT -> ONBOARDING -> ACTIVE ───────
  test("Bob: Transition Sipho to ONBOARDING", async ({ page }) => {
    await loginAs(page, "bob");
    await openClient(page, siphoClient());
    await startClientOnboarding(page);
  });

  test("Bob: Complete FICA checklist for Sipho — auto-transitions to ACTIVE", async ({ page }) => {
    test.setTimeout(90_000);
    await loginAs(page, "bob");
    await openClient(page, siphoClient());

    // Five required items need a linked client document: upload Sipho's certified FICA pack
    await openGroupedTab(page, "work", "documents");
    await page
      .getByRole("tabpanel")
      .getByRole("button", { name: "Upload Document" })
      .first()
      .click();
    // Phase 88 628A (ADR-357): label → "Upload Client Document"
    const upload = page.getByRole("dialog", { name: "Upload Client Document" });
    await upload.locator('input[type="file"]').setInputFiles({
      name: ficaPack(),
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"),
    });
    await expect(upload.getByRole("button", { name: "Dismiss" })).toBeVisible({ timeout: 15_000 });
    await page.keyboard.press("Escape");
    await expect(upload).toBeHidden();
    await expect(
      page.getByRole("tabpanel").getByRole("row", { name: new RegExp(escapeRegExp(ficaPack())) })
    ).toContainText("Uploaded", { timeout: 15_000 });

    await openGroupedTab(page, "compliance", "onboarding");
    const panel = page.getByRole("tabpanel");
    await expect(panel).toContainText("Legal Individual Client Onboarding", { timeout: 15_000 });
    for (const { name, requiresDocument } of FICA_ITEMS) {
      // The checklist item row has no role / test id — product follow-up: a `data-testid` (or
      // role="listitem") on components/compliance/ChecklistInstanceItemRow.tsx:136; class until then
      const item = panel
        .locator("div.rounded-lg.border.p-3")
        .filter({ has: page.getByText(name, { exact: true }) });
      const needsDoc = item.getByText(/^Requires document:/);
      await expect(needsDoc).toHaveCount(requiresDocument ? 1 : 0);
      await item.getByRole("button", { name: "Mark Complete" }).click();
      if (requiresDocument) {
        // Link the certified FICA pack uploaded above
        const documentPicker = item.getByRole("combobox");
        await documentPicker.click();
        await page.getByRole("option", { name: ficaPack() }).click();
        await expect(documentPicker).toContainText(ficaPack());
        await expect(page.getByRole("listbox")).toBeHidden();
      }
      const confirmItem = item.getByRole("button", { name: "Confirm" });
      await expect(confirmItem).toBeEnabled();
      await confirmItem.click();
      await expect(item.getByText("Completed", { exact: true }), name).toBeVisible({
        timeout: 15_000,
      });
    }
    await expect(panel).toContainText("9/9 completed");

    // Completing FICA auto-transitions the client to ACTIVE (address + country are on file)
    await page.reload();
    await expect(page.getByTestId("lifecycle-badge")).toHaveText("Active", { timeout: 15_000 });
  });

  // ── 1.16-1.22: Create matter from the Litigation template ─────────
  test("Alice: Create Litigation matter for Sipho from template", async ({ page }) => {
    await loginAs(page, "alice");
    const id = await createTemplateMatter(page, {
      template: /^Litigation \(Personal Injury \/ General\)/,
      name: siphoMatter(),
      client: siphoClient(),
    });
    recordMatterId("sipho", id);

    // The template's action items are the matter's tasks
    await openGroupedTab(page, "work", "tasks");
    const tasks = page.getByRole("tabpanel");
    for (const task of ["Initial consultation & case assessment", "Letter of demand"]) {
      await expect(tasks.getByRole("button", { name: `Open task detail for ${task}` })).toBeVisible(
        { timeout: 15_000 }
      );
    }
    // Screenshot of litigation template items loaded
    await captureScreenshot(page, "day-01-litigation-template-items-loaded");
    await captureScreenshot(page, "litigation-template-action-items", { curated: true });
  });

  // ── Day 1 team setup (Phase 88 619E, ADR-346): the lead adds Carol to the matter team ──
  // Org Members see only matters they are on. Without this step Carol's Day 7
  // time-recording test found no Sipho matter and skipped (OQ #11).
  test("Alice: Add Carol to the Sipho matter team (Team tab)", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await openMatter(page, siphoMatter());
    await openGroupedTab(page, "details", "team");
    const panel = page.getByTestId("matter-team-panel");
    await expect(panel.first()).toBeVisible({ timeout: 15_000 });

    await panel.getByRole("button", { name: "Add Member" }).click();
    const addDialog = page.getByRole("dialog", { name: "Add Member" });
    await expect(addDialog).toBeVisible();
    await addDialog.getByRole("option", { name: /Carol Member/ }).click();
    await expect(addDialog).toBeHidden({ timeout: 15_000 });
    // The row commits with the revalidated RSC. 15 s stays deliberately below the 30 s
    // bell poll: a stall here means the next@16.2.6 lost-ping regression is back
    // (fixed by next 16.3.8, #1651).
    await expect(panel.getByRole("row", { name: /Carol Member/ }).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  // ── Day 5 (scenario 5.1–5.2): matter header facts strip (Phase 88 627K; §13.3.17 D9, G-B9) ──
  // Sits in the Day 1 group, right after the matter it acts on is created. The strip (627A)
  // renders the legal-za header slugs (HEADER_FIELD_SLUGS.PROJECT) only once they hold a value,
  // and no seed or create path sets them, so the test fills them on the Custom fields tab first
  // (the only UI write path).
  test("Day 5 — Alice: Matter header shows case number, court and opposing party", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    const caseNumber = "2026/12345";
    const court = "Gauteng Division, Pretoria";
    const opposingParty = "Road Accident Fund";

    await loginAs(page, "alice");
    await openMatter(page, siphoMatter());

    const strip = page.getByTestId("matter-header-card").getByTestId("matter-header-fields");
    // A new matter has none of the three values, so the strip is not rendered at all
    await expect(strip).toHaveCount(0);

    await openGroupedTab(page, "details", "fields");
    const fieldsTab = page.getByTestId("matter-fields-tab");
    await expect(fieldsTab.getByText("SA Legal — Matter Details").first()).toBeVisible({
      timeout: 15_000,
    });
    await fieldsTab.getByLabel("Case Number", { exact: true }).fill(caseNumber);
    await fieldsTab.getByLabel("Court", { exact: true }).fill(court);
    await fieldsTab.getByLabel("Opposing Party", { exact: true }).fill(opposingParty);
    await fieldsTab.getByRole("button", { name: "Save Custom Fields" }).click();
    await expect(fieldsTab.getByText("Saved successfully")).toBeVisible({ timeout: 15_000 });

    // The save revalidates the matter page, so the header updates in place. 15 s stays below
    // the 30 s bell poll: a stall means the lost-ping regression is back (next 16.3.8, #1651).
    // Per-value assertions: the dt/dd text nodes have no whitespace between them, so the
    // strip's textContent is "Case no.2026/12345·Court…", not the visual "Case no. 2026/12345 · …".
    // toHaveText does not check visibility, so each value is also asserted visible
    const headerValues = strip.locator("dd");
    await expect(headerValues).toHaveText([caseNumber, court, opposingParty], {
      timeout: 15_000,
    });
    for (let i = 0; i < 3; i++) await expect(headerValues.nth(i)).toBeVisible();
    await expect(strip.getByTestId("matter-header-field-case_number").locator("dt")).toHaveText(
      "Case no."
    );

    // 5.1–5.2: a fresh load of the matter shows the persisted facts under the title
    await page.reload();
    await expect(page.getByRole("heading", { level: 1, name: siphoMatter() })).toBeVisible({
      timeout: 15_000,
    });
    await expect(headerValues).toHaveText([caseNumber, court, opposingParty]);
    for (let i = 0; i < 3; i++) await expect(headerValues.nth(i)).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 2 — Apex Holdings + Moroka Family Trust
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 2 — Apex Holdings & Moroka Trust Onboarding", () => {
  // ── Apex Holdings: Company, Commercial ────────────────────────────
  test("Bob: Conflict check for Apex Holdings — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await expectNoConflict(page, "Apex Holdings");
  });

  test("Bob: Create client Apex Holdings (Company) and start onboarding", async ({ page }) => {
    await loginAs(page, "bob");
    await createLifecycleClient(page, "apex", {
      name: apexClient(),
      type: "Company",
      emailLocal: "apex.holdings",
      // COMPANY clients need a tax number to activate and to send a fee note
      taxNumber: "9012345678",
    });

    // Screenshot — client with legal fields populated
    await captureScreenshot(page, "day-02-client-legal-fields-populated");
  });

  test("Alice: Create Commercial matter for Apex from template", async ({ page }) => {
    await loginAs(page, "alice");
    const id = await createTemplateMatter(page, {
      template: /^Commercial \(Corporate & Contract\)/,
      name: apexMatter(),
      client: apexClient(),
    });
    recordMatterId("apex", id);
    await openGroupedTab(page, "work", "tasks");
    await expect(
      page
        .getByRole("tabpanel")
        .getByRole("button", { name: "Open task detail for Due diligence review" })
    ).toBeVisible({ timeout: 15_000 });
  });

  // ── Moroka Family Trust: Trust, Deceased Estate ───────────────────
  test("Bob: Conflict check for Moroka Family Trust — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await expectNoConflict(page, "Moroka Family Trust");
  });

  test("Bob: Create client Moroka Family Trust (Trust) and start onboarding", async ({ page }) => {
    await loginAs(page, "bob");
    await createLifecycleClient(page, "moroka", {
      name: morokaClient(),
      type: "Trust",
      emailLocal: "moroka.trust",
      // TRUST clients need a tax number to activate and to send a fee note (scenario 8.2)
      taxNumber: "9123456789",
    });
  });

  test("Alice: Create Deceased Estate matter for Moroka from template", async ({ page }) => {
    await loginAs(page, "alice");
    const id = await createTemplateMatter(page, {
      template: /^Deceased Estate Administration/,
      name: morokaMatter(),
      client: morokaClient(),
    });
    recordMatterId("moroka", id);
    await openGroupedTab(page, "work", "tasks");
    await expect(
      page
        .getByRole("tabpanel")
        .getByRole("button", { name: "Open task detail for Report estate to Master of High Court" })
    ).toBeVisible({ timeout: 15_000 });
    await captureScreenshot(page, "day-02-estates-template-applied");
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 3 — QuickCollect Services
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 3 — QuickCollect Services Onboarding", () => {
  test("Bob: Conflict check for QuickCollect Services — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await expectNoConflict(page, "QuickCollect Services");
  });

  test("Bob: Create client QuickCollect Services (Company) and start onboarding", async ({
    page,
  }) => {
    await loginAs(page, "bob");
    await createLifecycleClient(page, "qc", {
      name: qcClient(),
      type: "Company",
      emailLocal: "quickcollect",
      taxNumber: "9234567890",
    });
  });

  test("Alice: Create initial Collections matter for QuickCollect from template", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    const id = await createTemplateMatter(page, {
      template: /^Collections \(Debt Recovery\)/,
      name: qcMatter(),
      client: qcClient(),
    });
    recordMatterId("qc", id);
    await openGroupedTab(page, "work", "tasks");
    await expect(
      page.getByRole("tabpanel").getByRole("button", {
        name: "Open task detail for Letter of demand -- Section 129 notice",
      })
    ).toBeVisible({ timeout: 15_000 });
  });

  // ── Verify all 4 clients exist ────────────────────────────────────
  test("Alice: Verify 4 clients on client list", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    for (const name of [siphoClient(), apexClient(), morokaClient(), qcClient()]) {
      await expect(page.getByRole("link", { name, exact: true }), name).toBeVisible({
        timeout: 15_000,
      });
    }
  });
});
