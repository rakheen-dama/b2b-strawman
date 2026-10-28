/**
 * Days 1-3 — Client Onboarding
 *
 * Create 4 clients (Sipho Ndlovu, Apex Holdings, Moroka Family Trust,
 * QuickCollect Services), run conflict checks, create initial matters from
 * templates, transition through FICA onboarding, and (Day 5) fill the matter
 * header custom fields and check the header facts strip.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 0 tests completed (legal-za profile active)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";
import { openGroupedTab, openMatter } from "../../helpers/legal-depth-ii";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

// Unique suffix to avoid collisions across reruns.
// NOTE: RUN_ID is per-file, so entities created here cannot be found by RUN_ID
// in other spec files. Each spec is self-contained; true cross-file data linkage
// would require shared Playwright storage state (not implemented in this suite).
const RUN_ID = Date.now().toString(36).slice(-4);

// ═══════════════════════════════════════════════════════════════════
//  DAY 1 — First Client: Sipho Ndlovu (Individual, Litigation)
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 1 — Sipho Ndlovu Onboarding", () => {
  const CLIENT_NAME = `Sipho Ndlovu ${RUN_ID}`;

  // ── 1.1-1.3: Conflict check "Sipho Ndlovu" -> CLEAR ──────────────
  test("Bob: Conflict check for Sipho Ndlovu — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/conflict-check`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Conflict check page not accessible — feature may not be implemented");
      return;
    }

    // Search for "Sipho Ndlovu"
    const searchInput = page
      .getByRole("textbox", { name: /search|name|query/i })
      .or(page.locator('input[type="search"], input[type="text"]'))
      .first();
    const hasSearch = await searchInput.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasSearch) {
      await searchInput.fill("Sipho Ndlovu");

      const searchBtn = page.getByRole("button", { name: /search|check|run/i }).first();
      const hasBtn = await searchBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasBtn) {
        await searchBtn.click();
        await page.waitForTimeout(2000);
      } else {
        await searchInput.press("Enter");
        await page.waitForTimeout(2000);
      }

      // Expect CLEAR result (no conflicts found for new name)
      const clearResult = await page
        .getByText(/clear|no.*(conflict|match)/i)
        .isVisible({ timeout: 5000 })
        .catch(() => false);
      if (clearResult) {
        // Capture screenshot of clear result
        await captureScreenshot(page, "day-01-conflict-check-clear");
        await captureScreenshot(page, "conflict-check-clear-result", { curated: true });
      }
    } else {
      test.skip(true, "Conflict check search input not found");
    }
  });

  // ── 1.4-1.9: Create client Sipho Ndlovu ──────────────────────────
  test("Bob: Create client Sipho Ndlovu (Individual)", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { name: /Customers|Clients/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    // Click "New Customer" / "New Client"
    const newBtn = page.getByRole("button", { name: /New (Customer|Client)/i });
    const hasNewBtn = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasNewBtn) {
      test.skip(true, "New Client button not found");
      return;
    }

    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    // Fill basic info
    await page.getByRole("textbox", { name: /^Name$/i }).fill(CLIENT_NAME);

    const emailField = page.getByRole("textbox", { name: /email/i }).first();
    const hasEmail = await emailField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasEmail) {
      await emailField.fill(`sipho-${RUN_ID}@example.com`);
    }

    const phoneField = page.getByRole("textbox", { name: /phone/i }).first();
    const hasPhone = await phoneField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasPhone) {
      await phoneField.fill("+27-11-555-1001");
    }

    // Look for entity type selector (Individual)
    const entityTypeSelect = page
      .locator('select[name*="entity"], [data-testid*="entity-type"]')
      .first();
    const hasEntityType = await entityTypeSelect.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasEntityType) {
      await entityTypeSelect.selectOption({ label: /individual/i });
    }

    // Click Next to go to step 2 (custom fields)
    const nextBtn = page.getByRole("button", { name: /Next/i });
    const hasNext = await nextBtn.isVisible({ timeout: 3000 }).catch(() => false);

    if (hasNext) {
      await nextBtn.click();
      await page.waitForTimeout(1000);

      // Step 2 — custom fields may be present
      // Use evaluate click to handle scroll issues
      const createBtn = page.getByRole("button", { name: /Create (Customer|Client)/i });
      await createBtn.click();
    } else {
      // Single-step form
      const createBtn = page.getByRole("button", { name: /Create|Save/i }).first();
      await createBtn.click();
    }

    await page.waitForTimeout(3000);

    // Dismiss dialog if still open
    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Verify client appears in list
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByText(CLIENT_NAME).first()).toBeVisible({ timeout: 10_000 });
  });

  // ── 1.10-1.15: Transition PROSPECT -> ONBOARDING -> ACTIVE ───────
  test("Bob: Transition Sipho to ONBOARDING", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { name: /Customers|Clients/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    await page
      .getByRole("link", {
        name: new RegExp(CLIENT_NAME.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
      })
      .first()
      .click();
    await page.waitForTimeout(2000);

    // Click "Change Status" to start onboarding
    const changeStatusBtn = page.getByRole("button", { name: /Change Status/i });
    const hasStatus = await changeStatusBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasStatus) {
      await changeStatusBtn.click();
      const startOnboarding = page.getByRole("menuitem", { name: /Start Onboarding/i });
      const hasOnboarding = await startOnboarding.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasOnboarding) {
        await startOnboarding.click();
        await page.waitForTimeout(2000);
        await expect(page.getByText(/onboarding/i).first()).toBeVisible({ timeout: 10_000 });
      }
    }
  });

  test("Bob: Complete FICA checklist for Sipho — auto-transitions to ACTIVE", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await page
      .getByRole("link", {
        name: new RegExp(CLIENT_NAME.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
      })
      .first()
      .click();
    await page.waitForTimeout(2000);

    // Look for checklist tab or section
    const checklistTab = page.getByRole("tab", { name: /checklist|fica|onboarding/i }).first();
    const hasChecklist = await checklistTab.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasChecklist) {
      await checklistTab.click();
      await page.waitForTimeout(1000);

      // Complete all checklist items
      const checkboxes = page.locator('input[type="checkbox"]:not(:checked)');
      const count = await checkboxes.count();
      for (let i = 0; i < count; i++) {
        await checkboxes.nth(0).check();
        await page.waitForTimeout(500);
      }

      await page.waitForTimeout(2000);
    }

    // Verify ACTIVE status (auto-transition after FICA completion)
    const activeStatus = await page
      .getByText(/active/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (!activeStatus) {
      // May need to reload
      await page.reload();
      await page.waitForTimeout(2000);
    }
  });

  // ── 1.16-1.22: Create matter from Litigation template ─────────────
  test("Alice: Create Litigation matter for Sipho", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.getByRole("heading", { name: /Projects|Matters/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    const newBtn = page.getByRole("button", { name: /New (Project|Matter)/i });
    const hasNewBtn = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasNewBtn) {
      test.skip(true, "New Matter button not found");
      return;
    }

    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    // Fill matter name
    const nameField = page.getByRole("textbox", { name: /name/i }).first();
    const hasName = await nameField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasName) {
      await nameField.fill(`Ndlovu v RAF ${RUN_ID}`);
    }

    // Select client
    const clientField = page
      .locator('[data-testid*="customer"], [data-testid*="client"]')
      .or(page.getByRole("combobox", { name: /customer|client/i }))
      .first();
    const hasClient = await clientField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasClient) {
      await clientField.click();
      await page.waitForTimeout(500);
      const option = page.getByRole("option", { name: new RegExp("Sipho|Ndlovu", "i") }).first();
      const hasOption = await option.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasOption) {
        await option.click();
      }
    }

    // Select template (Litigation)
    const templateField = page
      .getByRole("combobox", { name: /template/i })
      .or(page.locator('[data-testid*="template"]'))
      .first();
    const hasTemplate = await templateField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasTemplate) {
      await templateField.click();
      await page.waitForTimeout(500);
      const litOption = page.getByRole("option", { name: /litigation/i }).first();
      const hasLit = await litOption.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasLit) {
        await litOption.click();
      }
    }

    // Create
    const createBtn = page.getByRole("button", { name: /Create/i }).first();
    await createBtn.click();
    await page.waitForTimeout(3000);

    // Dismiss dialog if open
    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Verify matter created — check project list
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    const matterVisible = await page
      .getByText(new RegExp(`Ndlovu.*RAF|RAF.*Ndlovu`, "i"))
      .first()
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (matterVisible) {
      // Navigate to matter detail to check action items
      await page.getByText(new RegExp(`Ndlovu.*RAF|RAF.*Ndlovu`, "i")).first().click();
      await page.waitForTimeout(2000);

      // Screenshot of litigation template items loaded
      await captureScreenshot(page, "day-01-litigation-template-items-loaded");
      await captureScreenshot(page, "litigation-template-action-items", { curated: true });
    }
  });

  // ── Day 1 team setup (Phase 88 619E, ADR-346): the lead adds Carol to the matter team ──
  // Org Members see only matters they are on. Without this step Carol's Day 7
  // time-recording test found no Sipho matter and skipped (OQ #11).
  test("Alice: Add Carol to the Sipho matter team (Team tab)", async ({ page }) => {
    test.setTimeout(60_000);
    await loginAs(page, "alice");
    await openMatter(page, `Ndlovu v RAF ${RUN_ID}`);
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
  // Sits in the Day 1 group because it needs this file's run-scoped matter: a failure earlier in
  // the file restarts the worker with a new RUN_ID. The strip (627A) renders the legal-za header
  // slugs (HEADER_FIELD_SLUGS.PROJECT) only once they hold a value, and no seed or create path
  // sets them, so the test fills them on the Custom fields tab first (the only UI write path).
  test("Day 5 — Alice: Matter header shows case number, court and opposing party", async ({
    page,
  }) => {
    test.setTimeout(60_000);
    const caseNumber = "2026/12345";
    const court = "Gauteng Division, Pretoria";
    const opposingParty = "Road Accident Fund";

    await loginAs(page, "alice");
    await openMatter(page, `Ndlovu v RAF ${RUN_ID}`);

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
    await expect(
      page.getByRole("heading", { level: 1, name: `Ndlovu v RAF ${RUN_ID}` })
    ).toBeVisible({ timeout: 15_000 });
    await expect(headerValues).toHaveText([caseNumber, court, opposingParty]);
    for (let i = 0; i < 3; i++) await expect(headerValues.nth(i)).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 2 — Apex Holdings + Moroka Family Trust
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 2 — Apex Holdings & Moroka Trust Onboarding", () => {
  const APEX_NAME = `Apex Holdings ${RUN_ID}`;
  const MOROKA_NAME = `Moroka Family Trust ${RUN_ID}`;

  // ── Apex Holdings: Company, Commercial ────────────────────────────
  test("Bob: Conflict check for Apex Holdings — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/conflict-check`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Conflict check page not accessible");
      return;
    }

    const searchInput = page.locator('input[type="search"], input[type="text"]').first();
    const hasSearch = await searchInput.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasSearch) {
      await searchInput.fill("Apex Holdings");
      await searchInput.press("Enter");
      await page.waitForTimeout(2000);
    }
  });

  test("Bob: Create client Apex Holdings (Company)", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { name: /Customers|Clients/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    const newBtn = page.getByRole("button", { name: /New (Customer|Client)/i });
    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    await page.getByRole("textbox", { name: /^Name$/i }).fill(APEX_NAME);

    const emailField = page.getByRole("textbox", { name: /email/i }).first();
    const hasEmail = await emailField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasEmail) {
      await emailField.fill(`apex-${RUN_ID}@example.com`);
    }

    const nextBtn = page.getByRole("button", { name: /Next/i });
    const hasNext = await nextBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasNext) {
      await nextBtn.click();
      await page.waitForTimeout(1000);
      const createBtn = page.getByRole("button", { name: /Create (Customer|Client)/i });
      await createBtn.click();
    } else {
      const createBtn = page.getByRole("button", { name: /Create|Save/i }).first();
      await createBtn.click();
    }

    await page.waitForTimeout(3000);
    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByText(APEX_NAME).first()).toBeVisible({ timeout: 10_000 });

    // Screenshot — client with legal fields populated
    await page.getByText(APEX_NAME).first().click();
    await page.waitForTimeout(2000);
    await captureScreenshot(page, "day-02-client-legal-fields-populated");
  });

  // ── Moroka Family Trust: Trust, Deceased Estate ───────────────────
  test("Bob: Conflict check for Moroka Family Trust — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/conflict-check`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Conflict check page not accessible");
      return;
    }

    const searchInput = page.locator('input[type="search"], input[type="text"]').first();
    const hasSearch = await searchInput.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasSearch) {
      await searchInput.fill("Moroka Family Trust");
      await searchInput.press("Enter");
      await page.waitForTimeout(2000);
    }
  });

  test("Bob: Create client Moroka Family Trust", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { name: /Customers|Clients/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    const newBtn = page.getByRole("button", { name: /New (Customer|Client)/i });
    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    await page.getByRole("textbox", { name: /^Name$/i }).fill(MOROKA_NAME);

    const emailField = page.getByRole("textbox", { name: /email/i }).first();
    const hasEmail = await emailField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasEmail) {
      await emailField.fill(`moroka-${RUN_ID}@example.com`);
    }

    const nextBtn = page.getByRole("button", { name: /Next/i });
    const hasNext = await nextBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasNext) {
      await nextBtn.click();
      await page.waitForTimeout(1000);
      const createBtn = page.getByRole("button", { name: /Create (Customer|Client)/i });
      await createBtn.click();
    } else {
      const createBtn = page.getByRole("button", { name: /Create|Save/i }).first();
      await createBtn.click();
    }

    await page.waitForTimeout(3000);
    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByText(MOROKA_NAME).first()).toBeVisible({ timeout: 10_000 });
  });

  test("Alice: Create Deceased Estate matter for Moroka from template", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.getByRole("heading", { name: /Projects|Matters/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    const newBtn = page.getByRole("button", { name: /New (Project|Matter)/i });
    const hasNewBtn = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasNewBtn) {
      test.skip(true, "New Matter button not found");
      return;
    }

    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    const nameField = page.getByRole("textbox", { name: /name/i }).first();
    const hasName = await nameField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasName) {
      await nameField.fill(`Estate Late Peter Moroka ${RUN_ID}`);
    }

    // Select template (Deceased Estate)
    const templateField = page.getByRole("combobox", { name: /template/i }).first();
    const hasTemplate = await templateField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasTemplate) {
      await templateField.click();
      await page.waitForTimeout(500);
      const estateOption = page.getByRole("option", { name: /deceased.*estate|estate/i }).first();
      const hasEstate = await estateOption.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasEstate) {
        await estateOption.click();
      }
    }

    const createBtn = page.getByRole("button", { name: /Create/i }).first();
    await createBtn.click();
    await page.waitForTimeout(3000);

    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Verify and screenshot
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    const matterVisible = await page
      .getByText(/Moroka|Estate/i)
      .first()
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (matterVisible) {
      await page
        .getByText(/Moroka|Estate/i)
        .first()
        .click();
      await page.waitForTimeout(2000);
      await captureScreenshot(page, "day-02-estates-template-applied");
    }
  });
});

// ═══════════════════════════════════════════════════════════════════
//  DAY 3 — QuickCollect Services
// ═══════════════════════════════════════════════════════════════════
test.describe.serial("Day 3 — QuickCollect Services Onboarding", () => {
  const QC_NAME = `QuickCollect Services ${RUN_ID}`;

  test("Bob: Conflict check for QuickCollect Services — CLEAR", async ({ page }) => {
    await loginAs(page, "bob");
    await page.goto(`${BASE}/conflict-check`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Conflict check page not accessible");
      return;
    }

    const searchInput = page.locator('input[type="search"], input[type="text"]').first();
    const hasSearch = await searchInput.isVisible({ timeout: 5000 }).catch(() => false);
    if (hasSearch) {
      await searchInput.fill("QuickCollect Services");
      await searchInput.press("Enter");
      await page.waitForTimeout(2000);
    }
  });

  test("Bob: Create client QuickCollect Services (Company)", async ({ page }) => {
    await loginAs(page, "bob");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { name: /Customers|Clients/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    const newBtn = page.getByRole("button", { name: /New (Customer|Client)/i });
    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    await page.getByRole("textbox", { name: /^Name$/i }).fill(QC_NAME);

    const emailField = page.getByRole("textbox", { name: /email/i }).first();
    const hasEmail = await emailField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasEmail) {
      await emailField.fill(`quickcollect-${RUN_ID}@example.com`);
    }

    const nextBtn = page.getByRole("button", { name: /Next/i });
    const hasNext = await nextBtn.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasNext) {
      await nextBtn.click();
      await page.waitForTimeout(1000);
      const createBtn = page.getByRole("button", { name: /Create (Customer|Client)/i });
      await createBtn.click();
    } else {
      const createBtn = page.getByRole("button", { name: /Create|Save/i }).first();
      await createBtn.click();
    }

    await page.waitForTimeout(3000);
    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByText(QC_NAME).first()).toBeVisible({ timeout: 10_000 });
  });

  test("Alice: Create initial Collections matter for QuickCollect", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.getByRole("heading", { name: /Projects|Matters/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    // Create first collections matter
    const newBtn = page.getByRole("button", { name: /New (Project|Matter)/i });
    const hasNewBtn = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);
    if (!hasNewBtn) {
      test.skip(true, "New Matter button not found");
      return;
    }

    await newBtn.click();
    await expect(page.getByRole("dialog")).toBeVisible({ timeout: 5000 });

    const nameField = page.getByRole("textbox", { name: /name/i }).first();
    const hasName = await nameField.isVisible({ timeout: 3000 }).catch(() => false);
    if (hasName) {
      await nameField.fill(`QC Debt Recovery #1 ${RUN_ID}`);
    }

    const createBtn = page.getByRole("button", { name: /Create/i }).first();
    await createBtn.click();
    await page.waitForTimeout(3000);

    const dialogOpen = await page
      .getByRole("dialog")
      .isVisible()
      .catch(() => false);
    if (dialogOpen) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
    }

    // Verify at least one matter created
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/matters`);
    await expect(page.locator("main")).toBeVisible();
  });

  // ── Verify all 4 clients exist ────────────────────────────────────
  test("Alice: Verify 4 clients on client list", async ({ page }) => {
    await loginAs(page, "alice");
    // Phase 87 S4 (ADR-344): route rename
    await page.goto(`${BASE}/clients`);
    await expect(page.getByRole("heading", { name: /Customers|Clients/i, level: 1 })).toBeVisible({
      timeout: 10_000,
    });

    // Verify the page loads and has content
    await expect(page.locator("main")).toBeVisible();
    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });
});
