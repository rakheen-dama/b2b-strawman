import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import { todayInSA } from "@b2mash/shared/date";
import {
  RUN_ID,
  SEED,
  addDaysIso,
  cancelCourtDate,
  interruptPrescription,
  portalApiGet,
  scheduleAcmeCourtDate,
  startAcmePrescription,
} from "../../helpers/firm-api";

/**
 * Portal client-POV 90-day lifecycle: Day 45 → Day 75
 * (checkpoints 7–9 of `qa/testplan/demos/portal-client-90day-keycloak.md`).
 *   - Day 45: the client opens the seeded SENT fee note; with no PSP there is no online payment
 *   - Day 60: the client downloads the seeded Statement of Account from the matter page
 *   - Day 75: the firm's court date and prescription reach the client's Diary dates, sanitised
 *     (Phase 89 633C, §5b row 19); no mark-read (decision 7)
 *
 * Phase 88 628G: reads the boot-seed rows only (INV-0001 SENT, the shared Statement of Account on
 * Website Redesign — compose/seed/seed.sh Steps 9 and 11); nothing here changes them. Day 75
 * creates a run-unique court date and prescription and removes them again (helpers/firm-api.ts).
 *
 * Day 45 payment (scenario 45.4–45.8, E.9) is the Payments mandate exemption (CLAUDE.md §6): the
 * e2e profile has no PSP — MockPaymentGateway is @Profile({"local","dev","keycloak","test"})
 * (integration/payment/MockPaymentGateway.java:34) and MockPaymentController
 * @Profile({"local","dev","keycloak"}) (:40) — so a SENT fee note has no paymentUrl and the portal
 * asks the client to contact the firm. The test asserts exactly that state.
 *
 * Strict: nothing is soft-asserted or skipped at runtime; SKIP_PORTAL_BASELINES=true is the only
 * opt-out.
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

    // The button is "Download <file name>"; SEED.statementFile is anchored at both ends.
    const download = page.getByRole("button", {
      name: new RegExp(`^Download ${SEED.statementFile.source.slice(1)}`),
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

  // ── Checkpoint 9 — Day 75: court date + prescription on Diary dates ────
  // Phase 89 633C closes §5b row 19 (633B built the sync — ADR-363). The firm schedules a hearing
  // and starts a prescription on Website Redesign (owned by Acme) with firm-only judge / notes text;
  // the client sees the sanitised rows on /deadlines and never that text. There is no mark-read
  // control (Phase 89 decision 7). Cancel / interrupt DELETE the portal rows (B4), and the finally
  // block guarantees it, so /deadlines is empty again for Day 7 and the responsive suite. The
  // default window is today..+60 days (deadlines/page.tsx defaultWindow), so both dates sit inside.
  test("Day 75 [legal-za]: Client sees the court date and the prescription on Diary dates, sanitised, until the firm removes them", async ({
    page,
  }) => {
    const jwt = (await getPortalJwt(PORTAL_EMAIL))!;
    const today = todayInSA();
    const courtName = `Johannesburg High Court ${RUN_ID}`;
    const courtReference = `JHB-633C-${RUN_ID}`;
    const hearingLabel = `Hearing — ${courtName}`;
    const FIRM_ONLY_JUDGE = `Judge FIRM-ONLY-${RUN_ID}`;
    const FIRM_ONLY_NOTES = `FIRM-ONLY-NOTES ${RUN_ID}`;

    const hearing = await scheduleAcmeCourtDate({
      courtName,
      courtReference,
      scheduledDate: addDaysIso(today, 14),
      judgeMagistrate: FIRM_ONLY_JUDGE,
      description: FIRM_ONLY_NOTES,
    });
    let hearingLive = true;
    let tracker: { id: string } | null = null;
    try {
      tracker = await startAcmePrescription({
        due: addDaysIso(today, 21),
        notes: FIRM_ONLY_NOTES,
      });
      const trackerId = tracker.id;
      const ids = [hearing.id, trackerId].sort();
      const liveIds = async () =>
        (await portalApiGet<{ id: string }[]>("/portal/deadlines", jwt))
          .map((d) => d.id)
          .filter((id) => ids.includes(id))
          .sort();
      await expect.poll(liveIds).toEqual(ids);

      await page.goto("/deadlines");
      await expect(
        page.getByRole("heading", { level: 1, name: "Diary dates" }),
      ).toBeVisible();

      // 75.2–75.3 — the hearing: "Hearing — <court>", the court reference, no judge / notes.
      const hearingRow = page.getByTestId(
        `deadline-row-COURT_DATE-${hearing.id}`,
      );
      await expect(hearingRow).toHaveAccessibleName(
        `View details for ${hearingLabel}`,
      );
      await expect(hearingRow).toContainText("Court date");
      await hearingRow.click();
      const hearingPanel = page.getByRole("dialog", { name: hearingLabel });
      await expect(hearingPanel).toBeVisible();
      await expect(hearingPanel).toContainText(courtReference);
      await expect(hearingPanel).toContainText("From: Court date");
      await expect(hearingPanel).not.toContainText(FIRM_ONLY_JUDGE);
      await expect(hearingPanel).not.toContainText(FIRM_ONLY_NOTES);
      // Phase 89 decision 7: the portal has no mark-read control.
      await expect(
        hearingPanel.getByRole("button", { name: /mark (as )?read/i }),
      ).toHaveCount(0);
      // A captured frame for the walker, not a committed baseline (run-unique court name and
      // today-relative dates — decision 7 / P-E.2b).
      await page.screenshot({
        path: test.info().outputPath("day-75-deadline-detail.png"),
        fullPage: true,
      });
      await hearingPanel.getByRole("button", { name: "Close panel" }).click();

      // The prescription: fixed label, humanised type, no notes.
      const trackerRow = page.getByTestId(
        `deadline-row-PRESCRIPTION_TRACKER-${trackerId}`,
      );
      await expect(trackerRow).toHaveAccessibleName(
        "View details for Prescription deadline",
      );
      await expect(trackerRow).toContainText("Prescription");
      await trackerRow.click();
      const trackerPanel = page.getByRole("dialog", {
        name: "Prescription deadline",
      });
      await expect(trackerPanel).toBeVisible();
      await expect(trackerPanel).toContainText("General 3 years");
      await expect(trackerPanel).toContainText("From: Prescription");
      await expect(trackerPanel).not.toContainText(FIRM_ONLY_NOTES);
      await trackerPanel.getByRole("button", { name: "Close panel" }).click();

      // Cancelled / interrupted → gone from the client's Diary dates.
      await cancelCourtDate(hearing.id, `633C e2e ${RUN_ID}`);
      hearingLive = false;
      await interruptPrescription(trackerId, today, `633C e2e ${RUN_ID}`);
      tracker = null;
      await expect.poll(liveIds).toEqual([]);
      await page.reload();
      await expect(
        page.getByRole("heading", { level: 1, name: "Diary dates" }),
      ).toBeVisible();
      await expect(
        page.getByTestId(`deadline-row-COURT_DATE-${hearing.id}`),
      ).toHaveCount(0);
      await expect(
        page.getByTestId(`deadline-row-PRESCRIPTION_TRACKER-${trackerId}`),
      ).toHaveCount(0);
    } finally {
      // Cleanup, not an assertion: never leave a portal row behind for Day 7 / responsive.
      if (hearingLive)
        await cancelCourtDate(hearing.id, `633C e2e cleanup ${RUN_ID}`);
      if (tracker)
        await interruptPrescription(
          tracker.id,
          today,
          `633C e2e cleanup ${RUN_ID}`,
        );
    }
  });
});
