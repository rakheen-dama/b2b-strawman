import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import { SEED } from "../../helpers/firm-api";

/**
 * Portal client-POV 90-day lifecycle: Day 45 → Day 75
 * (checkpoints 7–9 of `qa/testplan/demos/portal-client-90day-keycloak.md`).
 *   - Day 45: the client opens the seeded SENT fee note; with no PSP there is no online payment
 *   - Day 60: the client downloads the seeded Statement of Account from the matter page
 *   - Day 75: deadline nudge → mark read → related document (628G-BLOCKER, see the test)
 *
 * Phase 88 628G: reads the boot-seed rows only (INV-0001 SENT, the shared Statement of Account on
 * Website Redesign — compose/seed/seed.sh Steps 9 and 11); nothing here changes them.
 *
 * Day 45 payment (scenario 45.4–45.8, E.9) is the Payments mandate exemption (CLAUDE.md §6): the
 * e2e profile has no PSP — MockPaymentGateway is @Profile({"local","dev","keycloak","test"})
 * (integration/payment/MockPaymentGateway.java:34) and MockPaymentController
 * @Profile({"local","dev","keycloak"}) (:40) — so a SENT fee note has no paymentUrl and the portal
 * asks the client to contact the firm. The test asserts exactly that state.
 *
 * Strict: nothing is soft-asserted or skipped at runtime; SKIP_PORTAL_BASELINES=true is the only
 * opt-out, and Day 75 is a declared, reproduced product blocker.
 */

const PORTAL_EMAIL = process.env.PORTAL_CONTACT_EMAIL || SEED.contactEmail;
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

test.describe
  .serial("Day 45–75 — Portal Client Lifecycle (Checkpoints 7–9)", () => {
  test.beforeAll(() => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — portal e2e disabled (no seeded stack).",
    );
  });

  test.beforeEach(async ({ page }) => {
    const jwt = await getPortalJwt(PORTAL_EMAIL);
    expect(
      jwt,
      `portal JWT for ${PORTAL_EMAIL}: stack up, contact seeded, and neither magic-link cap hit ` +
        `(3 tokens per contact per 5 min, DB; 3 request-link calls per email per 15 min, in-memory)`,
    ).toBeTruthy();
    await loginAsPortalContact(page, jwt!);
  });

  // ── Checkpoint 7 — Day 45: fee note ────────────────────────────────────
  test("Day 45: Client opens the seeded SENT fee note", async ({ page }) => {
    await page.goto("/invoices");
    await expect(
      page.getByRole("heading", { level: 1, name: "Fee notes" }),
    ).toBeVisible();
    await page
      .getByRole("table", { name: "Fee Note list" })
      .getByRole("link", { name: SEED.feeNoteSentNumber, exact: true })
      .click();

    await expect(
      page.getByRole("heading", {
        level: 1,
        name: SEED.feeNoteSentNumber,
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { level: 2, name: "Line Items" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", {
        name: `Download ${SEED.feeNoteSentNumber} as PDF`,
      }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot("day-45-invoice-detail.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 45: SENT fee note offers no online payment without a PSP", async ({
    page,
  }) => {
    // Payments mandate exemption — see the file header.
    await page.goto("/invoices");
    await page
      .getByRole("table", { name: "Fee Note list" })
      .getByRole("link", { name: SEED.feeNoteSentNumber, exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        level: 1,
        name: SEED.feeNoteSentNumber,
        exact: true,
      }),
    ).toBeVisible();

    await expect(
      page.getByText(`Contact ${SEED.orgName} to arrange payment`),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "Pay Now" })).toHaveCount(0);
    await expect(page.getByTestId("invoice-payment-inline-banner")).toHaveCount(
      0,
    );

    await expect(page).toHaveScreenshot("day-45-invoice-no-psp.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 8 — Day 60: Statement of Account ────────────────────────
  test("Day 60 [legal-za]: Client downloads the Statement of Account from the matter page", async ({
    page,
    request,
  }) => {
    await page.goto("/matters");
    await expect(
      page.getByRole("heading", { level: 1, name: "Your matters" }),
    ).toBeVisible();
    // By name, not `.first()`: the default-config specs create more matters for Acme Corp.
    await page
      .locator("main")
      .locator('a[href^="/matters/"]')
      .filter({ has: page.getByText(SEED.matter, { exact: true }) })
      .click();
    await expect(
      page.getByRole("heading", { level: 1, name: SEED.matter, exact: true }),
    ).toBeVisible();
    // The matter page renders sections, not tabs (scenario 60.2's "Documents tab").
    await expect(
      page.getByRole("heading", { level: 2, name: "Documents" }),
    ).toBeVisible();

    const download = page.getByRole("button", {
      name: /^Download statement-of-account-website-redesign-\d{4}-\d{2}-\d{2}\.pdf$/,
    });
    // `.first()`: a reseeded stack may carry an older statement too; the newest is listed first.
    await expect(download.first()).toBeVisible();

    // The button presigns the download (audited as portal.document.downloaded, which Day 90
    // reads) and opens the presigned URL in a new tab.
    const presigned = page.waitForResponse(
      (r) => r.url().includes("/presign-download") && r.ok(),
    );
    const popup = page.waitForEvent("popup");
    await download.first().click();
    const { presignedUrl } = (await (await presigned).json()) as {
      presignedUrl: string;
    };
    await (await popup).close();
    const pdf = await request.get(presignedUrl);
    expect(pdf.ok()).toBe(true);
    expect((await pdf.body()).subarray(0, 5).toString("latin1")).toBe("%PDF-");

    await expect(page).toHaveScreenshot("day-60-document-downloaded.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 9 — Day 75: deadline nudge ──────────────────────────────
  test("Day 75 [legal-za]: Client marks the deadline nudge read and downloads its document", async ({
    page,
  }) => {
    test.fixme(
      true,
      "628G-BLOCKER: no portal deadline can be produced on demand — a firm court date never reaches the portal (COURT_DATE/PRESCRIPTION/FILING portal sync dormant — customerbackend/service/DeadlinePortalSyncService.java:36-44,123-130) and the portal has no mark-read control or deadline documents — components/deadlines/deadline-detail-panel.tsx:83-170",
    );
    // Intended real steps (run once the product surfaces portal deadlines + mark-read):
    await page.goto("/deadlines");
    await expect(
      page.getByRole("heading", { level: 1, name: "Diary dates" }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: /^View details for / })
      .first()
      .click();
    const panel = page.getByRole("dialog");
    await expect(panel).toBeVisible();
    await panel.getByRole("button", { name: /mark (as )?read/i }).click();
    await page.reload();
    await page
      .getByRole("button", { name: /^View details for / })
      .first()
      .click();
    await expect(page.getByRole("dialog")).toContainText(/read/i);
    await expect(page).toHaveScreenshot("day-75-deadline-nudge-read.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
