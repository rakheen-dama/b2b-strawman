import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import {
  RECEIPT_PAYMENT_REFERENCE,
  RUN_ID,
  SEED,
  ensurePartPaidAcmeFeeNote,
  portalApiGet,
  recordAcmeDeposit,
  websiteRedesignId,
} from "../../helpers/firm-api";
import { listMessageIds, waitForNewEmail } from "../../helpers/mailpit";

/**
 * Portal client-POV 90-day lifecycle: Day 14 → Day 30
 * (checkpoint 5 of `qa/testplan/demos/portal-client-90day-keycloak.md`).
 *   - Day 21: trust-deposit nudge email → the client views the balance and the deposit on /trust
 *   - Day 30: the client downloads the receipt for a part payment on a fee note (Phase 89 632C)
 *
 * Phase 88 628G (R-2): the boot seed carries no Acme trust deposit (628F R-10), so `beforeAll`
 * records a RUN-UNIQUE deposit for Acme on Website Redesign through the firm API (RECORDED at once,
 * synced to the portal after commit). The test asserts the row by its run-unique description, never
 * a balance total (balances accumulate across runs). On a shared stack run this suite after the
 * legal lifecycle suite: a deposit gives Acme a trust balance, which a later Day 60 interest run
 * would include.
 *
 * Phase 89 632C (OQ #10, G88-F32): the seed's INV-0001 stays SENT (Day 45 reads it), so the receipt
 * case uses ONE stable Acme fee note found-or-created by ensurePartPaidAcmeFeeNote() (notes marker,
 * not RUN_ID): 2 000.00, part-paid 500.00 by EFT with reference RCPT-632C through the firm API. A
 * re-run reuses it — the stack gains exactly one extra fee note ever.
 *
 * Strict: nothing is soft-asserted or skipped at runtime; SKIP_PORTAL_BASELINES=true is the only
 * opt-out.
 */

const PORTAL_EMAIL = process.env.PORTAL_CONTACT_EMAIL || SEED.contactEmail;
const SKIP = process.env.SKIP_PORTAL_BASELINES === "true";

const DEPOSIT_AMOUNT = 1500;
const DESCRIPTION = `Portal Day 21 deposit ${RUN_ID}`;
const TRUST_EMAIL_SUBJECT = `${SEED.orgName}: Trust account activity`;

test.describe
  .serial("Day 14–30 — Portal Client Lifecycle (Checkpoint 5)", () => {
  let matterId: string;
  let mailBaseline: string[];
  let jwt: string;

  test.beforeAll(async () => {
    test.skip(
      SKIP,
      "SKIP_PORTAL_BASELINES=true — portal e2e disabled (no seeded stack).",
    );
    matterId = await websiteRedesignId();
    // Mailbox baseline first: the nudge for THIS deposit is the one new message with its subject.
    mailBaseline = await listMessageIds(PORTAL_EMAIL);
    const deposit = await recordAcmeDeposit(
      DEPOSIT_AMOUNT,
      DESCRIPTION,
      `E2E-PORTAL-${RUN_ID}`,
    );
    expect(deposit.status).toBe("RECORDED");
  });

  test.beforeEach(async ({ page }) => {
    const sessionJwt = await getPortalJwt(PORTAL_EMAIL);
    expect(
      sessionJwt,
      `portal JWT for ${PORTAL_EMAIL}: stack up, contact seeded, and neither magic-link cap hit ` +
        `(3 tokens per contact per 5 min, DB; 3 request-link calls per email per 15 min, in-memory)`,
    ).toBeTruthy();
    jwt = sessionJwt!;
    await loginAsPortalContact(page, jwt);
  });

  // ── Checkpoint 5 — Day 21: trust balance (legal-za) ────────────────────
  test("Day 21 [legal-za]: Client follows the trust-activity email to the balance and the new deposit", async ({
    page,
  }) => {
    // 21.1–21.2 — the deposit emails the contact; its button links the matter's trust page.
    // No token is minted (an authenticated route with ?orgId=), so the replay uses the session.
    const mail = await waitForNewEmail(PORTAL_EMAIL, {
      exclude: mailBaseline,
      subject: TRUST_EMAIL_SUBJECT,
    });
    const href = mail.HTML.match(/href="([^"]*\/trust\/[^"]+)"/)?.[1]?.replace(
      /&amp;/g,
      "&",
    );
    expect(href, `trust link in "${mail.Subject}"`).toBeTruthy();
    const link = new URL(href!);
    expect(link.pathname).toBe(`/trust/${matterId}`);
    expect(link.searchParams.get("orgId")).toBe(SEED.org);
    await page.goto(link.pathname + link.search);

    // 21.3–21.4 — the balance card and the deposit row (by its run-unique description).
    await expect(page).toHaveURL(new RegExp(`/trust/${matterId}(\\?|$)`));
    await expect(page.getByLabel("Current balance")).toBeVisible();
    const row = page
      .getByRole("table", { name: "Trust transactions" })
      .getByRole("row")
      .filter({ hasText: DESCRIPTION });
    await expect(row).toContainText("DEPOSIT");
    await expect(row).toContainText(/1\s?500,00/);

    // /trust reaches the same matter: it redirects when the client has exactly one trust matter
    // (app/(authenticated)/trust/page.tsx), else it lists them. Expect the state the portal API
    // reports — other suites on a shared stack may give Acme more trust matters.
    const { matters } = await portalApiGet<{
      matters: { matterId: string }[];
    }>("/portal/trust/summary", jwt);
    expect(matters.map((m) => m.matterId)).toContain(matterId);
    await page.goto("/trust");
    if (matters.length === 1) {
      await expect(page).toHaveURL(new RegExp(`/trust/${matterId}$`));
    } else {
      await expect(
        page.getByRole("heading", { level: 1, name: "Trust statement" }),
      ).toBeVisible();
      const matterLink = page.locator(`main a[href="/trust/${matterId}"]`);
      await expect(matterLink).toBeVisible();
      await matterLink.click();
      await expect(page).toHaveURL(new RegExp(`/trust/${matterId}$`));
    }
    await expect(
      page
        .getByRole("table", { name: "Trust transactions" })
        .getByRole("row")
        .filter({ hasText: DESCRIPTION }),
    ).toBeVisible();
    // Statements load independently of the balance: no loading skeleton may remain.
    await expect(page.locator('[data-slot="skeleton"]')).toHaveCount(0);

    // Balance + transactions grow with every run's deposit: masked (asserted above). By label:
    // below md the transactions render as a <ul aria-label="Trust transactions">.
    await expect(page).toHaveScreenshot("day-21-trust-balance.png", {
      fullPage: true,
      animations: "disabled",
      mask: [
        page.getByLabel("Current balance"),
        page.getByLabel("Trust transactions"),
      ],
    });
  });

  // ── Day 30: payment receipt (Phase 89 632C, G88-F32) ───────────────────
  test("Day 30 [legal-za]: Client downloads the receipt for a part payment on a fee note", async ({
    page,
    request,
  }) => {
    const feeNote = await ensurePartPaidAcmeFeeNote();

    // The portal read model syncs the status after commit; the receipt is written after commit.
    await expect
      .poll(
        async () =>
          (
            await portalApiGet<{ status: string }>(
              `/portal/invoices/${feeNote.id}`,
              jwt,
            )
          ).status,
      )
      .toBe("PARTIALLY_PAID");
    await expect
      .poll(
        async () =>
          (
            await portalApiGet<
              { reference: string | null; receiptAvailable: boolean }[]
            >(`/portal/invoices/${feeNote.id}/payments`, jwt)
          ).filter(
            (p) =>
              p.reference === RECEIPT_PAYMENT_REFERENCE && p.receiptAvailable,
          ).length,
      )
      .toBe(1);

    // 30.1 — the fee note from the list.
    await page.goto("/invoices");
    await expect(
      page.getByRole("heading", { level: 1, name: "Fee notes" }),
    ).toBeVisible();
    await page
      .getByRole("table", { name: "Fee Note list" })
      .getByRole("link", { name: feeNote.invoiceNumber, exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        level: 1,
        name: feeNote.invoiceNumber,
        exact: true,
      }),
    ).toBeVisible();
    await expect(
      page.getByText("Partially paid", { exact: true }),
    ).toBeVisible();

    // 30.2 — the payment row (by its stable reference).
    const row = page
      .getByRole("list", { name: "Payments" })
      .getByRole("listitem")
      .filter({ hasText: RECEIPT_PAYMENT_REFERENCE });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText("EFT");
    await expect(row).toContainText(/500,00/);

    // 30.3 — Download receipt → a presigned URL that answers 200 with a PDF.
    const receiptPath = new RegExp(
      `^/portal/invoices/${feeNote.id}/payments/[^/]+/receipt$`,
    );
    const receiptCall = page.waitForResponse(
      (r) => receiptPath.test(new URL(r.url()).pathname) && r.ok(),
    );
    const popup = page.waitForEvent("popup");
    await row.getByRole("button", { name: "Download receipt" }).click();
    const { downloadUrl } = (await (await receiptCall).json()) as {
      downloadUrl: string;
    };
    await (await popup).close();
    const pdf = await request.get(downloadUrl);
    expect(pdf.ok(), `GET receipt presigned URL → ${pdf.status()}`).toBe(true);
    expect((await pdf.body()).subarray(0, 5).toString("latin1")).toBe("%PDF-");

    // 30.4 — the fee-note download is still the fee note, not the receipt (a distinct object).
    const { downloadUrl: feeNoteUrl } = await portalApiGet<{
      downloadUrl: string;
    }>(`/portal/invoices/${feeNote.id}/download`, jwt);
    expect(new URL(feeNoteUrl).pathname).not.toBe(
      new URL(downloadUrl).pathname,
    );
  });
});
