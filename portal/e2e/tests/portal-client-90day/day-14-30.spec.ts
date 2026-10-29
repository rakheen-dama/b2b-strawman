import { test, expect } from "@playwright/test";
import { getPortalJwt, loginAsPortalContact } from "../../helpers/auth";
import {
  RUN_ID,
  SEED,
  recordAcmeDeposit,
  websiteRedesignId,
} from "../../helpers/firm-api";
import { listMessageIds, waitForNewEmail } from "../../helpers/mailpit";

/**
 * Portal client-POV 90-day lifecycle: Day 14 → Day 30
 * (checkpoint 5 of `qa/testplan/demos/portal-client-90day-keycloak.md`).
 *   - Day 21: trust-deposit nudge email → the client views the balance and the deposit on /trust
 *
 * Phase 88 628G (R-2): the boot seed carries no Acme trust deposit (628F R-10), so `beforeAll`
 * records a RUN-UNIQUE deposit for Acme on Website Redesign through the firm API (RECORDED at once,
 * synced to the portal after commit). The test asserts the row by its run-unique description, never
 * a balance total (balances accumulate across runs). On a shared stack run this suite after the
 * legal lifecycle suite: a deposit gives Acme a trust balance, which a later Day 60 interest run
 * would include.
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
    const jwt = await getPortalJwt(PORTAL_EMAIL);
    expect(
      jwt,
      `portal JWT for ${PORTAL_EMAIL}: stack up, contact seeded, and neither magic-link cap hit ` +
        `(3 tokens per contact per 5 min, DB; 3 request-link calls per email per 15 min, in-memory)`,
    ).toBeTruthy();
    await loginAsPortalContact(page, jwt!);
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

    // /trust itself lands on the same matter: Acme has exactly one trust matter.
    await page.goto("/trust");
    await expect(page).toHaveURL(new RegExp(`/trust/${matterId}$`));
    await expect(
      page
        .getByRole("table", { name: "Trust transactions" })
        .getByRole("row")
        .filter({ hasText: DESCRIPTION }),
    ).toBeVisible();

    await expect(page).toHaveScreenshot("day-21-trust-balance.png", {
      fullPage: true,
      animations: "disabled",
    });
  });
});
