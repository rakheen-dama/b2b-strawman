import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import {
  RUN_ID,
  SEED,
  acmeId,
  firmApiOk,
  portalApiGet,
  sendAcmeInfoRequest,
  websiteRedesignId,
} from "../../helpers/firm-api";

/**
 * Portal client-POV 90-day lifecycle: Day 0 → Day 7
 * (checkpoints 1–3 of `qa/testplan/demos/portal-client-90day-keycloak.md`).
 *   - Day 0: the client lands on /home, sees the pending info request, opens it
 *   - Day 3: the client uploads the requested document and submits the request
 *   - Day 7: the client opens /deadlines (no portal-visible deadline exists on the e2e tenant)
 *
 * Phase 88 628G (R-1): `beforeAll` sends a RUN-UNIQUE information request (one FILE_UPLOAD item) to
 * the Acme contact on Website Redesign through the firm API, and Day 0 / Day 3 drive that request.
 * The seeded REQ-0001 ("Certified ID copy") is read-only for this suite (628F R-5): Day 0 asserts it
 * is listed and never submits it, so E2E-DEFAULT's information-request specs keep their SENT row.
 * The send mints one magic-link token for the contact (cap 3 per contact per 5 min; a capped mint
 * is caught server-side and the request is still SENT).
 *
 * Stack: the e2e stack (bash compose/scripts/e2e-up.sh) with its boot seed. Run with
 * `pnpm test:e2e:portal-client-90day` (or `pnpm test:e2e:portal` together with the responsive
 * baselines — one globalSetup exchange for both). Strict: nothing is soft-asserted or skipped at
 * runtime; SKIP_PORTAL_BASELINES=true is the only opt-out.
 */

const PORTAL_EMAIL = process.env.PORTAL_CONTACT_EMAIL || SEED.contactEmail;
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

const ITEM = `${SEED.infoRequestItem} ${RUN_ID}`;

type PortalRequestSummary = {
  id: string;
  requestNumber: string;
  status: string;
  submittedItems: number;
  totalItems: number;
};

/** Same rule as the Home "Pending info requests" card (app/(authenticated)/home/page.tsx). */
const isPending = (r: PortalRequestSummary) =>
  (r.status === "SENT" || r.status === "IN_PROGRESS") &&
  r.submittedItems < r.totalItems;

async function portalJwt(): Promise<string> {
  const jwt = await getPortalJwt(PORTAL_EMAIL);
  expect(
    jwt,
    `portal JWT for ${PORTAL_EMAIL}: stack up, contact seeded, and neither magic-link cap hit ` +
      `(3 tokens per contact per 5 min, DB; 3 request-link calls per email per 15 min, in-memory)`,
  ).toBeTruthy();
  return jwt!;
}

test.describe
  .serial("Day 0–7 — Portal Client Lifecycle (Checkpoints 1–3)", () => {
  let requestId: string;
  let requestNumber: string;
  let pendingBefore: number;
  let seedRequest: { id: string; requestNumber: string };

  test.beforeAll(async () => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — portal e2e disabled (no seeded stack).",
    );
    // The seeded request (read-only): found by its item name, as the seed recognises it.
    const firmRequests = await firmApiOk<
      {
        id: string;
        requestNumber: string;
        status: string;
        items: { name: string }[];
      }[]
    >(
      `/api/information-requests?customerId=${await acmeId()}&projectId=${await websiteRedesignId()}`,
    );
    const seeded = firmRequests.find(
      (r) =>
        r.status === "SENT" &&
        r.items.some((i) => i.name === SEED.infoRequestItem),
    );
    expect(
      seeded,
      `seeded SENT request "${SEED.infoRequestItem}" — is the stack seeded?`,
    ).toBeDefined();
    seedRequest = seeded!;

    ({ id: requestId, requestNumber } = await sendAcmeInfoRequest(ITEM));

    // The portal read model is fed by the send's events: wait until the new request is listed.
    const jwt = await portalJwt();
    await expect
      .poll(
        async () =>
          (
            await portalApiGet<PortalRequestSummary[]>("/portal/requests", jwt)
          ).some((r) => r.id === requestId),
        { timeout: 15_000 },
      )
      .toBe(true);
    pendingBefore = (
      await portalApiGet<PortalRequestSummary[]>("/portal/requests", jwt)
    ).filter(isPending).length;
  });

  test.beforeEach(async ({ page }) => {
    await loginAsPortalContact(page, await portalJwt());
  });

  // ── Checkpoint 1 — Day 0: /home → pending info requests ───────────────
  test("Day 0: Client lands on /home and sees the pending info requests and the sent fee note", async ({
    page,
  }) => {
    await page.goto("/home");
    await expect(
      page.getByRole("heading", { level: 1, name: "Home" }),
    ).toBeVisible();
    const pendingCard = page.getByRole("link", {
      name: /Pending info requests/,
    });
    await expect(pendingCard.locator("p")).toHaveText(String(pendingBefore));
    // "Recent fee notes" lists the seeded SENT fee note by number.
    await expect(
      page.getByRole("link", { name: SEED.feeNoteSentNumber, exact: true }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot("day-00-home-landing.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  test("Day 0: Client opens the new info request from Home and sees its upload slot", async ({
    page,
  }) => {
    await page.goto("/home");
    await page.getByRole("link", { name: /Pending info requests/ }).click();
    await expect(page).toHaveURL(/\/requests$/);
    await expect(
      page.getByRole("heading", { level: 1, name: "Documents & requests" }),
    ).toBeVisible();
    // The seeded request is listed for the client too (read-only: never opened for submit).
    await expect(
      page.locator(`a[href="/requests/${seedRequest.id}"]`),
    ).toContainText(seedRequest.requestNumber);

    const row = page.locator(`a[href="/requests/${requestId}"]`);
    await expect(row).toContainText(requestNumber);
    await expect(row).toContainText("0/1 submitted");
    await row.click();
    await expect(page).toHaveURL(new RegExp(`/requests/${requestId}$`));

    await expect(
      page.getByRole("heading", { level: 1, name: SEED.matter }),
    ).toBeVisible();
    await expect(page.getByText(requestNumber, { exact: true })).toBeVisible();
    await expect(page.getByText("0/1 submitted • status SENT")).toBeVisible();
    await expect(page.getByLabel(`Upload file for ${ITEM}`)).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Upload and submit" }),
    ).toBeDisabled();

    await expect(page).toHaveScreenshot("day-00-info-request-detail.png", {
      fullPage: true,
      animations: "disabled",
    });
  });

  // ── Checkpoint 2 — Day 3: upload + submit info request ─────────────────
  test("Day 3: Client uploads the document and submits the info request", async ({
    page,
  }) => {
    await page.goto(`/requests/${requestId}`);
    await expect(
      page.getByRole("heading", { level: 1, name: SEED.matter }),
    ).toBeVisible();
    await page.getByLabel(`Upload file for ${ITEM}`).setInputFiles({
      name: "id-copy.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"),
    });
    const submit = page.getByRole("button", { name: "Upload and submit" });
    await expect(submit).toBeEnabled();
    await submit.click();

    await expect(page.getByText("Submitted — status: SUBMITTED")).toBeVisible({
      timeout: 15_000,
    });
    await expect(
      page.getByText("1/1 submitted • status IN_PROGRESS"),
    ).toBeVisible();

    await expect(page).toHaveScreenshot("day-03-info-request-submitted.png", {
      fullPage: true,
      animations: "disabled",
    });

    // 3.5 — the request is no longer pending on Home.
    await page.goto("/home");
    await expect(
      page.getByRole("link", { name: /Pending info requests/ }).locator("p"),
    ).toHaveText(String(pendingBefore - 1));
  });

  // ── Checkpoint 3 — Day 7: /deadlines ───────────────────────────────────
  // No portal-visible deadline can be produced on the e2e tenant: the COURT_DATE / PRESCRIPTION /
  // FILING portal sync is dormant (customerbackend/service/DeadlinePortalSyncService.java:36-44)
  // and the custom-field source fires only from the 06:00 scanner. The digest CTA goes to /home
  // (templates/email/portal-weekly-digest.html:72). So Day 7 checks what the client gets today.
  test("Day 7: Diary dates page renders for the client (no portal-visible deadlines on the e2e tenant)", async ({
    page,
  }) => {
    await page.goto("/deadlines");
    await expect(
      page.getByRole("heading", { level: 1, name: "Diary dates" }),
    ).toBeVisible();
    await expect(page.getByTestId("deadline-list-empty")).toContainText(
      "No deadlines in this view",
    );

    await expect(page).toHaveScreenshot("day-07-deadlines-list.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
