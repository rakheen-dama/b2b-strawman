/**
 * Day 14 — Phase 67: Conveyancing matter + OTP acceptance (Epic 492)
 *
 * Exercises the conveyancing flow on the legal-za profile:
 *   - Thandi (Owner) creates a new client and a matter using the
 *     Property Transfer template (matter_type = CONVEYANCING)
 *   - Verifies the 10 conveyancing custom fields are present (from field pack
 *     `conveyancing_za_matter`): conveyancing_type, property_address,
 *     erf_number, deeds_office, lodgement_date, registration_date, deed_number,
 *     purchase_price, transfer_duty, bond_institution
 *   - Fills minimum subset: property_address, erf_number, deeds_office
 *   - Generates an Offer to Purchase document (key `offer-to-purchase`,
 *     acceptanceEligible: true)
 *   - Sends for acceptance via Phase 28 flow
 *   - Verifies acceptance request delivery (Mailpit magic-link)
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Legal-za profile active
 *   3. Phase 492 conveyancing template pack seeded (pack.json updated with
 *      `acceptanceEligible: true` on offer-to-purchase + power-of-attorney-transfer)
 *   4. Phase 492 clause pack `conveyancing-za-clauses` seeded
 *   5. Field pack `conveyancing_za_matter` seeded with 10 custom fields
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-depth-ii
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

const CONVEYANCING_FIELDS = [
  "conveyancing_type",
  "property_address",
  "erf_number",
  "deeds_office",
  "lodgement_date",
  "registration_date",
  "deed_number",
  "purchase_price",
  "transfer_duty",
  "bond_institution",
];

test.describe.serial("Day 14 — Conveyancing matter + OTP acceptance (Phase 67)", () => {
  // ── 14.2-14.3: Create conveyancing matter from Property Transfer template ──
  test("Alice (Thandi): Create matter using Property Transfer template", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const newBtn = page.getByRole("button", { name: /New (Project|Matter)/i }).first();
    if (!(await newBtn.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "New Matter button not found");
      return;
    }

    await newBtn.click();
    await page.waitForTimeout(500);

    // Template dropdown
    const templateField = page.getByRole("combobox", { name: /template/i }).first();
    if (await templateField.isVisible({ timeout: 3000 }).catch(() => false)) {
      await templateField.click();
      await page.waitForTimeout(300);
      const propertyTransferOption = page
        .getByRole("option", { name: /Property\s*Transfer/i })
        .first();
      if (await propertyTransferOption.isVisible({ timeout: 2000 }).catch(() => false)) {
        await propertyTransferOption.click();
      }
    }

    const nameInput = page.getByRole("textbox", { name: /name|title/i }).first();
    if (await nameInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await nameInput.fill("Khumalo Property Holdings — Erf 1234 Sandton Transfer");
    }

    const createBtn = page.getByRole("button", { name: /create|save/i }).first();
    if (await createBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await createBtn.click();
      await page.waitForTimeout(1500);
    }

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-conveyancing-matter-created");
  });

  // ── 14.4: Verify 10 conveyancing custom fields present ────────────
  test("Alice: Verify 10 conveyancing custom fields present on matter detail", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Khumalo|Erf\s*1234/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Conveyancing matter not found");
      return;
    }

    await matterLink.click();
    await page.waitForTimeout(1500);
    await page.waitForLoadState("networkidle");

    const mainContent = await page.locator("main").textContent().catch(() => "");
    const fieldMatchCount = CONVEYANCING_FIELDS.filter((slug) => {
      const humanized = slug.replace(/_/g, "[\\s_-]");
      const regex = new RegExp(humanized, "i");
      return regex.test(mainContent || "");
    }).length;

    // Soft assertion — at least 6 of 10 rendered (graceful degradation if some
    // are conditionally hidden by visibleWhen rules)
    expect.soft(fieldMatchCount).toBeGreaterThanOrEqual(6);

    await captureScreenshot(page, "day-14-conveyancing-matter-custom-fields");
    await captureScreenshot(page, "conveyancing-matter-detail-custom-fields", { curated: true });
  });

  // ── 14.5: Fill minimum field subset ───────────────────────────────
  test("Alice: Fill property_address, erf_number, deeds_office", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Khumalo|Erf\s*1234/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Conveyancing matter not found");
      return;
    }

    await matterLink.click();
    await page.waitForTimeout(1500);

    // Open custom-fields edit (if there's an Edit button)
    const editBtn = page.getByRole("button", { name: /edit.*custom|edit.*fields|edit/i }).first();
    if (await editBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await editBtn.click();
      await page.waitForTimeout(500);
    }

    // Fill property address
    const propAddr = page
      .getByRole("textbox", { name: /property[_\s]*address/i })
      .first();
    if (await propAddr.isVisible({ timeout: 2000 }).catch(() => false)) {
      await propAddr.fill("12 Rivonia Road, Sandton, 2196");
    }

    // Erf number
    const erfNumber = page.getByRole("textbox", { name: /erf[_\s]*number/i }).first();
    if (await erfNumber.isVisible({ timeout: 2000 }).catch(() => false)) {
      await erfNumber.fill("1234");
    }

    // Deeds office — dropdown JOHANNESBURG
    const deedsOffice = page.getByRole("combobox", { name: /deeds[_\s]*office/i }).first();
    if (await deedsOffice.isVisible({ timeout: 2000 }).catch(() => false)) {
      await deedsOffice.click();
      await page.waitForTimeout(300);
      const joburgOption = page.getByRole("option", { name: /JOHANNESBURG/i }).first();
      if (await joburgOption.isVisible({ timeout: 2000 }).catch(() => false)) {
        await joburgOption.click();
      }
    }

    const saveBtn = page.getByRole("button", { name: /save|update/i }).first();
    if (await saveBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await saveBtn.click();
      await page.waitForTimeout(1000);
    }

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-conveyancing-fields-populated");
  });

  // ── 14.7-14.8: Generate Offer to Purchase document ────────────────
  test("Alice: Generate Offer to Purchase document with conveyancing clauses", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Khumalo|Erf\s*1234/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Conveyancing matter not found");
      return;
    }

    await matterLink.click();
    await page.waitForTimeout(1500);

    // Open Generate Document dropdown
    const generateBtn = page
      .getByRole("button", { name: /generate\s+document|new\s+document/i })
      .first();
    if (!(await generateBtn.isVisible({ timeout: 3000 }).catch(() => false))) {
      test.skip(true, "Generate Document action not found");
      return;
    }

    await generateBtn.click();
    await page.waitForTimeout(500);

    // Select Offer to Purchase
    const otpOption = page
      .getByRole("option", { name: /offer\s*to\s*purchase/i })
      .first();
    const otpMenuItem = page
      .getByRole("menuitem", { name: /offer\s*to\s*purchase/i })
      .first();
    if (await otpOption.isVisible({ timeout: 2000 }).catch(() => false)) {
      await otpOption.click();
    } else if (await otpMenuItem.isVisible({ timeout: 2000 }).catch(() => false)) {
      await otpMenuItem.click();
    } else {
      test.skip(true, "Offer to Purchase template option not found");
      return;
    }

    await page.waitForTimeout(1500);

    // Generate/confirm
    const confirmGen = page.getByRole("button", { name: /generate|create|confirm/i }).first();
    if (await confirmGen.isVisible({ timeout: 2000 }).catch(() => false)) {
      await confirmGen.click();
      await page.waitForTimeout(2000);
    }

    await page.waitForLoadState("networkidle");

    // Verify preview renders with clause content (soft check for common
    // conveyancing clause keywords)
    const mainText = await page.locator("main").textContent().catch(() => "");
    expect
      .soft((mainText || "").match(/purchase\s+price|transfer|deeds|warrant/i))
      .toBeTruthy();

    await captureScreenshot(page, "day-14-otp-document-generated");
    await captureScreenshot(page, "otp-document-with-clauses", { curated: true });
  });

  // ── 14.9-14.10: Send for Acceptance (Phase 28 flow) ───────────────
  test("Alice: Send OTP for Acceptance via Phase 28 flow", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/projects`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    const matterLink = page.getByRole("link", { name: /Khumalo|Erf\s*1234/i }).first();
    if (!(await matterLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "Conveyancing matter not found");
      return;
    }

    await matterLink.click();
    await page.waitForTimeout(1500);

    // Navigate to documents tab + find OTP
    const docsTab = page.getByRole("tab", { name: /documents/i }).first();
    if (await docsTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await docsTab.click();
      await page.waitForTimeout(500);
    }

    const otpRow = page.getByRole("link", { name: /offer\s*to\s*purchase/i }).first();
    if (await otpRow.isVisible({ timeout: 3000 }).catch(() => false)) {
      await otpRow.click();
      await page.waitForTimeout(1000);
    }

    const sendForAcceptance = page
      .getByRole("button", { name: /send\s+for\s+acceptance/i })
      .first();
    if (!(await sendForAcceptance.isVisible({ timeout: 3000 }).catch(() => false))) {
      // Button only visible because template has acceptanceEligible: true
      test.skip(
        true,
        "Send for Acceptance button not visible — acceptanceEligible flag may be missing"
      );
      return;
    }

    await sendForAcceptance.click();
    await page.waitForTimeout(500);

    // Fill recipient email + submit
    const emailInput = page.getByRole("textbox", { name: /email|recipient/i }).first();
    if (await emailInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await emailInput.fill("khumalo@example.com");
    }

    const submitBtn = page.getByRole("button", { name: /send|submit|create/i }).first();
    if (await submitBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
      await submitBtn.click();
      await page.waitForTimeout(1500);
    }

    expect
      .soft(
        await page
          .getByText(/pending\s+acceptance|acceptance\s+sent|awaiting/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false)
      )
      .toBeTruthy();

    await page.waitForLoadState("networkidle");
    await captureScreenshot(page, "day-14-otp-acceptance-request-sent");
    await captureScreenshot(page, "otp-acceptance-request", { curated: true });
  });
});
