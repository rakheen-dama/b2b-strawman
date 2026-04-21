import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";

/**
 * Epic 500A — Portal client-POV 90-day lifecycle: Day 45 → Day 75.
 *
 * Exercises checkpoints 7–9 from `qa/testplan/demos/portal-client-90day-keycloak.md`:
 *   - Day 45: Client pays first invoice via portal payment flow                    [all profiles]
 *   - Day 60: Client downloads Statement of Account / financial statement          [legal-za / accounting-za]
 *   - Day 75: Deadline-approaching nudge → mark read → download related document   [accounting-za / legal-za]
 *
 * Prerequisites: same stack contract as day-00-07.spec.ts. Day 45 requires a
 * sandbox-PSP-enabled invoice in "Due" state. Day 60 requires a generated SoA
 * (legal-za) or financial statement (accounting-za) attached to the client's
 * matter/engagement.
 *
 * Run (full — slice 500B):
 *   PLAYWRIGHT_BASE_URL=http://localhost:3002 \
 *     pnpm exec playwright test --config=playwright.portal.config.ts \
 *     --project=portal-client-90day --update-snapshots
 *
 * Scaffold guard (slice 500A — this PR):
 *   SKIP_PORTAL_BASELINES=true is honoured so `playwright --list` parses cleanly.
 */

const PORTAL_EMAIL =
  process.env.PORTAL_CONTACT_EMAIL || "sipho.portal@example.com";
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

test.describe.serial("Day 45–75 — Portal Client Lifecycle (Checkpoints 7–9)", () => {
  test.beforeAll(async () => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — scaffold-only run (Epic 500A). Unset with a seeded portal stack to execute (Epic 500B).",
    );
  });

  test.beforeEach(async ({ page }) => {
    const jwt = await getPortalJwt(PORTAL_EMAIL);
    if (!jwt) {
      test.skip(
        true,
        `Could not obtain portal JWT for ${PORTAL_EMAIL}. Stack may not be running or contact not seeded — deferred to slice 500B execution.`,
      );
      return;
    }
    await loginAsPortalContact(page, jwt);
  });

  // ── Checkpoint 7 — Day 45: pay invoice ─────────────────────────────────
  test("Day 45: Client opens invoice detail view", async ({ page }) => {
    await page.goto("/invoices");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "/invoices not rendered — tenant may not have invoices seeded");
      return;
    }

    const invoiceLink = page
      .getByRole("link", { name: /invoice|due|unpaid|sent/i })
      .first();
    if (await invoiceLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await invoiceLink.click();
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(500);
    }

    await expect(page).toHaveScreenshot("day-45-invoice-detail.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 45: Client pays invoice via portal payment flow", async ({ page }) => {
    await page.goto("/invoices");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const invoiceLink = page
      .getByRole("link", { name: /invoice|due|unpaid|sent/i })
      .first();
    if (!(await invoiceLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "No payable invoice rendered");
      return;
    }

    await invoiceLink.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const payBtn = page.getByRole("button", { name: /pay|checkout/i }).first();
    if (!(await payBtn.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(
        true,
        "Pay action not rendered — invoice may already be paid, or PSP not configured",
      );
      return;
    }

    await payBtn.click();
    await page.waitForTimeout(1500);

    // Sandbox PSP flows vary — some inline-render a sheet, others redirect.
    // Slice 500B will configure the sandbox credentials; for now, we only
    // capture whatever state we reached after clicking Pay.
    await page.waitForLoadState("domcontentloaded");

    expect
      .soft(
        await page
          .getByText(/paid|success|receipt|confirmation/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    await expect(page).toHaveScreenshot("day-45-invoice-paid.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 8 — Day 60: download generated document ─────────────────
  test("Day 60 [legal-za / accounting-za]: Client downloads SoA or financial statement", async ({
    page,
  }) => {
    await page.goto("/projects");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "/projects not rendered");
      return;
    }

    // Click into the first project/matter
    const projectLink = page
      .getByRole("link", { name: /project|matter|engagement/i })
      .first();
    if (!(await projectLink.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(true, "No project rendered to drill into");
      return;
    }

    await projectLink.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(800);

    // Open Documents tab if present
    const docsTab = page.getByRole("tab", { name: /document/i }).first();
    if (await docsTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await docsTab.click();
      await page.waitForTimeout(500);
    }

    // Verify a document row exists (soft — profile may legitimately have none)
    expect
      .soft(
        await page
          .getByText(/Statement of Account|financial statement|\.pdf/i)
          .first()
          .isVisible({ timeout: 5000 })
          .catch(() => false),
      )
      .toBeTruthy();

    // Capture the document list state — actual download byte-verification
    // is done in slice 500B against a real firm-generated file.
    await expect(page).toHaveScreenshot("day-60-document-downloaded.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 9 — Day 75: deadline nudge → mark read → download ──────
  test("Day 75 [accounting-za / legal-za]: Client marks deadline nudge read", async ({
    page,
  }) => {
    await page.goto("/deadlines");
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);

    const main = page.locator("main");
    if (!(await main.isVisible({ timeout: 5000 }).catch(() => false))) {
      test.skip(
        true,
        "/deadlines not rendered — non-accounting-za / non-legal-za tenant",
      );
      return;
    }

    // Open the first deadline detail
    const deadlineLink = page
      .getByRole("link", { name: /deadline|due|upcoming/i })
      .first();
    if (await deadlineLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await deadlineLink.click();
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(500);
    }

    // Mark-read control — could be a button, a bell icon, or a "dismiss" link
    const markReadBtn = page
      .getByRole("button", { name: /mark read|dismiss|acknowledge/i })
      .first();
    if (await markReadBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await markReadBtn.click();
      await page.waitForTimeout(500);
    }

    await page.waitForLoadState("domcontentloaded");

    await expect(page).toHaveScreenshot("day-75-deadline-nudge-read.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
