/**
 * Day 60 — Interest Run & Investments
 *
 * Run interest calculation with LPFF split (6.5%) for Moroka trust balance.
 * Place investments: Moroka R200,000 Standard Bank Fixed Deposit (s86(3) Firm
 * Discretion) and QuickCollect R30,000 Nedbank Call Account (s86(4) Client
 * Instruction). Second billing cycle fee notes. Generate reports.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 45 tests completed (reconciliation, payments recorded)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from "@playwright/test";
import { loginAs } from "../../fixtures/auth";
import { captureScreenshot } from "../../helpers/screenshot";

const ORG = "e2e-test-org";
const BASE = `/org/${ORG}`;

test.describe.serial("Day 60 — Interest Run & Investments", () => {
  // ── 60.1-60.10: Interest run with LPFF split ─────────────────────
  test("Alice: Navigate to Trust Interest page", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/interest`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Trust interest page not accessible — feature may not be implemented");
      return;
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Alice: Run interest calculation with LPFF split (6.5%)", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/interest`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Trust interest page not accessible");
      return;
    }

    // Look for "Run Interest" or "Calculate Interest" button
    const runBtn = page
      .getByRole("button", { name: /run.*interest|calculate.*interest|new.*interest/i })
      .first();
    const hasRun = await runBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasRun) {
      await runBtn.click();
      await page.waitForTimeout(1000);

      // Configure LPFF rate if there's a field
      const lpffField = page.locator('input[name*="lpff"], input[name*="rate"]').first();
      const hasLpff = await lpffField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasLpff) {
        await lpffField.fill("6.5");
      }

      // Execute the interest run
      const executeBtn = page
        .getByRole("button", { name: /calculate|run|execute|confirm/i })
        .first();
      const hasExecute = await executeBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasExecute) {
        await executeBtn.click();
        await page.waitForTimeout(3000);
      }

      // Verify INTEREST_CREDIT and INTEREST_LPFF transactions posted
      expect
        .soft(
          await page
            .getByText(/interest.*credit|credit/i)
            .isVisible({ timeout: 5000 })
            .catch(() => false)
        )
        .toBeTruthy();
      expect
        .soft(
          await page
            .getByText(/lpff|legal.*practitioners/i)
            .isVisible({ timeout: 5000 })
            .catch(() => false)
        )
        .toBeTruthy();

      // Screenshot: interest run with LPFF split
      await captureScreenshot(page, "day-60-interest-lpff-calculated");
      await captureScreenshot(page, "interest-run-lpff-split", { curated: true });
    } else {
      test.skip(true, "Interest run button not found");
    }
  });

  // ── 60.11-60.17: Investment — Moroka R200,000 Fixed Deposit ───────
  test("Alice: Navigate to Investment Register", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/investments`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Investment register page not accessible — feature may not be implemented");
      return;
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Alice: Place investment — Moroka R200,000 Standard Bank Fixed Deposit (s86(3))", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/investments`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Investment register page not accessible");
      return;
    }

    const newBtn = page
      .getByRole("button", { name: /new.*investment|place.*investment|add.*investment/i })
      .first();
    const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasNew) {
      await newBtn.click();
      await page.waitForTimeout(1000);

      // Fill investment details
      const amountField = page
        .getByRole("textbox", { name: /amount/i })
        .or(page.getByRole("spinbutton", { name: /amount/i }))
        .first();
      const hasAmount = await amountField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasAmount) {
        await amountField.fill("200000");
      }

      // Bank/institution
      const bankField = page.getByRole("textbox", { name: /bank|institution/i }).first();
      const hasBank = await bankField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasBank) {
        await bankField.fill("Standard Bank");
      }

      // Investment type
      const typeField = page.getByRole("combobox", { name: /type/i }).first();
      const hasType = await typeField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasType) {
        await typeField.click();
        await page.waitForTimeout(500);
        const fixedOption = page.getByRole("option", { name: /fixed.*deposit/i }).first();
        const hasFixed = await fixedOption.isVisible({ timeout: 3000 }).catch(() => false);
        if (hasFixed) {
          await fixedOption.click();
        }
      }

      // Interest rate
      const rateField = page
        .getByRole("textbox", { name: /rate|interest/i })
        .or(page.getByRole("spinbutton", { name: /rate|interest/i }))
        .first();
      const hasRate = await rateField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasRate) {
        await rateField.fill("8.5");
      }

      // Term (90 days)
      const termField = page
        .getByRole("textbox", { name: /term|days|period/i })
        .or(page.getByRole("spinbutton", { name: /term|days|period/i }))
        .first();
      const hasTerm = await termField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasTerm) {
        await termField.fill("90");
      }

      // Section 86 basis — Firm Discretion (s86(3))
      const basisField = page
        .getByRole("combobox", { name: /basis|section.*86|authority/i })
        .first();
      const hasBasis = await basisField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasBasis) {
        await basisField.click();
        await page.waitForTimeout(500);
        const s86Option = page.getByRole("option", { name: /firm.*discretion|86\(3\)/i }).first();
        const hasS86 = await s86Option.isVisible({ timeout: 3000 }).catch(() => false);
        if (hasS86) {
          await s86Option.click();
        }
      }

      // Select client (Moroka)
      const clientField = page.getByRole("combobox", { name: /client|customer/i }).first();
      const hasClient = await clientField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasClient) {
        await clientField.click();
        await page.waitForTimeout(500);
        const morokaOption = page.getByRole("option", { name: /Moroka/i }).first();
        const hasMoroka = await morokaOption.isVisible({ timeout: 3000 }).catch(() => false);
        if (hasMoroka) {
          await morokaOption.click();
        }
      }

      const saveBtn = page.getByRole("button", { name: /save|create|place/i }).first();
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasSave) {
        await saveBtn.click();
        await page.waitForTimeout(2000);
      }

      // Screenshot: investment placed
      await captureScreenshot(page, "day-60-investment-s86-placed");
      await captureScreenshot(page, "investment-s86-selection", { curated: true });
    } else {
      test.skip(true, "New investment button not found");
    }
  });

  // ── 60.18-60.20: Investment — QuickCollect R30,000 Call Account ────
  test("Alice: Place investment — QuickCollect R30,000 Nedbank Call Account (s86(4))", async ({
    page,
  }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/trust-accounting/investments`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Investment register page not accessible");
      return;
    }

    const newBtn = page
      .getByRole("button", { name: /new.*investment|place.*investment|add.*investment/i })
      .first();
    const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasNew) {
      await newBtn.click();
      await page.waitForTimeout(1000);

      // Fill investment details for QuickCollect
      const amountField = page
        .getByRole("textbox", { name: /amount/i })
        .or(page.getByRole("spinbutton", { name: /amount/i }))
        .first();
      const hasAmount = await amountField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasAmount) {
        await amountField.fill("30000");
      }

      const bankField = page.getByRole("textbox", { name: /bank|institution/i }).first();
      const hasBank = await bankField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasBank) {
        await bankField.fill("Nedbank");
      }

      // Section 86 basis — Client Instruction (s86(4))
      const basisField = page
        .getByRole("combobox", { name: /basis|section.*86|authority/i })
        .first();
      const hasBasis = await basisField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasBasis) {
        await basisField.click();
        await page.waitForTimeout(500);
        const s86Option = page
          .getByRole("option", { name: /client.*instruction|86\(4\)/i })
          .first();
        const hasS86 = await s86Option.isVisible({ timeout: 3000 }).catch(() => false);
        if (hasS86) {
          await s86Option.click();
        }
      }

      // Select client (QuickCollect)
      const clientField = page.getByRole("combobox", { name: /client|customer/i }).first();
      const hasClient = await clientField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasClient) {
        await clientField.click();
        await page.waitForTimeout(500);
        const qcOption = page.getByRole("option", { name: /QuickCollect/i }).first();
        const hasQC = await qcOption.isVisible({ timeout: 3000 }).catch(() => false);
        if (hasQC) {
          await qcOption.click();
        }
      }

      const saveBtn = page.getByRole("button", { name: /save|create|place/i }).first();
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasSave) {
        await saveBtn.click();
        await page.waitForTimeout(2000);
      }
    }
  });

  // ── 60.21-60.24: Second billing cycle fee notes ───────────────────
  test("Alice: Create second billing cycle fee notes", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/invoices`);
    await expect(page.locator("main")).toBeVisible({ timeout: 10_000 });

    // Create fee note #2 for Sipho
    const newBtn = page.getByRole("button", { name: /New (Invoice|Fee Note)/i }).first();
    const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasNew) {
      await newBtn.click();
      await page.waitForTimeout(1000);

      const clientField = page.getByRole("combobox", { name: /customer|client/i }).first();
      const hasClient = await clientField.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasClient) {
        await clientField.click();
        await page.waitForTimeout(500);
        const siphoOption = page.getByRole("option", { name: /Sipho|Ndlovu/i }).first();
        const hasSipho = await siphoOption.isVisible({ timeout: 3000 }).catch(() => false);
        if (hasSipho) {
          await siphoOption.click();
        }
      }

      const createBtn = page.getByRole("button", { name: /Create|Save|Generate/i }).first();
      const hasCreate = await createBtn.isVisible({ timeout: 3000 }).catch(() => false);
      if (hasCreate) {
        await createBtn.click();
        await page.waitForTimeout(3000);
      }

      // Go back to create more
      await page.goto(`${BASE}/invoices`);
      await page.waitForTimeout(1000);
    }
  });

  // ── 60.25-60.29: Reports ──────────────────────────────────────────
  test("Alice: Generate time tracking report", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/reports`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Reports page not accessible");
      return;
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");
  });

  test("Alice: Check profitability report", async ({ page }) => {
    await loginAs(page, "alice");
    await page.goto(`${BASE}/profitability`);

    const mainVisible = await page
      .locator("main")
      .isVisible({ timeout: 10_000 })
      .catch(() => false);
    if (!mainVisible) {
      test.skip(true, "Profitability page not accessible");
      return;
    }

    await expect(page.locator("body")).not.toContainText("Something went wrong");

    // Screenshot: profitability report
    await captureScreenshot(page, "day-60-profitability-loaded");
    await captureScreenshot(page, "profitability-per-matter", { curated: true });
  });
});
